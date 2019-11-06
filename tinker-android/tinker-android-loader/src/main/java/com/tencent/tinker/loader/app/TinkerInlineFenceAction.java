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
import android.os.Handler;
import android.os.Message;

/**
 * Created by tangyinsheng on 2019-11-05.
 */
public final class TinkerInlineFenceAction {
    public static final int ACTION_ON_BASE_CONTEXT_ATTACHED = 1;
    public static final int ACTION_ON_CREATE = 2;
    public static final int ACTION_ON_CONFIGURATION_CHANGED = 3;
    public static final int ACTION_ON_TRIM_MEMORY = 4;
    public static final int ACTION_ON_LOW_MEMORY = 5;
    public static final int ACTION_ON_TERMINATE = 6;
    public static final int ACTION_GET_CLASSLOADER = 7;
    public static final int ACTION_GET_BASE_CONTEXT = 8;
    public static final int ACTION_GET_ASSETS = 9;
    public static final int ACTION_GET_RESOURCES = 10;
    public static final int ACTION_GET_SYSTEM_SERVICE = 11;

    static void callOnBaseContextAttached(Handler inlineFence, Context context) {
        final Message msg = Message.obtain(inlineFence, ACTION_ON_BASE_CONTEXT_ATTACHED, context);
        inlineFence.handleMessage(msg);
    }

    static void callOnCreate(Handler inlineFence) {
        final Message msg = Message.obtain(inlineFence, ACTION_ON_CREATE);
        inlineFence.handleMessage(msg);
    }

    static void callOnConfigurationChanged(Handler inlineFence, Configuration newConfig) {
        final Message msg = Message.obtain(inlineFence, ACTION_ON_CONFIGURATION_CHANGED, newConfig);
        inlineFence.handleMessage(msg);
    }

    static void callOnTrimMemory(Handler inlineFence, int level) {
        final Message msg = Message.obtain(inlineFence, ACTION_ON_TRIM_MEMORY, level);
        inlineFence.handleMessage(msg);
    }

    static void callOnLowMemory(Handler inlineFence) {
        final Message msg = Message.obtain(inlineFence, ACTION_ON_LOW_MEMORY);
        inlineFence.handleMessage(msg);
    }

    static void callOnTerminate(Handler inlineFence) {
        final Message msg = Message.obtain(inlineFence, ACTION_ON_TERMINATE);
        inlineFence.handleMessage(msg);
    }

    static ClassLoader callGetClassLoader(Handler inlineFence, ClassLoader cl) {
        final Message msg = Message.obtain(inlineFence, ACTION_GET_CLASSLOADER, cl);
        inlineFence.handleMessage(msg);
        return (ClassLoader) msg.obj;
    }

    static Context callGetBaseContext(Handler inlineFence, Context base) {
        final Message msg = Message.obtain(inlineFence, ACTION_GET_BASE_CONTEXT, base);
        inlineFence.handleMessage(msg);
        return (Context) msg.obj;
    }

    static AssetManager callGetAssets(Handler inlineFence, AssetManager assets) {
        final Message msg = Message.obtain(inlineFence, ACTION_GET_ASSETS, assets);
        inlineFence.handleMessage(msg);
        return (AssetManager) msg.obj;
    }

    static Resources callGetResources(Handler inlineFence, Resources res) {
        final Message msg = Message.obtain(inlineFence, ACTION_GET_RESOURCES, res);
        inlineFence.handleMessage(msg);
        return (Resources) msg.obj;
    }

    static Object callGetSystemService(Handler inlineFence, String name, Object service) {
        final Message msg = Message.obtain(inlineFence, ACTION_GET_SYSTEM_SERVICE, new Object[] {name, service});
        inlineFence.handleMessage(msg);
        return msg.obj;
    }
}
