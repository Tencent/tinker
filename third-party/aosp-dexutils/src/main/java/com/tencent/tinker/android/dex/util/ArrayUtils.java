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

package com.tencent.tinker.android.dex.util;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Utilities for array comparing.
 */
public class ArrayUtils {
    private ArrayUtils() {
    }

    public static int compareArray(byte[] a, byte[] b) {
        int aLen = a.length;
        int bLen = b.length;
        if (aLen != bLen) return aLen - bLen;
        for (int i = 0; i < aLen; ++i) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        return 0;
    }

    public static int compareArray(short[] a, short[] b) {
        int aLen = a.length;
        int bLen = b.length;
        if (aLen != bLen) return aLen - bLen;
        for (int i = 0; i < aLen; ++i) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        return 0;
    }

    public static int compareArray(int[] a, int[] b) {
        int aLen = a.length;
        int bLen = b.length;
        if (aLen != bLen) return aLen - bLen;
        for (int i = 0; i < aLen; ++i) {
            if (a[i] != b[i]) {
                return a[i] - b[i];
            }
        }
        return 0;
    }

    public static <T extends Comparable<T>> int compareArray(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;
        if (aLen != bLen) return aLen - bLen;
        for (int i = 0; i < aLen; ++i) {
            int cmpRes = a[i].compareTo(b[i]);
            if (cmpRes != 0) {
                return cmpRes;
            }
        }
        return 0;
    }
}
