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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;

public interface ITinkerInlineFenceBridge {
    void attachBaseContext(TinkerApplication app, Context base);

    void onCreate(TinkerApplication app);

    void onConfigurationChanged(Configuration newConfig);

    void onTrimMemory(int level);

    void onLowMemory();

    void onTerminate();

    ClassLoader getClassLoader(ClassLoader cl);

    Context getBaseContext(Context base);

    AssetManager getAssets(AssetManager assets);

    Resources getResources(Resources res);

    Object getSystemService(String name, Object service);
}
