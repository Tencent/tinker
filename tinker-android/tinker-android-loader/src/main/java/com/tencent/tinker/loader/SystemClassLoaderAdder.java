/*
 * Copyright (C) 2016 THL A29 Limited, a Tencent company.
 * Copyright (C) 2013 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

/**
 * Created by zhangshaowen on 16/3/18.
 */
public class SystemClassLoaderAdder {
    private static final String TAG = "Tinker.ClassLoaderAdder";

    private static final String CHECK_DEX_CLASS = "com.tencent.tinker.loader.TinkerTestDexLoad";
    private static final String CHECK_DEX_FIELD = "isPatch";

    private static int sPatchDexCount = 0;

    @SuppressLint("NewApi")
    public static void installDexes(Application application, PathClassLoader loader, File dexOptDir, List<File> files)
        throws Throwable {

        if (!files.isEmpty()) {
            ClassLoader classLoader = loader;
            if (Build.VERSION.SDK_INT >= 24) {
                classLoader = AndroidNClassLoader.inject(loader, application);
            }
            //because in dalvik, if inner class is not the same classloader with it wrapper class.
            //it won't fail at dex2opt
            if (Build.VERSION.SDK_INT >= 23) {
                V23.install(classLoader, files, dexOptDir);
            } else if (Build.VERSION.SDK_INT >= 19) {
                V19.install(classLoader, files, dexOptDir);
            } else if (Build.VERSION.SDK_INT >= 14) {
                V14.install(classLoader, files, dexOptDir);
            } else {
                V4.install(classLoader, files, dexOptDir);
            }
            //install done
            sPatchDexCount = files.size();

            if (!checkDexInstall(classLoader)) {
                //reset patch dex
                SystemClassLoaderAdder.uninstallPatchDex(classLoader);
                throw new TinkerRuntimeException(ShareConstants.CHECK_DEX_INSTALL_FAIL);
            }
        }
    }

    public static void uninstallPatchDex(ClassLoader classLoader) throws Throwable {
        if (sPatchDexCount <= 0) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 14) {
            Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            Object dexPathList = pathListField.get(classLoader);
            ShareReflectUtil.reduceFieldArray(dexPathList, "dexElements", sPatchDexCount);
        } else {
            ShareReflectUtil.reduceFieldArray(classLoader, "mPaths", sPatchDexCount);
            ShareReflectUtil.reduceFieldArray(classLoader, "mFiles", sPatchDexCount);
            ShareReflectUtil.reduceFieldArray(classLoader, "mZips", sPatchDexCount);
            try {
                ShareReflectUtil.reduceFieldArray(classLoader, "mDexs", sPatchDexCount);
            } catch (Exception e) {
            }
        }
    }

    private static boolean checkDexInstall(ClassLoader classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = Class.forName(CHECK_DEX_CLASS, true, classLoader);
        Field filed = ShareReflectUtil.findField(clazz, CHECK_DEX_FIELD);
        boolean isPatch = (boolean) filed.get(null);
        Log.w(TAG, "checkDexInstall result:" + isPatch);
        return isPatch;
    }

    /**
     * Installer for platform versions 19.
     */
    private static final class V23 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
            throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = ShareReflectUtil.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makePathElements(dexPathList,
                new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makePathElement", e);
                    throw e;
                }

            }
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makePathElements}.
         */
        private static Object[] makePathElements(
            Object dexPathList, ArrayList<File> files, File optimizedDirectory,
            ArrayList<IOException> suppressedExceptions)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

            Method makePathElements;
            try {
                makePathElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class, File.class,
                    List.class);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "NoSuchMethodException: makePathElements(List,File,List) failure");
                try {
                    makePathElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", ArrayList.class, File.class, ArrayList.class);
                } catch (NoSuchMethodException e1) {
                    Log.e(TAG, "NoSuchMethodException: makeDexElements(ArrayList,File,ArrayList) failure");
                    try {
                        Log.e(TAG, "NoSuchMethodException: try use v19 instead");
                        return V19.makeDexElements(dexPathList, files, optimizedDirectory, suppressedExceptions);
                    } catch (NoSuchMethodException e2) {
                        Log.e(TAG, "NoSuchMethodException: makeDexElements(List,File,List) failure");
                        throw e2;
                    }
                }
            }

            return (Object[]) makePathElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
        }
    }

    /**
     * Installer for platform versions 19.
     */
    private static final class V19 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
            throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = ShareReflectUtil.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
                suppressedExceptions));
            if (suppressedExceptions.size() > 0) {
                for (IOException e : suppressedExceptions) {
                    Log.w(TAG, "Exception in makeDexElement", e);
                    throw e;
                }
            }
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
            Object dexPathList, ArrayList<File> files, File optimizedDirectory,
            ArrayList<IOException> suppressedExceptions)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

            Method makeDexElements = null;
            try {
                makeDexElements = ShareReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
                    ArrayList.class);
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "NoSuchMethodException: makeDexElements(ArrayList,File,ArrayList) failure");
                try {
                    makeDexElements = ShareReflectUtil.findMethod(dexPathList, "makeDexElements", List.class, File.class, List.class);
                } catch (NoSuchMethodException e1) {
                    Log.e(TAG, "NoSuchMethodException: makeDexElements(List,File,List) failure");
                    throw e1;
                }
            }

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
        }
    }

    /**
     * Installer for platform versions 14, 15, 16, 17 and 18.
     */
    private static final class V14 {

        private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
                                    File optimizedDirectory)
            throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Field pathListField = ShareReflectUtil.findField(loader, "pathList");
            Object dexPathList = pathListField.get(loader);
            ShareReflectUtil.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
                new ArrayList<File>(additionalClassPathEntries), optimizedDirectory));
        }

        /**
         * A wrapper around
         * {@code private static final dalvik.system.DexPathList#makeDexElements}.
         */
        private static Object[] makeDexElements(
            Object dexPathList, ArrayList<File> files, File optimizedDirectory)
            throws IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
            Method makeDexElements =
                ShareReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class);

            return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
        }
    }

    /**
     * Installer for platform versions 4 to 13.
     */
    private static final class V4 {
        private static void install(ClassLoader loader, List<File> additionalClassPathEntries, File optimizedDirectory)
            throws IllegalArgumentException, IllegalAccessException,
            NoSuchFieldException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.DexClassLoader. We modify its
             * fields mPaths, mFiles, mZips and mDexs to append additional DEX
             * file entries.
             */
            int extraSize = additionalClassPathEntries.size();

            Field pathField = ShareReflectUtil.findField(loader, "path");

            StringBuilder path = new StringBuilder((String) pathField.get(loader));
            String[] extraPaths = new String[extraSize];
            File[] extraFiles = new File[extraSize];
            ZipFile[] extraZips = new ZipFile[extraSize];
            DexFile[] extraDexs = new DexFile[extraSize];
            for (ListIterator<File> iterator = additionalClassPathEntries.listIterator();
                 iterator.hasNext();) {
                File additionalEntry = iterator.next();
                String entryPath = additionalEntry.getAbsolutePath();
                path.append(':').append(entryPath);
                int index = iterator.previousIndex();
                extraPaths[index] = entryPath;
                extraFiles[index] = additionalEntry;
                extraZips[index] = new ZipFile(additionalEntry);
                //edit by zhangshaowen
                String outputPathName = SharePatchFileUtil.optimizedPathFor(additionalEntry, optimizedDirectory);
                //for below 4.0, we must input jar or zip
                extraDexs[index] = DexFile.loadDex(entryPath, outputPathName, 0);
            }

            pathField.set(loader, path.toString());
            ShareReflectUtil.expandFieldArray(loader, "mPaths", extraPaths);
            ShareReflectUtil.expandFieldArray(loader, "mFiles", extraFiles);
            ShareReflectUtil.expandFieldArray(loader, "mZips", extraZips);
            try {
                ShareReflectUtil.expandFieldArray(loader, "mDexs", extraDexs);
            } catch (Exception e) {

            }
        }
    }

}
