package com.tencent.tinker.loader.hotplug;

import android.content.Context;
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
                if (IncrementComponentManager.init(app, checker)) {
                    sAMSInterceptor = new ServiceBinderInterceptor(app, EnvConsts.ACTIVITY_MANAGER_SRVNAME, new AMSInterceptHandler(app));
                    sPMSInterceptor = new ServiceBinderInterceptor(app, EnvConsts.PACKAGE_MANAGER_SRVNAME, new PMSInterceptHandler());

                    final Handler mH = fetchMHInstance(app);
                    sMHMessageInterceptor = new HandlerMessageInterceptor(mH, new MHMessageHandler(app));

                    sAMSInterceptor.install();
                    sPMSInterceptor.install();
                    sMHMessageInterceptor.install();

                    sInstalled = true;

                    Log.i(TAG, "installed successfully.");
                }
            } catch (Throwable thr) {
                uninstall();
                throw new UnsupportedEnvironmentException(thr);
            }
        }
    }

    public synchronized static void ensureComponentHotplugInstalled(TinkerApplication app) throws UnsupportedEnvironmentException {
        // Some environments may reset AMS, PMS and mH，which cause component hotplug feature
        // being unavailable. So we reinstall them here.
        if (sInstalled) {
            try {
                sAMSInterceptor.install();
                sPMSInterceptor.install();
                sMHMessageInterceptor.install();
            } catch (Throwable thr) {
                uninstall();
                throw new UnsupportedEnvironmentException(thr);
            }
        } else {
            Log.i(TAG, "method install() is not invoked, ignore ensuring operations.");
        }
    }

    private static Handler fetchMHInstance(Context context) {
        final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
        if (activityThread == null) {
            throw new IllegalStateException("failed to fetch instance of ActivityThread.");
        }
        try {
            final Field mHField = ShareReflectUtil.findField(activityThread, "mH");
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
