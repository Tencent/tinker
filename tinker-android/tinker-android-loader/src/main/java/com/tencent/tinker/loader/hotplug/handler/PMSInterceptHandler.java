package com.tencent.tinker.loader.hotplug.handler;

import android.content.ComponentName;
import android.util.Log;

import com.tencent.tinker.loader.hotplug.IncrementComponentManager;
import com.tencent.tinker.loader.hotplug.interceptor.ServiceBinderInterceptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class PMSInterceptHandler implements ServiceBinderInterceptor.BinderInvocationHandler {
    private static final String TAG = "Tinker.PMSIntrcptHndlr";

    @Override
    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        if ("getActivityInfo".equals(methodName)) {
            return handleGetActivityInfo(target, method, args);
        } else {
            return method.invoke(target, args);
        }
    }

    private Object handleGetActivityInfo(Object target, Method method, Object[] args) throws Throwable {
        Object res = null;
        try {
            res = method.invoke(target, args);
            if (res == null) {
                return IncrementComponentManager.queryActivityInfo(((ComponentName) args[0]).getClassName());
            } else {
                return res;
            }
        } catch (InvocationTargetException e) {
            throw e;
        } catch (Throwable thr) {
            Log.e(TAG, "unexpected exception.", thr);
            return res;
        }
    }
}
