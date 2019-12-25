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

import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;

import java.io.File;
import java.util.HashMap;

/**
 * sometimes, you may want to install tinker later, or never install tinker in some process.
 * you can use {@code TinkerApplicationHelper} API to get the tinker status!
 * Created by zhangshaowen on 16/6/28.
 */
public class TinkerApplicationHelper {
    private static final String TAG = "Tinker.TinkerApplicationHelper";

    public static boolean isTinkerEnableAll(ApplicationLike applicationLike) {
        return false;
    }

    public static boolean isTinkerEnableForDex(ApplicationLike applicationLike) {
        return false;
    }

    public static boolean isTinkerEnableForNativeLib(ApplicationLike applicationLike) {
        return false;
    }

    public static boolean isTinkerEnableForResource(ApplicationLike applicationLike) {
        return false;
    }

    public static File getTinkerPatchDirectory(ApplicationLike applicationLike) {
        return null;
    }

    public static boolean isTinkerLoadSuccess(ApplicationLike applicationLike) {
        return false;
    }

    public static HashMap<String, String> getLoadDexesAndMd5(ApplicationLike applicationLike) {
        return null;
    }

    public static HashMap<String, String> getLoadLibraryAndMd5(ApplicationLike applicationLike) {
        return null;
    }

    public static HashMap<String, String> getPackageConfigs(ApplicationLike applicationLike) {
        return null;
    }

    public static String getCurrentVersion(ApplicationLike applicationLike) {
        return null;
    }

    public static void cleanPatch(ApplicationLike applicationLike) {
        TinkerLog.e(TAG, "[-] Ignore this invocation since I'm no-op version.");
    }

    public static void loadArmV7aLibrary(ApplicationLike applicationLike, String libName) {
        System.loadLibrary(libName);
    }

    public static void loadArmLibrary(ApplicationLike applicationLike, String libName) {
        if (libName == null || libName.isEmpty() || applicationLike == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }

        System.loadLibrary(libName);
    }

    public static boolean loadLibraryFromTinker(ApplicationLike applicationLike, String relativePath, String libname) throws UnsatisfiedLinkError {
        return false;
    }
}
