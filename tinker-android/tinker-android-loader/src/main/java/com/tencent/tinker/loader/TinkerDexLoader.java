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

import android.content.Intent;

import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareDexDiffPatchInfo;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by zhangshaowen on 16/3/8.
 * check the complete of the dex files
 * pre-load patch dex files
 */
public class TinkerDexLoader {

    private static final String TAG = "Tinker.TinkerDexLoader";

    private static final String DEX_MEAT_FILE               = ShareConstants.DEX_META_FILE;
    private static final String DEX_PATH                    = ShareConstants.DEX_PATH;
    private static final String DEFAULT_DEX_OPTIMIZE_PATH   = ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH;
    private static final String INTERPRET_DEX_OPTIMIZE_PATH = ShareConstants.INTERPRET_DEX_OPTIMIZE_PATH;

    private static final ArrayList<ShareDexDiffPatchInfo> LOAD_DEX_LIST = new ArrayList<>();


    // private static File testOptDexFile;
    private static HashSet<ShareDexDiffPatchInfo> classNDexInfo = new HashSet<>();

    private static boolean isVmArt = ShareTinkerInternals.isVmArt();

    private TinkerDexLoader() {
    }

    /**
     * Load tinker JARs and add them to
     * the Application ClassLoader.
     *
     * @param application The application.
     */
    public static boolean loadTinkerJars(final TinkerApplication application, String directory, String oatDir, Intent intentResult, boolean isSystemOTA, boolean isProtectedApp) {
        if (LOAD_DEX_LIST.isEmpty() && classNDexInfo.isEmpty()) {
            ShareTinkerLog.w(TAG, "there is no dex to load");
            return true;
        }

        ClassLoader classLoader = TinkerDexLoader.class.getClassLoader();
        if (classLoader != null) {
            ShareTinkerLog.i(TAG, "classloader: " + classLoader.toString());
        } else {
            ShareTinkerLog.e(TAG, "classloader is null");
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_CLASSLOADER_NULL);
            return false;
        }
        String dexPath = directory + "/" + DEX_PATH + "/";

        ArrayList<File> legalFiles = new ArrayList<>();

        for (ShareDexDiffPatchInfo info : LOAD_DEX_LIST) {
            //for dalvik, ignore art support dex
            if (isJustArtSupportDex(info)) {
                continue;
            }

            String path = dexPath + info.realName;
            File file = new File(path);

            if (application.isTinkerLoadVerifyFlag()) {
                long start = System.currentTimeMillis();
                String checkMd5 = getInfoMd5(info);
                if (!SharePatchFileUtil.verifyDexFileMd5(file, checkMd5)) {
                    //it is good to delete the mismatch file
                    ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH);
                    intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISMATCH_DEX_PATH,
                        file.getAbsolutePath());
                    return false;
                }
                ShareTinkerLog.i(TAG, "verify dex file:" + file.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));
            }
            legalFiles.add(file);
        }
        // verify merge classN.apk
        if (isVmArt && !classNDexInfo.isEmpty()) {
            File classNFile = new File(dexPath + ShareConstants.CLASS_N_APK_NAME);
            long start = System.currentTimeMillis();

            if (application.isTinkerLoadVerifyFlag()) {
                for (ShareDexDiffPatchInfo info : classNDexInfo) {
                    if (!SharePatchFileUtil.verifyDexFileMd5(classNFile, info.rawName, info.destMd5InArt)) {
                        ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH);
                        intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISMATCH_DEX_PATH,
                            classNFile.getAbsolutePath());
                        return false;
                    }
                }
            }
            ShareTinkerLog.i(TAG, "verify dex file:" + classNFile.getPath() + " md5, use time: " + (System.currentTimeMillis() - start));

            legalFiles.add(classNFile);
        }
        File optimizeDir = new File(directory + "/" + oatDir);

        if (isSystemOTA) {
            final boolean[] parallelOTAResult = {true};
            final Throwable[] parallelOTAThrowable = new Throwable[1];
            String targetISA;
            try {
                targetISA = ShareTinkerInternals.getCurrentInstructionSet();
            } catch (Throwable throwable) {
                ShareTinkerLog.i(TAG, "getCurrentInstructionSet fail:" + throwable);
                // try {
                //     targetISA = ShareOatUtil.getOatFileInstructionSet(testOptDexFile);
                // } catch (Throwable throwable) {
                // don't ota on the front
                deleteOutOfDateOATFile(directory);

                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_INTERPRET_EXCEPTION, throwable);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_GET_OTA_INSTRUCTION_SET_EXCEPTION);
                return false;
                // }
            }

            deleteOutOfDateOATFile(directory);

            ShareTinkerLog.w(TAG, "systemOTA, try parallel oat dexes, targetISA:" + targetISA);
            // change dir
            optimizeDir = new File(directory + "/" + INTERPRET_DEX_OPTIMIZE_PATH);

            TinkerDexOptimizer.optimizeAll(
                  application, legalFiles, optimizeDir, true,
                  application.isUseDelegateLastClassLoader(), targetISA, false,
                  new TinkerDexOptimizer.ResultCallback() {
                      long start;

                      @Override
                      public void onStart(File dexFile, File optimizedDir) {
                          start = System.currentTimeMillis();
                          ShareTinkerLog.i(TAG, "start to optimize dex:" + dexFile.getPath());
                      }

                      @Override
                      public void onSuccess(File dexFile, File optimizedDir, File optimizedFile) {
                          // Do nothing.
                          ShareTinkerLog.i(TAG, "success to optimize dex " + dexFile.getPath() + ", use time " + (System.currentTimeMillis() - start));
                      }

                      @Override
                      public void onFailed(File dexFile, File optimizedDir, Throwable thr) {
                          parallelOTAResult[0] = false;
                          parallelOTAThrowable[0] = thr;
                          ShareTinkerLog.i(TAG, "fail to optimize dex " + dexFile.getPath() + ", use time " + (System.currentTimeMillis() - start));
                      }
                  }
            );


            if (!parallelOTAResult[0]) {
                ShareTinkerLog.e(TAG, "parallel oat dexes failed");
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_INTERPRET_EXCEPTION, parallelOTAThrowable[0]);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_OTA_INTERPRET_ONLY_EXCEPTION);
                return false;
            }
        }
        try {
            final boolean useDLC = application.isUseDelegateLastClassLoader();
            SystemClassLoaderAdder.installDexes(application, classLoader, optimizeDir, legalFiles, isProtectedApp, useDLC);
        } catch (Throwable e) {
            ShareTinkerLog.e(TAG, "install dexes failed");
            intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_EXCEPTION, e);
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_LOAD_EXCEPTION);
            return false;
        }

        return true;
    }

    /**
     * all the dex files in meta file exist?
     * fast check, only check whether exist
     *
     * @return boolean
     */
    public static boolean checkComplete(String directory, ShareSecurityCheck securityCheck, String oatDir, Intent intentResult) {
        String meta = securityCheck.getMetaContentMap().get(DEX_MEAT_FILE);
        //not found dex
        if (meta == null) {
            return true;
        }
        LOAD_DEX_LIST.clear();
        classNDexInfo.clear();

        ArrayList<ShareDexDiffPatchInfo> allDexInfo = new ArrayList<>();
        ShareDexDiffPatchInfo.parseDexDiffPatchInfo(meta, allDexInfo);

        if (allDexInfo.isEmpty()) {
            return true;
        }

        HashMap<String, String> dexes = new HashMap<>();

        ShareDexDiffPatchInfo testInfo = null;

        for (ShareDexDiffPatchInfo info : allDexInfo) {
            //for dalvik, ignore art support dex
            if (isJustArtSupportDex(info)) {
                continue;
            }
            if (!ShareDexDiffPatchInfo.checkDexDiffPatchInfo(info)) {
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED);
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL);
                return false;
            }
            if (isVmArt && info.rawName.startsWith(ShareConstants.TEST_DEX_NAME)) {
                testInfo = info;
            } else if (isVmArt && ShareConstants.CLASS_N_PATTERN.matcher(info.realName).matches()) {
                classNDexInfo.add(info);
            } else {
                dexes.put(info.realName, getInfoMd5(info));
                LOAD_DEX_LIST.add(info);
            }
        }

        if (isVmArt
            && (testInfo != null || !classNDexInfo.isEmpty())) {
            if (testInfo != null) {
                classNDexInfo.add(ShareTinkerInternals.changeTestDexToClassN(testInfo, classNDexInfo.size() + 1));
            }
            dexes.put(ShareConstants.CLASS_N_APK_NAME, "");
        }
        //tinker/patch.info/patch-641e634c/dex
        String dexDirectory = directory + "/" + DEX_PATH + "/";

        File dexDir = new File(dexDirectory);

        if (!dexDir.exists() || !dexDir.isDirectory()) {
            ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_DIRECTORY_NOT_EXIST);
            return false;
        }
        String optimizeDexDirectory = directory + "/" + oatDir + "/";
        File optimizeDexDirectoryFile = new File(optimizeDexDirectory);

        //fast check whether there is any dex files missing
        for (String name : dexes.keySet()) {
            File dexFile = new File(dexDirectory + name);

            if (!SharePatchFileUtil.isLegalFile(dexFile)) {
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISSING_DEX_PATH, dexFile.getAbsolutePath());
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_FILE_NOT_EXIST);
                return false;
            }
            //check dex opt whether complete also
            File dexOptFile = new File(SharePatchFileUtil.optimizedPathFor(dexFile, optimizeDexDirectoryFile));
            if (!SharePatchFileUtil.isLegalFile(dexOptFile)) {
                if (SharePatchFileUtil.shouldAcceptEvenIfIllegal(dexOptFile)) {
                    continue;
                }
                intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_MISSING_DEX_PATH, dexOptFile.getAbsolutePath());
                ShareIntentUtil.setIntentReturnCode(intentResult, ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_OPT_FILE_NOT_EXIST);
                return false;
            }
            // // find test dex
            // if (dexOptFile.getName().startsWith(ShareConstants.TEST_DEX_NAME)) {
            //     testOptDexFile = dexOptFile;
            // }
        }

        //if is ok, add to result intent
        intentResult.putExtra(ShareIntentUtil.INTENT_PATCH_DEXES_PATH, dexes);
        return true;
    }

    private static String getInfoMd5(ShareDexDiffPatchInfo info) {
        return isVmArt ? info.destMd5InArt : info.destMd5InDvm;
    }

    private static void deleteOutOfDateOATFile(String directory) {
        String optimizeDexDirectory = directory + "/" + DEFAULT_DEX_OPTIMIZE_PATH + "/";
        SharePatchFileUtil.deleteDir(optimizeDexDirectory);
        // delete android o
        if (ShareTinkerInternals.isAfterAndroidO()) {
            String androidODexDirectory = directory + "/" + DEX_PATH + "/" + ShareConstants.ANDROID_O_DEX_OPTIMIZE_PATH + "/";
            SharePatchFileUtil.deleteDir(androidODexDirectory);
        }
    }

    private static boolean isJustArtSupportDex(ShareDexDiffPatchInfo dexDiffPatchInfo) {
        if (isVmArt) {
            return false;
        }

        String destMd5InDvm = dexDiffPatchInfo.destMd5InDvm;

        if (destMd5InDvm.equals("0")) {
            return true;
        }

        return false;
    }
}
