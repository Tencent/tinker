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

package tinker.sample.android.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

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

/**
 * optional
 * tinker :patch process may killed by some reason, we can retry it to increase upgrade success rate
 * if patch file is at sdcard, copy it to dataDir first. because some software may delete it.
 * <p/>
 * Created by shwenzhang on 16/7/3.
 */
public class UpgradePatchRetry {
    private static final String TAG = "UpgradePatchRetry";

    private static final String RETRY_INFO_NAME = "patch.retry";
    private static final String TEMP_PATCH_NAME = "temp.apk";

    private static final String RETRY_FILE_PROPERTY  = "path";
    private static final String RETRY_COUNT_PROPERTY = "times";
    private static final int    RETRY_MAX_COUNT      = 2;

    private boolean isRetryEnable = false;
    private File    retryInfoFile = null;
    private File    tempPatchFile = null;

    private Context context = null;
    private String  dataDir = null;
    private static UpgradePatchRetry sInstance;

    /**
     * you must set after tinker has installed
     *
     * @param context
     */
    public UpgradePatchRetry(Context context) {
        this.context = context;
        ApplicationInfo appInfo = context.getApplicationInfo();
        if (appInfo != null) {
            this.dataDir = appInfo.dataDir;
        }
        retryInfoFile = new File(SharePatchFileUtil.getPatchDirectory(context), RETRY_INFO_NAME);
        tempPatchFile = new File(SharePatchFileUtil.getPatchDirectory(context), TEMP_PATCH_NAME);
    }

    public static UpgradePatchRetry getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new UpgradePatchRetry(context);
        }
        return sInstance;
    }

    /**
     * copy sdcard patch file to /data/data
     *
     * @param path
     * @param isUpgrade
     * @return
     */
    public String onPatchListenerPass(String path, boolean isUpgrade) {
        if (!isRetryEnable) {
            TinkerLog.w(TAG, "onPatchListenerPass retry disabled, just return");
            return path;
        }

        if (!isUpgrade) {
            TinkerLog.w(TAG, "onPatchListenerPass is not upgrade patch, just return");
            return path;
        }
        if (dataDir == null || path.startsWith(dataDir)) {
            TinkerLog.w(TAG, "onPatchListenerPass is already in dataDir:%s, just return", dataDir);
            return path;
        }
        File patchFile = new File(path);
        if (!patchFile.exists()) {
            TinkerLog.w(TAG, "onPatchListenerPass patch file is not exist, just return");
            return path;
        }

        if (patchFile.getAbsolutePath().equals(tempPatchFile.getAbsolutePath())) {
            TinkerLog.w(TAG, "onPatchListenerPass patch path is equal, just return");
            return path;
        }
        try {
            TinkerLog.w(TAG, "onPatchListenerPass copy %s to %s", path, tempPatchFile.getAbsolutePath());
            SharePatchFileUtil.copyFileUsingChannel(patchFile, tempPatchFile);
            return tempPatchFile.getAbsolutePath();
        } catch (IOException e) {
//            e.printStackTrace();
            SharePatchFileUtil.safeDeleteFile(tempPatchFile);
            TinkerLog.w(TAG, "onPatchListenerPass copy fail, just return");
            return path;
        }

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

        RetryInfo retryInfo = RetryInfo.readRetryProperty(retryInfoFile);
        String path = retryInfo.path;
        if (path == null || !new File(path).exists()) {
            TinkerLog.w(TAG, "onPatchRetryLoad patch file: %s is not exist, just return", path);
            return;
        }
        TinkerInstaller.onReceiveUpgradePatch(context, path);
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

        if (retryInfoFile.exists()) {
            retryInfo = RetryInfo.readRetryProperty(retryInfoFile);
            if (retryInfo.path == null || retryInfo.times == null || !path.equals(retryInfo.path)) {
                retryInfo.path = path;
                retryInfo.times = "1";
            } else {
                int nowTimes = Integer.parseInt(retryInfo.times);
                if (nowTimes >= RETRY_MAX_COUNT) {
                    SharePatchFileUtil.safeDeleteFile(retryInfoFile);
                    TinkerLog.w(TAG, "onPatchServiceStart retry more than max count, delete retry info file!");
                    return;
                } else {
                    retryInfo.times = String.valueOf(nowTimes + 1);
                }
            }

        } else {
            retryInfo = new RetryInfo(path, "1");
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
        String path;
        String times;

        RetryInfo(String path, String times) {
            this.path = path;
            this.times = times;
        }

        static RetryInfo readRetryProperty(File infoFile) {
            String path = null;
            String times = null;

            Properties properties = new Properties();
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(infoFile);
                properties.load(inputStream);
                path = properties.getProperty(RETRY_FILE_PROPERTY);
                times = properties.getProperty(RETRY_COUNT_PROPERTY);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                SharePatchFileUtil.closeQuietly(inputStream);
            }

            return new RetryInfo(path, times);
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
            newProperties.put(RETRY_FILE_PROPERTY, info.path);
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
