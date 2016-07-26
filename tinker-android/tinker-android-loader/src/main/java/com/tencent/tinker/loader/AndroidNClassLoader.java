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

package com.tencent.tinker.loader;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by shwenzhang on 16/7/24.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class AndroidNClassLoader extends BaseDexClassLoader {
    PathClassLoader originClassLoader;

    private AndroidNClassLoader(String dexPath, File optimizedDirectory, String libraryPath, PathClassLoader parent) {
        super(dexPath, optimizedDirectory, libraryPath, parent.getParent());
        originClassLoader = parent;
    }

    public Class<?> findClass(String name) throws ClassNotFoundException {
        Log.e("test", "AndroidNClassLoader find class:" + name);
        return super.findClass(name);
    }

    public static AndroidNClassLoader createAndroidNClassLoader(Application application,
        String nativeLibraryPath, PathClassLoader original) {
        ArrayList<String> apkDex = new ArrayList<>();
        apkDex.add(application.getApplicationInfo().sourceDir);
        String pathBuilder = createDexPath(apkDex);
        return new AndroidNClassLoader(pathBuilder, new File(application.getApplicationInfo().dataDir),
            nativeLibraryPath, original);
    }

    private static String createDexPath(List<String> dexes) {
        StringBuilder pathBuilder = new StringBuilder();
        boolean first = true;
        for (String dex : dexes) {
            if (first) {
                first = false;
            } else {
                pathBuilder.append(":");
            }
            pathBuilder.append(dex);
        }

        return pathBuilder.toString();
    }

    private static Field findField(Object instance, String name) throws NoSuchFieldException {
        for (Class<?> clazz = instance.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
            try {
                Field field = clazz.getDeclaredField(name);

                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }

                return field;
            } catch (NoSuchFieldException e) {
                // ignore and search next
            }
        }

        throw new NoSuchFieldException("Field " + name + " not found in " + instance.getClass());
    }

    public static void reflectPackageInfoClassloader(Application application, ClassLoader classLoader) throws NoSuchFieldException, IllegalAccessException {
        String defBase = "mBase";
        String defPackageInfo = "mPackageInfo";
        String defClassLoader = "mClassLoader";

        Context baseContext = (Context) findField(application, defBase).get(application);

        Object basePackageInfo = findField(baseContext, defPackageInfo).get(baseContext);
        Field classLoaderField = findField(basePackageInfo, defClassLoader);
        Thread.currentThread().setContextClassLoader(classLoader);
        classLoaderField.set(basePackageInfo, classLoader);
    }
}
