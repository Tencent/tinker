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
import android.content.Intent;

import com.tencent.tinker.lib.service.DefaultTinkerResultService;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.UpgradePatchRetry;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.util.List;

/**
 * Created by zhangshaowen on 16/3/14.
 * the default implement for PatchReporter
 * you can extent it for your own work
 * all is running in the :patch process
 */
public class DefaultPatchReporter implements PatchReporter {
    private static final String TAG = "Tinker.DefaultPatchReporter";
    private static boolean shouldRetry = false;
    protected final Context context;

    public DefaultPatchReporter(Context context) {
        this.context = context;
    }

    /************************************ :patch process below ***************************************/
    /**
     * use for report or some work at the beginning of TinkerPatchService
     * {@code TinkerPatchService.onHandleIntent} begin
     *
     * @param intent
     */
    @Override
    public void onPatchServiceStart(Intent intent) {
        TinkerLog.i(TAG, "patchReporter onPatchServiceStart: patch service start");
        shouldRetry = false;
        UpgradePatchRetry.getInstance(context).onPatchServiceStart(intent);

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
    public void onPatchPackageCheckFail(File patchFile, int errorCode) {
        TinkerLog.i(TAG, "patchReporter onPatchPackageCheckFail: package check failed. path: %s, code: %d",
            patchFile.getAbsolutePath(), errorCode);
        //only meta corrupted, need to delete temp files. others is just in the check time!
        if (errorCode == ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED
            || errorCode == ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED
            || errorCode == ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED) {
            //delete temp files
            Tinker.with(context).cleanPatchByPatchApk(patchFile);
        }
    }

    /**
     * for upgrade patch, patchFileVersion can't equal oldVersion or newVersion in oldPatchInfo
     * for repair patch, oldPatchInfo can 't be null, and patchFileVersion must equal with oldVersion and newVersion
     *
     * @param patchFile        the input patch file to recover
     * @param oldPatchInfo     the current patch info
     * @param patchFileVersion it is the md5 of the input patchFile
     */
    @Override
    public void onPatchVersionCheckFail(File patchFile, SharePatchInfo oldPatchInfo, String patchFileVersion) {
        TinkerLog.i(TAG, "patchReporter onPatchVersionCheckFail: patch version exist. path: %s, version: %s",
            patchFile.getAbsolutePath(), patchFileVersion);
        //no need to delete temp files, because it is only in the check time!
    }

    /**
     * try to recover file fail
     *
     * @param patchFile      the input patch file to recover
     * @param extractTo      the target file
     * @param filename
     * @param fileType       file type as following
     *                       {@code ShareConstants.TYPE_DEX}         extract patch dex file fail
     *                       {@code ShareConstants.TYPE_DEX_FOR_ART} extract patch small art dex file fail
     *                       {@code ShareConstants.TYPE_LIBRARY}     extract patch library fail
     *                       {@code ShareConstants.TYPE_PATCH_FILE}  copy patch file fail
     *                       {@code ShareConstants.TYPE_RESOURCE}    extract patch resource fail
     */
    @Override
    public void onPatchTypeExtractFail(File patchFile, File extractTo, String filename, int fileType) {
        TinkerLog.i(TAG, "patchReporter onPatchTypeExtractFail: file extract fail type: %s, path: %s, extractTo: %s, filename: %s",
            ShareTinkerInternals.getTypeString(fileType), patchFile.getPath(), extractTo.getPath(), filename);
        //delete temp files
        Tinker.with(context).cleanPatchByPatchApk(patchFile);
    }

    /**
     * dex opt failed
     *
     * @param patchFile      the input patch file to recover
     * @param dexFiles       the dex files
     * @param t
     */
    @Override
    public void onPatchDexOptFail(File patchFile, List<File> dexFiles, Throwable t) {
        TinkerLog.i(TAG, "patchReporter onPatchDexOptFail: dex opt fail path: %s, dex size: %d",
            patchFile.getAbsolutePath(), dexFiles.size());
        TinkerLog.printErrStackTrace(TAG, t, "onPatchDexOptFail:");

        // some phone such as VIVO/OPPO like to change dex2oat to interpreted may go here
        // check oat file if it is elf format
        if (t.getMessage().contains(ShareConstants.CHECK_DEX_OAT_EXIST_FAIL)
            || t.getMessage().contains(ShareConstants.CHECK_DEX_OAT_FORMAT_FAIL)) {
            shouldRetry = true;
            deleteOptFiles(dexFiles);
        } else {
            Tinker.with(context).cleanPatchByPatchApk(patchFile);
        }
    }

    /**
     * recover result, we will also send a result to {@link DefaultTinkerResultService}
     *
     * @param patchFile      the input patch file to recover
     * @param success        if it is success
     * @param cost           cost time in ms
     */
    @Override
    public void onPatchResult(File patchFile, boolean success, long cost) {
        TinkerLog.i(TAG, "patchReporter onPatchResult: patch all result path: %s, success: %b, cost: %d",
            patchFile.getAbsolutePath(), success, cost);
        // if should retry don't delete the temp file
        if (!shouldRetry) {
            UpgradePatchRetry.getInstance(context).onPatchServiceResult();
        }
    }

    /**
     * when we load a new patch, we need to rewrite the patch.info file.
     * but patch info corrupted, we can't recover from it
     *
     * @param patchFile      the input patch file to recover
     * @param oldVersion     old patch version
     * @param newVersion     new patch version
     */
    @Override
    public void onPatchInfoCorrupted(File patchFile, String oldVersion, String newVersion) {
        TinkerLog.i(TAG, "patchReporter onPatchInfoCorrupted: patch info is corrupted. old: %s, new: %s",
            oldVersion, newVersion);
        //patch.info is corrupted, just clean all patch
        Tinker.with(context).cleanPatch();
    }

    /**
     * recover patch occur unknown exception that we have wrap try catch for you!
     * you may need to report this exception and contact me
     * welcome to report a new issues for us!
     *
     * @param patchFile      the input file to patch
     * @param e
     */
    @Override
    public void onPatchException(File patchFile, Throwable e) {
        TinkerLog.i(TAG, "patchReporter onPatchException: patch exception path: %s, throwable: %s",
            patchFile.getAbsolutePath(), e.getMessage());
        TinkerLog.e(TAG, "tinker patch exception, welcome to submit issue to us: https://github.com/Tencent/tinker/issues");
        // if (e.getMessage().contains(ShareConstants.CHECK_VM_PROPERTY_FAIL)) {
        //     ShareTinkerInternals.setTinkerDisableWithSharedPreferences(context);
        //     TinkerLog.i(TAG, "check vm property exception disable tinker forever with sp");
        // }
        TinkerLog.printErrStackTrace(TAG, e, "tinker patch exception");
        //don't accept request any more!
        Tinker.with(context).setTinkerDisable();
        ////delete temp files, I think we don't have to clean all patch
        Tinker.with(context).cleanPatchByPatchApk(patchFile);
    }

    private void deleteOptFiles(List<File> dexFiles) {
        for (File file : dexFiles) {
            SharePatchFileUtil.safeDeleteFile(file);
        }
    }

}
