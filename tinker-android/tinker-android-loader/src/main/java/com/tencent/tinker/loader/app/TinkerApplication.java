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
    private final String  loaderClassName;

    /**
     * if we have load patch, we should use safe mode
     */
    private       boolean useSafeMode;

    public Intent getTinkerResultIntent() {
        return tinkerResultIntent;
    }

    private       Intent  tinkerResultIntent;

    /**
     * current build.
     */
    protected TinkerApplication(int tinkerFlags) {
        this(tinkerFlags, TinkerLoader.class.getName(), false);
    }

    protected TinkerApplication(int tinkerFlags, String loaderClassName, boolean tinkerLoadVerifyFlag) {
        this.tinkerFlags = tinkerFlags;
        this.loaderClassName = loaderClassName;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
    }

    /**
     * Hook for sub-classes to run logic after the {@link Application#attachBaseContext} has been
     * called but before the delegate is created. Implementors should be very careful what they do
     * here since {@link android.app.Application#onCreate} will not have yet been called.
     */
    private void onBaseContextAttached(Context base) {
        try {
            loadTinker();
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

    protected void onAfterAttachBaseContext() {
        //reset save mode
        if (useSafeMode) {
            ShareTinkerInternals.setSafeModeCount(this, 0);
        }
    }

    private void loadTinker() {
        try {
            //reflect tinker loader, because loaderClass may be define by user!
            Class<?> tinkerLoadClass = Class.forName(loaderClassName, false, getClassLoader());
            Method loadMethod = tinkerLoadClass.getMethod(TINKER_LOADER_METHOD, com.tencent.tinker.loader.app.TinkerApplication.class);
            Constructor<?> constructor = tinkerLoadClass.getConstructor();
            tinkerResultIntent = (Intent) loadMethod.invoke(constructor.newInstance(), this);
        } catch (Throwable e) {
            //has exception, put exception error code
            tinkerResultIntent = new Intent();
            ShareIntentUtil.setIntentReturnCode(tinkerResultIntent, ShareConstants.ERROR_LOAD_PATCH_UNKNOWN_EXCEPTION);
            tinkerResultIntent.putExtra(INTENT_PATCH_EXCEPTION, e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            try {
                ComponentHotplug.ensureComponentHotplugInstalled(null);
            } catch (UnsupportedEnvironmentException e) {
                throw new TinkerRuntimeException("failed to make sure that ComponentHotplug logic is fine.", e);
            }
        } catch (TinkerRuntimeException e) {
            throw e;
        } catch (Throwable thr) {
            throw new TinkerRuntimeException(thr.getMessage(), thr);
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
