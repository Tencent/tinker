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

package com.tencent.tinker.loader;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import dalvik.system.DelegateLastClassLoader;

/**
 * Created by tangyinsheng on 2019-10-31.
 */
final class NewClassLoaderInjector {
    public static ClassLoader inject(Application app, ClassLoader oldClassLoader, File dexOptDir,
                                     boolean useDLC, List<File> patchedDexes) throws Throwable {
        final String[] patchedDexPaths = new String[patchedDexes.size()];
        for (int i = 0; i < patchedDexPaths.length; ++i) {
            patchedDexPaths[i] = patchedDexes.get(i).getAbsolutePath();
        }
        final ClassLoader newClassLoader = createNewClassLoader(oldClassLoader,
              dexOptDir, useDLC, true, patchedDexPaths);
        doInject(app, newClassLoader);
        return newClassLoader;
    }

    public static ClassLoader triggerDex2Oat(Context context, File dexOptDir, boolean useDLC,
                                      String... dexPaths) throws Throwable {
        return createNewClassLoader(context.getClassLoader(), dexOptDir, useDLC, false, dexPaths);
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("unchecked")
    private static ClassLoader createNewClassLoader(ClassLoader oldClassLoader,
                                                    File dexOptDir,
                                                    boolean useDLC,
                                                    boolean forActualLoading,
                                                    String... patchDexPaths) throws Throwable {
        final Field pathListField = findField(
                Class.forName("dalvik.system.BaseDexClassLoader", false, oldClassLoader),
                "pathList");
        final Object oldPathList = pathListField.get(oldClassLoader);

        final StringBuilder dexPathBuilder = new StringBuilder();
        final boolean hasPatchDexPaths = patchDexPaths != null && patchDexPaths.length > 0;
        if (hasPatchDexPaths) {
            for (int i = 0; i < patchDexPaths.length; ++i) {
                if (i > 0) {
                    dexPathBuilder.append(File.pathSeparator);
                }
                dexPathBuilder.append(patchDexPaths[i]);
            }
        }

        final String combinedDexPath = dexPathBuilder.toString();


        final Field nativeLibraryDirectoriesField = findField(oldPathList.getClass(), "nativeLibraryDirectories");
        List<File> oldNativeLibraryDirectories = null;
        if (nativeLibraryDirectoriesField.getType().isArray()) {
            oldNativeLibraryDirectories = Arrays.asList((File[]) nativeLibraryDirectoriesField.get(oldPathList));
        } else {
            oldNativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(oldPathList);
        }
        final StringBuilder libraryPathBuilder = new StringBuilder();
        boolean isFirstItem = true;
        for (File libDir : oldNativeLibraryDirectories) {
            if (libDir == null) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                libraryPathBuilder.append(File.pathSeparator);
            }
            libraryPathBuilder.append(libDir.getAbsolutePath());
        }

        final String combinedLibraryPath = libraryPathBuilder.toString();

        ClassLoader result = null;
        if (useDLC && ShareTinkerInternals.isNewerOrEqualThanVersion(27, true)) {
            if (ShareTinkerInternals.isNewerOrEqualThanVersion(31, true)) {
                result = new DelegateLastClassLoader(combinedDexPath, combinedLibraryPath, oldClassLoader);
            } else {
                result = new DelegateLastClassLoader(combinedDexPath, combinedLibraryPath, ClassLoader.getSystemClassLoader());
                final Field parentField = ClassLoader.class.getDeclaredField("parent");
                parentField.setAccessible(true);
                parentField.set(result, oldClassLoader);
            }
        } else {
            result = new TinkerClassLoader(combinedDexPath, dexOptDir, combinedLibraryPath, oldClassLoader);
        }

        // 'EnsureSameClassLoader' mechanism which is first introduced in Android O
        // may cause exception if we replace definingContext of old classloader.
        if (forActualLoading && !ShareTinkerInternals.isNewerOrEqualThanVersion(26, true)) {
            findField(oldPathList.getClass(), "definingContext").set(oldPathList, result);
        }

        return result;
    }

    private static void doInject(Application app, ClassLoader classLoader) throws Throwable {
        Thread.currentThread().setContextClassLoader(classLoader);

        final Context baseContext = (Context) findField(app.getClass(), "mBase").get(app);
        try {
            findField(baseContext.getClass(), "mClassLoader").set(baseContext, classLoader);
        } catch (Throwable ignored) {
            // There's no mClassLoader field in ContextImpl before Android O.
            // However we should try our best to replace this field in case some
            // customized system has one.
        }

        final Object basePackageInfo = findField(baseContext.getClass(), "mPackageInfo").get(baseContext);
        findField(basePackageInfo.getClass(), "mClassLoader").set(basePackageInfo, classLoader);

        if (Build.VERSION.SDK_INT < 27) {
            final Resources res = app.getResources();
            try {
                findField(res.getClass(), "mClassLoader").set(res, classLoader);

                final Object drawableInflater = findField(res.getClass(), "mDrawableInflater").get(res);
                if (drawableInflater != null) {
                    findField(drawableInflater.getClass(), "mClassLoader").set(drawableInflater, classLoader);
                }
            } catch (Throwable ignored) {
                // Ignored.
            }
        }
    }

    private static Field findField(Class<?> clazz, String name) throws Throwable {
        Class<?> currClazz = clazz;
        while (true) {
            try {
                final Field result = currClazz.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (Throwable ignored) {
                if (currClazz == Object.class) {
                    throw new NoSuchFieldException("Cannot find field "
                            + name + " in class " + clazz.getName() + " and its super classes.");
                } else {
                    currClazz = currClazz.getSuperclass();
                }
            }
        }
    }

    private NewClassLoaderInjector() {
        throw new UnsupportedOperationException();
    }
}