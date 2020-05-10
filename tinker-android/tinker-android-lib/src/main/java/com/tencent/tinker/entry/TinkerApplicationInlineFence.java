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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import com.tencent.tinker.anno.Keep;

import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_GET_ASSETS;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_GET_BASE_CONTEXT;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_GET_CLASSLOADER;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_GET_RESOURCES;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_GET_SYSTEM_SERVICE;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_ON_BASE_CONTEXT_ATTACHED;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_ON_CONFIGURATION_CHANGED;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_ON_CREATE;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_ON_LOW_MEMORY;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_ON_TERMINATE;
import static com.tencent.tinker.loader.app.TinkerInlineFenceAction.ACTION_ON_TRIM_MEMORY;

/**
 * Created by tangyinsheng on 2019-11-05.
 */
@Keep
public final class TinkerApplicationInlineFence extends Handler {
    private final ApplicationLike mAppLike;

    public TinkerApplicationInlineFence(ApplicationLike appLike) {
        mAppLike = appLike;
    }

    @Override
    public void handleMessage(Message msg) {
        handleMessage_$noinline$(msg);
    }

    private void handleMessage_$noinline$(Message msg) {
        try {
            dummyThrowExceptionMethod();
        } finally {
            handleMessageImpl(msg);
        }
    }

    private void handleMessageImpl(Message msg) {
        switch (msg.what) {
            case ACTION_ON_BASE_CONTEXT_ATTACHED: {
                mAppLike.onBaseContextAttached((Context) msg.obj);
                break;
            }
            case ACTION_ON_CREATE: {
                mAppLike.onCreate();
                break;
            }
            case ACTION_ON_CONFIGURATION_CHANGED: {
                mAppLike.onConfigurationChanged((Configuration) msg.obj);
                break;
            }
            case ACTION_ON_TRIM_MEMORY: {
                mAppLike.onTrimMemory((Integer) msg.obj);
                break;
            }
            case ACTION_ON_LOW_MEMORY: {
                mAppLike.onLowMemory();
                break;
            }
            case ACTION_ON_TERMINATE: {
                mAppLike.onTerminate();
                break;
            }
            case ACTION_GET_CLASSLOADER: {
                msg.obj = mAppLike.getClassLoader((ClassLoader) msg.obj);
                break;
            }
            case ACTION_GET_BASE_CONTEXT: {
                msg.obj = mAppLike.getBaseContext((Context) msg.obj);
                break;
            }
            case ACTION_GET_ASSETS: {
                msg.obj = mAppLike.getAssets((AssetManager) msg.obj);
                break;
            }
            case ACTION_GET_RESOURCES : {
                msg.obj = mAppLike.getResources((Resources) msg.obj);
                break;
            }
            case ACTION_GET_SYSTEM_SERVICE : {
                final Object[] params = (Object[]) msg.obj;
                msg.obj = mAppLike.getSystemService((String) params[0], params[1]);
                break;
            }
            default: {
                throw new IllegalStateException("Should not be here.");
            }
        }
    }

    private static void dummyThrowExceptionMethod() {
        if (TinkerApplicationInlineFence.class.isPrimitive()) {
            throw new RuntimeException();
        }
    }
}
