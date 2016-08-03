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
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/18.
 *
 * if some of a patch data(such as so, dex) is deleted,
 * we will try to repair them via RepairPatch
 * you can implement your own patch processor class
 */
public class RepairPatch extends AbstractPatch {
    private static final String TAG = "RepairPatch";

    @Override
    public boolean tryPatch(Context context, String tempPatchPath) {

        Tinker manager = Tinker.with(context);

        final File patchFile = new File(tempPatchPath);

        if (!manager.isTinkerEnabled() || !ShareTinkerInternals.isTinkerEnableWithSharedPreferences(context)) {
            TinkerLog.e(TAG, "RepairPatch tryPatch:patch is disabled, just return");
            return false;
        }

        if (!patchFile.isFile() || !patchFile.exists()) {
            TinkerLog.e(TAG, "RepairPatch tryPatch:patch file is not found, just return");
            return false;
        }
        //check the signature, we should create a new checker
        ShareSecurityCheck signatureCheck = new ShareSecurityCheck(context);


        int returnCode = ShareTinkerInternals.checkSignatureAndTinkerID(context, patchFile, signatureCheck);
        if (returnCode != 0) {
            TinkerLog.e(TAG, "RepairPatch tryPatch:onPatchPackageCheckFail");
            manager.getPatchReporter().onPatchPackageCheckFail(patchFile, false, returnCode);
            return false;
        }

        //it is a old patch, so we should find a exist
        SharePatchInfo oldInfo = manager.getTinkerLoadResultIfPresent().patchInfo;
        String patchMd5 = SharePatchFileUtil.getMD5(patchFile);

        if (oldInfo == null) {
            TinkerLog.e(TAG, "OldPatchProcessor tryPatch:onPatchVersionCheckFail, oldInfo is null");
            manager.getPatchReporter().onPatchVersionCheckFail(patchFile, oldInfo, patchMd5, false);
            return false;
        } else {
            if (oldInfo.oldVersion == null || oldInfo.newVersion == null) {
                TinkerLog.e(TAG, "RepairPatch tryPatch:onPatchInfoCorrupted");
                manager.getPatchReporter().onPatchInfoCorrupted(patchFile, oldInfo.oldVersion, oldInfo.newVersion, false);
                return false;
            }
            //already have patch
            if (!oldInfo.oldVersion.equals(patchMd5) || !oldInfo.newVersion.equals(patchMd5)) {
                TinkerLog.e(TAG, "RepairPatch tryPatch:onPatchVersionCheckFail");
                manager.getPatchReporter().onPatchVersionCheckFail(patchFile, oldInfo, patchMd5, false);
                return false;
            }
        }

        //check ok
        final String patchDirectory = manager.getPatchDirectory().getAbsolutePath();

        final String patchName = SharePatchFileUtil.getPatchVersionDirectory(patchMd5);

        final String patchVersionDirectory = patchDirectory + "/" + patchName;

        if (!DexDiffPatchInternal.tryRecoverDexFiles(manager, signatureCheck, context, patchVersionDirectory, patchFile, false)) {
            TinkerLog.e(TAG, "RepairPatch tryPatch:try patch dex failed");
            return false;
        }

        if (!BsDiffPatchInternal.tryRecoverLibraryFiles(manager, signatureCheck, context, patchVersionDirectory, patchFile, false)) {
            TinkerLog.e(TAG, "RepairPatch tryPatch:try patch library failed");
            return false;
        }
        return true;
    }

}
