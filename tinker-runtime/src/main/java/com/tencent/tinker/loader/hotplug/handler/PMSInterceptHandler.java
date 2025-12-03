package com.tencent.tinker.loader.hotplug.handler;

import android.content.ComponentName;
import android.content.Intent;

import com.tencent.tinker.loader.hotplug.IncrementComponentManager;
import com.tencent.tinker.loader.hotplug.interceptor.ServiceBinderInterceptor;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

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
        } else if ("resolveIntent".equals(methodName)) {
            return handleResolveIntent(target, method, args);
        } else {
            return method.invoke(target, args);
        }
    }

    private Object handleGetActivityInfo(Object target, Method method, Object[] args) throws Throwable {
        final Class<?>[] methodExceptionTypes = method.getExceptionTypes();
        try {
            final Object res = method.invoke(target, args);
            if (res != null) {
                return res;
            } else {
                ComponentName componentName = null;
                int compNameIdx = 0;
                while (compNameIdx < args.length) {
                    if (args[compNameIdx] instanceof ComponentName) {
                        ShareTinkerLog.i(TAG, "locate componentName field of " + method.getName() + " done at idx: " + compNameIdx);
                        componentName = (ComponentName) args[compNameIdx];
                        break;
                    }
                    ++compNameIdx;
                }
                if (componentName != null) {
                    return IncrementComponentManager.queryActivityInfo(componentName.getClassName());
                } else {
                    ShareTinkerLog.w(TAG, "failed to locate componentName field of " + method.getName()
                            + ", notice any crashes or mistakes after resolve works.");
                    return null;
                }
            }
        } catch (InvocationTargetException e) {
            final Throwable targetThr = e.getTargetException();
            if (methodExceptionTypes != null && methodExceptionTypes.length > 0) {
                throw (targetThr != null ? targetThr : e);
            } else {
                ShareTinkerLog.e(TAG, "unexpected exception.", (targetThr != null ? targetThr : e));
                return null;
            }
        } catch (Throwable thr) {
            ShareTinkerLog.e(TAG, "unexpected exception.", thr);
            return null;
        }
    }

    private Object handleResolveIntent(Object target, Method method, Object[] args) throws Throwable {
        final Class<?>[] methodExceptionTypes = method.getExceptionTypes();
        try {
            final Object res = method.invoke(target, args);
            if (res != null) {
                return res;
            } else {
                ShareTinkerLog.w(TAG, "failed to resolve activity in base package, try again in patch package.");
                Intent intent = null;
                int intentIdx = 0;
                while (intentIdx < args.length) {
                    if (args[intentIdx] instanceof Intent) {
                        ShareTinkerLog.i(TAG, "locate intent field of " + method.getName() + " done at idx: " + intentIdx);
                        intent = (Intent) args[intentIdx];
                        break;
                    }
                    ++intentIdx;
                }
                if (intent != null) {
                    return IncrementComponentManager.resolveIntent(intent);
                } else {
                    ShareTinkerLog.w(TAG, "failed to locate intent field of " + method.getName()
                            + ", notice any crashes or mistakes after resolve works.");
                    return null;
                }
            }
        } catch (InvocationTargetException e) {
            final Throwable targetThr = e.getTargetException();
            if (methodExceptionTypes != null && methodExceptionTypes.length > 0) {
                throw (targetThr != null ? targetThr : e);
            } else {
                ShareTinkerLog.e(TAG, "unexpected exception.", (targetThr != null ? targetThr : e));
                return null;
            }
        } catch (Throwable thr) {
            ShareTinkerLog.e(TAG, "unexpected exception.", thr);
            return null;
        }
    }
}
