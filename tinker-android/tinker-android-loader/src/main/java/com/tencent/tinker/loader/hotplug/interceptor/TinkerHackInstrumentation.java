package com.tencent.tinker.loader.hotplug.interceptor;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;

import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.hotplug.EnvConsts;
import com.tencent.tinker.loader.hotplug.IncrementComponentManager;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.lang.reflect.Field;

/**
 * Created by tangyinsheng on 2018/3/9.
 */
public class TinkerHackInstrumentation extends Instrumentation {
    private static final String TAG = "Tinker.Instrumentation";

    private final Instrumentation mOriginal;
    private final Object mActivityThread;
    private final Field mInstrumentationField;

    public static TinkerHackInstrumentation create(Context context) {
        try {
            final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
            final Field mInstrumentationField = ShareReflectUtil.findField(activityThread, "mInstrumentation");
            final Instrumentation original = (Instrumentation) mInstrumentationField.get(activityThread);
            if (original instanceof TinkerHackInstrumentation) {
                return (TinkerHackInstrumentation) original;
            }
            return new TinkerHackInstrumentation(original, activityThread, mInstrumentationField);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("see next stacktrace", thr);
        }
    }

    public void install() throws IllegalAccessException {
        if (mInstrumentationField.get(mActivityThread) instanceof TinkerHackInstrumentation) {
            ShareTinkerLog.w(TAG, "already installed, skip rest logic.");
        } else {
            mInstrumentationField.set(mActivityThread, this);
        }
    }

    public void uninstall() throws IllegalAccessException {
        mInstrumentationField.set(mActivityThread, mOriginal);
    }

    private TinkerHackInstrumentation(Instrumentation original, Object activityThread, Field instrumentationField) throws TinkerRuntimeException {
        mOriginal = original;
        mActivityThread = activityThread;
        mInstrumentationField = instrumentationField;
        try {
            copyAllFields(original);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
        }
    }

    @Override
    public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws InstantiationException, IllegalAccessException {
        processIntent(context.getClassLoader(), intent);
        return super.newActivity(clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance);
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (processIntent(cl, intent)) {
            return super.newActivity(cl, intent.getComponent().getClassName(), intent);
        } else {
            return super.newActivity(cl, className, intent);
        }
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        if (activity != null) {
            final ActivityInfo targetAInfo = IncrementComponentManager.queryActivityInfo(activity.getClass().getName());
            if (targetAInfo != null) {
                fixActivityParams(activity, targetAInfo);
            }
        }
        super.callActivityOnCreate(activity, icicle);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        if (activity != null) {
            final ActivityInfo targetAInfo = IncrementComponentManager.queryActivityInfo(activity.getClass().getName());
            if (targetAInfo != null) {
                fixActivityParams(activity, targetAInfo);
            }
        }
        super.callActivityOnCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        if (activity != null) {
            processIntent(activity.getClass().getClassLoader(), intent);
        }
        super.callActivityOnNewIntent(activity, intent);
    }

    private boolean processIntent(ClassLoader cl, Intent intent) {
        if (intent == null) {
            return false;
        }
        ShareIntentUtil.fixIntentClassLoader(intent, cl);
        final ComponentName oldComponent = intent.getParcelableExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT);
        if (oldComponent == null) {
            ShareTinkerLog.w(TAG, "oldComponent was null, start " + intent.getComponent() + " next.");
            return false;
        }
        final String oldComponentName = oldComponent.getClassName();
        final ActivityInfo targetAInfo = IncrementComponentManager.queryActivityInfo(oldComponentName);
        if (targetAInfo == null) {
            ShareTinkerLog.e(TAG, "Failed to query target activity's info,"
                    + " perhaps the target is not hotpluged component. Target: " + oldComponentName);
            return false;
        }
        intent.setComponent(oldComponent);
        intent.removeExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT);
        return true;
    }

    private void fixActivityParams(Activity target, ActivityInfo targetAInfo) {
        target.setRequestedOrientation(targetAInfo.screenOrientation);
        target.setTheme(targetAInfo.theme);
        try {
            final Field aInfoField = ShareReflectUtil.findField(target, "mActivityInfo");
            aInfoField.set(target, targetAInfo);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("see next stacktrace.", thr);
        }
    }

    private void copyAllFields(Instrumentation src) throws IllegalAccessException {
        final Field[] fields = Instrumentation.class.getDeclaredFields();
        for (int i = 0; i < fields.length; ++i) {
            fields[i].setAccessible(true);
            final Object value = fields[i].get(src);
            fields[i].set(this, value);
        }
    }
}
