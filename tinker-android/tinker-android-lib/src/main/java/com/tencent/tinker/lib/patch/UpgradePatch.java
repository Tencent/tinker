/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.lib.patch;

import android.content.Context;

import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.io.IOException;


/**
 * generate new patch, you can implement your own patch processor class
 * Created by zhangshaowen on 16/3/14.
 */
public class UpgradePatch extends AbstractPatch {
    private static final String TAG = "UpgradePatch";

    @Override
    public boolean tryPatch(Context context, String tempPatchPath) {
        Tinker manager = Tinker.with(context);

        final File patchFile = new File(tempPatchPath);

        if (!manager.isTinkerEnabled() || !ShareTinkerInternals.isTinkerEnableWithSharedPreferences(context)) {
            TinkerLog.e(TAG, "UpgradePatch tryPatch:patch is disabled, just return");
            return false;
        }

        if (!patchFile.isFile() || !patchFile.exists()) {
            TinkerLog.e(TAG, "UpgradePatch tryPatch:patch file is not found, just return");
            return false;
        }
        //check the signature, we should create a new checker
        ShareSecurityCheck signatureCheck = new ShareSecurityCheck(context);

        int returnCode = ShareTinkerInternals.checkSignatureAndTinkerID(context, patchFile, signatureCheck);
        if (returnCode != 0) {
            TinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchPackageCheckFail");
            manager.getPatchReporter().onPatchPackageCheckFail(patchFile, true, returnCode);
            return false;
        }

        //it is a new patch, so we should not find a exist
        SharePatchInfo oldInfo = manager.getTinkerLoadResultIfPresent().patchInfo;
        String patchMd5 = SharePatchFileUtil.getMD5(patchFile);
        SharePatchInfo newInfo;

        //already have patch
        if (oldInfo != null) {
            if (oldInfo.oldVersion == null || oldInfo.newVersion == null) {
                TinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchInfoCorrupted");
                manager.getPatchReporter().onPatchInfoCorrupted(patchFile, oldInfo.oldVersion, oldInfo.newVersion, true);
                return false;
            }

            if (oldInfo.oldVersion.equals(patchMd5) || oldInfo.newVersion.equals(patchMd5)) {
                TinkerLog.e(TAG, "UpgradePatch tryPatch:onPatchVersionCheckFail");
                manager.getPatchReporter().onPatchVersionCheckFail(patchFile, oldInfo, patchMd5, true);
                return false;
            }
            newInfo = new SharePatchInfo(oldInfo.oldVersion, patchMd5);
        } else {
            newInfo = new SharePatchInfo("", patchMd5);
        }

        //check ok, we can real recover a new patch
        final String patchDirectory = manager.getPatchDirectory().getAbsolutePath();

        TinkerLog.d(TAG, "UpgradePatch tryPatch:patchMd5:%s", patchMd5);

        final String patchName = SharePatchFileUtil.getPatchVersionDirectory(patchMd5);

        final String patchVersionDirectory = patchDirectory + "/" + patchName;

        TinkerLog.d(TAG, "UpgradePatch tryPatch:patchVersionDirectory:%s", patchVersionDirectory);

        //it is a new patch, we first delete if there is any files
        //don't delete dir for faster retry
//        SharePatchFileUtil.deleteDir(patchVersionDirectory);

        //copy file
        File destPatchFile = new File(patchVersionDirectory + "/" + SharePatchFileUtil.getPatchVersionFile(patchMd5));
        try {
            SharePatchFileUtil.copyFileUsingChannel(patchFile, destPatchFile);
        } catch (IOException e) {
//            e.printStackTrace();
            TinkerLog.e(TAG, "UpgradePatch tryPatch:copy patch file fail from %s to %s", patchFile.getPath(), destPatchFile.getPath());
            manager.getPatchReporter().onPatchTypeExtractFail(patchFile, destPatchFile, patchFile.getName(), ShareConstants.TYPE_PATCH_FILE, true);
            return false;
        }

        //we use destPatchFile instead of patchFile, because patchFile may be deleted during the patch process
        if (!DexDiffPatchInternal.tryRecoverDexFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile, true)) {
            TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch dex failed");
            return false;
        }

        if (!BsDiffPatchInternal.tryRecoverLibraryFiles(manager, signatureCheck, context, patchVersionDirectory, destPatchFile, true)) {
            TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, try patch library failed");
            return false;
        }

        final File patchInfoFile = manager.getPatchInfoFile();

        if (!SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, newInfo, SharePatchFileUtil.getPatchInfoLockFile(patchDirectory))) {
            TinkerLog.e(TAG, "UpgradePatch tryPatch:new patch recover, rewrite patch info failed");
            manager.getPatchReporter().onPatchInfoCorrupted(patchFile, newInfo.oldVersion, newInfo.newVersion, true);
            return false;
        }


        TinkerLog.w(TAG, "UpgradePatch tryPatch: done, it is ok");
        return true;
    }

}
