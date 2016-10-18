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

import com.tencent.tinker.lib.service.TinkerPatchService;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public interface LoadReporter {

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
     * @param isUpgrade whether is a new patch, or just recover the old patch
     */
    void onLoadPatchListenerReceiveFail(File patchFile, int errorCode, boolean isUpgrade);

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
    void onLoadPatchVersionChanged(String oldVersion, String newVersion, File patchDirectoryFile, String currentPatchName);

    /**
     * the load patch process is end, we can see the cost times and the return code
     * return codes are define in {@link com.tencent.tinker.loader.shareutil.ShareConstants}
     *
     * @param patchDirectory the root patch directory {you_apk_data}/tinker
     * @param loadCode       {@code ShareConstants.ERROR_LOAD_OK}, 0 means success
     * @param cost           time in MS
     */
    void onLoadResult(File patchDirectory, int loadCode, long cost);

    /**
     * load patch occur unknown exception that we have wrap try catch for you!
     * you may need to report this exception and contact me
     * welcome to report a new issues for us!
     * you can disable patch as {@link DefaultLoadReporter#onLoadException(Throwable, int)}
     *
     * @param e
     * @param errorCode    exception code
     *                     {@code ShareConstants.ERROR_LOAD_EXCEPTION_UNKNOWN}        unknown exception
     *                     {@code ShareConstants.ERROR_LOAD_EXCEPTION_DEX}            exception when load dex
     *                     {@code ShareConstants.ERROR_LOAD_EXCEPTION_RESOURCE}       exception when load resource
     *                     {@code ShareConstants.ERROR_LOAD_EXCEPTION_UNCAUGHT}       exception unCaught
     */
    void onLoadException(Throwable e, int errorCode);

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
     *                    {@code ShareConstants.TYPE_RESOURCE}     patch lib file or directory not found
     *
     * @param isDirectory whether is directory for the file type
     */
    void onLoadFileNotFound(File file, int fileType, boolean isDirectory);

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
    void onLoadFileMd5Mismatch(File file, int fileType);

    /**
     * when we load a new patch, we need to rewrite the patch.info file.
     * but patch info corrupted, we can't recover from it
     * we can clean patch as {@link DefaultLoadReporter#onLoadPatchInfoCorrupted(String, String, File)}
     *
     * @param oldVersion    @nullable
     * @param newVersion    @nullable
     * @param patchInfoFile
     */
    void onLoadPatchInfoCorrupted(String oldVersion, String newVersion, File patchInfoFile);

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
    void onLoadPackageCheckFail(File patchFile, int errorCode);

}
