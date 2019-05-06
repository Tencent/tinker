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

package com.tencent.tinker.lib.library;

import android.content.Context;
import android.os.Build;

import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerApplicationHelper;
import com.tencent.tinker.lib.tinker.TinkerInstaller;
import com.tencent.tinker.lib.tinker.TinkerLoadResult;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by zhangshaowen on 17/1/5.
 * Thanks for Android Fragmentation
 */

public class TinkerLoadLibrary {
    private static final String TAG = "Tinker.LoadLibrary";

    /**
     * you can use TinkerInstaller.loadLibrary replace your System.loadLibrary for auto update library!
     * only support auto load lib/armeabi library from patch.
     * for other library in lib/* or assets,
     * you can load through {@code TinkerInstaller#loadLibraryFromTinker}
     */
    public static void loadArmLibrary(Context context, String libName) {
        if (libName == null || libName.isEmpty() || context == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        Tinker tinker = Tinker.with(context);

        if (tinker.isEnabledForNativeLib()) {
            if (TinkerLoadLibrary.loadLibraryFromTinker(context, "lib/armeabi", libName)) {
                return;
            }
        }
        System.loadLibrary(libName);
    }

    /**
     * The same as {@link #loadArmLibrary(Context, String)} but it can be called before
     * calling {@link TinkerInstaller#install}
     * @param appLike
     * @param libName
     */
    public static void loadArmLibraryWithoutTinkerInstalled(ApplicationLike appLike, String libName) {
        if (libName == null || libName.isEmpty() || appLike == null) {
            throw new TinkerRuntimeException("libName or appLike is null!");
        }
        if (TinkerApplicationHelper.isTinkerEnableForNativeLib(appLike)) {
            if (TinkerApplicationHelper.loadLibraryFromTinker(appLike, "lib/armeabi", libName)) {
                return;
            }
        }
        System.loadLibrary(libName);
    }

    /**
     * you can use TinkerInstaller.loadArmV7Library replace your System.loadLibrary for auto update library!
     * only support auto load lib/armeabi-v7a library from patch.
     * for other library in lib/* or assets,
     * you can load through {@code TinkerInstaller#loadLibraryFromTinker}
     */
    public static void loadArmV7Library(Context context, String libName) {
        if (libName == null || libName.isEmpty() || context == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        Tinker tinker = Tinker.with(context);
        if (tinker.isEnabledForNativeLib()) {
            if (TinkerLoadLibrary.loadLibraryFromTinker(context, "lib/armeabi-v7a", libName)) {
                return;
            }

        }
        System.loadLibrary(libName);
    }

    /**
     * The same as {@link #loadArmV7Library(Context, String)} but it can be called before
     * calling {@link TinkerInstaller#install}
     * @param appLike
     * @param libName
     */
    public static void loadArmV7LibraryWithoutTinkerInstalled(ApplicationLike appLike, String libName) {
        if (libName == null || libName.isEmpty() || appLike == null) {
            throw new TinkerRuntimeException("libName or appLike is null!");
        }
        if (TinkerApplicationHelper.isTinkerEnableForNativeLib(appLike)) {
            if (TinkerApplicationHelper.loadLibraryFromTinker(appLike, "lib/armeabi-v7a", libName)) {
                return;
            }
        }
        System.loadLibrary(libName);
    }

    /**
     * sample usage for native library
     *
     * @param context
     * @param relativePath such as lib/armeabi
     * @param libName      for the lib libTest.so, you can pass Test or libTest, or libTest.so
     * @return boolean
     * @throws UnsatisfiedLinkError
     */
    public static boolean loadLibraryFromTinker(Context context, String relativePath, String libName) throws UnsatisfiedLinkError {
        final Tinker tinker = Tinker.with(context);

        libName = libName.startsWith("lib") ? libName : "lib" + libName;
        libName = libName.endsWith(".so") ? libName : libName + ".so";
        String relativeLibPath = relativePath + "/" + libName;

        //TODO we should add cpu abi, and the real path later
        if (tinker.isEnabledForNativeLib() && tinker.isTinkerLoaded()) {
            TinkerLoadResult loadResult = tinker.getTinkerLoadResultIfPresent();
            if (loadResult.libs == null) {
                return false;
            }
            for (String name : loadResult.libs.keySet()) {
                if (!name.equals(relativeLibPath)) {
                    continue;
                }
                String patchLibraryPath = loadResult.libraryDirectory + "/" + name;
                File library = new File(patchLibraryPath);
                if (!library.exists()) {
                    continue;
                }
                //whether we check md5 when load
                boolean verifyMd5 = tinker.isTinkerLoadVerify();
                if (verifyMd5 && !SharePatchFileUtil.verifyFileMd5(library, loadResult.libs.get(name))) {
                    tinker.getLoadReporter().onLoadFileMd5Mismatch(library, ShareConstants.TYPE_LIBRARY);
                } else {
                    System.load(patchLibraryPath);
                    TinkerLog.i(TAG, "loadLibraryFromTinker success:" + patchLibraryPath);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * you can reflect your current abi to classloader library path
     * as you don't need to use load*Library method above
     * @param context
     * @param currentABI
     */
    public static boolean installNavitveLibraryABI(Context context, String currentABI) {
        Tinker tinker = Tinker.with(context);
        if (!tinker.isTinkerLoaded()) {
            TinkerLog.i(TAG, "tinker is not loaded, just return");
            return false;
        }
        TinkerLoadResult loadResult = tinker.getTinkerLoadResultIfPresent();
        if (loadResult.libs == null) {
            TinkerLog.i(TAG, "tinker libs is null, just return");
            return false;
        }
        File soDir = new File(loadResult.libraryDirectory, "lib/" + currentABI);
        if (!soDir.exists()) {
            TinkerLog.e(TAG, "current libraryABI folder is not exist, path: %s", soDir.getPath());
            return false;
        }
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            TinkerLog.e(TAG, "classloader is null");
            return false;
        }
        TinkerLog.i(TAG, "before hack classloader:" + classLoader.toString());

        try {
            installNativeLibraryPath(classLoader, soDir);
            return true;
        } catch (Throwable throwable) {
            TinkerLog.e(TAG, "installNativeLibraryPath fail:" + throwable);
            return false;
        } finally {
            TinkerLog.i(TAG, "after hack classloader:" + classLoader.toString());
        }
    }

    /**
     * The same as {@link #installNavitveLibraryABI(Context, String)} but it can be called before
     * calling {@link TinkerInstaller#install}
     * @param appLike
     * @param currentABI
     * @return
     */
    public static boolean installNativeLibraryABIWithoutTinkerInstalled(ApplicationLike appLike, String currentABI) {
        final String currentVersion = TinkerApplicationHelper.getCurrentVersion(appLike);
        if (ShareTinkerInternals.isNullOrNil(currentVersion)) {
            TinkerLog.e(TAG, "failed to get current patch version.");
            return false;
        }

        final File patchDirectory = SharePatchFileUtil.getPatchDirectory(appLike.getApplication());
        if (patchDirectory == null) {
            TinkerLog.e(TAG, "failed to get current patch directory.");
            return false;
        }

        File patchVersionDirectory = new File(patchDirectory.getAbsolutePath() + "/" + SharePatchFileUtil.getPatchVersionDirectory(currentVersion));
        File libPath = new File(patchVersionDirectory.getAbsolutePath() + "/lib/lib/" + currentABI);
        if (!libPath.exists()) {
            TinkerLog.e(TAG, "tinker lib path [%s] is not exists.", libPath);
            return false;
        }

        final ClassLoader classLoader = appLike.getApplication().getClassLoader();
        if (classLoader == null) {
            TinkerLog.e(TAG, "classloader is null");
            return false;
        } else {
            TinkerLog.i(TAG, "before hack classloader:" + classLoader.toString());
            try {
                final Method installNativeLibraryPathMethod =
                        TinkerLoadLibrary.class.getDeclaredMethod("installNativeLibraryPath", ClassLoader.class, File.class);
                installNativeLibraryPathMethod.setAccessible(true);
                installNativeLibraryPathMethod.invoke(null, classLoader, libPath);
                return true;
            } catch (Throwable thr) {
                TinkerLog.e(TAG, "installNativeLibraryPath fail:" + libPath + ", thr: " + thr);
                return false;
            } finally {
                TinkerLog.i(TAG, "after hack classloader:" + classLoader.toString());
            }
        }
    }

    /**
     * All version of install logic obey the following strategies:
     *   1. If path of {@code folder} is not injected into the classloader, inject it to the
     *   beginning of pathList in the classloader.
     *
     *   2. Otherwise remove path of {@code folder} first, then re-inject it to the
     *   beginning of pathList in the classloader.
     */
    private static void installNativeLibraryPath(ClassLoader classLoader, File folder)
        throws Throwable {
        if (folder == null || !folder.exists()) {
            TinkerLog.e(TAG, "installNativeLibraryPath, folder %s is illegal", folder);
            return;
        }
        // android o sdk_int 26
        // for android o preview sdk_int 25
        if ((Build.VERSION.SDK_INT == 25 && Build.VERSION.PREVIEW_SDK_INT != 0)
            || Build.VERSION.SDK_INT > 25) {
            try {
                V25.install(classLoader, folder);
            } catch (Throwable throwable) {
                // install fail, try to treat it as v23
                // some preview N version may go here
                TinkerLog.e(TAG, "installNativeLibraryPath, v25 fail, sdk: %d, error: %s, try to fallback to V23",
                        Build.VERSION.SDK_INT, throwable.getMessage());
                V23.install(classLoader, folder);
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            try {
                V23.install(classLoader, folder);
            } catch (Throwable throwable) {
                // install fail, try to treat it as v14
                TinkerLog.e(TAG, "installNativeLibraryPath, v23 fail, sdk: %d, error: %s, try to fallback to V14",
                    Build.VERSION.SDK_INT, throwable.getMessage());

                V14.install(classLoader, folder);
            }
        } else if (Build.VERSION.SDK_INT >= 14) {
            V14.install(classLoader, folder);
        } else {
            V4.install(classLoader, folder);
        }
    }

    private static final class V4 {
        private static void install(ClassLoader classLoader, File folder)  throws Throwable {
            String addPath = folder.getPath();
            Field pathField = ShareReflectUtil.findField(classLoader, "libPath");
            final String origLibPaths = (String) pathField.get(classLoader);
            final String[] origLibPathSplit = origLibPaths.split(":");
            final StringBuilder newLibPaths = new StringBuilder(addPath);

            for (String origLibPath : origLibPathSplit) {
                if (origLibPath == null || addPath.equals(origLibPath)) {
                    continue;
                }
                newLibPaths.append(':').append(origLibPath);
            }
            pathField.set(classLoader, newLibPaths.toString());

            final Field libraryPathElementsFiled = ShareReflectUtil.findField(classLoader, "libraryPathElements");
            final List<String> libraryPathElements = (List<String>) libraryPathElementsFiled.get(classLoader);
            final Iterator<String> libPathElementIt = libraryPathElements.iterator();
            while (libPathElementIt.hasNext()) {
                final String libPath = libPathElementIt.next();
                if (addPath.equals(libPath)) {
                    libPathElementIt.remove();
                    break;
                }
            }
            libraryPathElements.add(0, addPath);
            libraryPathElementsFiled.set(classLoader, libraryPathElements);
        }
    }

    private static final class V14 {
        private static void install(ClassLoader classLoader, File folder)  throws Throwable {
            final Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            final Object dexPathList = pathListField.get(classLoader);

            final Field nativeLibDirField = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");
            final File[] origNativeLibDirs = (File[]) nativeLibDirField.get(dexPathList);

            final List<File> newNativeLibDirList = new ArrayList<>(origNativeLibDirs.length + 1);
            newNativeLibDirList.add(folder);
            for (File origNativeLibDir : origNativeLibDirs) {
                if (!folder.equals(origNativeLibDir)) {
                    newNativeLibDirList.add(origNativeLibDir);
                }
            }
            nativeLibDirField.set(dexPathList, newNativeLibDirList.toArray(new File[0]));
        }
    }

    private static final class V23 {
        private static void install(ClassLoader classLoader, File folder)  throws Throwable {
            final Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            final Object dexPathList = pathListField.get(classLoader);

            final Field nativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");

            List<File> origLibDirs = (List<File>) nativeLibraryDirectories.get(dexPathList);
            if (origLibDirs == null) {
                origLibDirs = new ArrayList<>(2);
            }
            final Iterator<File> libDirIt = origLibDirs.iterator();
            while (libDirIt.hasNext()) {
                final File libDir = libDirIt.next();
                if (folder.equals(libDir)) {
                    libDirIt.remove();
                    break;
                }
            }
            origLibDirs.add(0, folder);

            final Field systemNativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "systemNativeLibraryDirectories");
            List<File> origSystemLibDirs = (List<File>) systemNativeLibraryDirectories.get(dexPathList);
            if (origSystemLibDirs == null) {
                origSystemLibDirs = new ArrayList<>(2);
            }

            final List<File> newLibDirs = new ArrayList<>(origLibDirs.size() + origSystemLibDirs.size() + 1);
            newLibDirs.addAll(origLibDirs);
            newLibDirs.addAll(origSystemLibDirs);

            final Method makeElements = ShareReflectUtil.findMethod(dexPathList,
                    "makePathElements", List.class, File.class, List.class);
            final ArrayList<IOException> suppressedExceptions = new ArrayList<>();

            final Object[] elements = (Object[]) makeElements.invoke(dexPathList, newLibDirs, null, suppressedExceptions);

            final Field nativeLibraryPathElements = ShareReflectUtil.findField(dexPathList, "nativeLibraryPathElements");
            nativeLibraryPathElements.set(dexPathList, elements);
        }
    }

    private static final class V25 {
        private static void install(ClassLoader classLoader, File folder)  throws Throwable {
            final Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            final Object dexPathList = pathListField.get(classLoader);

            final Field nativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");

            List<File> origLibDirs = (List<File>) nativeLibraryDirectories.get(dexPathList);
            if (origLibDirs == null) {
                origLibDirs = new ArrayList<>(2);
            }
            final Iterator<File> libDirIt = origLibDirs.iterator();
            while (libDirIt.hasNext()) {
                final File libDir = libDirIt.next();
                if (folder.equals(libDir)) {
                    libDirIt.remove();
                    break;
                }
            }
            origLibDirs.add(0, folder);

            final Field systemNativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "systemNativeLibraryDirectories");
            List<File> origSystemLibDirs = (List<File>) systemNativeLibraryDirectories.get(dexPathList);
            if (origSystemLibDirs == null) {
                origSystemLibDirs = new ArrayList<>(2);
            }

            final List<File> newLibDirs = new ArrayList<>(origLibDirs.size() + origSystemLibDirs.size() + 1);
            newLibDirs.addAll(origLibDirs);
            newLibDirs.addAll(origSystemLibDirs);

            final Method makeElements = ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class);

            final Object[] elements = (Object[]) makeElements.invoke(dexPathList, newLibDirs);

            final Field nativeLibraryPathElements = ShareReflectUtil.findField(dexPathList, "nativeLibraryPathElements");
            nativeLibraryPathElements.set(dexPathList, elements);
        }
    }
}
