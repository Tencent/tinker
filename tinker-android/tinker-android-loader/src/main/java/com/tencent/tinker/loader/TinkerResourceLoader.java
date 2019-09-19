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

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareResPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Created by liangwenxiang on 2016/4/14.
 */
public class TinkerResourceLoader {
    protected static final String RESOURCE_META_FILE = ShareConstants.RES_META_FILE;
    protected static final String RESOURCE_FILE      = ShareConstants.RES_NAME;
    protected static final String RESOURCE_PATH      = ShareConstants.RES_PATH;
    private static final String TAG = "Tinker.ResourceLoader";
    private static ShareResPatchInfo resPatchInfo = new ShareResPatchInfo();

    private TinkerResourceLoader() {
    }

    /**
     * Load tinker resources
     */
    public static boolean loadTinkerResources(TinkerApplication application, String directory, Intent intentResult) {
        if (resPatchInfo == null || resPatchInfo.resArscMd5 == null) {
            return true;
        }
        String resourceString = directory + "/" + RESOURCE_PATH +  "/" + RESOURCE_FILE;
        File resourceFile = new File(resourceString);
        long start = System.currentTimeMillis();

        if (application.isTinkerLoadVerifyFlag()) {
            if (!SharePatchFileUtil.checkResourceArscMd5(resourceFile, resPatchInfo.resArscMd5)) {
                Log.e(TAG, "Failed to load resource file, path: " + resourceFile.getPath() + ", expect md5: " + resPatchInfo.resArscMd5);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_MD5_MISMATCH);
                return false;
            }
            Log.i(TAG, "verify resource file:" + resourceFile.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));
        }
        try {
            TinkerResourcePatcher.monkeyPatchExistingResources(application, resourceString);
            Log.i(TAG, "monkeyPatchExistingResources resource file:" + resourceString + ", use time: " + (System.currentTimeMillis() - start));
        } catch (Throwable e) {
            Log.e(TAG, "install resources failed");
            //remove patch dex if resource is installed failed
            try {
                SystemClassLoaderAdder.uninstallPatchDex(application.getClassLoader());
            } catch (Throwable throwable) {
                Log.e(TAG, "uninstallPatchDex failed", e);
            }
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION);
            return false;
        }
        // tinker resources loaded, monitor runtime accident
        ResourceStateMonitor.tryStart(application);
        return true;
    }

    /**
     * resource file exist?
     * fast check, only check whether exist
     *
     * @param directory
     * @return boolean
     */
    public static boolean checkComplete(Context context, String directory, ShareSecurityCheck securityCheck, Intent intentResult) {
        String meta = securityCheck.getMetaContentMap().get(RESOURCE_META_FILE);
        //not found resource
        if (meta == null) {
            return true;
        }
        //only parse first line for faster
        ShareResPatchInfo.parseResPatchInfoFirstLine(meta, resPatchInfo);

        if (resPatchInfo.resArscMd5 == null) {
            return true;
        }
        if (!ShareResPatchInfo.checkResPatchInfo(resPatchInfo)) {
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED);
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
            return false;
        }
        String resourcePath = directory + "/" + RESOURCE_PATH + "/";

        File resourceDir = new File(resourcePath);

        if (!resourceDir.exists() || !resourceDir.isDirectory()) {
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_DIRECTORY_NOT_EXIST);
            return false;
        }

        File resourceFile = new File(resourcePath + RESOURCE_FILE);
        if (!SharePatchFileUtil.isLegalFile(resourceFile)) {
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_FILE_NOT_EXIST);
            return false;
        }
        try {
            TinkerResourcePatcher.isResourceCanPatch(context);
        } catch (Throwable e) {
            Log.e(TAG, "resource hook check failed.", e);
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION);
            return false;
        }
        return true;
    }


    /**
     * Some situations may cause our resource modification to be ineffective,
     * for example, an APPLICATION_INFO_CHANGED message will reset LoadedApk#mResDir
     * to default value, then a relaunch activity which using tinker resources may
     * throw an Resources$NotFoundException.
     *
     * Monitor and handle them.
     */
    private static class ResourceStateMonitor {

        private static boolean started = false;

        static void tryStart(Application app) {
            if (Build.VERSION.SDK_INT < 26 || started) {
                return;
            }
            try {
                interceptHandler(fetchMHObject(app));
                started = true;
            } catch (Throwable e) {
                Log.e(TAG, "ResourceStateMonitor start failed, simply ignore.", e);
            }
        }

        private static Handler fetchMHObject(Context context) throws Exception {
            final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
            final Field mHField = ShareReflectUtil.findField(activityThread, "mH");
            return (Handler) mHField.get(activityThread);
        }

        private static void interceptHandler(Handler mH) throws Exception {
            final Field mCallbackField = ShareReflectUtil.findField(Handler.class, "mCallback");
            final Handler.Callback originCallback = (Handler.Callback) mCallbackField.get(mH);
            HackerCallback hackerCallback = new HackerCallback(originCallback, mH.getClass());
            mCallbackField.set(mH, hackerCallback);
        }

        private static class HackerCallback implements Handler.Callback {

            private final int APPLICATION_INFO_CHANGED;

            private Handler.Callback origin;

            HackerCallback(Handler.Callback ori, Class $H) {
                this.origin = ori;
                int appInfoChanged;
                try {
                    appInfoChanged = ShareReflectUtil.findField($H, "APPLICATION_INFO_CHANGED").getInt(null);
                } catch (Throwable e) {
                    appInfoChanged = 156; // default value
                }
                APPLICATION_INFO_CHANGED = appInfoChanged;
            }

            @Override
            public boolean handleMessage(Message msg) {
                boolean consume = false;
                if (hackMessage(msg)) {
                    consume = true;
                } else if (origin != null) {
                    consume = origin.handleMessage(msg);
                }
                return consume;
            }

            private boolean hackMessage(Message msg) {
                if (msg.what == APPLICATION_INFO_CHANGED) {
                    // We are generally in the background this moment(signal trigger is
                    // in front of user), and the signal was going to relaunch all our
                    // activities to apply new overlay resources. So we could simply kill
                    // ourselves, or ignore this signal, or reload tinker resources.
                    Process.killProcess(Process.myPid());
                    return true;
                }
                return false;
            }

        }

    }

}
