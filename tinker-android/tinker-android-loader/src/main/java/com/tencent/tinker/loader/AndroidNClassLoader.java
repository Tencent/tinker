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

import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by zhangshaowen on 16/7/24.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class AndroidNClassLoader extends PathClassLoader {
    static ArrayList<DexFile> oldDexFiles = new ArrayList<>();
    PathClassLoader originClassLoader;

    private AndroidNClassLoader(String dexPath, PathClassLoader parent) {
        super(dexPath, parent.getParent());
        originClassLoader = parent;
    }

    private static AndroidNClassLoader createAndroidNClassLoader(PathClassLoader original) throws Exception {
        //let all element ""
        AndroidNClassLoader androidNClassLoader = new AndroidNClassLoader("",  original);
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
            additionalClassPathEntries.add(new File(dexFile.getName()));
            //protect for java.lang.AssertionError: Failed to close dex file in finalizer.
            oldDexFiles.add(dexFile);
        }
        Method makePathElements = ShareReflectUtil.findMethod(originPathListObject, "makePathElements", List.class, File.class,
            List.class);
        ArrayList<IOException> suppressedExceptions = new ArrayList<>();
        Object[] newDexElements = (Object[]) makePathElements.invoke(originPathListObject, additionalClassPathEntries, null, suppressedExceptions);
        dexElement.set(originPathListObject, newDexElements);
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
        AndroidNClassLoader classLoader = createAndroidNClassLoader(originClassLoader);
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
        return super.findClass(name);
    }

    @Override
    public String findLibrary(String name) {
        return super.findLibrary(name);
    }
}
