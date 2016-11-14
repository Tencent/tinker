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

package tinker.sample.android.util;

import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import tinker.sample.android.reporter.SampleTinkerReport;

/**
 * optional
 * tinker :patch process may killed by some reason, we can retry it to increase upgrade success rate
 * if patch file is at sdcard, copy it to dataDir first. because some software may delete it.
 *
 * Created by zhangshaowen on 16/7/3.
 */
public class UpgradePatchRetry {
    private static final String TAG = "Tinker.UpgradePatchRetry";

    private static final String RETRY_INFO_NAME = "patch.retry";
    private static final String TEMP_PATCH_NAME = "temp.apk";

    private static final String RETRY_FILE_MD5_PROPERTY = "md5";
    private static final String RETRY_COUNT_PROPERTY    = "times";
    private static final int    RETRY_MAX_COUNT         = 3;

    private boolean isRetryEnable = false;
    private File    retryInfoFile = null;
    private File    tempPatchFile = null;

    private Context context = null;
    private static UpgradePatchRetry sInstance;

    /**
     * you must set after tinker has installed
     *
     * @param context
     */
    public UpgradePatchRetry(Context context) {
        this.context = context;
        retryInfoFile = new File(SharePatchFileUtil.getPatchDirectory(context), RETRY_INFO_NAME);
        tempPatchFile = new File(SharePatchFileUtil.getPatchDirectory(context), TEMP_PATCH_NAME);
    }

    public static UpgradePatchRetry getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new UpgradePatchRetry(context);
        }
        return sInstance;
    }

    public void onPatchRetryLoad() {
        if (!isRetryEnable) {
            TinkerLog.w(TAG, "onPatchRetryLoad retry disabled, just return");
            return;
        }
        Tinker tinker = Tinker.with(context);
        //only retry on main process
        if (!tinker.isMainProcess()) {
            TinkerLog.w(TAG, "onPatchRetryLoad retry is not main process, just return");
            return;
        }

        if (!retryInfoFile.exists()) {
            TinkerLog.w(TAG, "onPatchRetryLoad retry info not exist, just return");
            return;
        }

        if (TinkerServiceInternals.isTinkerPatchServiceRunning(context)) {
            TinkerLog.w(TAG, "onPatchRetryLoad tinker service is running, just return");
            return;
        }
        //must use temp file
        String path = tempPatchFile.getAbsolutePath();
        if (path == null || !new File(path).exists()) {
            TinkerLog.w(TAG, "onPatchRetryLoad patch file: %s is not exist, just return", path);
            return;
        }
        TinkerLog.w(TAG, "onPatchRetryLoad patch file: %s is exist, retry to patch", path);
        TinkerInstaller.onReceiveUpgradePatch(context, path);
        SampleTinkerReport.onReportRetryPatch();
    }

    private void copyToTempFile(File patchFile) {
        if (patchFile.getAbsolutePath().equals(tempPatchFile.getAbsolutePath())) {
            return;
        }
        TinkerLog.w(TAG, "try copy file: %s to %s", patchFile.getAbsolutePath(), tempPatchFile.getAbsolutePath());

        try {
            SharePatchFileUtil.copyFileUsingStream(patchFile, tempPatchFile);
        } catch (IOException e) {
        }
    }

    public void onPatchServiceStart(Intent intent) {
        if (!isRetryEnable) {
            TinkerLog.w(TAG, "onPatchServiceStart retry disabled, just return");
            return;
        }

        if (intent == null) {
            TinkerLog.e(TAG, "onPatchServiceStart intent is null, just return");
            return;
        }

        boolean isUpgrade = TinkerPatchService.getPatchUpgradeExtra(intent);

        if (!isUpgrade) {
            TinkerLog.w(TAG, "onPatchServiceStart is not upgrade patch, just return");
            return;
        }

        String path = TinkerPatchService.getPatchPathExtra(intent);

        if (path == null) {
            TinkerLog.w(TAG, "onPatchServiceStart patch path is null, just return");
            return;
        }

        RetryInfo retryInfo;
        File patchFile = new File(path);

        String patchMd5 = SharePatchFileUtil.getMD5(patchFile);
        if (patchMd5 == null) {
            TinkerLog.w(TAG, "onPatchServiceStart patch md5 is null, just return");
            return;
        }

        if (retryInfoFile.exists()) {
            retryInfo = RetryInfo.readRetryProperty(retryInfoFile);
            if (retryInfo.md5 == null || retryInfo.times == null || !patchMd5.equals(retryInfo.md5)) {
                copyToTempFile(patchFile);
                retryInfo.md5 = patchMd5;
                retryInfo.times = "1";
            } else {
                int nowTimes = Integer.parseInt(retryInfo.times);
                if (nowTimes >= RETRY_MAX_COUNT) {
                    SharePatchFileUtil.safeDeleteFile(retryInfoFile);
                    SharePatchFileUtil.safeDeleteFile(tempPatchFile);
                    TinkerLog.w(TAG, "onPatchServiceStart retry more than max count, delete retry info file!");
                    return;
                } else {
                    retryInfo.times = String.valueOf(nowTimes + 1);
                }
            }

        } else {
            copyToTempFile(patchFile);
            retryInfo = new RetryInfo(patchMd5, "1");
        }

        RetryInfo.writeRetryProperty(retryInfoFile, retryInfo);

    }

    /**
     * if we receive any result, we can delete the temp retry info file
     *
     * @param isUpgradePatch
     */
    public void onPatchServiceResult(boolean isUpgradePatch) {
        if (!isRetryEnable) {
            TinkerLog.w(TAG, "onPatchServiceResult retry disabled, just return");
            return;
        }

        if (!isUpgradePatch) {
            TinkerLog.w(TAG, "onPatchServiceResult is not upgrade patch, just return");
            return;
        }

        //delete info file
        if (retryInfoFile.exists()) {
            SharePatchFileUtil.safeDeleteFile(retryInfoFile);
        }
        //delete temp patch file
        if (tempPatchFile.exists()) {
            SharePatchFileUtil.safeDeleteFile(tempPatchFile);
        }
    }

    public void setRetryEnable(boolean enable) {
        isRetryEnable = enable;
    }

    static class RetryInfo {
        String md5;
        String times;

        RetryInfo(String md5, String times) {
            this.md5 = md5;
            this.times = times;
        }

        static RetryInfo readRetryProperty(File infoFile) {
            String md5 = null;
            String times = null;

            Properties properties = new Properties();
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(infoFile);
                properties.load(inputStream);
                md5 = properties.getProperty(RETRY_FILE_MD5_PROPERTY);
                times = properties.getProperty(RETRY_COUNT_PROPERTY);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                SharePatchFileUtil.closeQuietly(inputStream);
            }

            return new RetryInfo(md5, times);
        }

        static void writeRetryProperty(File infoFile, RetryInfo info) {
            if (info == null) {
                return;
            }

            File parentFile = infoFile.getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }

            Properties newProperties = new Properties();
            newProperties.put(RETRY_FILE_MD5_PROPERTY, info.md5);
            newProperties.put(RETRY_COUNT_PROPERTY, info.times);
            FileOutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(infoFile, false);
                newProperties.store(outputStream, null);
            } catch (Exception e) {
//                e.printStackTrace();
                TinkerLog.printErrStackTrace(TAG, e, "retry write property fail");
            } finally {
                SharePatchFileUtil.closeQuietly(outputStream);
            }

        }
    }
}
