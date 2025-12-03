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

import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareResPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.File;

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
                ShareTinkerLog.e(TAG, "Failed to load resource file, path: " + resourceFile.getPath() + ", expect md5: " + resPatchInfo.resArscMd5);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_MD5_MISMATCH);
                return false;
            }
            ShareTinkerLog.i(TAG, "verify resource file:" + resourceFile.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));
        }
        try {
            TinkerResourcePatcher.monkeyPatchExistingResources(application, resourceString, false);
            ShareTinkerLog.i(TAG, "monkeyPatchExistingResources resource file:" + resourceString + ", use time: " + (System.currentTimeMillis() - start));
        } catch (Throwable e) {
            ShareTinkerLog.e(TAG, "install resources failed");
            //remove patch dex if resource is installed failed
            try {
                SystemClassLoaderAdder.uninstallPatchDex(application.getClassLoader());
            } catch (Throwable throwable) {
                ShareTinkerLog.e(TAG, "uninstallPatchDex failed", e);
            }
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION);
            return false;
        }
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
            ShareTinkerLog.e(TAG, "resource hook check failed.", e);
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION);
            return false;
        }
        return true;
    }
}
