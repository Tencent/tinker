/*
 * Copyright (C) 2016 THL A29 Limited, a Tencent company.
 * Copyright (C) 2013 The Android Open Source Project
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

package com.tencent.tinker.loader;

import android.app.Application;
import android.os.Build;

import com.tencent.tinker.loader.shareutil.ShareConstants;

import dalvik.system.BaseDexClassLoader;

/**
 * Created by zhangshaowen on 16/3/18.
 */
public class SystemClassLoaderAdder {
    public static final String CHECK_DEX_CLASS = "com.tencent.tinker.loader.TinkerTestDexLoad";

    public static ClassLoader injectNewClassLoaderOnDemand(Application application, BaseDexClassLoader loader, int gpExpansionMode) throws Throwable {
        if (Build.VERSION.SDK_INT >= 24
                && gpExpansionMode == ShareConstants.TINKER_GPMODE_REPLACE_CLASSLOADER_AND_CALL_INITIALIZER) {
            return AndroidNClassLoader.inject(loader, application, gpExpansionMode);
        } else {
            return loader;
        }
    }
}
