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

package com.tencent.tinker.lib.patch;

import android.content.Context;
import android.os.Build;

import com.tencent.tinker.lib.service.PatchResult;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;
import com.tencent.tinker.lib.util.UpgradePatchRetry;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.io.IOException;
import java.util.Map;


/**
 * generate new patch, you can implement your own patch processor class
 * Created by zhangshaowen on 16/3/14.
 */
public class UpgradePatch extends AbstractPatch {
    private static final String TAG = "Tinker.UpgradePatch";

    @Override
    public boolean tryPatch(Context context, String tempPatchPath, PatchResult patchResult) {
        Tinker manager = Tinker.with(context);

        final File patchFile = new File(tempPatchPath);

        if (!manager.isTinkerEnabled() || !ShareTinkerInternals.isTinkerEnableWithSharedPreferences(context)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:patch is disabled, just return");
            return false;
        }

        if (!SharePatchFileUtil.isLegalFile(patchFile)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:patch file is not found, just return");
            return false;
        }
        //check the signature, we should create a new checker
        ShareSecurityCheck signatureCheck = new ShareSecurityCheck(context);

        int returnCode = ShareTinkerInternals.checkTinkerPackage(context, manager.getTinkerFlags(), patchFile, signatureCheck);
        if (returnCode != ShareConstants.ERROR_PACKAGE_CHECK_OK) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchPackageCheckFail");
            manager.getPatchReporter().onPatchPackageCheckFail(patchFile, returnCode);
            return false;
        }

        String patchMd5 = SharePatchFileUtil.getMD5(patchFile);
        if (patchMd5 == null) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:patch md5 is null, just return");
            return false;
        }
        //use md5 as version
        patchResult.patchVersion = patchMd5;

        ShareTinkerLog.i(TAG, "UpgradePatch tryPatch:patchMd5:%s", patchMd5);

        //check ok, we can real recover a new patch
        final String patchDirectory = manager.getPatchDirectory().getAbsolutePath();

        File patchInfoLockFile = SharePatchFileUtil.getPatchInfoLockFile(patchDirectory);
        File patchInfoFile = SharePatchFileUtil.getPatchInfoFile(patchDirectory);

        final Map<String, String> pkgProps = signatureCheck.getPackagePropertiesIfPresent();
        if (pkgProps == null) {
            ShareTinkerLog.e(TAG, "UpgradePatch packageProperties is null, do we process a valid patch apk ?");
            return false;
        }

        final String isProtectedAppStr = pkgProps.get(ShareConstants.PKGMETA_KEY_IS_PROTECTED_APP);
        final boolean isProtectedApp = (isProtectedAppStr != null && !isProtectedAppStr.isEmpty() && !"0".equals(isProtectedAppStr));

        SharePatchInfo oldInfo = SharePatchInfo.readAndCheckPropertyWithLock(patchInfoFile, patchInfoLockFile);

        //it is a new patch, so we should not find a exist
        SharePatchInfo newInfo;

        //already have patch
        if (oldInfo != null) {
            if (oldInfo.oldVersion == null || oldInfo.newVersion == null || oldInfo.oatDir == null) {
                ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchInfoCorrupted");
                manager.getPatchReporter().onPatchInfoCorrupted(patchFile, oldInfo.oldVersion, oldInfo.newVersion);
                return false;
            }

            if (!SharePatchFileUtil.checkIfMd5Valid(patchMd5)) {
                ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchVersionCheckFail md5 %s is valid", patchMd5);
                manager.getPatchReporter().onPatchVersionCheckFail(patchFile, oldInfo, patchMd5);
                return false;
            }

            final boolean usingInterpret = oldInfo.oatDir.equals(ShareConstants.INTERPRET_DEX_OPTIMIZE_PATH);

            if (!usingInterpret && !ShareTinkerInternals.isNullOrNil(oldInfo.newVersion) && oldInfo.newVersion.equals(patchMd5) && !oldInfo.isRemoveNewVersion) {
                ShareTinkerLog.e(TAG, "patch already applied, md5: %s", patchMd5);

                // Reset patch apply retry count to let us be able to reapply without triggering
                // patch apply disable when we apply it successfully previously.
                UpgradePatchRetry.getInstance(context).onPatchResetMaxCheck(patchMd5);

                return true;
            }
            // if it is interpret now, use changing flag to wait main process
            final String finalOatDir = usingInterpret ? ShareConstants.CHANING_DEX_OPTIMIZE_PATH : oldInfo.oatDir;
            newInfo = new SharePatchInfo(oldInfo.oldVersion, patchMd5, isProtectedApp, false, Build.FINGERPRINT, finalOatDir, false);
        } else {
            newInfo = new SharePatchInfo("", patchMd5, isProtectedApp, false, Build.FINGERPRINT, ShareConstants.DEFAULT_DEX_OPTIMIZE_PATH, false);
        }

        // it is a new patch, we first delete if there is any files
        // don't delete dir for faster retry
        // SharePatchFileUtil.deleteDir(patchVersionDirectory);
        final String patchName = SharePatchFileUtil.getPatchVersionDirectory(patchMd5);

        final String patchVersionDirectory = patchDirectory + "/" + patchName;

        ShareTinkerLog.i(TAG, "UpgradePatch tryPatch:patchVersionDirectory:%s", patchVersionDirectory);

        //copy file
        File destPatchFile = new File(patchVersionDirectory + "/" + SharePatchFileUtil.getPatchVersionFile(patchMd5));

        try {
            // check md5 first
            if (!patchMd5.equals(SharePatchFileUtil.getMD5(destPatchFile))) {
                SharePatchFileUtil.copyFileUsingStream(patchFile, destPatchFile);
                ShareTinkerLog.w(TAG, "UpgradePatch copy patch file, src file: %s size: %d, dest file: %s size:%d", patchFile.getAbsolutePath(), patchFile.length(),
                    destPatchFile.getAbsolutePath(), destPatchFile.length());
            }
        } catch (IOException e) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:copy patch file fail from %s to %s", patchFile.getPath(), destPatchFile.getPath());
            manager.getPatchReporter().onPatchTypeExtractFail(patchFile, destPatchFile, patchFile.getName(), ShareConstants.TYPE_PATCH_FILE);
            return false;
        }

        //we use destPatchFile instead of patchFile, because patchFile may be deleted during the patch process
        if (!DexDiffPatchInternal.tryRecoverDexFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile, patchResult)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch dex failed");
            return false;
        }

        if (!ArkHotDiffPatchInternal.tryRecoverArkHotLibrary(manager, signatureCheck,
                context, patchVersionDirectory, destPatchFile)) {
            return false;
        }

        if (!BsDiffPatchInternal.tryRecoverLibraryFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch library failed");
            return false;
        }

        if (!ResDiffPatchInternal.tryRecoverResourceFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch resource failed");
            return false;
        }

        // check dex opt file at last, some phone such as VIVO/OPPO like to change dex2oat to interpreted
        if (!DexDiffPatchInternal.waitAndCheckDexOptFile(patchFile, manager)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, check dex opt file failed");
            return false;
        }

        if (!SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, newInfo, patchInfoLockFile)) {
            ShareTinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, rewrite patch info failed");
            manager.getPatchReporter().onPatchInfoCorrupted(patchFile, newInfo.oldVersion, newInfo.newVersion);
            return false;
        }

        // Reset patch apply retry count to let us be able to reapply without triggering
        // patch apply disable when we apply it successfully previously.
        UpgradePatchRetry.getInstance(context).onPatchResetMaxCheck(patchMd5);

        ShareTinkerLog.w(TAG, "UpgradePatch tryPatch: done, it is ok");
        return true;
    }

}
