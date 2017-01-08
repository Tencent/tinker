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

package com.tencent.tinker.loader.shareutil;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.IBinder;
import android.view.ContextThemeWrapper;

import java.lang.reflect.Field;

/**
 * Created by tangyinsheng on 2017/1/6.
 */

public class ActivityResFixInstrumentation extends InstrumentationProxy {
    private static Field mResourcesField = null;

    private final Context mContext;

    public ActivityResFixInstrumentation(Context context, Instrumentation base) throws NoSuchFieldException {
        super(base);
        this.mContext = context;
        mResourcesField = ShareReflectUtil.findField(ContextThemeWrapper.class, "mResources");
    }

    @Override
    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        final Activity result = super.newActivity(cl, className, intent);
        replaceResources(result);
        return result;
    }

    @Override
    public Activity newActivity(Class<?> clazz, Context context, IBinder token, Application application, Intent intent, ActivityInfo info, CharSequence title, Activity parent, String id, Object lastNonConfigurationInstance) throws InstantiationException, IllegalAccessException {
        final Activity result = super.newActivity(clazz, context, token, application, intent, info, title, parent, id, lastNonConfigurationInstance);
        replaceResources(result);
        return result;
    }

    private void replaceResources(ContextThemeWrapper component) {
        if (component == null) {
            return;
        }
        try {
            mResourcesField.set(component, mContext.getApplicationContext().getResources());
        } catch (IllegalAccessException e) {
            new IllegalStateException("cannot access 'mResources' field of component " + component);
        }
    }
}
