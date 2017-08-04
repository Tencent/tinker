package com.tencent.tinker.loader.hotplug;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.util.Log;

import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.hotplug.handler.AMSInterceptHandler;
import com.tencent.tinker.loader.hotplug.handler.MHMessageHandler;
import com.tencent.tinker.loader.hotplug.handler.PMSInterceptHandler;
import com.tencent.tinker.loader.hotplug.interceptor.HandlerMessageInterceptor;
import com.tencent.tinker.loader.hotplug.interceptor.ServiceBinderInterceptor;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareSecurityCheck;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public final class ComponentHotplug {
    private static final String TAG = "Tinker.ComponentHotplug";

    private static volatile boolean sInstalled = false;
    private static ServiceBinderInterceptor sAMSInterceptor;
    private static ServiceBinderInterceptor sPMSInterceptor;
    private static HandlerMessageInterceptor sMHMessageInterceptor;

    public synchronized static void install(TinkerApplication app, ShareSecurityCheck checker) throws UnsupportedEnvironmentException {
        if (!sInstalled) {
            try {
                IncrementComponentManager.init(app, checker);

                sAMSInterceptor = new ServiceBinderInterceptor(EnvConsts.ACTIVITY_MANAGER_SRVNAME, new AMSInterceptHandler());
                sPMSInterceptor = new ServiceBinderInterceptor(EnvConsts.PACKAGE_MANAGER_SRVNAME, new PMSInterceptHandler());

                final Handler mH = fetchMHInstance(app);
                sMHMessageInterceptor = new HandlerMessageInterceptor(mH, new MHMessageHandler());

                sAMSInterceptor.install();
                sPMSInterceptor.install();
                sMHMessageInterceptor.install();
            } catch (Throwable thr) {
                uninstall();
                throw new UnsupportedEnvironmentException(thr);
            }

            sInstalled = true;
        }
    }

    private static Handler fetchMHInstance(Context context) {
        Method currentActivityThreadMethod = null;
        Field mHField = null;
        try {
            final Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
            currentActivityThreadMethod = ShareReflectUtil.findMethod(activityThreadClazz,
                    "currentActivityThread");
            mHField = ShareReflectUtil.findField(activityThreadClazz, "mH");
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }

        Object activityThread = null;
        try {
            activityThread = currentActivityThreadMethod.invoke(null);
            if (activityThread == null) {
                throw new IllegalStateException("activityThread is null, try another method.");
            }
        } catch (Throwable thr) {
            // Try another method.
            while (context != null && context instanceof ContextWrapper) {
                context = ((ContextWrapper) context).getBaseContext();
            }
            try {
                activityThread = ShareReflectUtil.findField(context, "mMainThread").get(context);
            } catch (Throwable thr1) {
                throw new IllegalStateException(thr1);
            }
        }
        try {
            final Handler mH = (Handler) mHField.get(activityThread);
            return mH;
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    public synchronized static void uninstall()  {
        if (sInstalled) {
            try {
                sAMSInterceptor.uninstall();
                sPMSInterceptor.uninstall();
                sMHMessageInterceptor.uninstall();
            } catch (Throwable thr) {
                Log.e(TAG, "exception when uninstall.", thr);
            }

            sInstalled = false;
        }
    }

    private ComponentHotplug() {
        throw new UnsupportedOperationException();
    }
}
