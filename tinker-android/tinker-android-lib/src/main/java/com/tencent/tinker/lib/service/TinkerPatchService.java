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

import static com.tencent.tinker.lib.util.TinkerServiceInternals.getTinkerPatchServiceName;

import android.app.ActivityManager;
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
import com.tencent.tinker.lib.tinker.TinkerApplicationHelper;
import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public class TinkerPatchService extends IntentService {
    private static final String TAG = "Tinker.TinkerPatchService";

    private static final String PATCH_PATH_EXTRA = "patch_path_extra";
    private static final String RESULT_CLASS_EXTRA = "patch_result_class";

    private static AbstractPatch upgradePatchProcessor = null;
    private static int notificationId = ShareConstants.TINKER_PATCH_SERVICE_NOTIFICATION;
    private static Class<? extends AbstractResultService> resultServiceClass = null;

    public TinkerPatchService() {
        super("TinkerPatchService");
    }

    public static void runPatchService(final Context context, final String path) {
        ShareTinkerLog.i(TAG, "run patch service...");
        Intent intent = new Intent(context, TinkerPatchService.class);
        intent.putExtra(PATCH_PATH_EXTRA, path);
        intent.putExtra(RESULT_CLASS_EXTRA, resultServiceClass.getName());
        try {
            context.startService(intent);
        } catch (Throwable thr) {
            ShareTinkerLog.e(TAG, "run patch service fail, exception:" + thr);
        }
    }

    public static void setPatchProcessor(AbstractPatch upgradePatch, Class<? extends AbstractResultService> serviceClass) {
        upgradePatchProcessor = upgradePatch;
        resultServiceClass = serviceClass;
        //try to load
        try {
            Class.forName(serviceClass.getName());
        } catch (ClassNotFoundException e) {
            ShareTinkerLog.printErrStackTrace(TAG, e, "patch processor class not found.");
        }
    }

    public static String getPatchPathExtra(Intent intent) {
        if (intent == null) {
            throw new TinkerRuntimeException("getPatchPathExtra, but intent is null");
        }
        return ShareIntentUtil.getStringExtra(intent, PATCH_PATH_EXTRA);
    }

    public static String getPatchResultExtra(Intent intent) {
        if (intent == null) {
            throw new TinkerRuntimeException("getPatchResultExtra, but intent is null");
        }
        return ShareIntentUtil.getStringExtra(intent, RESULT_CLASS_EXTRA);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        increasingPriority();
        doApplyPatch(this, intent);
    }

    /**
     * set the tinker notification id you want
     * @param id
     */
    public static void setTinkerNotificationId(int id) {
        notificationId = id;
    }

    private static final String RUNNING_MARKER_FILE_RELPATH_PREFIX = "patch_service_status/running_";

    /**
     * Check if TinkerPatchService is running.
     * @param context
     */
    public static boolean isRunning(Context context) {
        try {
            final String serviceName = getTinkerPatchServiceName(context);
            if (serviceName == null) {
                return false;
            }
            final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return false;
            }
            final List<ActivityManager.RunningAppProcessInfo> runningProcInfos = am.getRunningAppProcesses();
            if (runningProcInfos == null || runningProcInfos.size() == 0) {
                return false;
            }
            int targetPid = 0;
            for (ActivityManager.RunningAppProcessInfo procInfo : runningProcInfos) {
                if (procInfo.processName.equals(serviceName)) {
                    targetPid = procInfo.pid;
                    break;
                }
            }
            if (targetPid == 0) {
                return false;
            }
            final File tinkerBaseDir = SharePatchFileUtil.getPatchDirectory(context);
            final File runningMarkerFile = new File(tinkerBaseDir, RUNNING_MARKER_FILE_RELPATH_PREFIX + targetPid);
            return runningMarkerFile.exists();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void markRunning(Context context) {
        final File tinkerBaseDir = SharePatchFileUtil.getPatchDirectory(context);
        final File runningMarkerFile = new File(tinkerBaseDir,
                RUNNING_MARKER_FILE_RELPATH_PREFIX + android.os.Process.myPid());
        if (runningMarkerFile.exists()) {
            return;
        }
        final File runningMarkerDir = runningMarkerFile.getParentFile();
        if (runningMarkerDir.exists()) {
            final File[] markerFiles = runningMarkerDir.listFiles();
            if (markerFiles != null) {
                for (File markerFile : markerFiles) {
                    markerFile.delete();
                }
            }
        } else {
            runningMarkerDir.mkdirs();
        }
        try {
            if (!runningMarkerFile.createNewFile()) {
                throw new IllegalStateException();
            }
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "Fail to create running marker file.");
        }
    }

    static void unmarkRunning(Context context) {
        final File tinkerBaseDir = SharePatchFileUtil.getPatchDirectory(context);
        final File runningMarkerFile = new File(tinkerBaseDir,
                RUNNING_MARKER_FILE_RELPATH_PREFIX + android.os.Process.myPid());
        if (runningMarkerFile.exists()) {
            runningMarkerFile.delete();
        }
    }

    private static AtomicBoolean sIsPatchApplying = new AtomicBoolean(false);

    private static void doApplyPatch(Context context, Intent intent) {
        // Since we may retry with IntentService, we should prevent
        // racing here again.
        if (!sIsPatchApplying.compareAndSet(false, true)) {
            ShareTinkerLog.w(TAG, "TinkerPatchService doApplyPatch is running by another runner.");
            return;
        }

        try {
            markRunning(context);

            Tinker tinker = Tinker.with(context);
            tinker.getPatchReporter().onPatchServiceStart(intent);

            if (intent == null) {
                ShareTinkerLog.e(TAG, "TinkerPatchService received a null intent, ignoring.");
                return;
            }
            String path = getPatchPathExtra(intent);
            if (path == null) {
                ShareTinkerLog.e(TAG, "TinkerPatchService can't get the path extra, ignoring.");
                return;
            }
            File patchFile = new File(path);

            long begin = SystemClock.elapsedRealtime();
            boolean result;
            long cost;
            Throwable e = null;

            PatchResult patchResult = new PatchResult();
            try {
                if (upgradePatchProcessor == null) {
                    throw new TinkerRuntimeException("upgradePatchProcessor is null.");
                }
                result = upgradePatchProcessor.tryPatch(context, path, patchResult);
            } catch (Throwable throwable) {
                e = throwable;
                result = false;
                tinker.getPatchReporter().onPatchException(patchFile, e);
            }

            cost = SystemClock.elapsedRealtime() - begin;
            tinker.getPatchReporter()
                    .onPatchResult(patchFile, result, cost);

            patchResult.isSuccess = result;
            patchResult.rawPatchFilePath = path;
            patchResult.costTime = cost;
            patchResult.e = e;

            unmarkRunning(context);
            sIsPatchApplying.set(false);

            AbstractResultService.runResultService(context, patchResult, getPatchResultExtra(intent));
        } finally {
            unmarkRunning(context);
        }
    }

    private void increasingPriority() {
        if (Build.VERSION.SDK_INT >= 26) {
            ShareTinkerLog.i(TAG, "for system version >= Android O, we just ignore increasingPriority "
                    + "job to avoid crash or toasts.");
            return;
        }

        if ("ZUK".equals(Build.MANUFACTURER)) {
            ShareTinkerLog.i(TAG, "for ZUK device, we just ignore increasingPriority "
                    + "job to avoid crash.");
            return;
        }

        ShareTinkerLog.i(TAG, "try to increase patch process priority");
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
            ShareTinkerLog.i(TAG, "try to increase patch process priority error:" + e);
        }
    }

    /**
     * I don't want to do this, believe me
     */
    public static class InnerService extends Service {
        @Override
        public void onCreate() {
            super.onCreate();
            try {
                startForeground(notificationId, new Notification());
            } catch (Throwable e) {
                ShareTinkerLog.e(TAG, "InnerService set service for push exception:%s.", e);
            }
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

