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

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;

/**
 * Created by shwenzhang on 16/7/28.
 */
public abstract class ApplicationLike implements ApplicationLifeCycle {
    private Resources[]    resources;
    private ClassLoader[]  classLoader;
    private AssetManager[] assetManager;

    private final Application application;
    private final Intent      tinkerResultIntent;
    private final long        applicationStartElapsedTime;
    private final long        applicationStartMillisTime;
    private final int         tinkerFlags;
    private final boolean     tinkerLoadVerifyFlag;

    public ApplicationLike(Application application, int tinkerFlags, boolean tinkerLoadVerifyFlag,
                           long applicationStartElapsedTime, long applicationStartMillisTime, Intent tinkerResultIntent,
                           Resources[] resources, ClassLoader[] classLoader, AssetManager[] assetManager) {
        this.application = application;
        this.tinkerFlags = tinkerFlags;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        this.applicationStartElapsedTime = applicationStartElapsedTime;
        this.applicationStartMillisTime = applicationStartMillisTime;
        this.tinkerResultIntent = tinkerResultIntent;
        this.resources = resources;
        this.classLoader = classLoader;
        this.assetManager = assetManager;
    }

    public void setResources(Resources resources) {
        this.resources[0] = resources;
    }

    public void setAssetManager(AssetManager assetManager) {
        this.assetManager[0] = assetManager;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader[0] = classLoader;
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
}
