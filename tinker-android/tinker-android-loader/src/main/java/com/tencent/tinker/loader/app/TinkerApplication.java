/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader.app;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;

import com.tencent.tinker.loader.TinkerLoader;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.TinkerUncaughtHandler;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Created by zhangshaowen on 16/3/8.
 * <p/>
 * A base class for implementing an Application that delegates to an {@link ApplicationLifeCycle}
 * instance. This is used in conjunction with secondary dex files so that the logic that would
 * normally live in the Application class is loaded after the secondary dexes are loaded.
 */
public abstract class TinkerApplication extends Application {

    //oh, we can use ShareConstants, because they are loader class and static final!
    private static final int    TINKER_DISABLE         = ShareConstants.TINKER_DISABLE;
    private static final String INTENT_PATCH_EXCEPTION = ShareIntentUtil.INTENT_PATCH_EXCEPTION;
    private static final String TINKER_LOADER_METHOD   = "tryLoad";
    /**
     * tinkerFlags, which types is supported
     * dex only, library only, all support
     * default: TINKER_ENABLE_ALL
     */
    private final int tinkerFlags;
    /**
     * whether verify md5 when we load dex or lib
     * they store at data/data/package, and we had verity them at the :patch process.
     * so we don't have to verity them every time for quicker!
     * default:false
     */
    private final boolean tinkerLoadVerifyFlag;
    private final String  delegateClassName;
    private final String  loaderClassName;
    /**
     * if we have load patch, we should use safe mode
     */
    private boolean useSafeMode;
    private       Intent  tinkerResultIntent;

    private Object         delegate      = null;
    private Resources[]    resources     = new Resources[1];
    private ClassLoader[]  classLoader   = new ClassLoader[1];
    private AssetManager[] assetManager  = new AssetManager[1];

    private long applicationStartElapsedTime;
    private long applicationStartMillisTime;

    /**
     * current build.
     */
    protected TinkerApplication(int tinkerFlags) {
        this(tinkerFlags, "com.tencent.tinker.loader.app.DefaultApplicationLike", TinkerLoader.class.getName(), false);
    }

    /**
     * @param delegateClassName The fully-qualified name of the {@link ApplicationLifeCycle} class
     *                          that will act as the delegate for application lifecycle callbacks.
     */
    protected TinkerApplication(int tinkerFlags, String delegateClassName,
                                String loaderClassName, boolean tinkerLoadVerifyFlag) {
        this.tinkerFlags = tinkerFlags;
        this.delegateClassName = delegateClassName;
        this.loaderClassName = loaderClassName;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;

    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName) {
        this(tinkerFlags, delegateClassName, TinkerLoader.class.getName(), false);
    }

    private Object createDelegate() {
        try {
            // Use reflection to create the delegate so it doesn't need to go into the primary dex.
            // And we can also patch it
            Class<?> delegateClass = Class.forName(delegateClassName, false, getClassLoader());
            Constructor<?> constructor = delegateClass.getConstructor(Application.class, int.class, boolean.class, long.class, long.class,
                Intent.class, Resources[].class, ClassLoader[].class, AssetManager[].class);
            return constructor.newInstance(this, tinkerFlags, tinkerLoadVerifyFlag,
                applicationStartElapsedTime, applicationStartMillisTime,
                tinkerResultIntent, resources, classLoader, assetManager);
        } catch (Throwable e) {
            throw new TinkerRuntimeException("createDelegate failed", e);
        }
    }

    private synchronized void ensureDelegate() {
        if (delegate == null) {
            delegate = createDelegate();
        }
    }

    /**
     * Hook for sub-classes to run logic after the {@link Application#attachBaseContext} has been
     * called but before the delegate is created. Implementors should be very careful what they do
     * here since {@link android.app.Application#onCreate} will not have yet been called.
     */
    private void onBaseContextAttached(Context base) {
        applicationStartElapsedTime = SystemClock.elapsedRealtime();
        applicationStartMillisTime = System.currentTimeMillis();
        loadTinker();
        ensureDelegate();
        try {
            Method method = ShareReflectUtil.findMethod(delegate, "onBaseContextAttached", Context.class);
            method.invoke(delegate, base);
        } catch (Throwable t) {
            throw new TinkerRuntimeException("onBaseContextAttached method not found", t);
        }
        //reset save mode
        if (useSafeMode) {
            String processName = ShareTinkerInternals.getProcessName(this);
            String preferName = ShareConstants.TINKER_OWN_PREFERENCE_CONFIG + processName;
            SharedPreferences sp = getSharedPreferences(preferName, Context.MODE_PRIVATE);
            sp.edit().putInt(ShareConstants.TINKER_SAFE_MODE_COUNT, 0).commit();
        }
    }

    @Override
    protected final void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Thread.setDefaultUncaughtExceptionHandler(new TinkerUncaughtHandler(this));
        onBaseContextAttached(base);
    }

    private void loadTinker() {
        //disable tinker, not need to install
        if (tinkerFlags == TINKER_DISABLE) {
            return;
        }
        tinkerResultIntent = new Intent();
        try {
            //reflect tinker loader, because loaderClass may be define by user!
            Class<?> tinkerLoadClass = Class.forName(loaderClassName, false, getClassLoader());

            Method loadMethod = tinkerLoadClass.getMethod(TINKER_LOADER_METHOD, TinkerApplication.class, int.class, boolean.class);
            Constructor<?> constructor = tinkerLoadClass.getConstructor();
            tinkerResultIntent = (Intent) loadMethod.invoke(constructor.newInstance(), this, tinkerFlags, tinkerLoadVerifyFlag);
        } catch (Throwable e) {
            //has exception, put exception error code
            ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_PATCH_UNKNOWN_EXCEPTION);
            tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, e);
        }
    }

    private void delegateMethod(String methodName) {
        if (delegate != null) {
            try {
                Method method = ShareReflectUtil.findMethod(delegate, methodName, new Class[0]);
                method.invoke(delegate, new Object[0]);
            } catch (Throwable t) {
                throw new TinkerRuntimeException(String.format("%s method not found", methodName), t);
            }
        }
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        ensureDelegate();
        delegateMethod("onCreate");
    }

    @Override
    public final void onTerminate() {
        super.onTerminate();
        delegateMethod("onTerminate");
    }

    @Override
    public final void onLowMemory() {
        super.onLowMemory();
        delegateMethod("onLowMemory");
    }

    private void delegateTrimMemory(int level) {
        if (delegate != null) {
            try {
                Method method = ShareReflectUtil.findMethod(delegate, "onTrimMemory", int.class);
                method.invoke(delegate, level);
            } catch (Throwable t) {
                throw new TinkerRuntimeException("onTrimMemory method not found", t);
            }
        }
    }

    @TargetApi(14)
    @Override
    public final void onTrimMemory(int level) {
        super.onTrimMemory(level);
        delegateTrimMemory(level);
    }

    private void delegateConfigurationChanged(Configuration newConfig) {
        if (delegate != null) {
            try {
                Method method = ShareReflectUtil.findMethod(delegate, "onConfigurationChanged", Configuration.class);
                method.invoke(delegate, newConfig);
            } catch (Throwable t) {
                throw new TinkerRuntimeException("onConfigurationChanged method not found", t);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        delegateConfigurationChanged(newConfig);
    }

    @Override
    public Resources getResources() {
        if (resources[0] != null) {
            return resources[0];
        }
        return super.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        if (classLoader[0] != null) {
            return classLoader[0];
        }
        return super.getClassLoader();
    }

    @Override
    public AssetManager getAssets() {
        if (assetManager[0] != null) {
            return assetManager[0];
        }
        return super.getAssets();
    }

    public void setUseSafeMode(boolean useSafeMode) {
        this.useSafeMode = useSafeMode;
    }
}
