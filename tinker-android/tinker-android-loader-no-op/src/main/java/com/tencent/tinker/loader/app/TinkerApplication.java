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

import com.tencent.tinker.anno.Keep;
import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.lang.reflect.Constructor;

/**
 * Created by zhangshaowen on 16/3/8.
 */
public abstract class TinkerApplication extends Application {
    private static final TinkerApplication[] SELF_HOLDER = {null};

    private final int tinkerFlags;
    private final boolean tinkerLoadVerifyFlag;
    private final String delegateClassName;
    private final boolean useDelegateLastClassLoader;

    /**
     * if we have load patch, we should use safe mode
     */
    protected Intent tinkerResultIntent;
    protected ClassLoader mCurrentClassLoader = null;
    private ApplicationLike mAppLike = null;

    protected TinkerApplication(int tinkerFlags) {
        this(ShareConstants.TINKER_DISABLE, "com.tencent.tinker.entry.DefaultApplicationLike",
                null, false, false);
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName) {
        this(ShareConstants.TINKER_DISABLE, delegateClassName, null, false, false);
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName,
                                String loaderClassName, boolean tinkerLoadVerifyFlag,
                                boolean useDelegateLastClassLoader) {
        synchronized (SELF_HOLDER) {
            SELF_HOLDER[0] = this;
        }
        this.tinkerFlags = ShareConstants.TINKER_DISABLE;
        this.delegateClassName = delegateClassName;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        this.useDelegateLastClassLoader = useDelegateLastClassLoader;
    }

    public static TinkerApplication getInstance() {
        synchronized (SELF_HOLDER) {
            if (SELF_HOLDER[0] == null) {
                throw new IllegalStateException("TinkerApplication is not initialized.");
            }
            return SELF_HOLDER[0];
        }
    }

    private ApplicationLike createDelegate(Application app,
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
            return (ApplicationLike) constructor.newInstance(app, tinkerFlags, tinkerLoadVerifyFlag,
                    applicationStartElapsedTime, applicationStartMillisTime, resultIntent);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("createDelegate failed", thr);
        }
    }

    protected void onBaseContextAttached(Context base, long applicationStartElapsedTime, long applicationStartMillisTime) {
        try {
            mCurrentClassLoader = base.getClassLoader();
            this.tinkerResultIntent = new Intent();
            ShareIntentUtil.setIntentReturnCode(this.tinkerResultIntent, ShareConstants.ERROR_LOAD_DISABLE);
            mAppLike = createDelegate(this, tinkerFlags, delegateClassName,
                    tinkerLoadVerifyFlag, applicationStartElapsedTime, applicationStartMillisTime,
                    tinkerResultIntent);
            mAppLike.onBaseContextAttached(base);
        } catch (TinkerRuntimeException e) {
            throw e;
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        final long applicationStartElapsedTime = SystemClock.elapsedRealtime();
        final long applicationStartMillisTime = System.currentTimeMillis();
        onBaseContextAttached(base, applicationStartElapsedTime, applicationStartMillisTime);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mAppLike != null) {
            mAppLike.onCreate();
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mAppLike != null) {
            mAppLike.onTerminate();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mAppLike != null) {
            mAppLike.onLowMemory();
        }
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mAppLike != null) {
            mAppLike.onTrimMemory(level);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mAppLike != null) {
            mAppLike.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public Resources getResources() {
        final Resources resources = super.getResources();
        if (mAppLike != null) {
            return mAppLike.getResources(resources);
        } else {
            return resources;
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        final ClassLoader classLoader = super.getClassLoader();
        if (mAppLike != null) {
            return mAppLike.getClassLoader(classLoader);
        } else {
            return classLoader;
        }
    }

    @Override
    public AssetManager getAssets() {
        final AssetManager assets = super.getAssets();
        if (mAppLike != null) {
            return mAppLike.getAssets(assets);
        } else {
            return assets;
        }
    }

    @Override
    public Object getSystemService(String name) {
        final Object service = super.getSystemService(name);
        if (mAppLike != null) {
            return mAppLike.getSystemService(name, service);
        } else {
            return service;
        }
    }

    @Override
    public Context getBaseContext() {
        final Context base = super.getBaseContext();
        if (mAppLike != null) {
            return mAppLike.getBaseContext(base);
        } else {
            return base;
        }
    }

    @Keep
    public int mzNightModeUseOf() {
        if (mAppLike != null) {
            return mAppLike.mzNightModeUseOf();
        } else {
            // Return 1 for default according to MeiZu's announcement.
            return 1;
        }
    }

    public void setUseSafeMode(boolean useSafeMode) {
        // Ignored.
    }

    public boolean isTinkerLoadVerifyFlag() {
        return false;
    }

    public int getTinkerFlags() {
        return tinkerFlags;
    }

    public boolean isUseDelegateLastClassLoader() {
        return useDelegateLastClassLoader;
    }
}
