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

package com.tencent.tinker.lib.reporter;


import android.content.Context;

import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.lib.tinker.TinkerLoadResult;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.UpgradePatchRetry;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/10.
 * the default implement for LoadReporter
 * you can extent it for your own work
 * all is running in the process which loading the patch
 */
public class DefaultLoadReporter implements LoadReporter {
    private static final String TAG = "Tinker.DefaultLoadReporter";
    protected final Context context;

    public DefaultLoadReporter(Context context) {
        this.context = context;
    }

    /**
     * we receive a patch, but it check fails by PatchListener
     * so we would not start a {@link TinkerPatchService}
     *
     * @param patchFile
     * @param errorCode errorCode define as following
     *                  {@code ShareConstants.ERROR_PATCH_OK}                  it is ok
     *                  {@code ShareConstants.ERROR_PATCH_DISABLE}             patch is disable
     *                  {@code ShareConstants.ERROR_PATCH_NOTEXIST}            the file of tempPatchPatch file is not exist
     *                  {@code ShareConstants.ERROR_PATCH_RUNNING}             the recover service is running now, try later
     *                  {@code ShareConstants.ERROR_PATCH_INSERVICE}           the recover service can't send patch request
     */
    @Override
    public void onLoadPatchListenerReceiveFail(File patchFile, int errorCode) {
        TinkerLog.i(TAG, "patch loadReporter onLoadPatchListenerReceiveFail: patch receive fail: %s, code: %d",
            patchFile.getAbsolutePath(), errorCode);
    }


    /**
     * we can only handle patch version change in the main process,
     * we will need to kill all other process to ensure that all process's code is the same.
     * you can delete the old patch version file as {@link DefaultLoadReporter#onLoadPatchVersionChanged(String, String, File, String)}
     * or you can restart your other process here
     *
     * @param oldVersion
     * @param newVersion
     * @param patchDirectoryFile
     * @param currentPatchName
     */
    @Override
    public void onLoadPatchVersionChanged(String oldVersion, String newVersion, File patchDirectoryFile, String currentPatchName) {
        TinkerLog.i(TAG, "patch loadReporter onLoadPatchVersionChanged: patch version change from " + oldVersion + " to " + newVersion);

        if (oldVersion == null || newVersion == null) {
            return;
        }
        if (oldVersion.equals(newVersion)) {
            return;
        }

        //check main process
        if (!Tinker.with(context).isMainProcess()) {
            return;
        }

        // // Unnecessary now. Since other processes are killed in TinkerLoader.
        // TinkerLog.i(TAG, "onLoadPatchVersionChanged, try kill all other process");
        // // kill all other process to ensure that all process's code is the same.
        // ShareTinkerInternals.killAllOtherProcess(context);

        // reset retry count to 1, for interpret retry
        UpgradePatchRetry.getInstance(context).onPatchResetMaxCheck(newVersion);

        // delete old patch files
        File[] files = patchDirectoryFile.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (file.isDirectory() && !name.equals(currentPatchName)) {
                    SharePatchFileUtil.deleteDir(file);
                }
            }
        }
    }

    /**
     * After system ota, we will try to load dex with interpret mode
     *
     * @param type type define as following
     *             {@code ShareConstants.TYPE_INTERPRET_OK}                                    it is ok, using interpret mode
     *             {@code ShareConstants.TYPE_INTERPRET_GET_INSTRUCTION_SET_ERROR}             get instruction set from exist oat file fail
     *             {@code ShareConstants.TYPE_INTERPRET_COMMAND_ERROR}                         use command line to generate interpret oat file fail
     * @param e
     */
    @Override
    public void onLoadInterpret(int type, Throwable e) {
        TinkerLog.i(TAG, "patch loadReporter onLoadInterpret: type: %d, throwable: %s",
            type, e);
        switch (type) {
            case ShareConstants.TYPE_INTERPRET_GET_INSTRUCTION_SET_ERROR:
                TinkerLog.e(TAG, "patch loadReporter onLoadInterpret fail, can get instruction set from existed oat file");
                break;
            case ShareConstants.TYPE_INTERPRET_COMMAND_ERROR:
                TinkerLog.e(TAG, "patch loadReporter onLoadInterpret fail, command line to interpret return error");
                break;
            case ShareConstants.TYPE_INTERPRET_OK:
                TinkerLog.i(TAG, "patch loadReporter onLoadInterpret ok");
                break;
            default:
                break;
        }

        retryPatch();
    }

    /**
     * some files is not found,
     * we'd like to recover the old patch with {@link TinkerPatchService} in OldPatchProcessor mode
     * as {@link DefaultLoadReporter#onLoadFileNotFound(File, int, boolean)}
     *
     * @param file        the missing file
     * @param fileType    file type as following
     *                    {@code ShareConstants.TYPE_PATCH_FILE}  patch file or directory not found
     *                    {@code ShareConstants.TYPE_PATCH_INFO}  patch info file or directory not found
     *                    {@code ShareConstants.TYPE_DEX}         patch dex file or directory not found
     *                    {@code ShareConstants.TYPE_LIBRARY}     patch lib file or directory not found
     *                    {@code ShareConstants.TYPE_RESOURCE}    patch lib file or directory not found
     * @param isDirectory whether is directory for the file type
     */
    @Override
    public void onLoadFileNotFound(File file, int fileType, boolean isDirectory) {
        TinkerLog.i(TAG, "patch loadReporter onLoadFileNotFound: patch file not found: %s, fileType: %d, isDirectory: %b",
            file.getAbsolutePath(), fileType, isDirectory);

        // only try to recover opt file
        // check dex opt file at last, some phone such as VIVO/OPPO like to change dex2oat to interpreted
        if (fileType == ShareConstants.TYPE_DEX_OPT) {
            retryPatch();
        } else {
            checkAndCleanPatch();
        }
    }

    /**
     * default, we don't check file's md5 when we load them. but you can set {@code TinkerApplication.tinkerLoadVerifyFlag}
     * with tinker-android-anno, you can set {@code DefaultLifeCycle.loadVerifyFlag}
     * some files' md5 is mismatch with the meta.txt file
     * we won't load these files, clean patch for safety
     *
     * @param file     the mismatch file
     * @param fileType file type, just now, only dex or library will go here
     *                 {@code ShareConstants.TYPE_DEX}         patch dex file md5 mismatch
     *                 {@code ShareConstants.TYPE_LIBRARY}     patch lib file md5 mismatch
     *                 {@code ShareConstants.TYPE_RESOURCE}    patch resource file md5 mismatch
     */
    @Override
    public void onLoadFileMd5Mismatch(File file, int fileType) {
        TinkerLog.i(TAG, "patch load Reporter onLoadFileMd5Mismatch: patch file md5 mismatch file: %s, fileType: %d", file.getAbsolutePath(), fileType);
        //clean patch for safety
        checkAndCleanPatch();
    }

    /**
     * when we load a new patch, we need to rewrite the patch.info file.
     * but patch info corrupted, we can't recover from it
     * we can clean patch as {@link DefaultLoadReporter#onLoadPatchInfoCorrupted(String, String, File)}
     *
     * @param oldVersion    @nullable
     * @param newVersion    @nullable
     * @param patchInfoFile
     */
    @Override
    public void onLoadPatchInfoCorrupted(String oldVersion, String newVersion, File patchInfoFile) {
        TinkerLog.i(TAG, "patch loadReporter onLoadPatchInfoCorrupted: patch info file damage: %s, from version: %s to version: %s",
            patchInfoFile.getAbsolutePath(), oldVersion, newVersion);

        checkAndCleanPatch();
    }

    /**
     * the load patch process is end, we can see the cost times and the return code
     * return codes are define in {@link com.tencent.tinker.loader.shareutil.ShareConstants}
     *
     * @param patchDirectory the root patch directory {you_apk_data}/tinker
     * @param loadCode       {@code ShareConstants.ERROR_LOAD_OK}, 0 means success
     * @param cost           time in ms
     */
    @Override
    public void onLoadResult(File patchDirectory, int loadCode, long cost) {
        TinkerLog.i(TAG, "patch loadReporter onLoadResult: patch load result, path:%s, code: %d, cost: %dms", patchDirectory.getAbsolutePath(), loadCode, cost);
        //you can just report the result here
    }

    /**
     * load patch occur unknown exception that we have wrap try catch for you!
     * you may need to report this exception and contact me
     * welcome to report a new issues for us!
     * you can disable patch as {@link DefaultLoadReporter#onLoadException(Throwable, int)}
     *
     * @param e
     * @param errorCode exception code
     *                  {@code ShareConstants.ERROR_LOAD_EXCEPTION_UNKNOWN}        unknown exception
     *                  {@code ShareConstants.ERROR_LOAD_EXCEPTION_DEX}            exception when load dex
     *                  {@code ShareConstants.ERROR_LOAD_EXCEPTION_RESOURCE}       exception when load resource
     *                  {@code ShareConstants.ERROR_LOAD_EXCEPTION_UNCAUGHT}       exception unCaught
     */
    @Override
    public void onLoadException(Throwable e, int errorCode) {
        //for unCaught or dex exception, disable tinker all the time with sp
        switch (errorCode) {
            case ShareConstants.ERROR_LOAD_EXCEPTION_DEX:
                if (e.getMessage().contains(ShareConstants.CHECK_DEX_INSTALL_FAIL)) {
                    TinkerLog.e(TAG, "patch loadReporter onLoadException: tinker dex check fail:" + e.getMessage());
                } else {
                    TinkerLog.i(TAG, "patch loadReporter onLoadException: patch load dex exception: %s", e);
                }
                ShareTinkerInternals.setTinkerDisableWithSharedPreferences(context);
                TinkerLog.i(TAG, "dex exception disable tinker forever with sp");
                break;
            case ShareConstants.ERROR_LOAD_EXCEPTION_RESOURCE:
                if (e.getMessage().contains(ShareConstants.CHECK_RES_INSTALL_FAIL)) {
                    TinkerLog.e(TAG, "patch loadReporter onLoadException: tinker res check fail:" + e.getMessage());
                } else {
                    TinkerLog.i(TAG, "patch loadReporter onLoadException: patch load resource exception: %s", e);
                }
                ShareTinkerInternals.setTinkerDisableWithSharedPreferences(context);
                TinkerLog.i(TAG, "res exception disable tinker forever with sp");
                break;
            case ShareConstants.ERROR_LOAD_EXCEPTION_UNCAUGHT:
                TinkerLog.i(TAG, "patch loadReporter onLoadException: patch load unCatch exception: %s", e);
                ShareTinkerInternals.setTinkerDisableWithSharedPreferences(context);
                TinkerLog.i(TAG, "unCaught exception disable tinker forever with sp");

                String uncaughtString = SharePatchFileUtil.checkTinkerLastUncaughtCrash(context);
                if (!ShareTinkerInternals.isNullOrNil(uncaughtString)) {
                    File laseCrashFile = SharePatchFileUtil.getPatchLastCrashFile(context);
                    SharePatchFileUtil.safeDeleteFile(laseCrashFile);
                    // found really crash reason
                    TinkerLog.e(TAG, "tinker uncaught real exception:" + uncaughtString);
                }
                break;
            case ShareConstants.ERROR_LOAD_EXCEPTION_UNKNOWN:
                TinkerLog.i(TAG, "patch loadReporter onLoadException: patch load unknown exception: %s", e);
                //exception can be caught, it is no need to disable Tinker with sharedPreference
                break;
            default:
                break;
        }
        TinkerLog.e(TAG, "tinker load exception, welcome to submit issue to us: https://github.com/Tencent/tinker/issues");
        TinkerLog.printErrStackTrace(TAG, e, "tinker load exception");

        Tinker.with(context).setTinkerDisable();
        checkAndCleanPatch();
    }
    /**
     * check patch signature, TINKER_ID and meta files
     *
     * @param patchFile the loading path file
     * @param errorCode 0 is ok, you should define the errorCode yourself
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_OK}                              it is ok
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_SIGNATURE_FAIL}                  patch file signature is not the same with the base apk
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND}          package meta: "assets/package_meta.txt" is not found
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED}              dex meta file's format check fail
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED}              lib meta file's format check fail
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND}         can't find TINKER_PATCH in old apk manifest
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND}       can't find TINKER_PATCH in patch meta file
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL}             apk and patch's TINKER_PATCH value is not equal
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED}         resource meta file's format check fail
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT}          some patch file type is not supported for current tinkerFlag
     */
    @Override
    public void onLoadPackageCheckFail(File patchFile, int errorCode) {
        TinkerLog.i(TAG, "patch loadReporter onLoadPackageCheckFail: "
            + "load patch package check fail file path: %s, errorCode: %d", patchFile.getAbsolutePath(), errorCode);
        checkAndCleanPatch();
    }

    /**
     * other process may have installed old patch version,
     * if we try to clean patch, we should kill other process first
     */
    public void checkAndCleanPatch() {
        Tinker tinker = Tinker.with(context);
        //only main process can load a new patch
        if (tinker.isMainProcess()) {
            TinkerLoadResult tinkerLoadResult = tinker.getTinkerLoadResultIfPresent();
            //if versionChange and the old patch version is not ""
            if (tinkerLoadResult.versionChanged) {
                SharePatchInfo sharePatchInfo = tinkerLoadResult.patchInfo;
                if (sharePatchInfo != null && !ShareTinkerInternals.isNullOrNil(sharePatchInfo.oldVersion)) {
                    TinkerLog.w(TAG, "checkAndCleanPatch, oldVersion %s is not null, try kill all other process",
                        sharePatchInfo.oldVersion);

                    ShareTinkerInternals.killAllOtherProcess(context);
                }
            }
        }
        tinker.cleanPatch();

    }

    public boolean retryPatch() {
        final Tinker tinker = Tinker.with(context);
        if (!tinker.isMainProcess()) {
            return false;
        }

        File patchVersionFile = tinker.getTinkerLoadResultIfPresent().patchVersionFile;
        if (patchVersionFile != null) {
            if (UpgradePatchRetry.getInstance(context).onPatchListenerCheck(SharePatchFileUtil.getMD5(patchVersionFile))) {
                TinkerLog.i(TAG, "try to repair oat file on patch process");
                TinkerInstaller.onReceiveUpgradePatch(context, patchVersionFile.getAbsolutePath());
                return true;
            }
            // else {
            //       TinkerLog.i(TAG, "repair retry exceed must max time, just clean");
            //       checkAndCleanPatch();
            // }
        }

        return false;
    }
}
