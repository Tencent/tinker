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
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

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

    /**
     * if we have load patch, we should use safe mode
     */
    private boolean useSafeMode;
    private Intent tinkerResultIntent;

    private ITinkerInlineFenceBridge mBridge = null;

    protected TinkerApplication(int tinkerFlags) {
        this(tinkerFlags, "com.tencent.tinker.entry.DefaultApplicationLike", TinkerLoader.class.getName(), false);
    }

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

    private ITinkerInlineFenceBridge createInlineFence(int tinkerFlags,
                                                       String delegateClassName,
                                                       boolean tinkerLoadVerifyFlag,
                                                       long applicationStartElapsedTime,
                                                       long applicationStartMillisTime,
                                                       Intent resultIntent) {
        try {
            final Class<?> inlineFenceClazz = Class.forName(
                    "com.tencent.tinker.entry.TinkerApplicationInlineFence",
                    true, super.getClassLoader());
            final Constructor<?> ctor = inlineFenceClazz.getConstructor(int.class, String.class,
                    boolean.class, long.class, long.class, Intent.class);
            ctor.setAccessible(true);
            return (ITinkerInlineFenceBridge) ctor.newInstance(tinkerFlags, delegateClassName,
                    tinkerLoadVerifyFlag, applicationStartElapsedTime,
                    applicationStartMillisTime, resultIntent);
        } catch (Throwable thr) {
            throw new TinkerRuntimeException("fail to create inline fence instance.", thr);
        }
    }

    private void onBaseContextAttached(Context base) {
        try {
            final long applicationStartElapsedTime = SystemClock.elapsedRealtime();
            final long applicationStartMillisTime = System.currentTimeMillis();
            loadTinker();
            mBridge = createInlineFence(tinkerFlags, delegateClassName,
                    tinkerLoadVerifyFlag, applicationStartElapsedTime, applicationStartMillisTime,
                    tinkerResultIntent);
            mBridge.attachBaseContext(this, base);
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

    @Override
    public void onCreate() {
        super.onCreate();
        if (mBridge != null) {
            mBridge.onCreate(this);
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (mBridge != null) {
            mBridge.onTerminate();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mBridge != null) {
            mBridge.onLowMemory();
        }
    }

    @TargetApi(14)
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mBridge != null) {
            mBridge.onTrimMemory(level);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mBridge != null) {
            mBridge.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public Resources getResources() {
        final Resources resources = super.getResources();
        return (mBridge != null ? mBridge.getResources(resources) : resources);
    }

    @Override
    public ClassLoader getClassLoader() {
        final ClassLoader classLoader = super.getClassLoader();
        return (mBridge != null ? mBridge.getClassLoader(classLoader) : classLoader);
    }

    @Override
    public AssetManager getAssets() {
        final AssetManager assetManager = super.getAssets();
        return (mBridge != null ? mBridge.getAssets(assetManager) : assetManager);
    }

    @Override
    public Object getSystemService(String name) {
        final Object service = super.getSystemService(name);
        return (mBridge != null ? mBridge.getSystemService(name, service) : service);
    }

    @Override
    public Context getBaseContext() {
        final Context base = super.getBaseContext();
        return (mBridge != null ? mBridge.getBaseContext(base) : base);
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
