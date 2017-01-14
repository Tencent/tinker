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

import com.tencent.tinker.lib.tinker.Tinker;
import com.tencent.tinker.lib.tinker.TinkerLoadResult;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
            if (loadResult.libs != null) {
                for (String name : loadResult.libs.keySet()) {
                    if (name.equals(relativeLibPath)) {
                        String patchLibraryPath = loadResult.libraryDirectory + "/" + name;
                        File library = new File(patchLibraryPath);
                        if (library.exists()) {
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
    public static void installNavitveLibraryABI(Context context, String currentABI) {
        Tinker tinker = Tinker.with(context);
        if (!tinker.isTinkerLoaded()) {
            TinkerLog.i(TAG, "tinker is not loaded, just return");
            return;
        }
        TinkerLoadResult loadResult = tinker.getTinkerLoadResultIfPresent();
        if (loadResult.libs == null) {
            TinkerLog.i(TAG, "tinker libs is null, just return");
            return;
        }
        File soDir = new File(loadResult.libraryDirectory, "lib/" + currentABI);
        if (!soDir.exists()) {
            TinkerLog.e(TAG, "current libraryABI folder is not exist, path: %s", soDir.getPath());
            return;
        }
        ClassLoader classLoader = context.getClassLoader();
        if (classLoader == null) {
            TinkerLog.e(TAG, "classloader is null");
            return;
        }
        TinkerLog.i(TAG, "before hack classloader:" + classLoader.toString());

        try {
            installNativeLibraryPath(classLoader, soDir);
        } catch (Throwable throwable) {
            TinkerLog.e(TAG, "installNativeLibraryPath fail:" + throwable);
        }
        TinkerLog.i(TAG, "after hack classloader:" + classLoader.toString());
    }

    private static void installNativeLibraryPath(ClassLoader classLoader, File folder)
        throws Throwable {
        if (folder == null || !folder.exists()) {
            TinkerLog.e(TAG, "installNativeLibraryPath, folder %s is illegal", folder);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                V23.install(classLoader, folder);
            } catch (Throwable throwable) {
                // install fail, try to treat it as v14
                TinkerLog.e(TAG, "installNativeLibraryPath, v23 fail, sdk: %d, error: %s",
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
            StringBuilder libPath = new StringBuilder((String) pathField.get(classLoader));
            libPath.append(':').append(addPath);
            pathField.set(classLoader, libPath.toString());

            Field libraryPathElementsFiled = ShareReflectUtil.findField(classLoader, "libraryPathElements");
            List<String> libraryPathElements = (List<String>) libraryPathElementsFiled.get(classLoader);
            libraryPathElements.add(0, addPath);
            libraryPathElementsFiled.set(classLoader, libraryPathElements);
        }
    }

    private static final class V14 {
        private static void install(ClassLoader classLoader, File folder)  throws Throwable {
            Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            Object dexPathList = pathListField.get(classLoader);

            ShareReflectUtil.expandFieldArray(dexPathList, "nativeLibraryDirectories", new File[]{folder});
        }
    }

    private static final class V23 {
        private static void install(ClassLoader classLoader, File folder)  throws Throwable {
            Field pathListField = ShareReflectUtil.findField(classLoader, "pathList");
            Object dexPathList = pathListField.get(classLoader);

            Field nativeLibraryDirectories = ShareReflectUtil.findField(dexPathList, "nativeLibraryDirectories");

            List<File> libDirs = (List<File>) nativeLibraryDirectories.get(dexPathList);
            libDirs.add(0, folder);
            Field systemNativeLibraryDirectories =
                ShareReflectUtil.findField(dexPathList, "systemNativeLibraryDirectories");
            List<File> systemLibDirs = (List<File>) systemNativeLibraryDirectories.get(dexPathList);
            Method makePathElements =
                ShareReflectUtil.findMethod(dexPathList, "makePathElements", List.class, File.class, List.class);
            ArrayList<IOException> suppressedExceptions = new ArrayList<>();
            libDirs.addAll(systemLibDirs);
            Object[] elements = (Object[]) makePathElements.
                invoke(dexPathList, libDirs, null, suppressedExceptions);
            Field nativeLibraryPathElements = ShareReflectUtil.findField(dexPathList, "nativeLibraryPathElements");
            nativeLibraryPathElements.setAccessible(true);
            nativeLibraryPathElements.set(dexPathList, elements);
        }
    }

}
