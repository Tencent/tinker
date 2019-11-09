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

import com.tencent.tinker.loader.SystemClassLoaderAdder;
import com.tencent.tinker.loader.TinkerLoader;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.TinkerUncaughtHandler;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import dalvik.system.BaseDexClassLoader;

/**
 * Created by zhangshaowen on 16/3/8.
 */
public abstract class TinkerApplication extends Application {
    private static final String INTENT_PATCH_EXCEPTION = ShareIntentUtil.INTENT_PATCH_EXCEPTION;
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

    private final int gpExpansionMode;
    private final String classLoaderInitializerClassName;

    /**
     * if we have load patch, we should use safe mode
     */
    private boolean useSafeMode;
    private Intent tinkerResultIntent;

    private Object mAppLike = null;
    private ClassLoader mCurrentClassLoader = null;

    protected TinkerApplication(int tinkerFlags) {
        this(tinkerFlags, "com.tencent.tinker.entry.DefaultApplicationLike",
                TinkerLoader.class.getName(), false, ShareConstants.TINKER_GPMODE_DISABLE, "");
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName,
                                String loaderClassName, boolean tinkerLoadVerifyFlag,
                                int gpExpansionMode, String classLoaderInitializerClassName) {
        this.tinkerFlags = tinkerFlags;
        this.delegateClassName = delegateClassName;
        this.loaderClassName = loaderClassName;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        this.gpExpansionMode = gpExpansionMode;
        this.classLoaderInitializerClassName = classLoaderInitializerClassName;
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName) {
        this(tinkerFlags, delegateClassName, TinkerLoader.class.getName(), false, ShareConstants.TINKER_GPMODE_DISABLE, "");
    }

    private void loadTinker() {
        try {
            //reflect tinker loader, because loaderClass may be define by user!
            Class<?> tinkerLoadClass = Class.forName(loaderClassName, false, TinkerApplication.class.getClassLoader());
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

    private void replaceAppClassLoader() {
        tinkerResultIntent = new Intent();
        if (gpExpansionMode == ShareConstants.TINKER_GPMODE_DISABLE) {
            ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_DISABLE);
            return;
        }
        ClassLoader newClassLoader = TinkerApplication.class.getClassLoader();
        if (gpExpansionMode == ShareConstants.TINKER_GPMODE_REPLACE_CLASSLOADER_AND_CALL_INITIALIZER) {
            try {
                newClassLoader = SystemClassLoaderAdder.injectNewClassLoaderOnDemand(this,
                        (BaseDexClassLoader) TinkerApplication.class.getClassLoader(), gpExpansionMode);
            } catch (Throwable thr) {
                ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_INJECT_CLASSLOADER_FAIL);
                tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, thr);
                return;
            }
        }
        if (classLoaderInitializerClassName != null && !classLoaderInitializerClassName.isEmpty()) {
            try {
                final Class<?> classLoaderInitializerClazz = Class.forName(classLoaderInitializerClassName, false, newClassLoader);
                IClassLoaderInitializer classLoaderInitializer = (IClassLoaderInitializer) classLoaderInitializerClazz.newInstance();
                classLoaderInitializer.initializeClassLoader(this, newClassLoader);
            } catch (Throwable thr) {
                ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_INIT_CLASSLOADER_FAIL);
                tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, thr);
                return;
            }
        }
        ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_DISABLE);
    }

    private Object createDelegate(Application app,
                                  int tinkerFlags,
                                  String delegateClassName,
                                  boolean tinkerLoadVerifyFlag,
                                  long applicationStartElapsedTime,
                                  long applicationStartMillisTime,
                                  Intent resultIntent) {
        try {
            // Use reflection to create the delegate so it doesn't need to go into the primary dex.
            // And we can also patch it
            Class<?> delegateClass = Class.forName(delegateClassName, false, mCurrentClassLoader);
            Constructor<?> constructor = delegateClass.getConstructor(Application.class, int.class, boolean.class,
                    long.class, long.class, Intent.class);
            return constructor.newInstance(app, tinkerFlags, tinkerLoadVerifyFlag,
                    applicationStartElapsedTime, applicationStartMillisTime, resultIntent);
        } catch (Throwable e) {
            throw new TinkerRuntimeException("createDelegate failed", e);
        }
    }

    private void onBaseContextAttached(Context base) {
        try {
            final long applicationStartElapsedTime = SystemClock.elapsedRealtime();
            final long applicationStartMillisTime = System.currentTimeMillis();
            if (gpExpansionMode != ShareConstants.TINKER_GPMODE_DISABLE) {
                replaceAppClassLoader();
            } else {
                loadTinker();
            }
            mCurrentClassLoader = base.getClassLoader();
            mAppLike = createDelegate(this, tinkerFlags, delegateClassName,
                    tinkerLoadVerifyFlag, applicationStartElapsedTime, applicationStartMillisTime,
                    tinkerResultIntent);
            callAppLikeOnBaseContextAttached(base);
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

    private Method mAppLikeOnBaseContextAttachedMethod = null;
    private void callAppLikeOnBaseContextAttached(Context base) {
        if (mAppLike == null) {
            throw new IllegalStateException("mAppLike must not be null when calling this method.");
        }
        try {
            synchronized (this) {
                if (mAppLikeOnBaseContextAttachedMethod == null) {
                    mAppLikeOnBaseContextAttachedMethod = findMethod(
                            mAppLike.getClass(), "onBaseContextAttached", Context.class);
                    mAppLikeOnBaseContextAttachedMethod.setAccessible(true);
                }
            }
            mAppLikeOnBaseContextAttachedMethod.invoke(mAppLike, base);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Thread.setDefaultUncaughtExceptionHandler(new TinkerUncaughtHandler(this));
        onBaseContextAttached(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        callAppLikeOnCreate();
    }

    private Method mAppLikeOnCreateMethod = null;
    private void callAppLikeOnCreate() {
        if (mAppLike == null) {
            return;
        }
        try {
            synchronized (this) {
                if (mAppLikeOnCreateMethod == null) {
                    mAppLikeOnCreateMethod = findMethod(
                            mAppLike.getClass(), "onCreate");
                    mAppLikeOnCreateMethod.setAccessible(true);
                }
            }
            mAppLikeOnCreateMethod.invoke(mAppLike);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        callAppLikeOnTerminate();
    }

    private Method mAppLikeOnTerminateMethod = null;
    private void callAppLikeOnTerminate() {
        if (mAppLike == null) {
            return;
        }
        try {
            synchronized (this) {
                if (mAppLikeOnTerminateMethod == null) {
                    mAppLikeOnTerminateMethod = findMethod(
                            mAppLike.getClass(), "onTerminate");
                    mAppLikeOnTerminateMethod.setAccessible(true);
                }
            }
            mAppLikeOnTerminateMethod.invoke(mAppLike);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        callAppLikeOnLowMemoryMethod();
    }

    private Method mAppLikeOnLowMemoryMethod = null;
    private void callAppLikeOnLowMemoryMethod() {
        if (mAppLike == null) {
            return;
        }
        try {
            synchronized (this) {
                if (mAppLikeOnLowMemoryMethod == null) {
                    mAppLikeOnLowMemoryMethod = findMethod(
                            mAppLike.getClass(), "onLowMemory");
                    mAppLikeOnLowMemoryMethod.setAccessible(true);
                }
            }
            mAppLikeOnLowMemoryMethod.invoke(mAppLike);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        callAppLikeOnTrimMemoryMethod(level);
    }

    private Method mAppLikeOnTrimMemoryMethod = null;
    private void callAppLikeOnTrimMemoryMethod(int level) {
        if (mAppLike == null) {
            return;
        }
        try {
            synchronized (this) {
                if (mAppLikeOnTrimMemoryMethod == null) {
                    mAppLikeOnTrimMemoryMethod = findMethod(
                            mAppLike.getClass(), "onTrimMemory", int.class);
                    mAppLikeOnTrimMemoryMethod.setAccessible(true);
                }
            }
            mAppLikeOnTrimMemoryMethod.invoke(mAppLike, level);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        callAppLikeOnConfigurationChangedMethod(newConfig);
    }

    private Method mAppLikeOnConfigurationChangedMethod = null;
    private void callAppLikeOnConfigurationChangedMethod(Configuration newConfig) {
        if (mAppLike == null) {
            return;
        }
        try {
            synchronized (this) {
                if (mAppLikeOnConfigurationChangedMethod == null) {
                    mAppLikeOnConfigurationChangedMethod = findMethod(
                            mAppLike.getClass(), "onConfigurationChanged", Configuration.class);
                    mAppLikeOnConfigurationChangedMethod.setAccessible(true);
                }
            }
            mAppLikeOnConfigurationChangedMethod.invoke(mAppLike, newConfig);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public Resources getResources() {
        final Resources resources = super.getResources();
        return callAppLikeGetResourcesMethod(resources);
    }

    private Method mAppLikeGetResourcesMethod = null;
    private Resources callAppLikeGetResourcesMethod(Resources resource) {
        if (mAppLike == null) {
            return resource;
        }
        try {
            synchronized (this) {
                if (mAppLikeGetResourcesMethod == null) {
                    mAppLikeGetResourcesMethod = findMethod(
                            mAppLike.getClass(), "getResources", Resources.class);
                    mAppLikeGetResourcesMethod.setAccessible(true);
                }
            }
            return (Resources) mAppLikeGetResourcesMethod.invoke(mAppLike, resource);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        final ClassLoader classLoader = super.getClassLoader();
        return callAppLikeGetClassLoaderMethod(classLoader);
    }

    private Method mAppLikeGetClassLoaderMethod = null;
    private ClassLoader callAppLikeGetClassLoaderMethod(ClassLoader classLoader) {
        if (mAppLike == null) {
            return classLoader;
        }
        try {
            synchronized (this) {
                if (mAppLikeGetClassLoaderMethod == null) {
                    mAppLikeGetClassLoaderMethod = findMethod(
                            mAppLike.getClass(), "getClassLoader", ClassLoader.class);
                    mAppLikeGetClassLoaderMethod.setAccessible(true);
                }
            }
            return (ClassLoader) mAppLikeGetClassLoaderMethod.invoke(mAppLike, classLoader);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public AssetManager getAssets() {
        final AssetManager assets = super.getAssets();
        return callAppLikeGetAssetsMethod(assets);
    }

    private Method mAppLikeGetAssetsMethod = null;
    private AssetManager callAppLikeGetAssetsMethod(AssetManager assets) {
        if (mAppLike == null) {
            return assets;
        }
        try {
            synchronized (this) {
                if (mAppLikeGetAssetsMethod == null) {
                    mAppLikeGetAssetsMethod = findMethod(
                            mAppLike.getClass(), "getAssets", AssetManager.class);
                    mAppLikeGetAssetsMethod.setAccessible(true);
                }
            }
            return (AssetManager) mAppLikeGetAssetsMethod.invoke(mAppLike, assets);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public Object getSystemService(String name) {
        final Object service = super.getSystemService(name);
        return callAppLikeGetSystemServiceMethod(name, service);
    }

    private Method mAppLikeGetSystemServiceMethod = null;
    private Object callAppLikeGetSystemServiceMethod(String name, Object service) {
        if (mAppLike == null) {
            return service;
        }
        try {
            synchronized (this) {
                if (mAppLikeGetSystemServiceMethod == null) {
                    mAppLikeGetSystemServiceMethod = findMethod(
                            mAppLike.getClass(), "getSystemService", String.class, Object.class);
                    mAppLikeGetSystemServiceMethod.setAccessible(true);
                }
            }
            return mAppLikeGetSystemServiceMethod.invoke(mAppLike, name, service);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    @Override
    public Context getBaseContext() {
        final Context base = super.getBaseContext();
        return callAppLikeGetBaseContextMethod(base);
    }

    private Method mAppLikeGetBaseContextMethod = null;
    private Context callAppLikeGetBaseContextMethod(Context base) {
        if (mAppLike == null) {
            return base;
        }
        try {
            synchronized (this) {
                if (mAppLikeGetBaseContextMethod == null) {
                    mAppLikeGetBaseContextMethod = findMethod(
                            mAppLike.getClass(), "getBaseContext", Context.class);
                    mAppLikeGetBaseContextMethod.setAccessible(true);
                }
            }
            return (Context) mAppLikeGetBaseContextMethod.invoke(mAppLike, base);
        } catch (Throwable thr) {
            throw new IllegalStateException(thr);
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... argTypes)
            throws NoSuchMethodException {
        Class<?> currClazz = clazz;
        while (true) {
            try {
                final Method result = currClazz.getDeclaredMethod(name, argTypes);
                result.setAccessible(true);
                return result;
            } catch (Throwable ignored) {
                if (currClazz == Object.class) {
                    throw new NoSuchMethodException("Cannot find method "
                            + name + Arrays.toString(argTypes)
                            + " in " + name
                            + " and its super classes.");
                }
                for (Class<?> itf : currClazz.getInterfaces()) {
                    try {
                        final Method result = itf.getDeclaredMethod(name, argTypes);
                        result.setAccessible(true);
                        return result;
                    } catch (Throwable ignored2) {
                        // Ignored.
                    }
                }
                currClazz = currClazz.getSuperclass();
            }
        }
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
