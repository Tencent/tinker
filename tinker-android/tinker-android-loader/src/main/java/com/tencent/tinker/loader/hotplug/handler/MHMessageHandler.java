package com.tencent.tinker.loader.hotplug.handler;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Message;
import android.util.Log;

import com.tencent.tinker.loader.hotplug.EnvConsts;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.lang.reflect.Field;

import static com.tencent.tinker.loader.hotplug.interceptor.HandlerMessageInterceptor.MessageHandler;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class MHMessageHandler implements MessageHandler {
    private static final String TAG = "Tinker.MHMessageHandler";

    private static final int LAUNCH_ACTIVITY;
    private static final int DESTROY_ACTIVITY;

    static {
        int launchActivity = 0;
        int destroyActivity = 0;
        try {
            final Class<?> hClazz = Class.forName("android.app.ActivityThread$H");
            launchActivity = ShareReflectUtil.findField(hClazz, "LAUNCH_ACTIVITY").getInt(null);
            destroyActivity = ShareReflectUtil.findField(hClazz, "DESTROY_ACTIVITY").getInt(null);
        } catch (Throwable thr) {
            // Fallback to default value.
            launchActivity = 100;
            destroyActivity = 109;
        }
        LAUNCH_ACTIVITY = launchActivity;
        DESTROY_ACTIVITY = destroyActivity;
    }

    @Override
    public boolean handleMessage(Message msg) {
        int what = msg.what;
        if (what == LAUNCH_ACTIVITY) {
            try {
                final Object activityClientRecord = msg.obj;
                if (activityClientRecord == null) {
                    return false;
                }
                final Field intentField = ShareReflectUtil.findField(activityClientRecord, "intent");
                final Intent maybeHackedIntent = (Intent) intentField.get(activityClientRecord);
                if (maybeHackedIntent == null) {
                    return false;
                }
                final ComponentName oldComponent = maybeHackedIntent.getParcelableExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT);
                if (oldComponent == null) {
                    return false;
                }
                maybeHackedIntent.setComponent(oldComponent);
                maybeHackedIntent.removeExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT);
            } catch (Throwable thr) {
                Log.e(TAG, "exception in handleMessage.", thr);
            }
        } else if (what == DESTROY_ACTIVITY) {
            // TODO recycle stub if needed.
        }
        return false;
    }
}
