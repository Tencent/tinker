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

package com.tencent.tinker.build.util;

/**
 * Container for a dynamically typed data value. Primarily used with
 */
public class TypedValue {
    public static final int BUFFER_SIZE = 8192;

    public static final String FILE_TXT      = ".txt";
    public static final String FILE_XML      = ".xml";
    public static final String FILE_APK      = ".apk";
    public static final String FILE_CONFIG   = "config.xml";
    public static final String FILE_LOG      = "log.txt";
    public static final String SO_LOG_FILE   = "so_log.txt";
    public static final String SO_META_FILE  = "so_meta.txt";
    public static final String DEX_LOG_FILE  = "dex_log.txt";
    public static final String DEX_META_FILE = "dex_meta.txt";

    public static final String TINKER_ID     = "TINKER_ID";
    public static final String NEW_TINKER_ID = "NEW_TINKER_ID";

    public static final String PACKAGE_META_FILE = "package_meta.txt";

    public static final String PATH_DEFAULT_OUTPUT = "tinkerPatch";

    public static final String PATH_PATCH_FILES   = "tinker_result";
    public static final String OUT_7ZIP_FILE_PATH = "out_7zip";

    public static final int ANDROID_40_API_LEVEL = 14;

    public static final double DEX_PATCH_MAX_RATIO = 0.6;

    public static final double DEX_JAR_PATCH_MAX_RATIO = 1.0;

    public static final double BSDIFF_PATCH_MAX_RATIO = 0.8;


}
