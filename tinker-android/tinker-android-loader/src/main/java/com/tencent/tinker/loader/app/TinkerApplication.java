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
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.SystemClock;

import com.tencent.tinker.loader.TinkerLoader;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.TinkerUncaughtHandler;
import com.tencent.tinker.loader.hotplug.ComponentHotplug;
import com.tencent.tinker.loader.hotplug.UnsupportedEnvironmentException;
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
    private final int     tinkerFlags;
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
    private       boolean useSafeMode;
    private       Intent  tinkerResultIntent;

    private Object applicationLike = null;

    private long applicationStartElapsedTime;
    private long applicationStartMillisTime;

    /**
     * current build.
     */
    protected TinkerApplication(int tinkerFlags) {
        this(tinkerFlags, "com.tencent.tinker.entry.DefaultApplicationLike", TinkerLoader.class.getName(), false);
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
            Constructor<?> constructor = delegateClass.getConstructor(Application.class, int.class, boolean.class,
                long.class, long.class, Intent.class);
            return constructor.newInstance(this, tinkerFlags, tinkerLoadVerifyFlag,
                applicationStartElapsedTime, applicationStartMillisTime, tinkerResultIntent);
        } catch (Throwable e) {
            throw new TinkerRuntimeException("createDelegate failed", e);
        }
    }

    private synchronized void ensureDelegate() {
        if (applicationLike == null) {
            applicationLike = createDelegate();
        }
    }

    private Method appLikeOnBaseContextAttached = null;

    private void invokeAppLikeOnBaseContextAttached(Object appLike, Context base) throws TinkerRuntimeException {
        try {
            if (appLikeOnBaseContextAttached == null) {
                appLikeOnBaseContextAttached = ShareReflectUtil.findMethod(applicationLike, "onBaseContextAttached", Context.class);
            }
            appLikeOnBaseContextAttached.invoke(appLike, base);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke onBaseContextAttached of appLike.", thr);
        }
    }

    /**
     * Hook for sub-classes to run logic after the {@link Application#attachBaseContext} has been
     * called but before the delegate is created. Implementors should be very careful what they do
     * here since {@link android.app.Application#onCreate} will not have yet been called.
     */
    private void onBaseContextAttached(Context base) {
        try {
            applicationStartElapsedTime = SystemClock.elapsedRealtime();
            applicationStartMillisTime = System.currentTimeMillis();
            loadTinker();
            ensureDelegate();
            invokeAppLikeOnBaseContextAttached(applicationLike, base);
            //reset save mode
            if (useSafeMode) {
                ShareTinkerInternals.setSafeModeCount(this, 0);
            }
        } catch (TinkerRuntimeException e) {
            throw e;
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Thread.setDefaultUncaughtExceptionHandler(new TinkerUncaughtHandler(this));
        onBaseContextAttached(base);
    }

    private void loadTinker() {
        try {
            //reflect tinker loader, because loaderClass may be define by user!
            Class<?> tinkerLoadClass = Class.forName(loaderClassName, false, getClassLoader());
            Method loadMethod = tinkerLoadClass.getMethod(TINKER_LOADER_METHOD, TinkerApplication.class);
            Constructor<?> constructor = tinkerLoadClass.getConstructor();
            tinkerResultIntent = (Intent) loadMethod.invoke(constructor.newInstance(), this);
        } catch (Throwable e) {
            //has exception, put exception error code
            tinkerResultIntent = new Intent();
            ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_PATCH_UNKNOWN_EXCEPTION);
            tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, e);
        }
    }

    private Method appLikeOnCreate = null;

    private void invokeAppLikeOnCreate(Object appLike) throws TinkerRuntimeException {
        try {
            if (appLikeOnCreate == null) {
                appLikeOnCreate = ShareReflectUtil.findMethod(applicationLike, "onCreate");
            }
            appLikeOnCreate.invoke(appLike);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke onCreate of appLike.", thr);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ensureDelegate();
            try {
                ComponentHotplug.ensureComponentHotplugInstalled(this);
            } catch (UnsupportedEnvironmentException e) {
                throw new TinkerRuntimeException("failed to make sure that ComponentHotplug logic is fine.", e);
            }
            invokeAppLikeOnCreate(applicationLike);
        } catch (TinkerRuntimeException e) {
            throw e;
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
        }
    }

    private Method appLikeOnTerminate = null;

    private void invokeAppLikeOnTerminate(Object appLike) throws TinkerRuntimeException {
        try {
            if (appLikeOnTerminate == null) {
                appLikeOnTerminate = ShareReflectUtil.findMethod(applicationLike, "onTerminate");
            }
            appLikeOnTerminate.invoke(appLike);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke onTerminate of appLike.", thr);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (applicationLike != null) {
            invokeAppLikeOnTerminate(applicationLike);
        }
    }

    private Method appLikeOnLowMemory = null;

    private void invokeAppLikeOnLowMemory(Object appLike) throws TinkerRuntimeException {
        try {
            if (appLikeOnLowMemory == null) {
                appLikeOnLowMemory = ShareReflectUtil.findMethod(applicationLike, "onLowMemory");
            }
            appLikeOnLowMemory.invoke(appLike);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke onLowMemory of appLike.", thr);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (applicationLike != null) {
            invokeAppLikeOnLowMemory(applicationLike);
        }
    }

    private Method appLikeOnTrimMemory = null;

    private void invokeAppLikeOnTrimMemory(Object appLike, int level) throws TinkerRuntimeException {
        try {
            if (appLikeOnTrimMemory == null) {
                appLikeOnTrimMemory = ShareReflectUtil.findMethod(applicationLike, "onTrimMemory", int.class);
            }
            appLikeOnTrimMemory.invoke(appLike, level);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke onTrimMemory of appLike.", thr);
        }
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (applicationLike != null) {
            invokeAppLikeOnTrimMemory(applicationLike, level);
        }
    }

    private Method appLikeOnConfigurationChanged = null;

    private void invokeAppLikeOnConfigurationChanged(Object appLike, Configuration newConfig) throws TinkerRuntimeException {
        try {
            if (appLikeOnConfigurationChanged == null) {
                appLikeOnConfigurationChanged = ShareReflectUtil.findMethod(applicationLike, "onConfigurationChanged", Configuration.class);
            }
            appLikeOnConfigurationChanged.invoke(appLike, newConfig);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke onConfigurationChanged of appLike.", thr);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (applicationLike != null) {
            invokeAppLikeOnConfigurationChanged(applicationLike, newConfig);
        }
    }

    private Method appLikeGetResources = null;

    private Resources invokeAppLikeGetResources(Object appLike, Resources resources) throws TinkerRuntimeException {
        try {
            if (appLikeGetResources == null) {
                appLikeGetResources = ShareReflectUtil.findMethod(applicationLike, "getResources", Resources.class);
            }
            return (Resources) appLikeGetResources.invoke(appLike, resources);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke getResources of appLike.", thr);
        }
    }

    @Override
    public Resources getResources() {
        Resources resources = super.getResources();
        if (applicationLike != null) {
            return invokeAppLikeGetResources(applicationLike, resources);
        }
        return resources;
    }

    private Method appLikeGetClassLoader = null;

    private ClassLoader invokeAppLikeGetClassLoader(Object appLike, ClassLoader classLoader) throws TinkerRuntimeException {
        try {
            if (appLikeGetClassLoader == null) {
                appLikeGetClassLoader = ShareReflectUtil.findMethod(applicationLike, "getClassLoader", ClassLoader.class);
            }
            return (ClassLoader) appLikeGetClassLoader.invoke(appLike, classLoader);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke getClassLoader of appLike.", thr);
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        ClassLoader classLoader = super.getClassLoader();
        if (applicationLike != null) {
            return invokeAppLikeGetClassLoader(applicationLike, classLoader);
        }
        return classLoader;
    }

    private Method appLikeGetAssets = null;

    private AssetManager invokeAppLikeGetAssets(Object appLike, AssetManager assetManager) throws TinkerRuntimeException {
        try {
            if (appLikeGetAssets == null) {
                appLikeGetAssets = ShareReflectUtil.findMethod(applicationLike, "getAssets", AssetManager.class);
            }
            return (AssetManager) appLikeGetAssets.invoke(appLike, assetManager);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke getAssets of appLike.", thr);
        }
    }

    @Override
    public AssetManager getAssets() {
        AssetManager assetManager = super.getAssets();
        if (applicationLike != null) {
            return invokeAppLikeGetAssets(applicationLike, assetManager);
        }
        return assetManager;
    }

    private Method appLikeGetSystemService = null;

    private Object invokeAppLikeGetSystemService(Object appLike, String name, Object service) throws TinkerRuntimeException {
        try {
            if (appLikeGetSystemService == null) {
                appLikeGetSystemService = ShareReflectUtil.findMethod(applicationLike, "getSystemService", String.class, Object.class);
            }
            return appLikeGetSystemService.invoke(appLike, name, service);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke getSystemService of appLike.", thr);
        }
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (applicationLike != null) {
            return invokeAppLikeGetSystemService(applicationLike, name, service);
        }
        return service;
    }

    private Method appLikeGetBaseContext = null;

    private Object invokeAppLikeGetBaseContext(Object appLike, Context base) throws TinkerRuntimeException {
        try {
            if (appLikeGetBaseContext == null) {
                appLikeGetBaseContext = ShareReflectUtil.findMethod(applicationLike, "getBaseContext", Context.class);
            }
            return appLikeGetBaseContext.invoke(appLike, base);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to invoke getBaseContext of appLike.", thr);
        }
    }

    @Override
    public Context getBaseContext() {
        Context base = super.getBaseContext();
        if (applicationLike != null) {
            return (Context) invokeAppLikeGetBaseContext(applicationLike, base);
        }
        return base;
    }

    public void setUseSafeMode(boolean useSafeMode) {
        this.useSafeMode = useSafeMode;
    }

    public boolean isTinkerLoadVerifyFlag() {
        return tinkerLoadVerifyFlag;
    }

    public int getTinkerFlags() {
        return tinkerFlags;
    }
}
