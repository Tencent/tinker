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


import android.content.Intent;

import com.tencent.tinker.lib.patch.UpgradePatch;
import com.tencent.tinker.lib.service.DefaultTinkerResultService;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;

import java.io.File;
import java.util.List;

/**
 * Created by zhangshaowen on 16/3/14.
 *
 * means that it is a newly patch, we would default use {@link UpgradePatch}
 * to do the job
 */
public interface PatchReporter {

    /**
     * use for report or some work at the beginning of TinkerPatchService
     * {@code TinkerPatchService.onHandleIntent} begin
     *
     * @param intent
     */
    void onPatchServiceStart(Intent intent);

    /**
     * check patch signature, TINKER_ID and meta files
     *
     * @param patchFile the loading path file
     * @param errorCode 0 is ok, you should define the errorCode yourself
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_OK}                              it is ok
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_SIGNATURE_FAIL}                  patch file signature is not the same with the base apk
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED}              dex meta file's format check fail
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED}              lib meta file's format check fail
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND}         can't find TINKER_PATCH in old apk manifest
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND}       can't find TINKER_PATCH in patch meta file
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL}             apk and patch's TINKER_PATCH value is not equal
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED}         resource meta file's format check fail
     *                  {@code ShareConstants.ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT}          some patch file type is not supported for current tinkerFlag
     */
    void onPatchPackageCheckFail(File patchFile, int errorCode);

    /**
     * for upgrade patch, patchFileVersion can't equal oldVersion or newVersion in oldPatchInfo
     * for repair patch, oldPatchInfo can 't be null, and patchFileVersion must equal with oldVersion and newVersion
     *
     * @param patchFile        the input patch file to recover
     * @param oldPatchInfo     the current patch info
     * @param patchFileVersion it is the md5 of the input patchFile
     */
    void onPatchVersionCheckFail(File patchFile, SharePatchInfo oldPatchInfo, String patchFileVersion);


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
    void onPatchTypeExtractFail(File patchFile, File extractTo, String filename, int fileType);


    /**
     * dex opt failed
     *
     * @param patchFile      the input patch file to recover
     * @param dexFiles       the dex file
     * @param t              throwable
     */
    void onPatchDexOptFail(File patchFile, List<File> dexFiles, Throwable t);


    /**
     * recover result, we will also send a result to {@link DefaultTinkerResultService}
     *
     * @param patchFile      the input patch file to recover
     * @param success        if it is success
     * @param cost           cost time in ms
     */
    void onPatchResult(File patchFile, boolean success, long cost);

    /**
     * recover patch occur unknown exception that we have wrap try catch for you!
     * you may need to report this exception and contact me
     * welcome to report a new issues for us!
     *
     * @param patchFile      the input file to patch
     * @param e
     */
    void onPatchException(File patchFile, Throwable e);

    /**
     * when we load a new patch, we need to rewrite the patch.info file.
     * but patch info corrupted, we can't recover from it
     *
     * @param patchFile      the input patch file to recover
     * @param oldVersion     old patch version
     * @param newVersion     new patch version
     */
    void onPatchInfoCorrupted(File patchFile, String oldVersion, String newVersion);

}
