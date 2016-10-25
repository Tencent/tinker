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

package com.tencent.tinker.server.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.tencent.tinker.lib.util.TinkerLog;

import static com.tencent.tinker.server.TinkerClientImp.TAG;

/**
 * Created by sun on 18/10/2016.
 */

public final class VersionUtils {

    private static final String CURRENT_VERSION = "current_version";
    private static final String PATCH_FILE_PREF = "patch_path_";
    private static final String SP_FILE_NAME    = "tkclient_sp_version";

    private VersionUtils() {
        // A Util Class
    }

    public static boolean update(Context context, Integer version, String path) {
        SharedPreferences sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE);
        Integer current = sp.getInt(CURRENT_VERSION, 0);
        if (version > current) {
            return sp.edit().putInt(CURRENT_VERSION, version)
                .putString(PATCH_FILE_PREF + version, path)
                .commit();
        } else {
            TinkerLog.w(TAG, "update failed, target version is not latest. current version is:" + version);
            return false;
        }
    }

    public static Integer getCurrentVersion(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE);
        return sp.getInt(CURRENT_VERSION, 0);
    }

    public static String getPatchFilePath(Context context, Integer version) {
        SharedPreferences sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE);
        return sp.getString(PATCH_FILE_PREF + version, "");
    }
}
