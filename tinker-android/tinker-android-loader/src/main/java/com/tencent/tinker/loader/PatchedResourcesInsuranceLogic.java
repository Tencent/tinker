package com.tencent.tinker.loader;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Message;

import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by tangyinsheng on 2020/5/10.
 * <p>
 * Some situations may cause our resource modification to be ineffective,
 * for example, an APPLICATION_INFO_CHANGED message will reset LoadedApk#mResDir
 * to default value, then a relaunch activity which using tinker resources may
 * throw an Resources$NotFoundException.
 * <p>
 * Monitor and handle them.
 * <p>
 *
 */
public final class PatchedResourcesInsuranceLogic {
    private static final String TAG = "Tinker.PatchedResourcesInsuranceLogic";
    private static final String LAUNCH_ACTIVITY_LIFECYCLE_ITEM_CLASSNAME = "android.app.servertransaction.LaunchActivityItem";

    private static long sStoredPatchedResModifiedTime = 0L;

    public static boolean install(Application application, String newResourcesApkPath) {
        try {
            ShareTinkerLog.i(TAG, "install called.");
            interceptHandler(application, fetchMHObject(application), newResourcesApkPath);
            ShareTinkerLog.i(TAG, "install done.");
            return true;
        } catch (Throwable e) {
            ShareTinkerLog.e(TAG, "AppInfoChangedBlocker start failed.", e);
            return false;
        }
    }

    public static void recordCurrentPatchedResModifiedTime(String patchedResPath) {
        try {
            sStoredPatchedResModifiedTime = new File(patchedResPath).lastModified();
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "Fail to store patched res modified time.");
            sStoredPatchedResModifiedTime = 0L;
        }
    }

    private static boolean isPatchedResModifiedAfterLastLoad(String patchedResPath) {
        long patchedResModifiedTime;
        try {
            patchedResModifiedTime = new File(patchedResPath).lastModified();
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "Fail to get patched res modified time.");
            patchedResModifiedTime = 0L;
        }
        if (patchedResModifiedTime == 0) {
            return false;
        }
        if (patchedResModifiedTime == sStoredPatchedResModifiedTime) {
            return false;
        }
        return true;
    }

    private static Handler fetchMHObject(Context context) throws Exception {
        final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
        final Field mHField = ShareReflectUtil.findField(activityThread, "mH");
        return (Handler) mHField.get(activityThread);
    }

    private static void interceptHandler(Application application, Handler mH, String newResourcesApkPath) throws Exception {
        final Field mCallbackField = ShareReflectUtil.findField(Handler.class, "mCallback");
        final Handler.Callback originCallback = (Handler.Callback) mCallbackField.get(mH);
        if (!(originCallback instanceof HackerCallback)) {
            HackerCallback hackerCallback = new HackerCallback(application, originCallback, mH.getClass(), newResourcesApkPath);
            mCallbackField.set(mH, hackerCallback);
        } else {
            ShareTinkerLog.w(TAG, "Already intercepted, skip rest logic.");
        }
    }

    private static class HackerCallback implements Handler.Callback {
        private final int LAUNCH_ACTIVITY;
        private final int RELAUNCH_ACTIVITY;
        private final int RECEIVER;
        private final int CREATE_SERVICE;
        private final int SERVICE_ARGS;
        private final int BIND_SERVICE;
        private final int INSTALL_PROVIDER;
        private final int EXECUTE_TRANSACTION;

        private final Application mApplication;
        private Method mGetCallbacksMethod = null;
        private boolean mSkipInterceptExecuteTransaction = false;
        private final Handler.Callback mOriginalCallback;
        private final String mNewResourcesApkPath;
        private boolean mAvoidInfiniteLoop = false;

        HackerCallback(Application application, Handler.Callback originalCallback, Class<?> mhClazz, String newResourcesApkPath) {
            mApplication = application;
            mOriginalCallback = originalCallback;
            mNewResourcesApkPath = newResourcesApkPath;
            LAUNCH_ACTIVITY = fetchMessageId(mhClazz, "LAUNCH_ACTIVITY", 100);
            RELAUNCH_ACTIVITY = fetchMessageId(mhClazz, "RELAUNCH_ACTIVITY", 126);
            RECEIVER = fetchMessageId(mhClazz, "RECEIVER", 113);
            CREATE_SERVICE = fetchMessageId(mhClazz, "CREATE_SERVICE", 114);
            SERVICE_ARGS = fetchMessageId(mhClazz, "SERVICE_ARGS", 115);
            BIND_SERVICE = fetchMessageId(mhClazz, "BIND_SERVICE", 121);
            INSTALL_PROVIDER = fetchMessageId(mhClazz, "INSTALL_PROVIDER", 145);
            if (ShareTinkerInternals.isNewerOrEqualThanVersion(28, true)) {
                EXECUTE_TRANSACTION = fetchMessageId(mhClazz, "EXECUTE_TRANSACTION ", 159);
            } else {
                EXECUTE_TRANSACTION = -1;
            }
        }

        private int fetchMessageId(Class<?> hClazz, String name, int defVal) {
            int value;
            try {
                value = ShareReflectUtil.findField(hClazz, name).getInt(null);
            } catch (Throwable e) {
                value = defVal;
            }
            return value;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (mAvoidInfiniteLoop) {
                ShareTinkerLog.w(TAG, "found a loop invocation to handleMessage.");
                return false;
            }
            boolean consume = false;
            if (hackMessage(msg)) {
                consume = true;
            } else if (mOriginalCallback != null) {
                mAvoidInfiniteLoop = true;
                consume = mOriginalCallback.handleMessage(msg);
                mAvoidInfiniteLoop = false;
            }
            return consume;
        }

        @SuppressWarnings("unchecked")
        private boolean hackMessage(Message msg) {
            if (msg.obj instanceof ApplicationInfo) {
                ShareTinkerLog.w(TAG, "intercepted APPLICATION_INFO_CHANGED, update sourceDir and " +
                        "publicSourceDir before dispatching back to system.");
                final ApplicationInfo appInfo = ((ApplicationInfo) msg.obj);
                appInfo.sourceDir = appInfo.publicSourceDir = mNewResourcesApkPath;
                return false;
            }

            if (msg.what == LAUNCH_ACTIVITY ||
                    msg.what == RELAUNCH_ACTIVITY ||
                    msg.what == RECEIVER ||
                    msg.what == CREATE_SERVICE ||
                    msg.what == SERVICE_ARGS ||
                    msg.what == BIND_SERVICE ||
                    msg.what == INSTALL_PROVIDER ||
                    (EXECUTE_TRANSACTION != -1 && msg.what == EXECUTE_TRANSACTION)
            ) {
                if (!isPatchedResModifiedAfterLastLoad(mNewResourcesApkPath)) {
                    return false;
                }
                if (msg.what == EXECUTE_TRANSACTION) {
                    if (mSkipInterceptExecuteTransaction) {
                        return false;
                    }
                    final Object transaction = msg.obj;
                    if (transaction == null) {
                        ShareTinkerLog.w(TAG, "transaction is null or not a Transaction instance, skip rest " +
                                "insurance logic.");
                        return false;
                    }
                    if (mGetCallbacksMethod == null) {
                        try {
                            mGetCallbacksMethod = ShareReflectUtil.findMethod(transaction, "getCallbacks");
                        } catch (Throwable ignored) {
                            // Ignored.
                        }
                    }
                    if (mGetCallbacksMethod == null) {
                        ShareTinkerLog.e(TAG, "fail to find getLifecycleStateRequest method, skip rest " +
                                "insurance logic.");
                        mSkipInterceptExecuteTransaction = true;
                        return false;
                    }
                    try {
                        final List<Object> req = (List<Object>) mGetCallbacksMethod.invoke(transaction);
                        if (req != null && req.size() > 0) {
                            final Object cb = req.get(0);
                            if (cb == null ||
                                    !cb.getClass().getName().equals(LAUNCH_ACTIVITY_LIFECYCLE_ITEM_CLASSNAME)) {
                                return false;
                            }
                        }
                    } catch (Throwable thr) {
                        ShareTinkerLog.printErrStackTrace(TAG, thr, "fail to call getLifecycleStateRequest " +
                                "method, skip rest insurance logic.");
                        return false;
                    }
                }

                try {
                    ShareTinkerLog.i(TAG, "re-inject patched resources since its backed APK file was updated.");
                    TinkerResourcePatcher.monkeyPatchExistingResources(mApplication, mNewResourcesApkPath, true);
                } catch (Throwable thr) {
                    ShareTinkerLog.printErrStackTrace(TAG, thr, "fail to ensure patched resources available " +
                            "after it's modified.");
                }
            }

            return false;
        }
    }
}
