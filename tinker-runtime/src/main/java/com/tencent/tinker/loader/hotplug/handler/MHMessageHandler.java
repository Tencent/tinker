package com.tencent.tinker.loader.hotplug.handler;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;

import com.tencent.tinker.loader.hotplug.EnvConsts;
import com.tencent.tinker.loader.hotplug.IncrementComponentManager;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static com.tencent.tinker.loader.hotplug.interceptor.HandlerMessageInterceptor.MessageHandler;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class MHMessageHandler implements MessageHandler {
    private static final String TAG = "Tinker.MHMsgHndlr";

    private static final int LAUNCH_ACTIVITY;

    static {
        // Hardcoded Value.
        int launchActivity = 100;
        if (Build.VERSION.SDK_INT < 27) {
            try {
                final Class<?> hClazz = Class.forName("android.app.ActivityThread$H");
                launchActivity = ShareReflectUtil.findField(hClazz, "LAUNCH_ACTIVITY").getInt(null);
            } catch (Throwable thr) {
                // Fallback to default value.
                launchActivity = 100;
            }
        }
        LAUNCH_ACTIVITY = launchActivity;
    }

    private final Context mContext;

    public MHMessageHandler(Context context) {
        while (context instanceof ContextWrapper) {
            final Context baseCtx = ((ContextWrapper) context).getBaseContext();
            if (baseCtx == null) {
                break;
            }
            context = baseCtx;
        }
        mContext = context;
    }

    @Override
    public boolean handleMessage(Message msg) {
        int what = msg.what;
        if (what == LAUNCH_ACTIVITY) {
            try {
                final Object activityClientRecord = msg.obj;
                if (activityClientRecord == null) {
                    ShareTinkerLog.w(TAG, "msg: [" + msg.what + "] has no 'obj' value.");
                    return false;
                }
                final Field intentField = ShareReflectUtil.findField(activityClientRecord, "intent");
                final Intent maybeHackedIntent = (Intent) intentField.get(activityClientRecord);
                if (maybeHackedIntent == null) {
                    ShareTinkerLog.w(TAG, "cannot fetch intent from message received by mH.");
                    return false;
                }

                ShareIntentUtil.fixIntentClassLoader(maybeHackedIntent, mContext.getClassLoader());

                final ComponentName oldComponent = maybeHackedIntent.getParcelableExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT);
                if (oldComponent == null) {
                    ShareTinkerLog.w(TAG, "oldComponent was null, start " + maybeHackedIntent.getComponent() + " next.");
                    return false;
                }
                final Field activityInfoField = ShareReflectUtil.findField(activityClientRecord, "activityInfo");
                final ActivityInfo aInfo = (ActivityInfo) activityInfoField.get(activityClientRecord);
                if (aInfo == null) {
                    return false;
                }
                final ActivityInfo targetAInfo = IncrementComponentManager.queryActivityInfo(oldComponent.getClassName());
                if (targetAInfo == null) {
                    ShareTinkerLog.e(TAG, "Failed to query target activity's info,"
                            + " perhaps the target is not hotpluged component. Target: " + oldComponent.getClassName());
                    return false;
                }
                fixActivityScreenOrientation(activityClientRecord, targetAInfo.screenOrientation);
                fixStubActivityInfo(aInfo, targetAInfo);
                maybeHackedIntent.setComponent(oldComponent);
                maybeHackedIntent.removeExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT);
            } catch (Throwable thr) {
                ShareTinkerLog.e(TAG, "exception in handleMessage.", thr);
            }
        }

        return false;
    }

    private void fixStubActivityInfo(ActivityInfo stubAInfo, ActivityInfo targetAInfo) {
        copyInstanceFields(targetAInfo, stubAInfo);
    }

    private <T> void copyInstanceFields(T srcObj, T destObj) {
        if (srcObj == null || destObj == null) {
            return;
        }
        Class<?> infoClazz = srcObj.getClass();
        while (!infoClazz.equals(Object.class)) {
            final Field[] fields = infoClazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isSynthetic()) {
                    continue;
                }
                final int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    continue;
                }
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                try {
                    field.set(destObj, field.get(srcObj));
                } catch (Throwable ignored) {
                    // Ignored.
                }
            }
            infoClazz = infoClazz.getSuperclass();
        }
    }

    private void fixActivityScreenOrientation(Object activityClientRecord, int screenOrientation) {
        if (screenOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_USER;
        }
        try {
            final Field tokenField = ShareReflectUtil.findField(activityClientRecord, "token");
            final Object token = tokenField.get(activityClientRecord);
            final Class<?> activityManagerNativeClazz = Class.forName("android.app.ActivityManagerNative");
            final Method getDefaultMethod = ShareReflectUtil.findMethod(activityManagerNativeClazz, "getDefault");
            final Object amn = getDefaultMethod.invoke(null);
            final Method setRequestedOrientationMethod = ShareReflectUtil.findMethod(amn, "setRequestedOrientation", IBinder.class, int.class);
            setRequestedOrientationMethod.invoke(amn, token, screenOrientation);
        } catch (Throwable thr) {
            ShareTinkerLog.e(TAG, "Failed to fix screen orientation.", thr);
        }
    }
}
