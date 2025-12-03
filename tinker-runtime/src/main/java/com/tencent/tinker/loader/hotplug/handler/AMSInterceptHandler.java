package com.tencent.tinker.loader.hotplug.handler;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;

import com.tencent.tinker.loader.hotplug.ActivityStubManager;
import com.tencent.tinker.loader.hotplug.EnvConsts;
import com.tencent.tinker.loader.hotplug.IncrementComponentManager;
import com.tencent.tinker.loader.hotplug.interceptor.ServiceBinderInterceptor.BinderInvocationHandler;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.lang.reflect.Method;

/**
 * Created by tangyinsheng on 2017/7/31.
 */

public class AMSInterceptHandler implements BinderInvocationHandler {
    private static final String TAG = "Tinker.AMSIntrcptHndlr";

    private static final int[] TRANSLUCENT_ATTR_ID = {android.R.attr.windowIsTranslucent};
    private static final int INTENT_SENDER_ACTIVITY;

    static {
        // Hardcoded Value.
        int val = 2;
        if (Build.VERSION.SDK_INT < 27) {
            try {
                val = (int) ShareReflectUtil.findField(ActivityManager.class, "INTENT_SENDER_ACTIVITY").get(null);
            } catch (Throwable thr) {
                thr.printStackTrace();
                val = 2;
            }
        }
        INTENT_SENDER_ACTIVITY = val;
    }

    private final Context mContext;

    public AMSInterceptHandler(Context context) {
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
    public Object invoke(Object target, Method method, Object[] args) throws Throwable {
        final String methodName = method.getName();
        if ("startActivity".equals(methodName)) {
            return handleStartActivity(target, method, args);
        } else if ("startActivities".equals(methodName)) {
            return handleStartActivities(target, method, args);
        } else if ("startActivityAndWait".equals(methodName)) {
            return handleStartActivity(target, method, args);
        } else if ("startActivityWithConfig".equals(methodName)) {
            return handleStartActivity(target, method, args);
        } else if ("startActivityAsUser".equals(methodName)) {
            return handleStartActivity(target, method, args);
        } else if ("getIntentSender".equals(methodName)) {
            return handleGetIntentSender(target, method, args);
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
            final Intent newIntent = new Intent((Intent) args[intentIdx]);
            processActivityIntent(newIntent);
            args[intentIdx] = newIntent;
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
            for (int i = 0; i < oldIntentArr.length; ++i) {
                final Intent newIntent = new Intent(oldIntentArr[i]);
                processActivityIntent(newIntent);
                oldIntentArr[i] = newIntent;
            }
        }
        return method.invoke(target, args);
    }

    private Object handleGetIntentSender(Object target, Method method, Object[] args) throws Throwable {
        int intentArrIdx = -1;
        for (int i = 0; i < args.length; ++i) {
            if (args[i] instanceof Intent[]) {
                intentArrIdx = i;
                break;
            }
        }
        if (intentArrIdx != -1) {
            final int intentType = (int) args[0];
            if (intentType == INTENT_SENDER_ACTIVITY) {
                final Intent[] oldIntentArr = (Intent[]) args[intentArrIdx];
                for (int i = 0; i < oldIntentArr.length; ++i) {
                    final Intent newIntent = new Intent(oldIntentArr[i]);
                    processActivityIntent(newIntent);
                    oldIntentArr[i] = newIntent;
                }
            }
        }
        return method.invoke(target, args);
    }

    private void processActivityIntent(Intent intent) {
        String origPackageName = null;
        String origClassName = null;
        if (intent.getComponent() != null) {
            origPackageName = intent.getComponent().getPackageName();
            origClassName = intent.getComponent().getClassName();
        } else {
            ResolveInfo rInfo = mContext.getPackageManager().resolveActivity(intent, 0);
            if (rInfo == null) {
                rInfo = IncrementComponentManager.resolveIntent(intent);
            }
            if (rInfo != null && rInfo.filter != null && rInfo.filter.hasCategory(Intent.CATEGORY_DEFAULT)) {
                origPackageName = rInfo.activityInfo.packageName;
                origClassName = rInfo.activityInfo.name;
            }
        }
        if (IncrementComponentManager.isIncrementActivity(origClassName)) {
            final ActivityInfo origInfo = IncrementComponentManager.queryActivityInfo(origClassName);
            final boolean isTransparent = hasTransparentTheme(origInfo);
            final String stubClassName = ActivityStubManager.assignStub(origClassName, origInfo.launchMode, isTransparent);
            storeAndReplaceOriginalComponentName(intent, origPackageName, origClassName, stubClassName);
        }
    }

    private void storeAndReplaceOriginalComponentName(Intent intent, String origPackageName, String origClassName, String stubClassName) {
        final ComponentName origComponentName = new ComponentName(origPackageName, origClassName);
        ShareIntentUtil.fixIntentClassLoader(intent, mContext.getClassLoader());
        intent.putExtra(EnvConsts.INTENT_EXTRA_OLD_COMPONENT, origComponentName);
        final ComponentName stubComponentName = new ComponentName(origPackageName, stubClassName);
        intent.setComponent(stubComponentName);
    }

    private boolean hasTransparentTheme(ActivityInfo activityInfo) {
        final int theme = activityInfo.getThemeResource();
        final Resources.Theme themeObj = mContext.getResources().newTheme();
        themeObj.applyStyle(theme, true);
        TypedArray ta = null;
        try {
            ta = themeObj.obtainStyledAttributes(TRANSLUCENT_ATTR_ID);
            return ta.getBoolean(0, false);
        } catch (Throwable thr) {
            return false;
        } finally {
            if (ta != null) {
                ta.recycle();
            }
        }
    }
}
