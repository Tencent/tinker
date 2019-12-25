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

package com.tencent.tinker.loader.shareutil;

import android.content.Intent;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by zhangshaowen on 16/3/18.
 */
public class ShareIntentUtil {
    //intent
    public static final  String INTENT_RETURN_CODE               = "intent_return_code";
    public static final  String INTENT_PATCH_OLD_VERSION         = "intent_patch_old_version";
    public static final  String INTENT_PATCH_NEW_VERSION         = "intent_patch_new_version";
    public static final  String INTENT_IS_PROTECTED_APP          = "intent_is_protected_app";
    public static final  String INTENT_PATCH_MISMATCH_DEX_PATH   = "intent_patch_mismatch_dex_path";
    public static final  String INTENT_PATCH_MISSING_DEX_PATH    = "intent_patch_missing_dex_path";
    public static final  String INTENT_PATCH_DEXES_PATH          = "intent_patch_dexes_path";
    public static final  String INTENT_PATCH_MISMATCH_LIB_PATH   = "intent_patch_mismatch_lib_path";
    public static final  String INTENT_PATCH_MISSING_LIB_PATH    = "intent_patch_missing_lib_path";
    public static final  String INTENT_PATCH_LIBS_PATH           = "intent_patch_libs_path";
    public static final  String INTENT_PATCH_COST_TIME           = "intent_patch_cost_time";
    public static final  String INTENT_PATCH_EXCEPTION           = "intent_patch_exception";
    public static final  String INTENT_PATCH_PACKAGE_PATCH_CHECK = "intent_patch_package_patch_check";
    public static final  String INTENT_PATCH_PACKAGE_CONFIG      = "intent_patch_package_config";
    public static final  String INTENT_PATCH_SYSTEM_OTA          = "intent_patch_system_ota";
    public static final  String INTENT_PATCH_OAT_DIR             = "intent_patch_oat_dir";
    public static final  String INTENT_PATCH_INTERPRET_EXCEPTION = "intent_patch_interpret_exception";


    private static final String TAG                              = "ShareIntentUtil";

    public static void setIntentReturnCode(Intent intent, int code) {
        intent.putExtra(INTENT_RETURN_CODE, code);
    }

    public static int getIntentReturnCode(Intent intent) {
        return getIntExtra(intent, INTENT_RETURN_CODE, ShareConstants.ERROR_LOAD_GET_INTENT_FAIL);
    }

    public static void setIntentPatchCostTime(Intent intent, long cost) {
        intent.putExtra(INTENT_PATCH_COST_TIME, cost);
    }

    public static long getIntentPatchCostTime(Intent intent) {
        return intent.getLongExtra(INTENT_PATCH_COST_TIME, 0);
    }

    public static Throwable getIntentPatchException(Intent intent) {
        Serializable serializable = getSerializableExtra(intent, INTENT_PATCH_EXCEPTION);
        if (serializable != null) {
            return (Throwable) serializable;
        }
        return null;
    }

    public static Throwable getIntentInterpretException(Intent intent) {
        Serializable serializable = getSerializableExtra(intent, INTENT_PATCH_INTERPRET_EXCEPTION);
        if (serializable != null) {
            return (Throwable) serializable;
        }
        return null;
    }

    public static HashMap<String, String> getIntentPatchDexPaths(Intent intent) {
        Serializable serializable = getSerializableExtra(intent, INTENT_PATCH_DEXES_PATH);
        if (serializable != null) {
            return (HashMap<String, String>) serializable;
        }
        return null;
    }

    public static HashMap<String, String> getIntentPatchLibsPaths(Intent intent) {
        Serializable serializable = getSerializableExtra(intent, INTENT_PATCH_LIBS_PATH);
        if (serializable != null) {
            return (HashMap<String, String>) serializable;
        }
        return null;
    }

    public static HashMap<String, String> getIntentPackageConfig(Intent intent) {
        Serializable serializable = getSerializableExtra(intent, INTENT_PATCH_PACKAGE_CONFIG);
        if (serializable != null) {
            return (HashMap<String, String>) serializable;
        }
        return null;
    }


    public static ArrayList<String> getStringArrayListExtra(Intent intent, String name) {
        if (null == intent) {
            return null;
        }
        ArrayList<String> ret = null;
        try {
            ret = intent.getStringArrayListExtra(name);
        } catch (Exception e) {
            Log.e(TAG, "getStringExtra exception:" + e.getMessage());
            ret = null;
        }
        return ret;
    }


    public static String getStringExtra(Intent intent, String name) {
        if (null == intent) {
            return null;
        }
        String ret = null;
        try {
            ret = intent.getStringExtra(name);
        } catch (Exception e) {
            Log.e(TAG, "getStringExtra exception:" + e.getMessage());
            ret = null;
        }
        return ret;
    }

    public static Serializable getSerializableExtra(Intent intent, String name) {
        if (null == intent) {
            return null;
        }
        Serializable ret = null;
        try {
            ret = intent.getSerializableExtra(name);
        } catch (Exception e) {
            Log.e(TAG, "getSerializableExtra exception:" + e.getMessage());
            ret = null;
        }
        return ret;
    }

    public static int getIntExtra(Intent intent, String name, int defaultValue) {
        if (null == intent) {
            return defaultValue;
        }
        int ret = defaultValue;
        try {
            ret = intent.getIntExtra(name, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "getIntExtra exception:" + e.getMessage());
            ret = defaultValue;
        }
        return ret;
    }


    public static boolean getBooleanExtra(Intent intent, String name, boolean defaultValue) {
        if (null == intent) {
            return defaultValue;
        }
        boolean ret = defaultValue;
        try {
            ret = intent.getBooleanExtra(name, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "getBooleanExtra exception:" + e.getMessage());
            ret = defaultValue;
        }
        return ret;
    }

    public static long getLongExtra(Intent intent, String name, long defaultValue) {
        if (null == intent) {
            return defaultValue;
        }
        long ret = defaultValue;
        try {
            ret = intent.getLongExtra(name, defaultValue);
        } catch (Exception e) {
            Log.e(TAG, "getIntExtra exception:" + e.getMessage());
            ret = defaultValue;
        }
        return ret;
    }

    public static void fixIntentClassLoader(Intent intent, ClassLoader cl) {
        try {
            intent.setExtrasClassLoader(cl);
        } catch (Throwable thr) {
            thr.printStackTrace();
        }
    }
}
