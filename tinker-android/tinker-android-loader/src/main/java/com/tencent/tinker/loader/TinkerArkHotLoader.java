/*
 * Copyright (C) 2019-2019. Huawei Technologies Co., Ltd. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the BSD 3-Clause License
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * the BSD 3-Clause License for more details.
 */

package com.tencent.tinker.loader;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.ShareArkHotDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import dalvik.system.PathClassLoader;

/**
 * Created by  on 16/3/8.
 * check the complete of the dex files
 * pre-load patch dex files
 */
public class TinkerArkHotLoader {
    private static final String TAG = "Tinker.TinkerArkHotLoader";

    private static final String ARK_MEAT_FILE = ShareConstants.ARKHOT_META_FILE;
    private static final String ARKHOT_PATH = ShareConstants.ARKHOTFIX_PATH;

    // private static File testOptDexFile;
    private static HashSet<ShareArkHotDiffPatchInfo> arkHotApkInfo = new HashSet<>();

    private static boolean isArkHotRuning = ShareTinkerInternals.isArkHotRuning();

    private TinkerArkHotLoader() {
    }

    /**
     * Load tinker JARs and add them to
     * the Application ClassLoader.
     *
     * @param application The application.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public static boolean loadTinkerArkHot(final TinkerApplication application, String directory, Intent intentResult) {
        if (arkHotApkInfo.isEmpty()) {
            Log.w(TAG, "there is no apk to load");
            return true;
        }

        PathClassLoader classLoader = (PathClassLoader) TinkerArkHotLoader.class.getClassLoader();
        if (classLoader != null) {
            Log.i(TAG, "classloader: " + classLoader.toString());
        } else {
            Log.e(TAG, "classloader is null");
            ShareIntentUtil.setIntentReturnCode(intentResult,
                    ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_CLASSLOADER_NULL);
            return false;
        }
        String apkPath = directory + "/" + ARKHOT_PATH + "/";

        ArrayList<File> legalFiles = new ArrayList<>();

        // verify merge classN.apk
        if (isArkHotRuning && !arkHotApkInfo.isEmpty()) {
            File classNFile = null;
            classNFile = new File(apkPath + ShareConstants.ARKHOT_PATCH_NAME);
            legalFiles.add(classNFile);
        }

        try {
            // 加载Apk
            SystemClassLoaderAdder.installApk(classLoader, legalFiles);
        } catch (Throwable e) {
            Log.e(TAG, "install dexes failed");
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
            ShareIntentUtil.setIntentReturnCode(intentResult,
                    ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_LOAD_EXCEPTION);
            return false;
        }

        return true;
    }

    /**
     * all the ark apk in meta file exist?
     * fast check, only check whether exist
     *
     * @return boolean
     */
    public static boolean checkComplete(String directory, ShareSecurityCheck securityCheck, Intent intentResult) {
        String meta = securityCheck.getMetaContentMap().get(ARK_MEAT_FILE);
        if (meta == null) {
            return true;
        }
        arkHotApkInfo.clear();

        ArrayList<ShareArkHotDiffPatchInfo> allDexInfo = new ArrayList<>();
        ShareArkHotDiffPatchInfo.parseDiffPatchInfo(meta, allDexInfo);

        if (allDexInfo.isEmpty()) {
            return true;
        }

        HashMap<String, String> apks = new HashMap<>(1);

        for (ShareArkHotDiffPatchInfo info : allDexInfo) {
            // for dalvik, ignore art support dex
            if (!ShareArkHotDiffPatchInfo.checkDiffPatchInfo(info)) {
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK,
                        ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
                return false;
            }
            if (isArkHotRuning && ShareConstants.ARKHOT_PATCH_NAME.equals(info.name)) {
                arkHotApkInfo.add(info);
            }
        }

        if (isArkHotRuning
                && !arkHotApkInfo.isEmpty()) {
            apks.put(ShareConstants.ARKHOT_PATCH_NAME, "");
        }
        String apkDirectory = directory + "/" + ARKHOT_PATH + "/";

        File dexDir = new File(apkDirectory);

        if (!dexDir.exists() || !dexDir.isDirectory()) {
            ShareIntentUtil.setIntentReturnCode(intentResult,
                    ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_DIRECTORY_NOT_EXIST);
            return false;
        }

        // fast check whether there is any dex files missing
        for (String name : apks.keySet()) {
            File apkFile = new File(apkDirectory + name);

            if (!SharePatchFileUtil.isLegalFile(apkFile)) {
                try {
                    intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISSING_DEX_PATH, apkFile.getCanonicalPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ShareIntentUtil.setIntentReturnCode(intentResult,
                        ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_FILE_NOT_EXIST);
                return false;
            }
        }

        // if is ok, add to result intent
        intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_DEXES_PATH, apks);
        return true;
    }
}