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

package com.tencent.tinker.lib.tinker;

import android.content.Context;

import com.tencent.tinker.lib.listener.PatchListener;
import com.tencent.tinker.lib.patch.AbstractPatch;
import com.tencent.tinker.lib.reporter.LoadReporter;
import com.tencent.tinker.lib.reporter.PatchReporter;
import com.tencent.tinker.lib.service.AbstractResultService;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.app.ApplicationLike;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/19.
 */
public class TinkerInstaller {
    private static final String TAG = "Tinker.TinkerInstaller";

    /**
     * install tinker with default config, you must install tinker before you use their api
     * or you can just use {@link TinkerApplicationHelper}'s api
     *
     * @param applicationLike
     */
    public static Tinker install(ApplicationLike applicationLike) {
        Tinker tinker = new Tinker.Builder(applicationLike.getApplication()).build();
        Tinker.create(tinker);
        tinker.install(applicationLike.getTinkerResultIntent());
        return tinker;
    }

    /**
     * install tinker with custom config, you must install tinker before you use their api
     * or you can just use {@link TinkerApplicationHelper}'s api
     *
     * @param applicationLike
     * @param loadReporter
     * @param patchReporter
     * @param listener
     * @param resultServiceClass
     * @param upgradePatchProcessor
     */
    public static Tinker install(ApplicationLike applicationLike, LoadReporter loadReporter, PatchReporter patchReporter,
                               PatchListener listener, Class<? extends AbstractResultService> resultServiceClass,
                               AbstractPatch upgradePatchProcessor) {

        Tinker tinker = new Tinker.Builder(applicationLike.getApplication())
            .tinkerFlags(applicationLike.getTinkerFlags())
            .loadReport(loadReporter)
            .listener(listener)
            .patchReporter(patchReporter)
            .tinkerLoadVerifyFlag(applicationLike.getTinkerLoadVerifyFlag()).build();

        Tinker.create(tinker);
        tinker.install(applicationLike.getTinkerResultIntent(), resultServiceClass, upgradePatchProcessor);
        return tinker;
    }

    /**
     * clean all patch files!
     *
     * @param context
     */
    public static void cleanPatch(Context context) {
        Tinker.with(context).cleanPatch();
    }

    /**
     * new patch file to install, try install them with :patch process
     *
     * @param context
     * @param patchLocation
     */
    public static void onReceiveUpgradePatch(Context context, String patchLocation) {
        Tinker.with(context).getPatchListener().onPatchReceived(patchLocation);
    }

    /**
     * set logIml for TinkerLog
     *
     * @param imp
     */
    public static void setLogIml(TinkerLog.TinkerLogImp imp) {
        TinkerLog.setTinkerLogImp(imp);
    }

    /**
     * sample usage for native library
     *
     * @param context
     * @param relativePath such as lib/armeabi
     * @param libname      for the lib libTest.so, you can pass Test or libTest, or libTest.so
     * @return boolean
     * @throws UnsatisfiedLinkError
     */
    public static boolean loadLibraryFromTinker(Context context, String relativePath, String libname) throws UnsatisfiedLinkError {
        final Tinker tinker = Tinker.with(context);

        libname = libname.startsWith("lib") ? libname : "lib" + libname;
        libname = libname.endsWith(".so") ? libname : libname + ".so";
        String relativeLibPath = relativePath + "/" + libname;

        //TODO we should add cpu abi, and the real path later
        if (tinker.isEnabledForNativeLib() && tinker.isTinkerLoaded()) {
            TinkerLoadResult loadResult = tinker.getTinkerLoadResultIfPresent();
            if (loadResult.libs != null) {
                for (String name : loadResult.libs.keySet()) {
                    if (name.equals(relativeLibPath)) {
                        String patchLibraryPath = loadResult.libraryDirectory + "/" + name;
                        File library = new File(patchLibraryPath);
                        if (library.exists()) {
                            //whether we check md5 when load
                            boolean verifyMd5 = tinker.isTinkerLoadVerify();
                            if (verifyMd5 && !SharePatchFileUtil.verifyFileMd5(library, loadResult.libs.get(name))) {
                                tinker.getLoadReporter().onLoadFileMd5Mismatch(library, ShareConstants.TYPE_LIBRARY);
                            } else {
                                System.load(patchLibraryPath);
                                TinkerLog.i(TAG, "loadLibraryFromTinker success:" + patchLibraryPath);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * you can use TinkerInstaller.loadLibrary replace your System.loadLibrary for auto update library!
     * only support auto load lib/armeabi library from patch.
     * for other library in lib/* or assets,
     * you can load through {@code TinkerInstaller#loadLibraryFromTinker}
     */
    public static void loadArmLibrary(Context context, String libName) {
        if (libName == null || libName.isEmpty() || context == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        Tinker tinker = Tinker.with(context);
        if (tinker.isEnabledForNativeLib()) {
            if (TinkerInstaller.loadLibraryFromTinker(context, "lib/armeabi", libName)) {
                return;
            }

        }
        System.loadLibrary(libName);
    }

    /**
     * you can use TinkerInstaller.loadArmV7Library replace your System.loadLibrary for auto update library!
     * only support auto load lib/armeabi-v7a library from patch.
     * for other library in lib/* or assets,
     * you can load through {@code TinkerInstaller#loadLibraryFromTinker}
     */
    public static void loadArmV7Library(Context context, String libName) {
        if (libName == null || libName.isEmpty() || context == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        Tinker tinker = Tinker.with(context);
        if (tinker.isEnabledForNativeLib()) {
            if (TinkerInstaller.loadLibraryFromTinker(context, "lib/armeabi-v7a", libName)) {
                return;
            }

        }
        System.loadLibrary(libName);
    }


}
