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

import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.support.annotation.Nullable;

import com.tencent.tinker.lib.patch.AbstractPatch;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Created by zhangshaowen on 16/3/14.
 */
public class TinkerPatchService {
    private static final String TAG = "Tinker.TinkerPatchService";

    private static final String PATCH_PATH_EXTRA = "patch_path_extra";
    private static final String RESULT_CLASS_EXTRA = "patch_result_class";
    private static final int MIN_SDKVER_TO_USE_JOBSCHEDULER = 26;

    private static AbstractPatch upgradePatchProcessor = null;
    private static int notificationId = ShareConstants.TINKER_PATCH_SERVICE_NOTIFICATION;
    private static Class<? extends AbstractResultService> resultServiceClass = null;
    private static Handler mHandler = new Handler(Looper.getMainLooper());

    public static void runPatchService(final Context context, final String path) {
        try {
            if (Build.VERSION.SDK_INT < MIN_SDKVER_TO_USE_JOBSCHEDULER) {
                runPatchServiceByIntentService(context, path);
            } else {
                if (!runPatchServiceByJobScheduler(context, path)) {
                    TinkerLog.e(TAG, "start patch job service fail, try to fallback to intent service.");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // This method will tell us whether the intent service or the job scheduler
                            // is running.
                            TinkerLog.i(TAG, "fallback: prepare trying to run patch service by intent service.");
                            if (!TinkerServiceInternals.isTinkerPatchServiceRunning(context)) {
                                runPatchServiceByIntentService(context, path);
                            }
                        }
                    }, TimeUnit.SECONDS.toMillis(3));
                }
            }
        } catch (Throwable throwable) {
            TinkerLog.e(TAG, "start patch service fail, exception:" + throwable);
        }
    }

    private static void runPatchServiceByIntentService(Context context, String path) {
        TinkerLog.i(TAG, "run patch service by intent service.");
        Intent intent = new Intent(context, IntentServiceRunner.class);
        intent.putExtra(PATCH_PATH_EXTRA, path);
        intent.putExtra(RESULT_CLASS_EXTRA, resultServiceClass.getName());
        context.startService(intent);
    }

    @TargetApi(21)
    private static boolean runPatchServiceByJobScheduler(Context context, String path) {
        TinkerLog.i(TAG, "run patch service by job scheduler.");
        final JobInfo.Builder jobInfoBuilder = new JobInfo.Builder(
                1, new ComponentName(context, JobServiceRunner.class)
        );
        final PersistableBundle extras = new PersistableBundle();
        extras.putString(PATCH_PATH_EXTRA, path);
        extras.putString(RESULT_CLASS_EXTRA, resultServiceClass.getName());
        jobInfoBuilder.setExtras(extras);
        jobInfoBuilder.setOverrideDeadline(5);
        final JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (jobScheduler == null) {
            TinkerLog.e(TAG, "jobScheduler is null.");
            return false;
        }
        return (jobScheduler.schedule(jobInfoBuilder.build()) == JobScheduler.RESULT_SUCCESS);
    }

    public static void setPatchProcessor(AbstractPatch upgradePatch, Class<? extends AbstractResultService> serviceClass) {
        upgradePatchProcessor = upgradePatch;
        resultServiceClass = serviceClass;
        //try to load
        try {
            Class.forName(serviceClass.getName());
        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
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

    public static Class<? extends Service> getRealRunnerClass() {
        if (Build.VERSION.SDK_INT < MIN_SDKVER_TO_USE_JOBSCHEDULER) {
            return IntentServiceRunner.class;
        } else {
            return JobServiceRunner.class;
        }
    }

    /**
     * set the tinker notification id you want
     * @param id
     */
    public static void setTinkerNotificationId(int id) {
        notificationId = id;
    }

    private static void doApplyPatch(Context context, Intent intent) {
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
        tinker.getPatchReporter().
            onPatchResult(patchFile, result, cost);

        patchResult.isSuccess = result;
        patchResult.rawPatchFilePath = path;
        patchResult.costTime = cost;
        patchResult.e = e;

        AbstractResultService.runResultService(context, patchResult, getPatchResultExtra(intent));
    }

    public static class IntentServiceRunner extends IntentService {

        public IntentServiceRunner() {
            super("TinkerPatchService");
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            increasingPriority();
            doApplyPatch(getApplicationContext(), intent);
        }

        private void increasingPriority() {
//        if (Build.VERSION.SDK_INT > 24) {
//            TinkerLog.i(TAG, "for Android 7.1, we just ignore increasingPriority job");
//            return;
//        }
            if (Build.VERSION.SDK_INT >= 26) {
                TinkerLog.i(TAG, "for system version >= Android O, we just ignore increasingPriority "
                        + "job to avoid crash or toasts.");
                return;
            }

            if ("ZUK".equals(Build.MANUFACTURER)) {
                TinkerLog.i(TAG, "for ZUK device, we just ignore increasingPriority "
                        + "job to avoid crash.");
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static class JobServiceRunner extends JobService {
        private JobAsyncTask mTask = null;

        @Override
        public boolean onStartJob(JobParameters params) {
            mTask = new JobAsyncTask(this);
            mTask.execute(params);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            TinkerLog.w(TAG, "Stopping TinkerPatchJob service.");
            if (mTask != null) {
                mTask.cancel(true);
                mTask = null;
            }
            return false;
        }

        private static class JobAsyncTask extends AsyncTask<JobParameters, Void, Void> {
            private final WeakReference<JobService> mHolderRef;

            JobAsyncTask(JobService holder) {
                mHolderRef = new WeakReference<>(holder);
            }

            @Override
            protected Void doInBackground(JobParameters... paramsList) {
                final JobParameters params = paramsList[0];
                final PersistableBundle extras = params.getExtras();
                final Intent paramIntent = new Intent();
                paramIntent.putExtra(PATCH_PATH_EXTRA, extras.getString(PATCH_PATH_EXTRA));
                paramIntent.putExtra(RESULT_CLASS_EXTRA, extras.getString(RESULT_CLASS_EXTRA));
                final JobService holder = mHolderRef.get();
                if (holder == null) {
                    TinkerLog.e(TAG, "unexpected case: holder job service is null.");
                    return null;
                }
                doApplyPatch(holder.getApplicationContext(), paramIntent);
                notifyFinished(params);
                return null;
            }

            private void notifyFinished(JobParameters params) {
                final JobService holder = mHolderRef.get();
                if (holder != null) {
                    holder.jobFinished(params, false);
                }
            }
        }
    }
}

