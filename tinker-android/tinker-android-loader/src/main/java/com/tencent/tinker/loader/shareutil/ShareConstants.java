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

import com.tencent.tinker.loader.BuildConfig;

import java.util.regex.Pattern;

/**
 * Created by zhangshaowen on 16/3/24.
 */
public class ShareConstants {
    public static final String TINKER_VERSION = BuildConfig.TINKER_VERSION;

    public static final int BUFFER_SIZE         = 16384;
    public static final int MD5_LENGTH          = 32;
    public static final int MD5_FILE_BUF_LENGTH = 1024 * 100;

    public static final int MAX_EXTRACT_ATTEMPTS = 2;

    public static final String TINKER_ID     = "TINKER_ID";
    public static final String NEW_TINKER_ID = "NEW_TINKER_ID";

    // Please keep it synchronized with the one defined in TypedValue.
    public static final String PKGMETA_KEY_IS_PROTECTED_APP = "is_protected_app";

    public static final String OLD_VERSION     = "old";
    public static final String NEW_VERSION     = "new";
    public static final String PATCH_BASE_NAME = "patch-";
    public static final String PATCH_SUFFIX    = ".apk";

    public static final String PACKAGE_META_FILE = "assets/package_meta.txt";

    public static final String SO_META_FILE = "assets/so_meta.txt";
    public static final String SO_PATH      = "lib";


    public static final String DEX_META_FILE               = "assets/dex_meta.txt";
    public static final String DEX_PATH                    = "dex";
    public static final String ARKHOTFIX_PATH = "arkHot";
    public static final String DEFAULT_DEX_OPTIMIZE_PATH   = "odex";
    public static final String ANDROID_O_DEX_OPTIMIZE_PATH = "oat";

    public static final String INTERPRET_DEX_OPTIMIZE_PATH = "interpet";
    public static final String CHANING_DEX_OPTIMIZE_PATH   = "changing";

    public static final String DEX_SUFFIX  = ".dex";
    public static final String JAR_SUFFIX  = ".jar";
    public static final String APK_SUFFIX  = ".apk";
    public static final String ODEX_SUFFIX = ".odex";

    public static final String TEST_DEX_NAME            = "test.dex";
    public static final String CLASS_N_APK_NAME         = "tinker_classN.apk";
    public static final String ARKHOT_PATCH_NAME = "patch.apk";

    public static final Pattern CLASS_N_PATTERN = Pattern.compile("classes(?:[2-9]?|[1-9][0-9]+)\\.dex(\\.jar)?");

    public static final String CHECK_DEX_INSTALL_FAIL = "checkDexInstall failed";
    public static final String CHECK_RES_INSTALL_FAIL = "checkResInstall failed";

    public static final String CHECK_DEX_OAT_EXIST_FAIL  = "checkDexOptExist failed";
    public static final String CHECK_DEX_OAT_FORMAT_FAIL = "checkDexOptFormat failed";

    // public static final String CHECK_VM_PROPERTY_FAIL = "checkVmArtProperty failed";


    public static final String RES_META_FILE       = "assets/res_meta.txt";
    public static final String RES_ARSC            = "resources.arsc";
    public static final String RES_MANIFEST        = "AndroidManifest.xml";
    public static final String RES_TITLE           = "resources_out.zip";
    public static final String RES_PATH            = "res";
    public static final String RES_NAME            = "resources.apk";
    public static final String RES_ADD_TITLE       = "add:";
    public static final String RES_MOD_TITLE       = "modify:";
    public static final String RES_LARGE_MOD_TITLE = "large modify:";
    public static final String RES_DEL_TITLE       = "delete:";
    public static final String RES_PATTERN_TITLE   = "pattern:";
    public static final String RES_STORE_TITLE     = "store:";

    public static final String ARKHOT_META_FILE = "assets/arkHot_meta.txt";

    public static final String DEXMODE_RAW = "raw";
    public static final String DEXMODE_JAR = "jar";
    public static final String DEX_IN_JAR  = "classes.dex";

    public static final String PATCH_DIRECTORY_NAME       = "tinker";
    public static final String PATCH_TEMP_DIRECTORY_NAME  = "tinker_temp";
    public static final String PATCH_TEMP_LAST_CRASH_NAME = "tinker_last_crash";

    public static final String PATCH_INFO_NAME      = "patch.info";
    public static final String PATCH_INFO_LOCK_NAME = "info.lock";

    public static final String META_SUFFIX = "meta.txt";

    /**
     * multi process share
     */
    public static final String TINKER_SHARE_PREFERENCE_CONFIG = "tinker_share_config";
    public static final String TINKER_ENABLE_CONFIG_PREFIX    = "tinker_enable_";

    /**
     * only for each process
     */
    public static final String TINKER_OWN_PREFERENCE_CONFIG_PREFIX = "tinker_own_config_";
    public static final String TINKER_SAFE_MODE_COUNT_PREFIX       = "safe_mode_count_";
    public static final int    TINKER_SAFE_MODE_MAX_COUNT          = 3;


    /**
     * notification id, use to Increasing the patch process priority
     * your app shouldn't use the same notification id.
     * if you want to define it, use {@code TinkerPatchService.setTinkerNotificationId}
     */
    public static final int TINKER_PATCH_SERVICE_NOTIFICATION = -1119860829;

    //resource type
    public static final int TYPE_PATCH_FILE = 1;
    public static final int TYPE_PATCH_INFO = 2;
    public static final int TYPE_DEX        = 3;

    public static final int TYPE_DEX_OPT     = 4;
    public static final int TYPE_LIBRARY     = 5;
    public static final int TYPE_RESOURCE    = 6;
    public static final int TYPE_CLASS_N_DEX = 7;
    public static final int TYPE_ARKHOT_SO = 8;


    public static final int TINKER_DISABLE             = 0x00;
    public static final int TINKER_DEX_MASK            = 0x01;
    public static final int TINKER_NATIVE_LIBRARY_MASK = 0x02;
    public static final int TINKER_RESOURCE_MASK       = 0x04;
    public static final int TINKER_ARKHOT_MASK = 0x08;
    public static final int TINKER_DEX_AND_LIBRARY     = TINKER_DEX_MASK | TINKER_NATIVE_LIBRARY_MASK | TINKER_ARKHOT_MASK;
    public static final int TINKER_ENABLE_ALL          = TINKER_DEX_MASK | TINKER_NATIVE_LIBRARY_MASK | TINKER_RESOURCE_MASK | TINKER_ARKHOT_MASK;

    //load error code
    public static final int ERROR_LOAD_OK                                      = 0;
    public static final int ERROR_LOAD_DISABLE                                 = -1;
    public static final int ERROR_LOAD_PATCH_DIRECTORY_NOT_EXIST               = -2;
    public static final int ERROR_LOAD_PATCH_INFO_NOT_EXIST                    = -3;
    public static final int ERROR_LOAD_PATCH_INFO_CORRUPTED                    = -4;
    public static final int ERROR_LOAD_PATCH_INFO_BLANK                        = -5;
    public static final int ERROR_LOAD_PATCH_VERSION_DIRECTORY_NOT_EXIST       = -6;
    public static final int ERROR_LOAD_PATCH_VERSION_FILE_NOT_EXIST            = -7;
    public static final int ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL                = -8;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_DIRECTORY_NOT_EXIST   = -9;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_FILE_NOT_EXIST        = -10;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_OPT_FILE_NOT_EXIST    = -11;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_CLASSLOADER_NULL      = -12;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH          = -13;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_LOAD_EXCEPTION        = -14;
    public static final int ERROR_LOAD_PATCH_GET_OTA_INSTRUCTION_SET_EXCEPTION = -15;
    public static final int ERROR_LOAD_PATCH_OTA_INTERPRET_ONLY_EXCEPTION      = -16;

    public static final int ERROR_LOAD_PATCH_VERSION_LIB_DIRECTORY_NOT_EXIST      = -17;
    public static final int ERROR_LOAD_PATCH_VERSION_LIB_FILE_NOT_EXIST           = -18;
    public static final int ERROR_LOAD_PATCH_REWRITE_PATCH_INFO_FAIL              = -19;
    public static final int ERROR_LOAD_PATCH_UNKNOWN_EXCEPTION                    = -20;
    //resource
    public static final int ERROR_LOAD_PATCH_VERSION_RESOURCE_DIRECTORY_NOT_EXIST = -21;
    public static final int ERROR_LOAD_PATCH_VERSION_RESOURCE_FILE_NOT_EXIST      = -22;
    public static final int ERROR_LOAD_PATCH_VERSION_RESOURCE_LOAD_EXCEPTION      = -23;
    public static final int ERROR_LOAD_PATCH_VERSION_RESOURCE_MD5_MISMATCH        = -24;
    public static final int ERROR_LOAD_PATCH_UNCAUGHT_EXCEPTION                   = -25;
    // -26 & -27 is used by WeChat internal logic.
    public static final int ERROR_LOAD_PATCH_BAIL_HACK_FAILURE                    = -28;

    public static final int ERROR_LOAD_GET_INTENT_FAIL = -10000;

    //load exception code
    //recover error code
    public static final int ERROR_LOAD_EXCEPTION_UNKNOWN  = -1;
    public static final int ERROR_LOAD_EXCEPTION_DEX      = -2;
    public static final int ERROR_LOAD_EXCEPTION_RESOURCE = -3;
    public static final int ERROR_LOAD_EXCEPTION_UNCAUGHT = -4;
    public static final int ERROR_LOAD_EXCEPTION_COMPONENT_HOTPLUG = -5;

    //patch listener error code
    public static final int ERROR_PATCH_OK                = 0;
    public static final int ERROR_PATCH_DISABLE           = -1;
    public static final int ERROR_PATCH_NOTEXIST          = -2;
    public static final int ERROR_PATCH_RUNNING           = -3;
    public static final int ERROR_PATCH_INSERVICE         = -4;
    public static final int ERROR_PATCH_JIT               = -5;
    public static final int ERROR_PATCH_ALREADY_APPLY     = -6;
    public static final int ERROR_PATCH_RETRY_COUNT_LIMIT = -7;


    //package check error code
    public static final int ERROR_PACKAGE_CHECK_OK                        = 0;
    public static final int ERROR_PACKAGE_CHECK_SIGNATURE_FAIL            = -1;
    public static final int ERROR_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND    = -2;
    public static final int ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED        = -3;
    public static final int ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED        = -4;
    public static final int ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND   = -5;
    public static final int ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND = -6;
    public static final int ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL       = -7;
    public static final int ERROR_PACKAGE_CHECK_RESOURCE_META_CORRUPTED   = -8;
    public static final int ERROR_PACKAGE_CHECK_TINKERFLAG_NOT_SUPPORT    = -9;

    // interpret error type
    public static final int TYPE_INTERPRET_OK                        = 0;
    public static final int TYPE_INTERPRET_GET_INSTRUCTION_SET_ERROR = 1;
    public static final int TYPE_INTERPRET_COMMAND_ERROR             = 2;


}
