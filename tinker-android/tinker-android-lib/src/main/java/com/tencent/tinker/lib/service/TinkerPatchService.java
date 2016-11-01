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

package com.tencent.tinker.lib.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import com.tencent.tinker.lib.patch.AbstractPatch;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public class TinkerPatchService extends IntentService {
    private static final String TAG = "Tinker.TinkerPatchService";

    private static final String        PATCH_PATH_EXTRA      = "patch_path_extra";
    private static final String        PATCH_NEW_EXTRA       = "patch_new_extra";
    private static       AbstractPatch upgradePatchProcessor = null;
    private static       AbstractPatch repairPatchProcessor  = null;
    private static       int           notificationId        = ShareConstants.TINKER_PATCH_SERVICE_NOTIFICATION;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     */
    public TinkerPatchService() {
        super(TinkerPatchService.class.getSimpleName());
    }

    public static void runPatchService(Context context, String path, boolean isUpgradePatch) {
        Intent intent = new Intent(context, TinkerPatchService.class);
        intent.putExtra(PATCH_PATH_EXTRA, path);
        intent.putExtra(PATCH_NEW_EXTRA, isUpgradePatch);

        context.startService(intent);
    }

    public static void setPatchProcessor(AbstractPatch upgradePatch, AbstractPatch repairPatch) {
        upgradePatchProcessor = upgradePatch;
        repairPatchProcessor = repairPatch;
    }

    public static String getPatchPathExtra(Intent intent) {
        if (intent == null) {
            throw new TinkerRuntimeException("getPatchPathExtra, but intent is null");
        }
        return ShareIntentUtil.getStringExtra(intent, PATCH_PATH_EXTRA);
    }

    public static boolean getPatchUpgradeExtra(Intent intent) {
        if (intent == null) {
            throw new TinkerRuntimeException("getPatchUpgradeExtra, but intent is null");
        }
        return ShareIntentUtil.getBooleanExtra(intent, PATCH_NEW_EXTRA, false);
    }

    /**
     * set the tinker notification id you want
     * @param id
     */
    public static void setTinkerNotificationId(int id) {
        notificationId = id;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final Context context = getApplicationContext();
        Tinker tinker = Tinker.with(context);
        tinker.getPatchReporter().onPatchServiceStart(intent);

        if (intent == null) {
            TinkerLog.e(TAG, "TinkerPatchService received a null intent, ignoring.");
            return;
        }
        String path = getPatchPathExtra(intent);
        if (path == null) {
            TinkerLog.e(TAG, "TinkerPatchService can't get the path extra, ignoring.");
            return;
        }
        File patchFile = new File(path);

        boolean isUpgradePatch = getPatchUpgradeExtra(intent);

        long begin = SystemClock.elapsedRealtime();
        boolean result;
        long cost;
        Throwable e = null;

        increasingPriority();
        PatchResult patchResult = new PatchResult();
        try {
            if (isUpgradePatch) {
                if (upgradePatchProcessor == null) {
                    throw new TinkerRuntimeException("upgradePatchProcessor is null.");
                }
                result = upgradePatchProcessor.tryPatch(context, path, patchResult);

            } else {
                //just recover from exist patch
                if (repairPatchProcessor == null) {
                    throw new TinkerRuntimeException("upgradePatchProcessor is null.");
                }
                result = repairPatchProcessor.tryPatch(context, path, patchResult);
            }
        } catch (Throwable throwable) {
            e = throwable;
            result = false;
            tinker.getPatchReporter().onPatchException(patchFile, e, isUpgradePatch);
        }

        cost = SystemClock.elapsedRealtime() - begin;
        tinker.getPatchReporter().
            onPatchResult(patchFile, result, cost, isUpgradePatch);

        patchResult.isSuccess = result;
        patchResult.isUpgradePatch = isUpgradePatch;
        patchResult.rawPatchFilePath = path;
        patchResult.costTime = cost;
        patchResult.e = e;

        AbstractResultService.runResultService(context, patchResult);

    }

    private void increasingPriority() {
        if (Build.VERSION.SDK_INT > 24) {
            TinkerLog.i(TAG, "for Android 7.1, we just ignore increasingPriority job");
            return;
        }
        TinkerLog.i(TAG, "try to increase patch process priority");
        try {
            Notification notification = new Notification();
            if (Build.VERSION.SDK_INT < 18) {
                startForeground(notificationId, notification);
            } else {
                startForeground(notificationId, notification);
                // start InnerService
                startService(new Intent(this, InnerService.class));
            }
        } catch (Throwable e) {
            TinkerLog.i(TAG, "try to increase patch process priority error:" + e);
        }
    }

    /**
     * I don't want to do this, believe me
     */
    //InnerService
    public static class InnerService extends Service {
        @Override
        public void onCreate() {
            super.onCreate();
            try {
                startForeground(notificationId, new Notification());
            } catch (Throwable e) {
                TinkerLog.e(TAG, "InnerService set service for push exception:%s.", e);
            }
            // kill
            stopSelf();
        }

        @Override
        public void onDestroy() {
            stopForeground(true);
            super.onDestroy();
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }

}

