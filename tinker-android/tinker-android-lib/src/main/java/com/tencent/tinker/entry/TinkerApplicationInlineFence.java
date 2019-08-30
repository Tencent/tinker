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

package com.tencent.tinker.entry;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.annotation.Keep;

import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.app.ITinkerInlineFenceBridge;
import com.tencent.tinker.loader.app.TinkerApplication;
import com.tencent.tinker.loader.hotplug.ComponentHotplug;
import com.tencent.tinker.loader.hotplug.UnsupportedEnvironmentException;

import java.lang.reflect.Constructor;

@Keep
public final class TinkerApplicationInlineFence implements ITinkerInlineFenceBridge {
    private final int mTinkerFlags;
    private final String mDelegateClassName;
    private final boolean mTinkerLoadVerifyFlag;

    private final long mApplicationStartElapsedTime;
    private final long mApplicationStartMillisTime;

    private final Intent mTinkerResultIntent;

    private ApplicationLike mApplicationLike = null;

    public TinkerApplicationInlineFence(int tinkerFlags,
                                        String delegateClassName,
                                        boolean tinkerLoadVerifyFlag,
                                        long applicationStartElapsedTime,
                                        long applicationStartMillisTime,
                                        Intent resultIntent) {
        mTinkerFlags = tinkerFlags;
        mDelegateClassName = delegateClassName;
        mTinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        mApplicationStartElapsedTime = applicationStartElapsedTime;
        mApplicationStartMillisTime = applicationStartMillisTime;
        mTinkerResultIntent = resultIntent;
    }

    private static void dummyThrowExceptionMethod() {
        if (TinkerApplicationInlineFence.class.isPrimitive()) {
            throw new RuntimeException();
        }
    }

    private Object createDelegate(TinkerApplication app) {
        try {
            // Use reflection to create the delegate so it doesn't need to go into the primary dex.
            // And we can also patch it
            Class<?> delegateClass = Class.forName(mDelegateClassName, false, this.getClass().getClassLoader());
            Constructor<?> constructor = delegateClass.getConstructor(Application.class, int.class, boolean.class,
                    long.class, long.class, Intent.class);
            return constructor.newInstance(app, mTinkerFlags, mTinkerLoadVerifyFlag,
                    mApplicationStartElapsedTime, mApplicationStartMillisTime, mTinkerResultIntent);
        } catch (Throwable e) {
            throw new TinkerRuntimeException("createDelegate failed", e);
        }
    }

    private synchronized void ensureDelegate(TinkerApplication app) {
        if (mApplicationLike == null) {
            mApplicationLike = (ApplicationLike) createDelegate(app);
        }
    }

    @Keep
    private void attachBaseContextImpl_$noinline$(TinkerApplication app, Context base) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            ensureDelegate(app);
            if (mApplicationLike != null) {
                mApplicationLike.onBaseContextAttached(base);
            }
        }
    }

    @Override
    public void attachBaseContext(TinkerApplication app, Context base) {
        attachBaseContextImpl_$noinline$(app, base);
    }

    @Keep
    private void onCreateImpl_$noinline$(TinkerApplication app) {
        try {
            ensureDelegate(app);
            try {
                ComponentHotplug.ensureComponentHotplugInstalled(app);
            } catch (UnsupportedEnvironmentException e) {
                throw new TinkerRuntimeException("failed to make sure that ComponentHotplug logic is fine.", e);
            }
            if (mApplicationLike != null) {
                mApplicationLike.onCreate();
            }
        } catch (TinkerRuntimeException e) {
            throw e;
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
        }
    }

    @Override
    public void onCreate(TinkerApplication app) {
        onCreateImpl_$noinline$(app);
    }

    @Keep
    private void onConfigurationChangedImpl_$noinline$(Configuration newConfig) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                mApplicationLike.onConfigurationChanged(newConfig);
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        onConfigurationChangedImpl_$noinline$(newConfig);
    }

    @Keep
    private void onTrimMemoryImpl_$noinline$(int level) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                mApplicationLike.onTrimMemory(level);
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        onTrimMemoryImpl_$noinline$(level);
    }

    @Keep
    private void onLowMemoryImpl_$noinline$() {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                mApplicationLike.onLowMemory();
            }
        }
    }

    @Override
    public void onLowMemory() {
        onLowMemoryImpl_$noinline$();
    }

    @Keep
    private void onTerminateImpl_$noinline$() {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                mApplicationLike.onTerminate();
            }
        }
    }

    @Override
    public void onTerminate() {
        onTerminateImpl_$noinline$();
    }

    @Keep
    private ClassLoader getClassLoaderImpl_$noinline$(ClassLoader cl) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                return mApplicationLike.getClassLoader(cl);
            } else {
                return cl;
            }
        }
    }

    @Override
    public ClassLoader getClassLoader(ClassLoader cl) {
        return getClassLoaderImpl_$noinline$(cl);
    }

    @Keep
    private Context getBaseContextImpl_$noinline$(Context base) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                return mApplicationLike.getBaseContext(base);
            } else {
                return base;
            }
        }
    }

    @Override
    public Context getBaseContext(Context base) {
        return getBaseContextImpl_$noinline$(base);
    }

    @Keep
    private AssetManager getAssetsImpl_$noinline$(AssetManager assets) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                return mApplicationLike.getAssets(assets);
            } else {
                return assets;
            }
        }
    }

    @Override
    public AssetManager getAssets(AssetManager assets) {
        return getAssetsImpl_$noinline$(assets);
    }

    @Keep
    private Resources getResourcesImpl_$noinline$(Resources res) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                return mApplicationLike.getResources(res);
            } else {
                return res;
            }
        }
    }

    @Override
    public Resources getResources(Resources res) {
        return getResourcesImpl_$noinline$(res);
    }

    @Keep
    private Object getSystemServiceImpl_$noinline$(String name, Object service) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            if (mApplicationLike != null) {
                return mApplicationLike.getSystemService(name, service);
            } else {
                return service;
            }
        }
    }

    @Override
    public Object getSystemService(String name, Object service) {
        return getSystemServiceImpl_$noinline$(name, service);
    }
}
