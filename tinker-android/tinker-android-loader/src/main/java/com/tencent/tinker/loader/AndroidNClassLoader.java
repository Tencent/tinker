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

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by zhangshaowen on 16/7/24.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class AndroidNClassLoader extends PathClassLoader {
    private static final String TAG = "Tinker.NClassLoader";

    private static Object oldDexPathListHolder = null;

    private final PathClassLoader originClassLoader;
    private String applicationClassName;

    private AndroidNClassLoader(String dexPath, PathClassLoader parent, Application application) {
        super(dexPath, parent.getParent());
        originClassLoader = parent;
        String name = application.getClass().getName();
        if (name != null && !name.equals("android.app.Application")) {
            applicationClassName = name;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object recreateDexPathList(Object originalDexPathList, ClassLoader newDefiningContext, boolean createEmptyOne) throws Exception {
        final Constructor<?> dexPathListConstructor = ShareReflectUtil.findConstructor(originalDexPathList, ClassLoader.class, String.class, String.class, File.class);
        if (createEmptyOne) {
            return dexPathListConstructor.newInstance(newDefiningContext, "", null, null);
        }
        final Field dexElementsField = ShareReflectUtil.findField(originalDexPathList, "dexElements");
        final Object[] dexElements = (Object[]) dexElementsField.get(originalDexPathList);
        final Field nativeLibraryDirectoriesField = ShareReflectUtil.findField(originalDexPathList, "nativeLibraryDirectories");
        final List<File> nativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(originalDexPathList);

        final StringBuilder dexPathBuilder = new StringBuilder();
        final Field dexFileField = ShareReflectUtil.findField(dexElements.getClass().getComponentType(), "dexFile");

        boolean isFirstItem = true;
        for (Object dexElement : dexElements) {
            final DexFile dexFile = (DexFile) dexFileField.get(dexElement);
            if (dexFile == null) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                dexPathBuilder.append(File.pathSeparator);
            }
            dexPathBuilder.append(dexFile.getName());
        }

        final String dexPath = dexPathBuilder.toString();

        final StringBuilder libraryPathBuilder = new StringBuilder();
        isFirstItem = true;
        for (File libDir : nativeLibraryDirectories) {
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

        final String libraryPath = libraryPathBuilder.toString();
        return dexPathListConstructor.newInstance(newDefiningContext, dexPath, libraryPath, null);
    }

    private static AndroidNClassLoader createAndroidNClassLoader(PathClassLoader originalClassLoader, Application application) throws Exception {
        //let all element ""
        final AndroidNClassLoader androidNClassLoader = new AndroidNClassLoader("",  originalClassLoader, application);
        final Field pathListField = ShareReflectUtil.findField(originalClassLoader, "pathList");
        final Object originPathList = pathListField.get(originalClassLoader);

        // To avoid 'dex file register with multiple classloader' exception on Android O, we must keep old
        // dexPathList in original classloader so that after the newly loaded base dex was bound to
        // AndroidNClassLoader we can still load class in base dex from original classloader.
        Object newPathList = recreateDexPathList(originPathList, androidNClassLoader, false);

        // Update new classloader's pathList.
        pathListField.set(androidNClassLoader, newPathList);

        // Recreate old dexPathList.
        oldDexPathListHolder = originPathList;
        Object emptyOldPathList = recreateDexPathList(originPathList, originalClassLoader, true);
        pathListField.set(originalClassLoader, emptyOldPathList);
        Object recreatedOldPathList = recreateDexPathList(originPathList, originalClassLoader, false);
        pathListField.set(originalClassLoader, recreatedOldPathList);

        return androidNClassLoader;
    }

    private static void reflectPackageInfoClassloader(Application application, ClassLoader reflectClassLoader) throws Exception {
        Context baseContext = (Context) ShareReflectUtil.findField(application, "mBase").get(application);
        Object basePackageInfo = ShareReflectUtil.findField(baseContext, "mPackageInfo").get(baseContext);
        ShareReflectUtil.findField(basePackageInfo, "mClassLoader").set(basePackageInfo, reflectClassLoader);

        // There's compatibility risk here when omit these hacking logic.
        // However I still have no idea about how to solve it without touching the Android P's
        // dark greylist API.
        if (Build.VERSION.SDK_INT < 27) {
            Resources res = application.getResources();
            ShareReflectUtil.findField(res, "mClassLoader").set(res, reflectClassLoader);

            Object drawableInflater = ShareReflectUtil.findField(res, "mDrawableInflater").get(res);
            if (drawableInflater != null) {
                ShareReflectUtil.findField(drawableInflater, "mClassLoader").set(drawableInflater, reflectClassLoader);
            }
        }

        Thread.currentThread().setContextClassLoader(reflectClassLoader);
    }

    public static AndroidNClassLoader inject(PathClassLoader originClassLoader, Application application) throws Exception {
        AndroidNClassLoader classLoader = createAndroidNClassLoader(originClassLoader, application);
        reflectPackageInfoClassloader(application, classLoader);
        return classLoader;
    }

//    public static String getLdLibraryPath(ClassLoader loader) throws Exception {
//        String nativeLibraryPath;
//
//        nativeLibraryPath = (String) loader.getClass()
//            .getMethod("getLdLibraryPath", new Class[0])
//            .invoke(loader, new Object[0]);
//
//        return nativeLibraryPath;
//    }

    public Class<?> findClass(String name) throws ClassNotFoundException {
        // loader class use default pathClassloader to load
        if ((name != null
                && name.startsWith("com.tencent.tinker.loader.")
                && !name.equals(SystemClassLoaderAdder.CHECK_DEX_CLASS))
                || (applicationClassName != null && applicationClassName.equals(name))) {
            return originClassLoader.loadClass(name);
        }
        return super.findClass(name);
    }

    @Override
    public String findLibrary(String name) {
        return super.findLibrary(name);
    }
}
