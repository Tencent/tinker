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
import com.tencent.tinker.loader.shareutil.SharePatchInfo;

import java.io.File;

import tinker.sample.android.util.UpgradePatchRetry;

/**
 * optional, you can just use DefaultPatchReporter
 * Created by zhangshaowen on 16/4/8.
 */
public class SamplePatchReporter extends DefaultPatchReporter {
    public SamplePatchReporter(Context context) {
        super(context);
    }

    @Override
    public void onPatchServiceStart(Intent intent) {
        super.onPatchServiceStart(intent);
        SampleTinkerReport.onApplyPatchServiceStart();
        UpgradePatchRetry.getInstance(context).onPatchServiceStart(intent);
    }

    @Override
    public void onPatchDexOptFail(File patchFile, File dexFile, String optDirectory, String dexName, Throwable t, boolean isUpgradePatch) {
        super.onPatchDexOptFail(patchFile, dexFile, optDirectory, dexName, t, isUpgradePatch);
        SampleTinkerReport.onApplyDexOptFail(t);
    }

    @Override
    public void onPatchException(File patchFile, Throwable e, boolean isUpgradePatch) {
        super.onPatchException(patchFile, e, isUpgradePatch);
        SampleTinkerReport.onApplyCrash(e);
    }

    @Override
    public void onPatchInfoCorrupted(File patchFile, String oldVersion, String newVersion, boolean isUpgradePatch) {
        super.onPatchInfoCorrupted(patchFile, oldVersion, newVersion, isUpgradePatch);
        SampleTinkerReport.onApplyInfoCorrupted();
    }

    @Override
    public void onPatchPackageCheckFail(File patchFile, boolean isUpgradePatch, int errorCode) {
        super.onPatchPackageCheckFail(patchFile, isUpgradePatch, errorCode);
        SampleTinkerReport.onApplyPackageCheckFail(errorCode);
    }

    @Override
    public void onPatchResult(File patchFile, boolean success, long cost, boolean isUpgradePatch) {
        super.onPatchResult(patchFile, success, cost, isUpgradePatch);
        SampleTinkerReport.onApplied(isUpgradePatch, cost, success);
        UpgradePatchRetry.getInstance(context).onPatchServiceResult(isUpgradePatch);
    }

    @Override
    public void onPatchTypeExtractFail(File patchFile, File extractTo, String filename, int fileType, boolean isUpgradePatch) {
        super.onPatchTypeExtractFail(patchFile, extractTo, filename, fileType, isUpgradePatch);
        SampleTinkerReport.onApplyExtractFail(fileType);
    }

    @Override
    public void onPatchVersionCheckFail(File patchFile, SharePatchInfo oldPatchInfo, String patchFileVersion, boolean isUpgradePatch) {
        super.onPatchVersionCheckFail(patchFile, oldPatchInfo, patchFileVersion, isUpgradePatch);
        SampleTinkerReport.onApplyVersionCheckFail();
    }
}
