/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.tencent.tinker.lib.util;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobServiceEngine;
import android.app.job.JobWorkItem;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class is copied and renamed from JobIntentService in android support compat
 * library 28.0.0-alpha1. Some changes was made for adapting Tinker's specific usage.
 *
 * Since We can't get rid of the resources dependencies that android support compat library
 * has to include with, instead of hacking into the gradle dependency mechanism We choose to
 * copy what we need directly. Copy with renaming is to avoid conflicting when developers
 * happen to include android support compat library which has the same class in their App.
 *
 * Helper for processing work that has been enqueued for a job/service.  When running on
 * {@link android.os.Build.VERSION_CODES#O Android O} or later, the work will be dispatched
 * as a job via {@link android.app.job.JobScheduler#enqueue JobScheduler.enqueue}.  When running
 * on older versions of the platform, it will use
 * {@link android.content.Context#startService Context.startService}.
 *
 * <p>You must publish your subclass in your manifest for the system to interact with.  This
 * should be published as a {@link android.app.job.JobService}, as described for that class,
 * since on O and later platforms it will be executed that way.</p>
 *
 * <p>Use {@link #enqueueWork(Context, Class, int, Intent)} to enqueue new work to be
 * dispatched to and handled by your service.  It will be executed in
 * {@link #onHandleWork(Intent)}.</p>
 *
 * <p>You do not need to use {@link android.support.v4.content.WakefulBroadcastReceiver}
 * when using this class.  When running on {@link android.os.Build.VERSION_CODES#O Android O},
 * the JobScheduler will take care of wake locks for you (holding a wake lock from the time
 * you enqueue work until the job has been dispatched and while it is running).  When running
 * on previous versions of the platform, this wake lock handling is emulated in the class here
 * by directly calling the PowerManager; this means the application must request the
 * {@link android.Manifest.permission#WAKE_LOCK} permission.</p>
 *
 * <p>There are a few important differences in behavior when running on
 * {@link android.os.Build.VERSION_CODES#O Android O} or later as a Job vs. pre-O:</p>
 *
 * <ul>
 *     <li><p>When running as a pre-O service, the act of enqueueing work will generally start
 *     the service immediately, regardless of whether the device is dozing or in other
 *     conditions.  When running as a Job, it will be subject to standard JobScheduler
 *     policies for a Job with a {@link android.app.job.JobInfo.Builder#setOverrideDeadline(long)}
 *     of 0: the job will not run while the device is dozing, it may get delayed more than
 *     a service if the device is under strong memory pressure with lots of demand to run
 *     jobs.</p></li>
 *     <li><p>When running as a pre-O service, the normal service execution semantics apply:
 *     the service can run indefinitely, though the longer it runs the more likely the system
 *     will be to outright kill its process, and under memory pressure one should expect
 *     the process to be killed even of recently started services.  When running as a Job,
 *     the typical {@link android.app.job.JobService} execution time limit will apply, after
 *     which the job will be stopped (cleanly, not by killing the process) and rescheduled
 *     to continue its execution later.  Job are generally not killed when the system is
 *     under memory pressure, since the number of concurrent jobs is adjusted based on the
 *     memory state of the device.</p></li>
 * </ul>
 *
 * <p>Here is an example implementation of this class:</p>
 *
 * {@sample frameworks/support/samples/Support4Demos/src/main/java/com/example/android/supportv4/app/SimpleJobIntentService.java
 *      complete}
 */
public abstract class TinkerJobIntentService extends Service {
    static final String TAG = "TinkerJobIntentService";

    static final boolean DEBUG = false;

    CompatJobEngine mJobImpl;
    WorkEnqueuer mCompatWorkEnqueuer;
    CommandProcessor mCurProcessor;
    boolean mInterruptIfStopped = false;
    boolean mStopped = false;
    boolean mDestroyed = false;

    final ArrayList<CompatWorkItem> mCompatQueue;

    static final Object sLock = new Object();
    static final HashMap<ComponentName, WorkEnqueuer> sClassWorkEnqueuer = new HashMap<>();

    /**
     * Base class for the target service we can deliver work to and the implementation of
     * how to deliver that work.
     */
    abstract static class WorkEnqueuer {
        final ComponentName mComponentName;

        boolean mHasJobId;
        int mJobId;

        WorkEnqueuer(Context context, ComponentName cn) {
            mComponentName = cn;
        }

        void ensureJobId(int jobId) {
            if (!mHasJobId) {
                mHasJobId = true;
                mJobId = jobId;
            } else if (mJobId != jobId) {
                throw new IllegalArgumentException("Given job ID " + jobId
                        + " is different than previous " + mJobId);
            }
        }

        abstract void enqueueWork(Intent work);

        public void serviceStartReceived() {
        }

        public void serviceProcessingStarted() {
        }

        public void serviceProcessingFinished() {
        }
    }

    /**
     * Get rid of lint warnings about API levels.
     */
    interface CompatJobEngine {
        IBinder compatGetBinder();
        GenericWorkItem dequeueWork();
    }

    /**
     * An implementation of WorkEnqueuer that works for pre-O (raw Service-based).
     */
    static final class CompatWorkEnqueuer extends WorkEnqueuer {
        private final Context mContext;
        private final PowerManager.WakeLock mLaunchWakeLock;
        private final PowerManager.WakeLock mRunWakeLock;
        boolean mLaunchingService;
        boolean mServiceProcessing;

        CompatWorkEnqueuer(Context context, ComponentName cn) {
            super(context, cn);
            mContext = context.getApplicationContext();
            if (mContext.checkPermission(Manifest.permission.WAKE_LOCK, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED) {
                // Make wake locks.  We need two, because the launch wake lock wants to have
                // a timeout, and the system does not do the right thing if you mix timeout and
                // non timeout (or even changing the timeout duration) in one wake lock.
                PowerManager pm = ((PowerManager) context.getSystemService(Context.POWER_SERVICE));
                mLaunchWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, cn.getClassName() + ":launch");
                mLaunchWakeLock.setReferenceCounted(false);
                mRunWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, cn.getClassName() + ":run");
                mRunWakeLock.setReferenceCounted(false);
            } else {
                TinkerLog.w(TAG, "it would be better to grant WAKE_LOCK permission to your app so that tinker can use WakeLock to keep system awake.");
                mLaunchWakeLock = mRunWakeLock = null;
            }
        }

        @Override
        void enqueueWork(Intent work) {
            Intent intent = new Intent(work);
            intent.setComponent(mComponentName);
            if (DEBUG) Log.d(TAG, "Starting service for work: " + work);
            if (mContext.startService(intent) != null) {
                synchronized (this) {
                    if (!mLaunchingService) {
                        mLaunchingService = true;
                        if (!mServiceProcessing && mLaunchWakeLock != null) {
                            // If the service is not already holding the wake lock for
                            // itself, acquire it now to keep the system running until
                            // we get this work dispatched.  We use a timeout here to
                            // protect against whatever problem may cause it to not get
                            // the work.
                            mLaunchWakeLock.acquire(60 * 1000);
                        }
                    }
                }
            }
        }

        @Override
        public void serviceStartReceived() {
            synchronized (this) {
                // Once we have started processing work, we can count whatever last
                // enqueueWork() that happened as handled.
                mLaunchingService = false;
            }
        }

        @Override
        public void serviceProcessingStarted() {
            synchronized (this) {
                // We hold the wake lock as long as the service is processing commands.
                if (!mServiceProcessing) {
                    mServiceProcessing = true;
                    // Keep the device awake, but only for at most 10 minutes at a time
                    // (Similar to JobScheduler.)
                    if (mRunWakeLock != null) {
                        mRunWakeLock.acquire(10 * 60 * 1000L);
                    }
                    if (mLaunchWakeLock != null) {
                        mLaunchWakeLock.release();
                    }
                }
            }
        }

        @Override
        public void serviceProcessingFinished() {
            synchronized (this) {
                if (mServiceProcessing) {
                    // If we are transitioning back to a wakelock with a timeout, do the same
                    // as if we had enqueued work without the service running.
                    if (mLaunchingService && mLaunchWakeLock != null) {
                        mLaunchWakeLock.acquire(60 * 1000);
                    }
                    mServiceProcessing = false;
                    if (mRunWakeLock != null) {
                        mRunWakeLock.release();
                    }
                }
            }
        }
    }

    /**
     * Implementation of a JobServiceEngine for interaction with TinkerJobIntentService.
     */
    @RequiresApi(26)
    static final class JobServiceEngineImpl extends JobServiceEngine
            implements TinkerJobIntentService.CompatJobEngine {
        static final String TAG = "JobServiceEngineImpl";

        static final boolean DEBUG = false;

        final TinkerJobIntentService mService;
        final Object mLock = new Object();
        JobParameters mParams;

        final class WrapperWorkItem implements TinkerJobIntentService.GenericWorkItem {
            final JobWorkItem mJobWork;

            WrapperWorkItem(JobWorkItem jobWork) {
                mJobWork = jobWork;
            }

            @Override
            public Intent getIntent() {
                return mJobWork.getIntent();
            }

            @Override
            public void complete() {
                synchronized (mLock) {
                    if (mParams != null) {
                        mParams.completeWork(mJobWork);
                    }
                }
            }
        }

        JobServiceEngineImpl(TinkerJobIntentService service) {
            super(service);
            mService = service;
        }

        @Override
        public IBinder compatGetBinder() {
            return getBinder();
        }

        @Override
        public boolean onStartJob(JobParameters params) {
            if (DEBUG) Log.d(TAG, "onStartJob: " + params);
            mParams = params;
            // We can now start dequeuing work!
            mService.ensureProcessorRunningLocked(false);
            return true;
        }

        @Override
        public boolean onStopJob(JobParameters params) {
            if (DEBUG) Log.d(TAG, "onStartJob: " + params);
            boolean result = mService.doStopCurrentWork();
            synchronized (mLock) {
                // Once we return, the job is stopped, so its JobParameters are no
                // longer valid and we should not be doing anything with them.
                mParams = null;
            }
            return result;
        }

        /**
         * Dequeue some work.
         */
        @Override
        public TinkerJobIntentService.GenericWorkItem dequeueWork() {
            JobWorkItem work;
            synchronized (mLock) {
                if (mParams == null) {
                    return null;
                }
                try {
                    work = mParams.dequeueWork();
                } catch (Throwable thr) {
                    Log.w(TAG, "exception occurred.", thr);
                    work = null;
                }
            }
            if (work != null) {
                work.getIntent().setExtrasClassLoader(mService.getClassLoader());
                return new WrapperWorkItem(work);
            } else {
                return null;
            }
        }
    }

    @RequiresApi(26)
    static final class JobWorkEnqueuer extends TinkerJobIntentService.WorkEnqueuer {
        private final JobInfo mJobInfo;
        private final JobScheduler mJobScheduler;

        JobWorkEnqueuer(Context context, ComponentName cn, int jobId) {
            super(context, cn);
            ensureJobId(jobId);
            JobInfo.Builder b = new JobInfo.Builder(jobId, mComponentName);
            mJobInfo = b.setOverrideDeadline(0).build();
            mJobScheduler = (JobScheduler) context.getApplicationContext().getSystemService(
                    Context.JOB_SCHEDULER_SERVICE);
        }

        @Override
        void enqueueWork(Intent work) {
            if (DEBUG) Log.d(TAG, "Enqueueing work: " + work);
            mJobScheduler.enqueue(mJobInfo, new JobWorkItem(work));
        }
    }

    /**
     * Abstract definition of an item of work that is being dispatched.
     */
    interface GenericWorkItem {
        Intent getIntent();
        void complete();
    }

    /**
     * An implementation of GenericWorkItem that dispatches work for pre-O platforms: intents
     * received through a raw service's onStartCommand.
     */
    final class CompatWorkItem implements GenericWorkItem {
        final Intent mIntent;
        final int mStartId;

        CompatWorkItem(Intent intent, int startId) {
            mIntent = intent;
            mStartId = startId;
        }

        @Override
        public Intent getIntent() {
            return mIntent;
        }

        @Override
        public void complete() {
            if (DEBUG) Log.d(TAG, "Stopping self: #" + mStartId);
            stopSelf(mStartId);
        }
    }

    /**
     * This is a task to dequeue and process work in the background.
     */
    final class CommandProcessor extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            GenericWorkItem work;

            if (DEBUG) Log.d(TAG, "Starting to dequeue work...");

            while ((work = dequeueWork()) != null) {
                if (DEBUG) Log.d(TAG, "Processing next work: " + work);
                onHandleWork(work.getIntent());
                if (DEBUG) Log.d(TAG, "Completing work: " + work);
                work.complete();
            }

            if (DEBUG) Log.d(TAG, "Done processing work!");

            return null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            processorFinished();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            processorFinished();
        }
    }

    /**
     * Default empty constructor.
     */
    public TinkerJobIntentService() {
        if (Build.VERSION.SDK_INT >= 26) {
            mCompatQueue = null;
        } else {
            mCompatQueue = new ArrayList<>();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "CREATING: " + this);
        if (Build.VERSION.SDK_INT >= 26) {
            mJobImpl = new JobServiceEngineImpl(this);
            mCompatWorkEnqueuer = null;
        } else {
            mJobImpl = null;
            ComponentName cn = new ComponentName(this, this.getClass());
            mCompatWorkEnqueuer = getWorkEnqueuer(this, cn, false, 0);
        }
    }

    /**
     * Processes start commands when running as a pre-O service, enqueueing them to be
     * later dispatched in {@link #onHandleWork(Intent)}.
     */
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (mCompatQueue != null) {
            mCompatWorkEnqueuer.serviceStartReceived();
            if (DEBUG) Log.d(TAG, "Received compat start command #" + startId + ": " + intent);
            synchronized (mCompatQueue) {
                mCompatQueue.add(new CompatWorkItem(intent != null ? intent : new Intent(),
                        startId));
                ensureProcessorRunningLocked(true);
            }
            return START_REDELIVER_INTENT;
        } else {
            if (DEBUG) Log.d(TAG, "Ignoring start command: " + intent);
            return START_NOT_STICKY;
        }
    }

    /**
     * Returns the IBinder for the {@link android.app.job.JobServiceEngine} when
     * running as a JobService on O and later platforms.
     */
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        if (mJobImpl != null) {
            IBinder engine = mJobImpl.compatGetBinder();
            if (DEBUG) Log.d(TAG, "Returning engine: " + engine);
            return engine;
        } else {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCompatQueue != null) {
            synchronized (mCompatQueue) {
                mDestroyed = true;
                mCompatWorkEnqueuer.serviceProcessingFinished();
            }
        }
    }

    /**
     * Call this to enqueue work for your subclass of {@link TinkerJobIntentService}.  This will
     * either directly start the service (when running on pre-O platforms) or enqueue work
     * for it as a job (when running on O and later).  In either case, a wake lock will be
     * held for you to ensure you continue running.  The work you enqueue will ultimately
     * appear at {@link #onHandleWork(Intent)}.
     *
     * @param context Context this is being called from.
     * @param cls The concrete class the work should be dispatched to (this is the class that
     * is published in your manifest).
     * @param jobId A unique job ID for scheduling; must be the same value for all work
     * enqueued for the same class.
     * @param work The Intent of work to enqueue.
     */
    public static void enqueueWork(@NonNull Context context, @NonNull Class cls, int jobId,
                                   @NonNull Intent work) {
        enqueueWork(context, new ComponentName(context, cls), jobId, work);
    }

    /**
     * Like {@link #enqueueWork(Context, Class, int, Intent)}, but supplies a ComponentName
     * for the service to interact with instead of its class.
     *
     * @param context Context this is being called from.
     * @param component The published ComponentName of the class this work should be
     * dispatched to.
     * @param jobId A unique job ID for scheduling; must be the same value for all work
     * enqueued for the same class.
     * @param work The Intent of work to enqueue.
     */
    public static void enqueueWork(@NonNull Context context, @NonNull ComponentName component,
                                   int jobId, @NonNull Intent work) {
        if (work == null) {
            throw new IllegalArgumentException("work must not be null");
        }
        synchronized (sLock) {
            WorkEnqueuer we = getWorkEnqueuer(context, component, true, jobId);
            we.ensureJobId(jobId);
            we.enqueueWork(work);
        }
    }

    static WorkEnqueuer getWorkEnqueuer(Context context, ComponentName cn, boolean hasJobId,
                                        int jobId) {
        WorkEnqueuer we = sClassWorkEnqueuer.get(cn);
        if (we == null) {
            if (Build.VERSION.SDK_INT >= 26) {
                if (!hasJobId) {
                    throw new IllegalArgumentException("Can't be here without a job id");
                }
                we = new JobWorkEnqueuer(context, cn, jobId);
            } else {
                we = new CompatWorkEnqueuer(context, cn);
            }
            sClassWorkEnqueuer.put(cn, we);
        }
        return we;
    }

    /**
     * Called serially for each work dispatched to and processed by the service.  This
     * method is called on a background thread, so you can do long blocking operations
     * here.  Upon returning, that work will be considered complete and either the next
     * pending work dispatched here or the overall service destroyed now that it has
     * nothing else to do.
     *
     * <p>Be aware that when running as a job, you are limited by the maximum job execution
     * time and any single or total sequential items of work that exceeds that limit will
     * cause the service to be stopped while in progress and later restarted with the
     * last unfinished work.  (There is currently no limit on execution duration when
     * running as a pre-O plain Service.)</p>
     *
     * @param intent The intent describing the work to now be processed.
     */
    protected abstract void onHandleWork(@NonNull Intent intent);

    /**
     * Control whether code executing in {@link #onHandleWork(Intent)} will be interrupted
     * if the job is stopped.  By default this is false.  If called and set to true, any
     * time {@link #onStopCurrentWork()} is called, the class will first call
     * {@link AsyncTask#cancel(boolean) AsyncTask.cancel(true)} to interrupt the running
     * task.
     *
     * @param interruptIfStopped Set to true to allow the system to interrupt actively
     * running work.
     */
    public void setInterruptIfStopped(boolean interruptIfStopped) {
        mInterruptIfStopped = interruptIfStopped;
    }

    /**
     * Returns true if {@link #onStopCurrentWork()} has been called.  You can use this,
     * while executing your work, to see if it should be stopped.
     */
    public boolean isStopped() {
        return mStopped;
    }

    /**
     * This will be called if the JobScheduler has decided to stop this job.  The job for
     * this service does not have any constraints specified, so this will only generally happen
     * if the service exceeds the job's maximum execution time.
     *
     * @return True to indicate to the JobManager whether you'd like to reschedule this work,
     * false to drop this and all following work. Regardless of the value returned, your service
     * must stop executing or the system will ultimately kill it.  The default implementation
     * returns true, and that is most likely what you want to return as well (so no work gets
     * lost).
     */
    public boolean onStopCurrentWork() {
        return true;
    }

    boolean doStopCurrentWork() {
        if (mCurProcessor != null) {
            mCurProcessor.cancel(mInterruptIfStopped);
        }
        mStopped = true;
        return onStopCurrentWork();
    }

    @TargetApi(11)
    void ensureProcessorRunningLocked(boolean reportStarted) {
        if (mCurProcessor == null) {
            mCurProcessor = new CommandProcessor();
            if (mCompatWorkEnqueuer != null && reportStarted) {
                mCompatWorkEnqueuer.serviceProcessingStarted();
            }
            if (DEBUG) Log.d(TAG, "Starting processor: " + mCurProcessor);
            mCurProcessor.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    void processorFinished() {
        if (mCompatQueue != null) {
            synchronized (mCompatQueue) {
                mCurProcessor = null;
                // The async task has finished, but we may have gotten more work scheduled in the
                // meantime.  If so, we need to restart the new processor to execute it.  If there
                // is no more work at this point, either the service is in the process of being
                // destroyed (because we called stopSelf on the last intent started for it), or
                // someone has already called startService with a new Intent that will be
                // arriving shortly.  In either case, we want to just leave the service
                // waiting -- either to get destroyed, or get a new onStartCommand() callback
                // which will then kick off a new processor.
                if (mCompatQueue != null && mCompatQueue.size() > 0) {
                    ensureProcessorRunningLocked(false);
                } else if (!mDestroyed) {
                    mCompatWorkEnqueuer.serviceProcessingFinished();
                }
            }
        }
    }

    GenericWorkItem dequeueWork() {
        if (mJobImpl != null) {
            return mJobImpl.dequeueWork();
        } else {
            synchronized (mCompatQueue) {
                if (mCompatQueue.size() > 0) {
                    return mCompatQueue.remove(0);
                } else {
                    return null;
                }
            }
        }
    }
}