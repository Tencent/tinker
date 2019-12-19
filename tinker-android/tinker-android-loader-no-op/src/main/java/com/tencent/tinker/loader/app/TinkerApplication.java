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

import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;

import java.lang.reflect.Constructor;

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
    private ApplicationLike mAppLike = null;

    protected TinkerApplication(int tinkerFlags) {
        this(ShareConstants.TINKER_DISABLE, "com.tencent.tinker.entry.DefaultApplicationLike",
                null, false);
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName) {
        this(ShareConstants.TINKER_DISABLE, delegateClassName, null, false);
    }

    protected TinkerApplication(int tinkerFlags, String delegateClassName,
                                String loaderClassName, boolean tinkerLoadVerifyFlag) {
        this.tinkerFlags = ShareConstants.TINKER_DISABLE;
        this.delegateClassName = delegateClassName;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
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

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            final long applicationStartElapsedTime = SystemClock.elapsedRealtime();
            final long applicationStartMillisTime = System.currentTimeMillis();
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
    public void onCreate() {
        super.onCreate();
        mAppLike.onCreate();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        mAppLike.onTerminate();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mAppLike.onLowMemory();
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        mAppLike.onTrimMemory(level);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAppLike.onConfigurationChanged(newConfig);
    }

    @Override
    public Resources getResources() {
        final Resources resources = super.getResources();
        return mAppLike.getResources(resources);
    }

    @Override
    public ClassLoader getClassLoader() {
        final ClassLoader classLoader = super.getClassLoader();
        return mAppLike.getClassLoader(classLoader);
    }

    @Override
    public AssetManager getAssets() {
        final AssetManager assets = super.getAssets();
        return mAppLike.getAssets(assets);
    }

    @Override
    public Object getSystemService(String name) {
        final Object service = super.getSystemService(name);
        return mAppLike.getSystemService(name, service);
    }

    @Override
    public Context getBaseContext() {
        final Context base = super.getBaseContext();
        return mAppLike.getBaseContext(base);
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
}
