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

/**
 * Created by zhangshaowen on 16/3/8.
 */

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.Log;

/**
 * Empty implementation of {@link ApplicationLike}.
 */
public class DefaultApplicationLike extends ApplicationLike {
    private static final String TAG = "DefaultLifeCycle";

    public DefaultApplicationLike(Application application, int tinkerFlags, boolean tinkerLoadVerifyFlag,
                                  long applicationStartElapsedTime, long applicationStartMillisTime, Intent tinkerResultIntent,
                                  Resources[] resources, ClassLoader[] classLoader, AssetManager[] assetManager) {
        super(application, tinkerFlags, tinkerLoadVerifyFlag, applicationStartElapsedTime, applicationStartMillisTime, tinkerResultIntent, resources, classLoader, assetManager);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
    }

    @Override
    public void onLowMemory() {
        Log.d(TAG, "onLowMemory");
    }

    @Override
    public void onTrimMemory(int level) {
        Log.d(TAG, "onTrimMemory level:" + level);
    }

    @Override
    public void onTerminate() {
        Log.d(TAG, "onTerminate");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged:" + newConfig.toString());
    }

    @Override
    public void onBaseContextAttached(Context base) {
        Log.d(TAG, "onBaseContextAttached:");
    }
}
