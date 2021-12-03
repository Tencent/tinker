/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader;

import static com.tencent.tinker.loader.shareutil.ShareConstants.ODEX_SUFFIX;
import static com.tencent.tinker.loader.shareutil.ShareConstants.VDEX_SUFFIX;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.os.SystemClock;

import com.tencent.tinker.loader.shareutil.ShareElfFile;
import com.tencent.tinker.loader.shareutil.ShareFileLockHelper;
import com.tencent.tinker.loader.shareutil.ShareOatUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import dalvik.system.DexFile;


/**
 * Created by tangyinsheng on 2016/11/15.
 */

public final class TinkerDexOptimizer {
    private static final String TAG = "Tinker.ParallelDex";

    private static final String INTERPRET_LOCK_FILE_NAME = "interpret.lock";

    /**
     * Optimize (trigger dexopt or dex2oat) dexes.
     *
     * @param dexFiles
     * @param optimizedDir
     * @param cb
     * @return If all dexes are optimized successfully, return true. Otherwise return false.
     */
    public static boolean optimizeAll(Context context, Collection<File> dexFiles, File optimizedDir,
                                      boolean useDLC, ResultCallback cb) {
        return optimizeAll(context, dexFiles, optimizedDir, false, useDLC, null, cb);
    }

    public static boolean optimizeAll(Context context, Collection<File> dexFiles, File optimizedDir,
                                      boolean useInterpretMode, boolean useDLC,
                                      String targetISA, ResultCallback cb) {
        ArrayList<File> sortList = new ArrayList<>(dexFiles);
        // sort input dexFiles with its file length in reverse order.
        Collections.sort(sortList, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                final long lhsSize = lhs.length();
                final long rhsSize = rhs.length();
                if (lhsSize < rhsSize) {
                    return 1;
                } else if (lhsSize == rhsSize) {
                    return 0;
                } else {
                    return -1;
                }
            }
        });
        for (File dexFile : sortList) {
            OptimizeWorker worker = new OptimizeWorker(context, dexFile, optimizedDir, useInterpretMode,
                  useDLC, targetISA, cb);
            if (!worker.run()) {
                return false;
            }
        }
        return true;
    }

    public interface ResultCallback {
        void onStart(File dexFile, File optimizedDir);

        void onSuccess(File dexFile, File optimizedDir, File optimizedFile);

        void onFailed(File dexFile, File optimizedDir, Throwable thr);
    }

    private static class OptimizeWorker {
        private static ClassLoader patchClassLoaderStrongRef = null;
        private final String targetISA;
        private final Context context;
        private final File dexFile;
        private final File optimizedDir;
        private final boolean useInterpretMode;
        private final boolean useDLC;
        private final ResultCallback callback;

        OptimizeWorker(Context context, File dexFile, File optimizedDir, boolean useInterpretMode,
                       boolean useDLC, String targetISA, ResultCallback cb) {
            this.context = context;
            this.dexFile = dexFile;
            this.optimizedDir = optimizedDir;
            this.useInterpretMode = useInterpretMode;
            this.useDLC = useDLC;
            this.callback = cb;
            this.targetISA = targetISA;
        }

        boolean run() {
            try {
                if (!SharePatchFileUtil.isLegalFile(dexFile)) {
                    if (callback != null) {
                        callback.onFailed(dexFile, optimizedDir,
                                new IOException("dex file " + dexFile.getAbsolutePath() + " is not exist!"));
                        return false;
                    }
                }
                if (callback != null) {
                    callback.onStart(dexFile, optimizedDir);
                }
                String optimizedPath = SharePatchFileUtil.optimizedPathFor(this.dexFile, this.optimizedDir);
                if (!ShareTinkerInternals.isArkHotRuning()) {
                    if (useInterpretMode) {
                        interpretDex2Oat(dexFile.getAbsolutePath(), optimizedPath, targetISA);
                    } else if (Build.VERSION.SDK_INT >= 26
                            || (Build.VERSION.SDK_INT >= 25 && Build.VERSION.PREVIEW_SDK_INT != 0)) {
                        patchClassLoaderStrongRef = NewClassLoaderInjector.triggerDex2Oat(context, optimizedDir,
                                useDLC, dexFile.getAbsolutePath());
                        if (Build.VERSION.SDK_INT < 31 && !(Build.VERSION.SDK_INT == 30 && Build.VERSION.PREVIEW_SDK_INT != 0)) {
                            // Android Q is significantly slowed down by Fallback Dex Loading procedure, so we
                            // trigger background dexopt to generate executable odex here.
                            triggerPMDexOptOnDemand(context, dexFile.getAbsolutePath(), optimizedPath);
                        } else {
                            triggerPMDexOptOnDemand(context, dexFile.getAbsolutePath(), optimizedPath);
                            final String vdexPath = optimizedPath.substring(0, optimizedPath.lastIndexOf(ODEX_SUFFIX)) + VDEX_SUFFIX;
                            waitUntilVdexGeneratedOrTimeout(context, vdexPath);
                        }
                    } else {
                        DexFile.loadDex(dexFile.getAbsolutePath(), optimizedPath, 0);
                    }
                }
                if (callback != null) {
                    callback.onSuccess(dexFile, optimizedDir, new File(optimizedPath));
                }
            } catch (final Throwable e) {
                ShareTinkerLog.e(TAG, "Failed to optimize dex: " + dexFile.getAbsolutePath(), e);
                if (callback != null) {
                    callback.onFailed(dexFile, optimizedDir, e);
                    return false;
                }
            }
            return true;
        }
    }

    private static void triggerPMDexOptOnDemand(Context context, String dexPath, String oatPath) {
        if (Build.VERSION.SDK_INT < 29) {
            // Only do this trick on Android Q, R and newer devices.
            ShareTinkerLog.w(TAG, "[+] Not API 29, 30 and newer device, skip triggering dexopt.");
            return;
        }

        ShareTinkerLog.i(TAG, "[+] Hit target device, do dexopt logic now.");

        try {
            final File oatFile = new File(oatPath);
            if (oatFile.exists()) {
                ShareTinkerLog.i(TAG, "[+] Remove existed oat file: %s", oatPath);
                oatFile.delete();
            } else {
                try {
                    final File oatDir = oatFile.getParentFile();
                    if (!oatDir.exists()) {
                        oatDir.mkdirs();
                    }
                    oatFile.createNewFile();
                } catch (Throwable thr) {
                    ShareTinkerLog.printErrStackTrace(TAG, thr, "[-] Fail to pre-create oat file.");
                }
            }
            int waitTimes = 0;
            while (true) {
                try {
                    reconcileSecondaryDexFiles(context);
                } catch (Throwable thr) {
                    ShareTinkerLog.printErrStackTrace(TAG, thr, "[-] Fail to call reconcileSecondaryDexFiles.");
                }
                if (ShareTinkerInternals.isNewerOrEqualThanVersion(31 /* Android S */, true)) {
                    // registerDexModule will force classloader context change into VariableClassLoaderContext,
                    // which makes ART skip caching class verification info on Android Q and R. The running
                    // performance of patched app will have no improvement.
                    try {
                        registerDexModule(context, dexPath);
                    } catch (Throwable thr) {
                        ShareTinkerLog.printErrStackTrace(TAG, thr, "[-] Fail to call registerDexModule.");
                    }
                    if (oatFile.exists()) {
                        ShareTinkerLog.i(TAG, "[+] Dexopt was triggered by registerDexModule successfully.");
                        return;
                    }
                }
                try {
                    performDexOptSecondary(context);
                } catch (Throwable thr) {
                    ShareTinkerLog.printErrStackTrace(TAG, thr, "[-] Fail to call performDexOptSecondary.");
                }
                if (oatFile.exists()) {
                    ShareTinkerLog.i(TAG, "[+] Dexopt was triggered by performDexOptSecondary successfully.");
                    return;
                }
                if (waitTimes >= 3) {
                    throw new IllegalStateException("Dexopt was triggered, but no odex file was generated.");
                }
                // Take a rest. And hope any asynchronous mechanism may generate odex/vdex we need.
                ShareTinkerLog.w(TAG, "[!] No odex file was generated, wait for retry.");
                ++waitTimes;
                SystemClock.sleep(5000);
            }
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "[-] Fail to call triggerPMDexOptAsyncOnDemand.");
        }
    }

    private static void registerDexModule(Context context, String dexPath) throws IllegalStateException {
        final PackageManager synchronizedPM = getSynchronizedPackageManager(context);
        try {
            final Class<?> dexModuleRegisterCallbackClazz = Class
                    .forName("android.content.pm.PackageManager$DexModuleRegisterCallback");
            ShareReflectUtil
                    .findMethod(synchronizedPM, "registerDexModule", String.class, dexModuleRegisterCallbackClazz)
                    .invoke(synchronizedPM, dexPath, null);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getTargetException());
        } catch (Throwable thr) {
            if (thr instanceof IllegalStateException) {
                throw (IllegalStateException) thr;
            } else {
                throw new IllegalStateException(thr);
            }
        }
    }

    private static void reconcileSecondaryDexFiles(Context context) throws IllegalStateException {
        final String[] args = {
                "reconcile-secondary-dex-files",
                context.getPackageName()
        };
        executePMSShellCommand(context, args);
    }

    private static void performDexOptSecondary(Context context) throws IllegalStateException {
        /*
         * Use 'speed-profile' as compile filter can take advantage bring by profiling jit and '.art' cache.
         * Meanwhile dex2oat can still be done in almost the same time as using 'quicken'.
         * Thanks to Chen MinSheng for his advice.
         */
        final String[] args = {
                "compile",
                "-f",
                "--secondary-dex",
                "-m", ShareTinkerInternals.isNewerOrEqualThanVersion(31 /* Android S */, true)
                        ? "verify" : "speed-profile",
                context.getPackageName()
        };
        executePMSShellCommand(context, args);
    }

    private static final IBinder[] sPMSBinderProxy = { null };

    private static IBinder getPMSBinderProxy(Context context) throws IllegalStateException {
        synchronized (sPMSBinderProxy) {
            final IBinder cachedPMSBinderProxy = sPMSBinderProxy[0];
            if (cachedPMSBinderProxy != null && cachedPMSBinderProxy.isBinderAlive()) {
                return cachedPMSBinderProxy;
            }
            try {
                final Class<?> smClazz = Class.forName("android.os.ServiceManager");
                final Method getServiceMethod = ShareReflectUtil.findMethod(smClazz, "getService", String.class);
                sPMSBinderProxy[0] = (IBinder) getServiceMethod.invoke(null, "package");
                return sPMSBinderProxy[0];
            } catch (Throwable thr) {
                if (thr instanceof InvocationTargetException) {
                    throw new IllegalStateException(((InvocationTargetException) thr).getTargetException());
                } else {
                    throw new IllegalStateException(thr);
                }
            }
        }
    }

    private static final PackageManager[] sSynchronizedPMCache = { null };

    private static final PackageManager getSynchronizedPackageManager(Context context) throws IllegalStateException {
        synchronized (sSynchronizedPMCache) {
            try {
                if (sSynchronizedPMCache[0] != null) {
                    synchronized (sPMSBinderProxy) {
                        if (sPMSBinderProxy[0] != null && sPMSBinderProxy[0].isBinderAlive()) {
                            return sSynchronizedPMCache[0];
                        }
                    }
                }
                final IBinder pmsBinderProxy = getPMSBinderProxy(context);
                final IBinder syncPMSBinderProxy = (IBinder) Proxy.newProxyInstance(
                        context.getClassLoader(),
                        pmsBinderProxy.getClass().getInterfaces(),
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                if ("transact".equals(method.getName())) {
                                    // FLAG_ONEWAY => NONE.
                                    args[3] = 0;
                                }
                                return method.invoke(pmsBinderProxy, args);
                            }
                        }
                );
                final Class<?> pmsStubClazz = Class.forName("android.content.pm.IPackageManager$Stub");
                final Object pmsStubProxy = ShareReflectUtil
                        .findMethod(pmsStubClazz, "asInterface", IBinder.class)
                        .invoke(null, syncPMSBinderProxy);
                final Class<?> appPMClazz = Class.forName("android.app.ApplicationPackageManager");
                final Object contextImpl = (context instanceof ContextWrapper)
                        ? ((ContextWrapper) context).getBaseContext() : context;
                final Class<?> pmsItfClazz = Class.forName("android.content.pm.IPackageManager");
                final PackageManager appPM = (PackageManager) ShareReflectUtil
                        .findConstructor(appPMClazz, contextImpl.getClass(), pmsItfClazz)
                        .newInstance(contextImpl, pmsStubProxy);
                sSynchronizedPMCache[0] = appPM;
                return appPM;
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e.getTargetException());
            } catch (Throwable thr) {
                if (thr instanceof IllegalStateException) {
                    throw (IllegalStateException) thr;
                } else {
                    throw new IllegalStateException(thr);
                }
            }
        }
    }

    private static final int SHELL_COMMAND_TRANSACTION = ('_' << 24) | ('C' << 16) | ('M' << 8) | 'D';
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final ResultReceiver sEmptyResultReceiver = new ResultReceiver(sHandler);

    /**
     * Clever way to avoid hacking an unstable binder transaction code of PMS.
     * Credit: https://mp.weixin.qq.com/s/5kwU-84TbsO3Tk5QDzNKwA
     */
    private static void executePMSShellCommand(Context context, String[] args) throws IllegalStateException {
        final IBinder pmsBinderProxy = getPMSBinderProxy(context);
        Parcel data = null;
        Parcel reply = null;
        long lastIdentity = Binder.clearCallingIdentity();
        try {
            ShareTinkerLog.i(TAG, "[+] Start trigger secondary dexopt.");
            data = Parcel.obtain();
            reply = Parcel.obtain();
            data.writeFileDescriptor(FileDescriptor.in);
            data.writeFileDescriptor(FileDescriptor.out);
            data.writeFileDescriptor(FileDescriptor.err);
            data.writeStringArray(args);
            data.writeStrongBinder(null /* ShellCallback */);
            sEmptyResultReceiver.writeToParcel(data, 0);
            pmsBinderProxy.transact(SHELL_COMMAND_TRANSACTION, data, reply, 0);
            reply.readException();
            ShareTinkerLog.i(TAG, "[+] Secondary dexopt done.");
        } catch (Throwable thr) {
            throw new IllegalStateException("Failure on triggering secondary dexopt", thr);
        } finally {
            if (reply != null) {
                reply.recycle();
            }
            if (data != null) {
                data.recycle();
            }
            Binder.restoreCallingIdentity(lastIdentity);
        }
    }

    private static void waitUntilVdexGeneratedOrTimeout(Context context, String vdexPath) {
        final File vdexFile = new File(vdexPath);
        final long[] delaySeq = {1000, 2000, 4000, 8000, 16000, 32000};
        int delaySeqIdx = 0;
        while (!vdexFile.exists() && delaySeqIdx < delaySeq.length) {
            SystemClock.sleep(delaySeq[delaySeqIdx++]);
            ShareTinkerLog.w(TAG, "[!] Vdex %s does not exist after waiting %s time(s), wait again.", vdexPath, delaySeqIdx);
        }
        if (vdexFile.exists()) {
            ShareTinkerLog.i(TAG, "[+] Vdex %s was found.", vdexPath);
        } else {
            ShareTinkerLog.e(TAG, "[-] Vdex %s does not exist after waiting for %s times.", vdexPath, delaySeq.length);
        }
    }

    private static void interpretDex2Oat(String dexFilePath, String oatFilePath, String targetISA) throws IOException {
        // add process lock for interpret mode
        final File oatFile = new File(oatFilePath);
        if (!oatFile.exists()) {
            oatFile.getParentFile().mkdirs();
        }

        File lockFile = new File(oatFile.getParentFile(), INTERPRET_LOCK_FILE_NAME);
        ShareFileLockHelper fileLock = null;
        try {
            fileLock = ShareFileLockHelper.getFileLock(lockFile);

            final List<String> commandAndParams = new ArrayList<>();
            commandAndParams.add("dex2oat");
            // for 7.1.1, duplicate class fix
            if (Build.VERSION.SDK_INT >= 24) {
                commandAndParams.add("--runtime-arg");
                commandAndParams.add("-classpath");
                commandAndParams.add("--runtime-arg");
                commandAndParams.add("&");
            }
            commandAndParams.add("--dex-file=" + dexFilePath);
            commandAndParams.add("--oat-file=" + oatFilePath);
            commandAndParams.add("--instruction-set=" + targetISA);
            if (Build.VERSION.SDK_INT > 25) {
                commandAndParams.add("--compiler-filter=quicken");
            } else {
                commandAndParams.add("--compiler-filter=interpret-only");
            }

            final ProcessBuilder pb = new ProcessBuilder(commandAndParams);
            pb.redirectErrorStream(true);
            final Process dex2oatProcess = pb.start();
            StreamConsumer.consumeInputStream(dex2oatProcess.getInputStream());
            StreamConsumer.consumeInputStream(dex2oatProcess.getErrorStream());
            try {
                final int ret = dex2oatProcess.waitFor();
                if (ret != 0) {
                    throw new IOException("dex2oat works unsuccessfully, exit code: " + ret);
                }
            } catch (InterruptedException e) {
                throw new IOException("dex2oat is interrupted, msg: " + e.getMessage(), e);
            }
        } finally {
            try {
                if (fileLock != null) {
                    fileLock.close();
                }
            } catch (IOException e) {
                ShareTinkerLog.w(TAG, "release interpret Lock error", e);
            }
        }
    }

    private static class StreamConsumer {
        static final Executor STREAM_CONSUMER = Executors.newSingleThreadExecutor();

        static void consumeInputStream(final InputStream is) {
            STREAM_CONSUMER.execute(new Runnable() {
                @Override
                public void run() {
                    if (is == null) {
                        return;
                    }
                    final byte[] buffer = new byte[256];
                    try {
                        while ((is.read(buffer)) > 0) {
                            // To satisfy checkstyle rules.
                        }
                    } catch (IOException ignored) {
                        // Ignored.
                    } finally {
                        try {
                            is.close();
                        } catch (Exception ignored) {
                            // Ignored.
                        }
                    }
                }
            });
        }
    }
}
