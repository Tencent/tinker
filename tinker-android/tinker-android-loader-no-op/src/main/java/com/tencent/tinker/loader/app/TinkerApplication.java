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
import android.os.Handler;
import android.os.SystemClock;

import com.tencent.tinker.loader.SystemClassLoaderAdder;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import dalvik.system.BaseDexClassLoader;

import static com.tencent.tinker.loader.shareutil.ShareIntentUtil.INTENT_PATCH_EXCEPTION;

/**
 * Created by zhangshaowen on 16/3/8.
 */
public abstract class TinkerApplication extends Application {
    private final int tinkerFlags;
    private final boolean tinkerLoadVerifyFlag;
    private final String delegateClassName;

    /**
     * if we have load patch, we should use safe mode
     */
    private Intent tinkerResultIntent;
    private ClassLoader mCurrentClassLoader = null;
    private Handler mInlineFence = null;

    private final int gpExpansionMode;
    private final String classLoaderInitializerClassName;

    protected TinkerApplication(int tinkerFlags) {
        this(ShareConstants.TINKER_DISABLE, "com.tencent.tinker.entry.DefaultApplicationLike",
                null, false, ShareConstants.TINKER_GPMODE_DISABLE, "");
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName) {
        this(ShareConstants.TINKER_DISABLE, delegateClassName, null, false, ShareConstants.TINKER_GPMODE_DISABLE, "");
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName,
                                String loaderClassName, boolean tinkerLoadVerifyFlag,
                                int gpExpansionMode, String classLoaderInitializerClassName) {
        this.tinkerFlags = ShareConstants.TINKER_DISABLE;
        this.delegateClassName = delegateClassName;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        this.gpExpansionMode = gpExpansionMode;
        this.classLoaderInitializerClassName = classLoaderInitializerClassName;
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
                final Class<?> classLoaderInitializerClazz
                        = Class.forName(classLoaderInitializerClassName, false, newClassLoader);
                final Method initializeClassLoaderMethod
                        = classLoaderInitializerClazz.getDeclaredMethod(
                        "initializeClassLoader", Application.class, ClassLoader.class);
                final Object classLoaderInitializer = classLoaderInitializerClazz.newInstance();
                initializeClassLoaderMethod.invoke(classLoaderInitializer, this, newClassLoader);
            } catch (Throwable thr) {
                ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_INIT_CLASSLOADER_FAIL);
                tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, thr);
                return;
            }
        }
        ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_DISABLE);
    }

    private Handler createInlineFence(Application app,
                                      int tinkerFlags,
                                      String delegateClassName,
                                      boolean tinkerLoadVerifyFlag,
                                      long applicationStartElapsedTime,
                                      long applicationStartMillisTime,
                                      Intent resultIntent) {
        try {
            // Use reflection to create the delegate so it doesn't need to go into the primary dex.
            // And we can also patch it
            final Class<?> delegateClass = Class.forName(delegateClassName, false, mCurrentClassLoader);
            final Constructor<?> constructor = delegateClass.getConstructor(Application.class, int.class, boolean.class,
                    long.class, long.class, Intent.class);
            final Object appLike = constructor.newInstance(app, tinkerFlags, tinkerLoadVerifyFlag,
                    applicationStartElapsedTime, applicationStartMillisTime, resultIntent);
            final Class<?> inlineFenceClass = Class.forName(
                    "com.tencent.tinker.entry.TinkerApplicationInlineFence", false, mCurrentClassLoader);
            final Class<?> appLikeClass = Class.forName(
                    "com.tencent.tinker.entry.ApplicationLike", false, mCurrentClassLoader);
            final Constructor<?> inlineFenceCtor = inlineFenceClass.getConstructor(appLikeClass);
            inlineFenceCtor.setAccessible(true);
            return (Handler) inlineFenceCtor.newInstance(appLike);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("createInlineFence failed", thr);
        }
    }

    private void onBaseContextAttached(Context base) {
        try {
            final long applicationStartElapsedTime = SystemClock.elapsedRealtime();
            final long applicationStartMillisTime = System.currentTimeMillis();
            if (gpExpansionMode != ShareConstants.TINKER_GPMODE_DISABLE) {
                replaceAppClassLoader();
            }
            mCurrentClassLoader = base.getClassLoader();
            mInlineFence = createInlineFence(this, tinkerFlags, delegateClassName,
                    tinkerLoadVerifyFlag, applicationStartElapsedTime, applicationStartMillisTime,
                    tinkerResultIntent);
            TinkerInlineFenceAction.callOnBaseContextAttached(mInlineFence, base);
        } catch (TinkerRuntimeException e) {
            throw e;
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        onBaseContextAttached(base);
    }

    private static void dummyThrownExceptionMethod() throws Throwable {
        if (File.pathSeparator == null) {
            throw new Throwable();
        }
    }

    @Override
    public void onCreate() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            super.onCreate();
            if (mInlineFence == null) {
                return;
            }
            TinkerInlineFenceAction.callOnCreate(mInlineFence);
        }
    }

    @Override
    public void onTerminate() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            super.onTerminate();
            if (mInlineFence == null) {
                return;
            }
            TinkerInlineFenceAction.callOnTerminate(mInlineFence);
        }
    }

    @Override
    public void onLowMemory() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            super.onLowMemory();
            if (mInlineFence == null) {
                return;
            }
            TinkerInlineFenceAction.callOnLowMemory(mInlineFence);
        }
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            super.onTrimMemory(level);
            if (mInlineFence == null) {
                return;
            }
            TinkerInlineFenceAction.callOnTrimMemory(mInlineFence, level);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            super.onConfigurationChanged(newConfig);
            if (mInlineFence == null) {
                return;
            }
            TinkerInlineFenceAction.callOnConfigurationChanged(mInlineFence, newConfig);
        }
    }

    @Override
    public Resources getResources() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            final Resources resources = super.getResources();
            if (mInlineFence == null) {
                return resources;
            }
            return TinkerInlineFenceAction.callGetResources(mInlineFence, resources);
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            final ClassLoader classLoader = super.getClassLoader();
            if (mInlineFence == null) {
                return classLoader;
            }
            return TinkerInlineFenceAction.callGetClassLoader(mInlineFence, classLoader);
        }
    }

    @Override
    public AssetManager getAssets() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            final AssetManager assets = super.getAssets();
            if (mInlineFence == null) {
                return assets;
            }
            return TinkerInlineFenceAction.callGetAssets(mInlineFence, assets);
        }
    }

    @Override
    public Object getSystemService(String name) {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            final Object service = super.getSystemService(name);
            if (mInlineFence == null) {
                return service;
            }
            return TinkerInlineFenceAction.callGetSystemService(mInlineFence, name, service);
        }
    }

    @Override
    public Context getBaseContext() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            final Context base = super.getBaseContext();
            if (mInlineFence == null) {
                return base;
            }
            return TinkerInlineFenceAction.callGetBaseContext(mInlineFence, base);
        }
    }

    public void setUseSafeMode(boolean useSafeMode) {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        }
    }

    public boolean isTinkerLoadVerifyFlag() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            return tinkerLoadVerifyFlag;
        }
    }

    public int getTinkerFlags() {
        try {
            dummyThrownExceptionMethod();
        } catch (Throwable ignored) {
            // ignored.
        } finally {
            return tinkerFlags;
        }
    }
}
