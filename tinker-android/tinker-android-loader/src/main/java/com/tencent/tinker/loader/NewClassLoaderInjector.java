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

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import com.tencent.tinker.loader.app.TinkerApplication;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by tangyinsheng on 2019-10-31.
 */
final class NewClassLoaderInjector {
    private static final class DispatchClassLoader extends ClassLoader {
        private final String mApplicationClassName;
        private final ClassLoader mOldClassLoader;
        private final ClassLoader mOldParentClassLoader;
        private final boolean mIsOldParentABootClassLoader;

        private ClassLoader mNewClassLoader;

        private final ThreadLocal<Boolean> mCallFindClassOfLeafDirectly = new ThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() {
                return false;
            }
        };

        DispatchClassLoader(String applicationClassName, ClassLoader oldClassLoader) {
            super(ClassLoader.getSystemClassLoader());
            mApplicationClassName = applicationClassName;
            mOldClassLoader = oldClassLoader;
            mOldParentClassLoader = oldClassLoader.getParent();
            mIsOldParentABootClassLoader = (mOldParentClassLoader == ClassLoader.getSystemClassLoader());
        }

        void setNewClassLoader(ClassLoader classLoader) {
            mNewClassLoader = classLoader;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (mCallFindClassOfLeafDirectly.get()) {
                return null;
            }

            if (name.equals(mApplicationClassName)) {
                return findClass(mOldClassLoader, name);
            }
            if (name.startsWith("com.tencent.tinker.loader.")
                    && !name.equals(SystemClassLoaderAdder.CHECK_DEX_CLASS)) {
                return findClass(mOldClassLoader, name);
            }
            if (name.startsWith("org.apache.commons.codec.")
                    || name.startsWith("org.apache.commons.logging.")
                    || name.startsWith("org.apache.http.")) {
                // Here's the whole story:
                //   Some app use apache wrapper library to access Apache utilities. Classes in apache wrapper
                //   library may be conflict with those preloaded in BootClassLoader.
                //   So with the build option:
                //       useLibrary 'org.apache.http.legacy'
                //   appears, the Android Framework will inject a jar called 'org.apache.http.legacy.boot.jar'
                //   in front of the path of user's apk. After that, PathList in app's PathClassLoader should
                //   look like this:
                //       ["/system/framework/org.apache.http.legacy.boot.jar", "path-to-user-apk", "path-to-other-preload-jar"]
                //   When app runs to the code refer to Apache classes, the referred classes in the first
                //   jar override those in user's app, which avoids any conflicts and crashes.
                //
                //   When it comes to Tinker, to block the cached instances in class table of app's
                //   PathClassLoader we create a new PathClassLoader to replace the original one.
                //   At the beginning it's fine to imitate system's behavior and construct the DexPathList in new PathClassLoader
                //   like below:
                //       ["/system/framework/org.apache.http.legacy.boot.jar", "path-to-new-dexes", "path-to-other-preload-jar"]
                //   However, the ART VM of Android P adds a new feature that checks whether the inlined class is loaded by the same
                //   ClassLoader that loads the callsite's class. If any Apache classes is inlined in old dex(oat), after we replacing
                //   the App's ClassLoader we will receive an assert since the Apache classes is loaded by another ClassLoader now.
                return findClass(mOldClassLoader, name);
            }

            try {
                return findClass(mNewClassLoader, name);
            } catch (ClassNotFoundException ignored) {
                // Ignored.
            }

            if (!mIsOldParentABootClassLoader) {
                try {
                    return mOldParentClassLoader.loadClass(name);
                } catch (ClassNotFoundException ignored) {
                    // Ignored.
                }
            }

            return findClass(mOldClassLoader, name);
        }

        private Class<?> findClass(ClassLoader classLoader, String name) throws ClassNotFoundException {
            try {
                mCallFindClassOfLeafDirectly.set(true);
                return classLoader.loadClass(name);
            } finally {
                mCallFindClassOfLeafDirectly.set(false);
            }
        }
    }

    public static ClassLoader inject(Application app, ClassLoader oldClassLoader) throws Throwable {
        final DispatchClassLoader dispatchClassLoader
                = new DispatchClassLoader(app.getClass().getName(), oldClassLoader);
        final ClassLoader newClassLoader
                = createNewClassLoader(app, oldClassLoader, dispatchClassLoader);
        dispatchClassLoader.setNewClassLoader(newClassLoader);
        doInject(app, newClassLoader);
        return newClassLoader;
    }

    public static void triggerDex2Oat(Context context, String dexPath) throws Throwable {
        // Suggestion from Huawei: Only PathClassLoader (Perhaps other ClassLoaders known by system
        // like DexClassLoader also works ?) can be used here to trigger dex2oat so that JIT
        // mechanism can participate in runtime Dex optimization.
        final ClassLoader appClassLoader = TinkerApplication.class.getClassLoader();
        final ClassLoader triggerClassLoader = createNewClassLoader(context, appClassLoader, null, dexPath);
    }

    @SuppressWarnings("unchecked")
    private static ClassLoader createNewClassLoader(Context context, ClassLoader oldClassLoader,
                                                    ClassLoader dispatchClassLoader, String... patchDexPaths) throws Throwable {
        final Field pathListField = findField(BaseDexClassLoader.class, "pathList");
        final Object oldPathList = pathListField.get(oldClassLoader);

        final Field dexElementsField = findField(oldPathList.getClass(), "dexElements");
        final Object[] oldDexElements = (Object[]) dexElementsField.get(oldPathList);

        final Field nativeLibraryDirectoriesField = findField(oldPathList.getClass(), "nativeLibraryDirectories");
        final List<File> oldNativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(oldPathList);

        final Field dexFileField = findField(oldDexElements.getClass().getComponentType(), "dexFile");

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

        final String packageName = context.getPackageName();
        boolean isFirstItem = dexPathBuilder.length() == 0;
        for (Object oldDexElement : oldDexElements) {
            String dexPath = null;
            final DexFile dexFile = (DexFile) dexFileField.get(oldDexElement);
            if (dexFile != null) {
                dexPath = dexFile.getName();
            }
            if (dexPath == null || dexPath.isEmpty()) {
                continue;
            }
            if (!dexPath.contains("/" + packageName)) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                dexPathBuilder.append(File.pathSeparator);
            }
            dexPathBuilder.append(dexPath);
        }

        final String combinedDexPath = dexPathBuilder.toString();

        final StringBuilder libraryPathBuilder = new StringBuilder();
        isFirstItem = true;
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

        final ClassLoader result = new PathClassLoader(combinedDexPath, combinedLibraryPath, oldClassLoader.getParent());

        if (!hasPatchDexPaths) {
            // findField(oldPathList.getClass(), "definingContext").set(oldPathList, result);
            final Field parentField = findField(ClassLoader.class, "parent");
            parentField.set(result, dispatchClassLoader);
            parentField.set(oldClassLoader, dispatchClassLoader);
        }

        return result;
    }

    private static void doInject(Application app, ClassLoader classLoader) throws Throwable {
        Thread.currentThread().setContextClassLoader(classLoader);

        final Context baseContext = (Context) findField(app.getClass(), "mBase").get(app);
        final Object basePackageInfo = findField(baseContext.getClass(), "mPackageInfo").get(baseContext);
        findField(basePackageInfo.getClass(), "mClassLoader").set(basePackageInfo, classLoader);

        if (Build.VERSION.SDK_INT < 27) {
            final Resources res = app.getResources();
            findField(res.getClass(), "mClassLoader").set(res, classLoader);

            final Object drawableInflater = findField(res.getClass(), "mDrawableInflater").get(res);
            if (drawableInflater != null) {
                findField(drawableInflater.getClass(), "mClassLoader").set(drawableInflater, classLoader);
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