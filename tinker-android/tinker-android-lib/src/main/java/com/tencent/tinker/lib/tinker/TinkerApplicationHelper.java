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

package com.tencent.tinker.lib.tinker;

import android.content.Intent;

import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * sometimes, you may want to install tinker later, or never install tinker in some process.
 * you can use {@code TinkerApplicationHelper} API to get the tinker status!
 * Created by zhangshaowen on 16/6/28.
 */
public class TinkerApplicationHelper {
    private static final String TAG = "Tinker.TinkerApplicationHelper";

    /**
     * they can use without Tinker is installed!
     * same as {@code Tinker.isTinkerEnabled}
     *
     * @return
     */
    public static boolean isTinkerEnableAll(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }
        int tinkerFlags = applicationLike.getTinkerFlags();
        return ShareTinkerInternals.isTinkerEnabledAll(tinkerFlags);
    }

    /**
     * same as {@code Tinker.isEnabledForDex}
     *
     * @param applicationLike
     * @return
     */
    public static boolean isTinkerEnableForDex(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }
        int tinkerFlags = applicationLike.getTinkerFlags();
        return ShareTinkerInternals.isTinkerEnabledForDex(tinkerFlags);
    }

    /**
     * same as {@code Tinker.isEnabledForNativeLib}
     *
     * @param applicationLike
     * @return
     */
    public static boolean isTinkerEnableForNativeLib(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }
        int tinkerFlags = applicationLike.getTinkerFlags();
        return ShareTinkerInternals.isTinkerEnabledForNativeLib(tinkerFlags);
    }

    /**
     * same as {@code Tinker.isTinkerEnabledForResource}
     *
     * @param applicationLike
     * @return
     */
    public static boolean isTinkerEnableForResource(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }
        int tinkerFlags = applicationLike.getTinkerFlags();
        return ShareTinkerInternals.isTinkerEnabledForResource(tinkerFlags);
    }

    /**
     * same as {@code Tinker.getPatchDirectory}
     *
     * @param applicationLike
     * @return
     */
    public static File getTinkerPatchDirectory(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }

        return SharePatchFileUtil.getPatchDirectory(applicationLike.getApplication());
    }

    /**
     * whether tinker is success loaded
     * same as {@code Tinker.isTinkerLoaded}
     *
     * @param applicationLike
     * @return
     */
    public static boolean isTinkerLoadSuccess(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }

        Intent tinkerResultIntent = applicationLike.getTinkerResultIntent();

        if (tinkerResultIntent == null) {
            return false;
        }
        int loadCode = ShareIntentUtil.getIntentReturnCode(tinkerResultIntent);

        return (loadCode == ShareConstants.ERROR_LOAD_OK);
    }

    /**
     * you can use this api to get load dexes before tinker is installed
     * same as {@code Tinker.getTinkerLoadResultIfPresent.dexes}
     *
     * @return
     */
    public static HashMap<String, String> getLoadDexesAndMd5(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }

        Intent tinkerResultIntent = applicationLike.getTinkerResultIntent();

        if (tinkerResultIntent == null) {
            return null;
        }
        int loadCode = ShareIntentUtil.getIntentReturnCode(tinkerResultIntent);

        if (loadCode == ShareConstants.ERROR_LOAD_OK) {
            return ShareIntentUtil.getIntentPatchDexPaths(tinkerResultIntent);
        }
        return null;
    }


    /**
     * you can use this api to get load libs before tinker is installed
     * same as {@code Tinker.getTinkerLoadResultIfPresent.libs}
     *
     * @return
     */
    public static HashMap<String, String> getLoadLibraryAndMd5(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }

        Intent tinkerResultIntent = applicationLike.getTinkerResultIntent();

        if (tinkerResultIntent == null) {
            return null;
        }
        int loadCode = ShareIntentUtil.getIntentReturnCode(tinkerResultIntent);

        if (loadCode == ShareConstants.ERROR_LOAD_OK) {
            return ShareIntentUtil.getIntentPatchLibsPaths(tinkerResultIntent);
        }
        return null;
    }

    /**
     * you can use this api to get tinker package configs before tinker is installed
     * same as {@code Tinker.getTinkerLoadResultIfPresent.packageConfig}
     *
     * @return
     */
    public static HashMap<String, String> getPackageConfigs(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }

        Intent tinkerResultIntent = applicationLike.getTinkerResultIntent();

        if (tinkerResultIntent == null) {
            return null;
        }
        int loadCode = ShareIntentUtil.getIntentReturnCode(tinkerResultIntent);

        if (loadCode == ShareConstants.ERROR_LOAD_OK) {
            return ShareIntentUtil.getIntentPackageConfig(tinkerResultIntent);
        }
        return null;
    }

    /**
     * you can use this api to get tinker current version before tinker is installed
     *
     * @return
     */
    public static String getCurrentVersion(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }

        Intent tinkerResultIntent = applicationLike.getTinkerResultIntent();

        if (tinkerResultIntent == null) {
            return null;
        }
        final String oldVersion = ShareIntentUtil.getStringExtra(tinkerResultIntent, ShareIntentUtil.INTENT_PATCH_OLD_VERSION);
        final String newVersion = ShareIntentUtil.getStringExtra(tinkerResultIntent, ShareIntentUtil.INTENT_PATCH_NEW_VERSION);
        final boolean isMainProcess = ShareTinkerInternals.isInMainProcess(applicationLike.getApplication());
        if (oldVersion != null && newVersion != null) {
            if (isMainProcess) {
                return newVersion;
            } else {
                return oldVersion;
            }
        }
        return null;
    }

    /**
     * clean all patch files without install tinker
     * same as {@code Tinker.cleanPatch}
     *
     * @param applicationLike
     */
    public static void cleanPatch(ApplicationLike applicationLike) {
        if (applicationLike == null || applicationLike.getApplication() == null) {
            throw new TinkerRuntimeException("tinkerApplication is null");
        }
        final File tinkerDir = SharePatchFileUtil.getPatchDirectory(applicationLike.getApplication());
        if (!tinkerDir.exists()) {
            TinkerLog.w(TAG, "try to clean patch while there're not any applied patches.");
            return;
        }
        final File patchInfoFile = SharePatchFileUtil.getPatchInfoFile(tinkerDir.getAbsolutePath());
        if (!patchInfoFile.exists()) {
            TinkerLog.w(TAG, "try to clean patch while patch info file does not exist.");
            return;
        }
        final File patchInfoLockFile = SharePatchFileUtil.getPatchInfoLockFile(tinkerDir.getAbsolutePath());
        final SharePatchInfo patchInfo = SharePatchInfo.readAndCheckPropertyWithLock(patchInfoFile, patchInfoLockFile);
        if (patchInfo != null) {
            patchInfo.isRemoveNewVersion = true;
            SharePatchInfo.rewritePatchInfoFileWithLock(patchInfoFile, patchInfo, patchInfoLockFile);
        }
    }

    /**
     * only support auto load lib/armeabi-v7a library from patch.
     * in some process, you may not want to install tinker
     * and you can load patch dex and library without install tinker!
     * }
     */
    public static void loadArmV7aLibrary(ApplicationLike applicationLike, String libName) {
        if (libName == null || libName.isEmpty() || applicationLike == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        if (TinkerApplicationHelper.isTinkerEnableForNativeLib(applicationLike)) {
            if (TinkerApplicationHelper.loadLibraryFromTinker(applicationLike, "lib/armeabi-v7a", libName)) {
                return;
            }

        }
        System.loadLibrary(libName);
    }


    /**
     * only support auto load lib/armeabi library from patch.
     * in some process, you may not want to install tinker
     * and you can load patch dex and library without install tinker!
     */
    public static void loadArmLibrary(ApplicationLike applicationLike, String libName) {
        if (libName == null || libName.isEmpty() || applicationLike == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        if (TinkerApplicationHelper.isTinkerEnableForNativeLib(applicationLike)) {
            if (TinkerApplicationHelper.loadLibraryFromTinker(applicationLike, "lib/armeabi", libName)) {
                return;
            }

        }
        System.loadLibrary(libName);
    }

    /**
     * you can use these api to load tinker library without tinker is installed!
     * same as {@code TinkerInstaller#loadLibraryFromTinker}
     *
     * @param applicationLike
     * @param relativePath
     * @param libname
     * @return
     * @throws UnsatisfiedLinkError
     */
    public static boolean loadLibraryFromTinker(ApplicationLike applicationLike, String relativePath, String libname) throws UnsatisfiedLinkError {
        libname = libname.startsWith("lib") ? libname : "lib" + libname;
        libname = libname.endsWith(".so") ? libname : libname + ".so";
        String relativeLibPath = relativePath + "/" + libname;

        //TODO we should add cpu abi, and the real path later
        if (!TinkerApplicationHelper.isTinkerEnableForNativeLib(applicationLike)) {
            return false;
        }
        if (!TinkerApplicationHelper.isTinkerEnableForNativeLib(applicationLike)) {
            return false;
        }

        final HashMap<String, String> loadLibraries = TinkerApplicationHelper.getLoadLibraryAndMd5(applicationLike);
        if (loadLibraries == null) {
            return false;
        }

        final String currentVersion = TinkerApplicationHelper.getCurrentVersion(applicationLike);
        if (ShareTinkerInternals.isNullOrNil(currentVersion)) {
            return false;
        }
        final File patchDirectory = SharePatchFileUtil.getPatchDirectory(applicationLike.getApplication());
        if (patchDirectory == null) {
            return false;
        }
        final File patchVersionDirectory = new File(patchDirectory.getAbsolutePath() + "/" + SharePatchFileUtil.getPatchVersionDirectory(currentVersion));
        final String libPrePath = patchVersionDirectory.getAbsolutePath() + "/" + ShareConstants.SO_PATH;

        for (Map.Entry<String, String> libEntry : loadLibraries.entrySet()) {
            final String name = libEntry.getKey();
            if (!name.equals(relativeLibPath)) {
                continue;
            }
            final String patchLibraryPath = libPrePath + "/" + name;
            final File library = new File(patchLibraryPath);
            if (!library.exists()) {
                continue;
            }
            //whether we check md5 when load
            final boolean verifyMd5 = applicationLike.getTinkerLoadVerifyFlag();
            if (verifyMd5 && !SharePatchFileUtil.verifyFileMd5(library, loadLibraries.get(name))) {
                //do not report, because tinker is not install
                TinkerLog.i(TAG, "loadLibraryFromTinker md5mismatch fail:" + patchLibraryPath);
            } else {
                System.load(patchLibraryPath);
                TinkerLog.i(TAG, "loadLibraryFromTinker success:" + patchLibraryPath);
                return true;
            }
        }

        return false;
    }
}
