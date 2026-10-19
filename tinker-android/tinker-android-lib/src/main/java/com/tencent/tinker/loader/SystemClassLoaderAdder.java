/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by zhangshaowen on 16/3/18.
 */
public class SystemClassLoaderAdder {
    public static final String CHECK_DEX_CLASS = "com.tencent.tinker.loader.TinkerTestDexLoad";
    public static final String CHECK_DEX_FIELD = "isPatch";
    private static final String TAG = "Tinker.ClassLoaderAdder";
    private static int sPatchDexCount = 0;

    @SuppressWarnings("unchecked")
    public static void installDexes(Application application, ClassLoader loader, File dexOptDir, List<File> files)
        throws Throwable {
        Log.i(TAG, "installDexes dexOptDir: " + dexOptDir.getAbsolutePath() + ", dex size:" + files.size());

        if (!files.isEmpty()) {
            files = createSortedAdditionalPathEntries(files);
            ClassLoader classLoader = loader;
            if (Build.VERSION.SDK_INT >= 24 && !checkIsProtectedApp(files)) {
                classLoader = NewClassLoaderInjector.inject(application, loader, dexOptDir, files);
            } else {
                if (Build.VERSION.SDK_INT >= 23) {
                    V23.install(classLoader, files, dexOptDir);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    V19.install(classLoader, files, dexOptDir);
                } else if (Build.VERSION.SDK_INT >= 14) {
                    V14.install(classLoader, files, dexOptDir);
                } else {
                    V4.install(classLoader, files, dexOptDir);
                }
            }
            if (classLoader != null && checkDexInstall(classLoader)) {
                if (Build.VERSION.SDK_INT >= 28) {
                    suppressClassTableP(classLoader);
                } else {
                    suppressClassTable(classLoader);
                }
                sPatchDexCount = files.size();
                Log.i(TAG, "after loaded classloader: " + classLoader + ", dex size:" + sPatchDexCount);
            } else {
                throw new TinkerRuntimeException(ShareConstants.CHECK_DEX_INSTALL_FAIL);
            }
        }
    }

    private static void suppressClassTable(ClassLoader classLoader) {
        try {
            Field classTableField = ShareReflectUtil.findField(classLoader, "classTable");
            classTableField.set(classLoader, 0L);
        } catch (Throwable thr) {
            Log.w(TAG, "suppressClassTable failed: " + thr.getMessage(), thr);
        }
    }

    private static void suppressClassTableP(ClassLoader classLoader) {
        try {
            Field classTableField = null;
            try {
                classTableField = ShareReflectUtil.findField(classLoader, "classTable");
            } catch (NoSuchFieldException ignored) {
                try {
                    classTableField = ShareReflectUtil.findField(classLoader, "mClassTable");
                } catch (NoSuchFieldException ignored2) {
                    classTableField = null;
                }
            }
            if (classTableField != null) {
                classTableField.setAccessible(true);
                classTableField.set(classLoader, 0L);
            }
            try {
                Method skipHiddenAndCheck = Class.forName("dalvik.system.VMRuntime")
                    .getDeclaredMethod("setHiddenApiExemptions", String[].class);
                Method getRuntime = Class.forName("dalvik.system.VMRuntime").getDeclaredMethod("getRuntime");
                Object runtime = getRuntime.invoke(null);
                skipHiddenAndCheck.invoke(runtime, new Object[]{new String[]{"L"}});
            } catch (Throwable ignored) {
            }
            try {
                Field dexElementsField = ShareReflectUtil.findField(
                    ShareReflectUtil.findField(classLoader, "pathList").get(classLoader), "dexElements");
                Object[] dexElements = (Object[]) dexElementsField.get(
                    ShareReflectUtil.findField(classLoader, "pathList").get(classLoader));
                if (dexElements != null) {
                    for (Object element : dexElements) {
                        try {
                            Field dexFileField = ShareReflectUtil.findField(element, "dexFile");
                            Object dexFile = dexFileField.get(element);
                            if (dexFile != null) {
                                try {
                                    Field cookieField = ShareReflectUtil.findField(dexFile, "mCookie");
                                    cookieField.setAccessible(true);
                                } catch (NoSuchFieldException ignored3) {
                                }
                            }
                        } catch (Throwable ignored4) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable thr) {
            Log.w(TAG, "suppressClassTableP failed: " + thr.getMessage(), thr);
        }
    }

    public static boolean checkIsProtectedApp(List<File> files) {
        for (File file : files) {
            if (ShareConstants.PROTECTED_APP_DEX_PATTERN.matcher(file.getName()).matches()) {
                return true;
            }
        }
        return false;
    }

    private static List<File> createSortedAdditionalPathEntries(List<File> additionalPathEntries) {
        final List<File> result = new ArrayList<>(additionalPathEntries);
        java.util.Collections.sort(result);
        return result;
    }

    public static boolean checkDexInstall(ClassLoader classLoader) throws Throwable {
        Class<?> clazz = Class.forName(CHECK_DEX_CLASS, true, classLoader);
        Field field = ShareReflectUtil.findField(clazz, CHECK_DEX_FIELD);
        boolean isPatch = (boolean) field.get(null);
        return isPatch;
    }

    public static int getPatchDexCount() {
        return sPatchDexCount;
    }

    private static final class V4 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries, File optimizedDirectory) throws Throwable {
            throw new UnsupportedOperationException("V4 install is not supported in this build.");
        }
    }

    private static final class V14 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries, File optimizedDirectory) throws Throwable {
            throw new UnsupportedOperationException("V14 install is not supported in this build.");
        }
    }

    private static final class V19 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries, File optimizedDirectory) throws Throwable {
            throw new UnsupportedOperationException("V19 install is not supported in this build.");
        }
    }

    private static final class V23 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries, File optimizedDirectory) throws Throwable {
            throw new UnsupportedOperationException("V23 install is not supported in this build.");
        }
    }
}
