/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.tencent.tinker.android.dex;

/**
 * Constants that show up in and are otherwise related to {@code .dex}
 * files, and helper methods for same.
 */
public final class DexFormat {
    /**
     * API level to target in order to produce the most modern file
     * format
     */
    public static final int API_CURRENT = 14;
    /** API level to target in order to suppress extended opcode usage */
    public static final int API_NO_EXTENDED_OPCODES = 13;
    /**
     * file name of the primary {@code .dex} file inside an
     * application or library {@code .jar} file
     */
    public static final String DEX_IN_JAR_NAME = "classes.dex";
    /** common prefix for all dex file "magic numbers" */
    public static final String MAGIC_PREFIX = "dex\n";
    /** common suffix for all dex file "magic numbers" */
    public static final String MAGIC_SUFFIX = "\0";
    /** dex file version number for the current format variant */
    public static final String VERSION_CURRENT = "036";
    /** dex file version number for API level 13 and earlier */
    public static final String VERSION_FOR_API_13 = "035";
    /**
     * value used to indicate endianness of file contents
     */
    public static final int ENDIAN_TAG = 0x12345678;
    /**
     * Maximum addressable field or method index.
     * The largest addressable member is 0xffff, in the "instruction formats" spec as field@CCCC or
     * meth@CCCC.
     */
    public static final int MAX_MEMBER_IDX = 0xFFFF;
    /**
     * Maximum addressable type index.
     * The largest addressable type is 0xffff, in the "instruction formats" spec as type@CCCC.
     */
    public static final int MAX_TYPE_IDX = 0xFFFF;

    private DexFormat() { }

    /**
     * Returns the API level corresponding to the given magic number,
     * or {@code -1} if the given array is not a well-formed dex file
     * magic number.
     */
    public static int magicToApi(byte[] magic) {
        if (magic.length != 8) {
            return -1;
        }

        if ((magic[0] != 'd') || (magic[1] != 'e') || (magic[2] != 'x') || (magic[3] != '\n')
                || (magic[7] != '\0')) {
            return -1;
        }

        String version = "" + ((char) magic[4]) + ((char) magic[5]) + ((char) magic[6]);

        if (version.equals(VERSION_CURRENT)) {
            return API_CURRENT;
        } else if (version.equals(VERSION_FOR_API_13)) {
            return 13;
        }

        return -1;
    }

    /**
     * Returns the magic number corresponding to the given target API level.
     */
    public static String apiToMagic(int targetApiLevel) {
        String version;

        if (targetApiLevel >= API_CURRENT) {
            version = VERSION_CURRENT;
        } else {
            version = VERSION_FOR_API_13;
        }

        return MAGIC_PREFIX + version + MAGIC_SUFFIX;
    }
}
