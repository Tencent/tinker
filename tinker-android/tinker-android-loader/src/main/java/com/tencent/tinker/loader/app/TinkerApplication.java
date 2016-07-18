/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader.app;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;

import com.tencent.tinker.loader.TinkerLoader;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Created by shwenzhang on 16/3/8.
 *
 * A base class for implementing an Application that delegates to an {@link ApplicationLifeCycle}
 * instance. This is used in conjunction with secondary dex files so that the logic that would
 * normally live in the Application class is loaded after the secondary dexes are loaded.
 */
public abstract class TinkerApplication extends Application {

    //oh, we can use ShareConstants, because they are loader class and static final!
    public static final int    TINKER_DISABLE         = ShareConstants.TINKER_DISABLE;
    public static final int    TINKER_DEX_ONLY        = ShareConstants.TINKER_DEX_MASK;
    public static final int    TINKER_LIBRARY_ONLY    = ShareConstants.TINKER_NATIVE_LIBRARY_MASK;
    public static final int    TINKER_ENABLE_ALL      = ShareConstants.TINKER_ENABLE_ALL;
    public static final String INTENT_PATCH_EXCEPTION = ShareIntentUtil.INTENT_PATCH_EXCEPTION;
    private static final String TINKER_LOADER_METHOD = "tryLoad";
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
    private final String delegateClassName;
    private final String loaderClassName;
    private Intent tinkerResultIntent;
    private ApplicationLifeCycle delegate;

    private Resources    resources;
    private ClassLoader  classLoader;
    private AssetManager assetManager;

    private long applicationStartElapsedTime;
    private long applicationStartMillisTime;

    /**
     * current build.
     */
    protected TinkerApplication(int tinkerFlags) {
        this(tinkerFlags, DefaultApplicationLifeCycle.class.getName(), TinkerLoader.class.getName(), false);
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

    private ApplicationLifeCycle createDelegate() {
        try {
            Class<ApplicationLifeCycle> implClass = (Class<ApplicationLifeCycle>) Class.forName(delegateClassName);
            Constructor<ApplicationLifeCycle> constructor = implClass.getConstructor(TinkerApplication.class);
            return constructor.newInstance(this);
        } catch (Exception e) {
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
        delegate.onBaseContextAttached(base);
    }

    @Override
    protected final void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        onBaseContextAttached(base);
    }

    private void loadTinker() {
        //disable tinker, not need to install
        if (tinkerFlags == TINKER_DISABLE) {
            return;
        }

        tinkerResultIntent = new Intent();
        try {
            // Use reflection to create the delegate so it doesn't need to go into the primary dex.
            Class<?> tinkerLoadClass = Class.forName(loaderClassName);

            Method loadMethod = tinkerLoadClass.getMethod(TINKER_LOADER_METHOD, Application.class, int.class, boolean.class);
            Constructor<?> constructor = tinkerLoadClass.getConstructor();
            tinkerResultIntent = (Intent) loadMethod.invoke(constructor.newInstance(), this, tinkerFlags, tinkerLoadVerifyFlag);
        } catch (Throwable e) {
//            e.printStackTrace();
            //has exception, put exception error code
            ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_PATCH_EXCEPTION);
            tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, e);
        }
    }

    @Override
    public final void onCreate() {
        super.onCreate();
        ensureDelegate();
        delegate.onCreate();
    }

    @Override
    public final void onTerminate() {
        super.onTerminate();
        if (delegate != null) {
            delegate.onTerminate();
        }
    }

    @Override
    public final void onLowMemory() {
        super.onLowMemory();
        if (delegate != null) {
            delegate.onLowMemory();
        }
    }

    @TargetApi(14)
    @Override
    public final void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (delegate != null) {
            delegate.onTrimMemory(level);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (delegate != null) {
            delegate.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public Resources getResources() {
        if (resources != null) {
            return resources;
        }
        return super.getResources();
    }

    @Override
    public ClassLoader getClassLoader() {
        if (classLoader != null) {
            return classLoader;
        }
        return super.getClassLoader();
    }

    @Override
    public AssetManager getAssets() {
        if (assetManager != null) {
            return assetManager;
        }
        return super.getAssets();
    }

    public final Intent getTinkerResultIntent() {
        return tinkerResultIntent;
    }

    public final int getTinkerFlags() {
        return tinkerFlags;
    }

    public final boolean getTinkerLoadVerifyFlag() {
        return tinkerLoadVerifyFlag;
    }

    /**
     * @return the delegate, or {@code null} if not set up.
     */
    // @Nullable  - Don't want to force a reference to that annotation in the primary dex.
    public final ApplicationLifeCycle getDelegateIfPresent() {
        return delegate;
    }

    /**
     * sometimes, we may need to set our own resource for application
     *
     * @param res
     */
    public void setTinkerResources(Resources res) {
        this.resources = res;
    }

    /**
     * sometimes, we may need to set our own resource for application
     *
     * @param assets
     */
    public void setTinkerAssets(AssetManager assets) {
        this.assetManager = assets;
    }

    /**
     * sometimes, we may need to set our own classloader for application
     *
     * @param loader
     */
    public void setTinkerClassLoader(ClassLoader loader) {
        this.classLoader = loader;
    }

    /**
     * start time {@code SystemClock.elapsedRealtime()}
     * @return
     */
    public long getApplicationStartElapsedTime() {
        return applicationStartElapsedTime;
    }

    /**
     * start time {@code  System.currentTimeMillis()}
     * @return
     */
    public long getApplicationStartMillisTime() {
        return applicationStartMillisTime;
    }

}
