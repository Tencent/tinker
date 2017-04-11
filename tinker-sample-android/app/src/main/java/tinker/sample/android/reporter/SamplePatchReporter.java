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

package tinker.sample.android.reporter;

import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.lib.reporter.DefaultPatchReporter;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;

import java.io.File;
import java.util.List;

import tinker.sample.android.util.UpgradePatchRetry;

/**
 * optional, you can just use DefaultPatchReporter
 * Created by zhangshaowen on 16/4/8.
 */
public class SamplePatchReporter extends DefaultPatchReporter {
    private final static String TAG = "Tinker.SamplePatchReporter";

    private static boolean shouldRetry = false;

    public SamplePatchReporter(Context context) {
        super(context);
    }

    @Override
    public void onPatchServiceStart(Intent intent) {
        super.onPatchServiceStart(intent);
        shouldRetry = false;
        SampleTinkerReport.onApplyPatchServiceStart();
        UpgradePatchRetry.getInstance(context).onPatchServiceStart(intent);
    }

    /**
     * warning, do use its super method!
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
            Tinker.with(context).cleanPatchByVersion(patchFile);
        }
        SampleTinkerReport.onApplyDexOptFail(t);
    }

    private void deleteOptFiles(List<File> dexFiles) {
        for (File file : dexFiles) {
            SharePatchFileUtil.safeDeleteFile(file);
        }
    }

    @Override
    public void onPatchException(File patchFile, Throwable e) {
        super.onPatchException(patchFile, e);
        SampleTinkerReport.onApplyCrash(e);
    }

    @Override
    public void onPatchInfoCorrupted(File patchFile, String oldVersion, String newVersion) {
        super.onPatchInfoCorrupted(patchFile, oldVersion, newVersion);
        SampleTinkerReport.onApplyInfoCorrupted();
    }

    @Override
    public void onPatchPackageCheckFail(File patchFile, int errorCode) {
        super.onPatchPackageCheckFail(patchFile, errorCode);
        SampleTinkerReport.onApplyPackageCheckFail(errorCode);
    }

    @Override
    public void onPatchResult(File patchFile, boolean success, long cost) {
        super.onPatchResult(patchFile, success, cost);
        SampleTinkerReport.onApplied(cost, success);
        // if should retry don't delete the temp file
        if (!shouldRetry) {
            UpgradePatchRetry.getInstance(context).onPatchServiceResult();
        }
    }

    @Override
    public void onPatchTypeExtractFail(File patchFile, File extractTo, String filename, int fileType) {
        super.onPatchTypeExtractFail(patchFile, extractTo, filename, fileType);
        SampleTinkerReport.onApplyExtractFail(fileType);
    }

    @Override
    public void onPatchVersionCheckFail(File patchFile, SharePatchInfo oldPatchInfo, String patchFileVersion) {
        super.onPatchVersionCheckFail(patchFile, oldPatchInfo, patchFileVersion);
        SampleTinkerReport.onApplyVersionCheckFail();
    }
}
