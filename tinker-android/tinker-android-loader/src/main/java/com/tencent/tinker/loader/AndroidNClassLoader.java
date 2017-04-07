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
import android.os.Build;
import android.text.TextUtils;

import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by zhangshaowen on 16/7/24.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class AndroidNClassLoader extends PathClassLoader {
    private static final String CHECK_CLASSLOADER_CLASS = "com.tencent.tinker.loader.TinkerTestAndroidNClassLoader";

    private static ArrayList<DexFile> oldDexFiles = new ArrayList<>();
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

    private static AndroidNClassLoader createAndroidNClassLoader(PathClassLoader original, Application application) throws Exception {
        //let all element ""
        AndroidNClassLoader androidNClassLoader = new AndroidNClassLoader("",  original, application);
        Field originPathList = ShareReflectUtil.findField(original, "pathList");
        Object originPathListObject = originPathList.get(original);
        //should reflect definingContext also
        Field originClassloader = ShareReflectUtil.findField(originPathListObject, "definingContext");
        originClassloader.set(originPathListObject, androidNClassLoader);
        //copy pathList
        Field pathListField = ShareReflectUtil.findField(androidNClassLoader, "pathList");
        //just use PathClassloader's pathList
        pathListField.set(androidNClassLoader, originPathListObject);

        //we must recreate dexFile due to dexCache
        List<File> additionalClassPathEntries = new ArrayList<>();
        Field dexElement = ShareReflectUtil.findField(originPathListObject, "dexElements");
        Object[] originDexElements = (Object[]) dexElement.get(originPathListObject);
        for (Object element : originDexElements) {
            DexFile dexFile = (DexFile) ShareReflectUtil.findField(element, "dexFile").get(element);
            if (dexFile == null) {
                continue;
            }
            additionalClassPathEntries.add(new File(dexFile.getName()));
            //protect for java.lang.AssertionError: Failed to close dex file in finalizer.
            oldDexFiles.add(dexFile);
        }
        Method makePathElements = ShareReflectUtil.findMethod(originPathListObject, "makePathElements", List.class, File.class,
            List.class);
        ArrayList<IOException> suppressedExceptions = new ArrayList<>();
        Object[] newDexElements = (Object[]) makePathElements.invoke(originPathListObject, additionalClassPathEntries, null, suppressedExceptions);
        dexElement.set(originPathListObject, newDexElements);

        try {
            Class.forName(CHECK_CLASSLOADER_CLASS, true, androidNClassLoader);
        } catch (Throwable thr) {
            fixDexElementsForProtectedApp(application, newDexElements);
        }

        return androidNClassLoader;
    }

    private static void reflectPackageInfoClassloader(Application application, ClassLoader reflectClassLoader) throws Exception {
        String defBase = "mBase";
        String defPackageInfo = "mPackageInfo";
        String defClassLoader = "mClassLoader";

        Context baseContext = (Context) ShareReflectUtil.findField(application, defBase).get(application);
        Object basePackageInfo = ShareReflectUtil.findField(baseContext, defPackageInfo).get(baseContext);
        Field classLoaderField = ShareReflectUtil.findField(basePackageInfo, defClassLoader);
        Thread.currentThread().setContextClassLoader(reflectClassLoader);
        classLoaderField.set(basePackageInfo, reflectClassLoader);
    }

    public static AndroidNClassLoader inject(PathClassLoader originClassLoader, Application application) throws Exception {
        AndroidNClassLoader classLoader = createAndroidNClassLoader(originClassLoader, application);
        reflectPackageInfoClassloader(application, classLoader);
        return classLoader;
    }

    // Basically this method would use base.apk to create a dummy DexFile object,
    // then set its fileName, cookie, internalCookie field to the value
    // comes from original DexFile object so that the encrypted dex would be taking effect.
    private static void fixDexElementsForProtectedApp(Application application, Object[] newDexElements) throws Exception {
        Field zipField = null;
        Field dexFileField = null;
        final Field mFileNameField = ShareReflectUtil.findField(DexFile.class, "mFileName");
        final Field mCookieField = ShareReflectUtil.findField(DexFile.class, "mCookie");
        final Field mInternalCookieField = ShareReflectUtil.findField(DexFile.class, "mInternalCookie");

        // Always ignore the last element since it should always be the base.apk.
        for (int i = 0; i < newDexElements.length - 1; ++i) {
            final Object newElement = newDexElements[i];

            if (zipField == null && dexFileField == null) {
                zipField = ShareReflectUtil.findField(newElement, "zip");
                dexFileField = ShareReflectUtil.findField(newElement, "dexFile");
            }

            final DexFile origDexFile = oldDexFiles.get(i);
            final String origFileName = (String) mFileNameField.get(origDexFile);
            final Object origCookie = mCookieField.get(origDexFile);
            final Object origInternalCookie = mInternalCookieField.get(origDexFile);

            final DexFile dupOrigDexFile = DexFile.loadDex(application.getApplicationInfo().sourceDir, null, 0);
            mFileNameField.set(dupOrigDexFile, origFileName);
            mCookieField.set(dupOrigDexFile, origCookie);
            mInternalCookieField.set(dupOrigDexFile, origInternalCookie);

            dexFileField.set(newElement, dupOrigDexFile);

            // Just for better looking when dump new classloader.
            // Avoid such output like this: DexPathList{zip file: /xx/yy/zz/uu.odex}
            final File newZip = (File) zipField.get(newElement);
            final String newZipPath = (newZip != null ? newZip.getAbsolutePath() : null);
            if (newZipPath != null && !newZipPath.endsWith(".zip") && !newZipPath.endsWith(".jar") && !newZipPath.endsWith(".apk")) {
                zipField.set(newElement, null);
            }
        }
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
                && !name.equals(SystemClassLoaderAdder.CHECK_DEX_CLASS)
                && !name.equals(CHECK_CLASSLOADER_CLASS))
                || (applicationClassName != null && TextUtils.equals(applicationClassName, name))) {
            return originClassLoader.loadClass(name);
        }
        return super.findClass(name);
    }

    @Override
    public String findLibrary(String name) {
        return super.findLibrary(name);
    }
}
