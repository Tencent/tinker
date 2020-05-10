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

import com.tencent.tinker.anno.Keep;

/**
 * Created by zhangshaowen on 16/7/28.
 */
@Keep
public abstract class ApplicationLike implements ApplicationLifeCycle {
    private final Application application;
    private final Intent      tinkerResultIntent;
    private final long        applicationStartElapsedTime;
    private final long        applicationStartMillisTime;
    private final int         tinkerFlags;
    private final boolean     tinkerLoadVerifyFlag;


    public ApplicationLike(Application application, int tinkerFlags, boolean tinkerLoadVerifyFlag,
                           long applicationStartElapsedTime, long applicationStartMillisTime, Intent tinkerResultIntent) {
        this.application = application;
        this.tinkerFlags = tinkerFlags;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        this.applicationStartElapsedTime = applicationStartElapsedTime;
        this.applicationStartMillisTime = applicationStartMillisTime;
        this.tinkerResultIntent = tinkerResultIntent;
    }

    public Application getApplication() {
        return application;
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

    public long getApplicationStartElapsedTime() {
        return applicationStartElapsedTime;
    }

    public long getApplicationStartMillisTime() {
        return applicationStartMillisTime;
    }

    @Override
    public void onCreate() {

    }

    @Override
    public void onLowMemory() {

    }

    @Override
    public void onTrimMemory(int level) {

    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

    }

    @Override
    public void onBaseContextAttached(Context base) {

    }
    //some get methods that may be overwrite
    @Keep
    public Resources getResources(Resources resources) {
        return resources;
    }

    @Keep
    public ClassLoader getClassLoader(ClassLoader classLoader) {
        return classLoader;
    }

    @Keep
    public AssetManager getAssets(AssetManager assetManager) {
       return assetManager;
    }

    @Keep
    public Object getSystemService(String name, Object service) {
        return service;
    }

    @Keep
    public Context getBaseContext(Context base) {
        return base;
    }
}

