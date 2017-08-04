package com.tencent.tinker.loader.hotplug.handler;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;

import com.tencent.tinker.loader.hotplug.ActivityStubManager;
import com.tencent.tinker.loader.hotplug.EnvConsts;
import com.tencent.tinker.loader.hotplug.IncrementComponentManager;
import com.tencent.tinker.loader.hotplug.interceptor.ServiceBinderInterceptor.BinderInvocationHandler;

import java.lang.reflect.Method;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class AMSInterceptHandler implements BinderInvocationHandler {
    private static final String TAG = "Tinker.AMSInterceptHandler";

    @Override
    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        // TODO hook rest necessary methods.
        if ("startActivity".equals(methodName)) {
            return handleStartActivity(target, method, args);
        } else if ("startActivities".equals(methodName)) {
            return handleStartActivities(target, method, args);
        }
        return method.invoke(target, args);
    }

    private Object handleStartActivity(Object target, Method method, Object[] args) throws Throwable {
        int intentIdx = -1;
        for (int i = 0; i < args.length; ++i) {
            if (args[i] instanceof Intent) {
                intentIdx = i;
                break;
            }
        }
        if (intentIdx != -1) {
            final Intent oldIntent = (Intent) args[intentIdx];
            processIntent(oldIntent);
        }
        return method.invoke(target, args);
    }

    private Object handleStartActivities(Object target, Method method, Object[] args) throws Throwable {
        int intentArrIdx = -1;
        for (int i = 0; i < args.length; ++i) {
            if (args[i] instanceof Intent[]) {
                intentArrIdx = i;
                break;
            }
        }
        if (intentArrIdx != -1) {
            final Intent[] oldIntentArr = (Intent[]) args[intentArrIdx];
            for (Intent oldIntent : oldIntentArr) {
                processIntent(oldIntent);
            }
        }
        return method.invoke(target, args);
    }

    private void processIntent(Intent intent) {
        final String targetPackageName = intent.getComponent().getPackageName();
        final String targetClassName = intent.getComponent().getClassName();
        if (IncrementComponentManager.isIncrementActivity(targetClassName)) {
            intent.putExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT, intent.getComponent());
            final ActivityInfo targetInfo = IncrementComponentManager.queryActivityInfo(targetClassName);
            intent.setComponent(new ComponentName(targetPackageName,
                    ActivityStubManager.assignStub(targetClassName, targetInfo.launchMode)));
        }
    }
}
