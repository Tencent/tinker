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
import android.os.Looper;
import android.os.MessageQueue;

import com.tencent.tinker.lib.reporter.DefaultLoadReporter;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;

import tinker.sample.android.util.UpgradePatchRetry;

/**
 * optional, you can just use DefaultLoadReporter
 * Created by zhangshaowen on 16/4/13.
 */
public class SampleLoadReporter extends DefaultLoadReporter {
    private final static String TAG = "Tinker.SampleLoadReporter";

    public SampleLoadReporter(Context context) {
        super(context);
    }

    @Override
    public void onLoadPatchListenerReceiveFail(final File patchFile, int errorCode) {
        super.onLoadPatchListenerReceiveFail(patchFile, errorCode);
        SampleTinkerReport.onTryApplyFail(errorCode);
    }

    @Override
    public void onLoadResult(File patchDirectory, int loadCode, long cost) {
        super.onLoadResult(patchDirectory, loadCode, cost);
        switch (loadCode) {
            case ShareConstants.ERROR_LOAD_OK:
                SampleTinkerReport.onLoaded(cost);
                break;
        }
        Looper.getMainLooper().myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
            @Override public boolean queueIdle() {
                UpgradePatchRetry.getInstance(context).onPatchRetryLoad();
                return false;
            }
        });
    }
    @Override
    public void onLoadException(Throwable e, int errorCode) {
        super.onLoadException(e, errorCode);
        switch (errorCode) {
            case ShareConstants.ERROR_LOAD_EXCEPTION_UNCAUGHT:
                String uncaughtString = SharePatchFileUtil.checkTinkerLastUncaughtCrash(context);
                if (!ShareTinkerInternals.isNullOrNil(uncaughtString)) {
                    File laseCrashFile = SharePatchFileUtil.getPatchLastCrashFile(context);
                    SharePatchFileUtil.safeDeleteFile(laseCrashFile);
                    // found really crash reason
                    TinkerLog.e(TAG, "tinker uncaught real exception:" + uncaughtString);
                }
                break;
        }
        SampleTinkerReport.onLoadException(e, errorCode);
    }

    @Override
    public void onLoadFileMd5Mismatch(File file, int fileType) {
        super.onLoadFileMd5Mismatch(file, fileType);
        SampleTinkerReport.onLoadFileMisMatch(fileType);
    }

    /**
     * try to recover patch oat file
     * @param file
     * @param fileType
     * @param isDirectory
     */
    @Override
    public void onLoadFileNotFound(File file, int fileType, boolean isDirectory) {
        TinkerLog.i(TAG, "patch loadReporter onLoadFileNotFound: patch file not found: %s, fileType:%d, isDirectory:%b",
            file.getAbsolutePath(), fileType, isDirectory);

        // only try to recover opt file
        // check dex opt file at last, some phone such as VIVO/OPPO like to change dex2oat to interpreted
        if (fileType == ShareConstants.TYPE_DEX_OPT) {
            Tinker tinker = Tinker.with(context);
            //we can recover at any process except recover process
            if (tinker.isMainProcess()) {
                File patchVersionFile = tinker.getTinkerLoadResultIfPresent().patchVersionFile;
                if (patchVersionFile != null) {
                    if (UpgradePatchRetry.getInstance(context).onPatchListenerCheck(SharePatchFileUtil.getMD5(patchVersionFile))) {
                        TinkerLog.i(TAG, "try to repair oat file on patch process");
                        TinkerInstaller.onReceiveUpgradePatch(context, patchVersionFile.getAbsolutePath());
                    } else {
                        TinkerLog.i(TAG, "repair retry exceed must max time, just clean");
                        checkAndCleanPatch();
                    }
                }
            }
        } else {
            checkAndCleanPatch();
        }
        SampleTinkerReport.onLoadFileNotFound(fileType);
    }

    @Override
    public void onLoadPackageCheckFail(File patchFile, int errorCode) {
        super.onLoadPackageCheckFail(patchFile, errorCode);
        SampleTinkerReport.onLoadPackageCheckFail(errorCode);
    }

    @Override
    public void onLoadPatchInfoCorrupted(String oldVersion, String newVersion, File patchInfoFile) {
        super.onLoadPatchInfoCorrupted(oldVersion, newVersion, patchInfoFile);
        SampleTinkerReport.onLoadInfoCorrupted();
    }

    @Override
    public void onLoadPatchVersionChanged(String oldVersion, String newVersion, File patchDirectoryFile, String currentPatchName) {
        super.onLoadPatchVersionChanged(oldVersion, newVersion, patchDirectoryFile, currentPatchName);
    }

}
