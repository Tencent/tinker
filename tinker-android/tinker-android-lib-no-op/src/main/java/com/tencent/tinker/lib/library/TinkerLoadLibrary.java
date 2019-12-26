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

import com.tencent.tinker.entry.ApplicationLike;
import com.tencent.tinker.loader.TinkerRuntimeException;

/**
 * Created by zhangshaowen on 17/1/5.
 * Thanks for Android Fragmentation
 */

public class TinkerLoadLibrary {
    public static void loadArmLibrary(Context context, String libName) {
        if (libName == null || libName.isEmpty() || context == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }
        System.loadLibrary(libName);
    }

    public static void loadArmLibraryWithoutTinkerInstalled(ApplicationLike appLike, String libName) {
        if (libName == null || libName.isEmpty() || appLike == null) {
            throw new TinkerRuntimeException("libName or appLike is null!");
        }
        System.loadLibrary(libName);
    }

    public static void loadArmV7Library(Context context, String libName) {
        if (libName == null || libName.isEmpty() || context == null) {
            throw new TinkerRuntimeException("libName or context is null!");
        }
        System.loadLibrary(libName);
    }

    public static void loadArmV7LibraryWithoutTinkerInstalled(ApplicationLike appLike, String libName) {
        if (libName == null || libName.isEmpty() || appLike == null) {
            throw new TinkerRuntimeException("libName or appLike is null!");
        }
        System.loadLibrary(libName);
    }

    public static boolean loadLibraryFromTinker(Context context, String relativePath, String libName) throws UnsatisfiedLinkError {
        return false;
    }

    public static boolean installNavitveLibraryABI(Context context, String currentABI) {
        return false;
    }

    public static boolean installNativeLibraryABIWithoutTinkerInstalled(ApplicationLike appLike, String currentABI) {
        return false;
    }
}
