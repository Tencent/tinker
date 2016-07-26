/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
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

package com.tencent.tinker.loader.shareutil;

/**
 * Created by shwenzhang on 16/3/24.
 */
public class ShareConstants {
    public static final int BUFFER_SIZE         = 16384;
    public static final int MD5_LENGTH          = 32;
    public static final int MD5_FILE_BUF_LENGTH = 1024 * 100;

    public static final int MAX_EXTRACT_ATTEMPTS = 2;

    public static final String TINKER_ID     = "TINKER_ID";
    public static final String NEW_TINKER_ID = "NEW_TINKER_ID";

    public static final String OLD_VERSION     = "old";
    public static final String NEW_VERSION     = "new";
    public static final String PATCH_BASE_NAME = "patch-";
    public static final String PATCH_SUFFIX    = ".apk";

    public static final String PACKAGE_META_FILE = "assets/package_meta.txt";

    public static final String SO_META_FILE = "assets/so_meta.txt";
    public static final String SO_PATH      = "lib";

    public static final String DEX_META_FILE     = "assets/dex_meta.txt";
    public static final String DEX_PATH          = "dex";
    public static final String DEX_OPTIMIZE_PATH = "odex";
    public static final String DEX_SUFFIX        = ".dex";
    public static final String JAR_SUFFIX        = ".jar";

    public static final String DEXMODE_RAW = "raw";
    public static final String DEXMODE_JAR = "jar";
    public static final String DEX_IN_JAR  = "classes.dex";

    public static final String PATCH_DIRECTORY_NAME = "tinker";
    public static final String PATCH_INFO_NAME      = "patch.info";
    public static final String PATCH_INFO_LOCK_NAME = "info.lock";

    public static final String META_SUFFIX = "meta.txt";

    public static final String TINKER_PREFERENCE_CONFIG = "tinker_config";
    public static final String TINKER_ENABLE_CONFIG     = "tinker_enable";

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
    public static final int TYPE_DEX_OPT    = 4;
    public static final int TYPE_LIBRARY    = 5;


    public static final int TINKER_DISABLE             = 0x00;
    public static final int TINKER_DEX_MASK            = 0x01;
    public static final int TINKER_NATIVE_LIBRARY_MASK = 0x02;
    public static final int TINKER_ENABLE_ALL          = TINKER_DEX_MASK | TINKER_NATIVE_LIBRARY_MASK;

    //load error code
    public static final int ERROR_LOAD_OK                        = 0;
    public static final int ERROR_LOAD_DISABLE                   = -1;
    public static final int ERROR_LOAD_PATCH_DIRECTORY_NOT_EXIST = -2;
    public static final int ERROR_LOAD_PATCH_INFO_NOT_EXIST      = -3;
    public static final int ERROR_LOAD_PATCH_INFO_CORRUPTED      = -4;

    public static final int ERROR_LOAD_PATCH_INFO_BLANK                  = -5;
    public static final int ERROR_LOAD_PATCH_VERSION_DIRECTORY_NOT_EXIST = -6;
    public static final int ERROR_LOAD_PATCH_VERSION_FILE_NOT_EXIST      = -7;
    public static final int ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL          = -9;

    public static final int ERROR_LOAD_PATCH_VERSION_DEX_DIRECTORY_NOT_EXIST = -10;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_FILE_NOT_EXIST      = -11;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_OPT_FILE_NOT_EXIST  = -12;

    public static final int ERROR_LOAD_PATCH_VERSION_DEX_CLASSLOADER_NULL    = -13;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH        = -14;
    public static final int ERROR_LOAD_PATCH_VERSION_DEX_LOAD_FAIL           = -15;
    public static final int ERROR_LOAD_PATCH_VERSION_LIB_DIRECTORY_NOT_EXIST = -16;
    public static final int ERROR_LOAD_PATCH_VERSION_LIB_FILE_NOT_EXIST      = -17;
    public static final int ERROR_LOAD_PATCH_REWRITE_PATCH_INFO_FAIL         = -18;
    public static final int ERROR_LOAD_PATCH_EXCEPTION                       = -19;
    public static final int ERROR_LOAD_PATCH_NOT_SUPPORTED                   = -20;

    public static final int ERROR_LOAD_GET_INTENT_FAIL                       = -10000;

    //recover error code
    public static final int ERROR_PATCH_OK        = 0;
    public static final int ERROR_PATCH_DISABLE   = -1;
    public static final int ERROR_PATCH_NOTEXIST  = -2;
    public static final int ERROR_PATCH_RUNNING   = -3;
    public static final int ERROR_PATCH_INSERVICE = -4;

    //package check error code
    public static final int ERROR_PACKAGE_CHECK_OK                        = 0;
    public static final int ERROR_PACKAGE_CHECK_SIGNATURE_FAIL            = -1;
    public static final int ERROR_PACKAGE_CHECK_PACKAGE_META_NOT_FOUND    = -2;
    public static final int ERROR_PACKAGE_CHECK_DEX_META_CORRUPTED        = -3;
    public static final int ERROR_PACKAGE_CHECK_LIB_META_CORRUPTED        = -4;
    public static final int ERROR_PACKAGE_CHECK_APK_TINKER_ID_NOT_FOUND   = -5;
    public static final int ERROR_PACKAGE_CHECK_PATCH_TINKER_ID_NOT_FOUND = -6;
    public static final int ERROR_PACKAGE_CHECK_TINKER_ID_NOT_EQUAL       = -7;


}
