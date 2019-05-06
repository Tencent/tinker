package com.tencent.tinker.android.dex.util;

import java.lang.reflect.Array;
import java.util.Arrays;

public final class HashCodeHelper {
    public static int hash(Object... values) {
        if (values == null || values.length == 0) {
            return 0;
        }
        int result = 0;
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            if (v instanceof Number) {
                result += v.hashCode();
            } else if (v instanceof boolean[]) {
                result += Arrays.hashCode((boolean[]) v);
            } else if (v instanceof byte[]) {
                result += Arrays.hashCode((byte[]) v);
            } else if (v instanceof char[]) {
                result += Arrays.hashCode((char[]) v);
            } else if (v instanceof short[]) {
                result += Arrays.hashCode((short[]) v);
            } else if (v instanceof int[]) {
                result += Arrays.hashCode((int[]) v);
            } else if (v instanceof long[]) {
                result += Arrays.hashCode((long[]) v);
            } else if (v instanceof float[]) {
                result += Arrays.hashCode((float[]) v);
            } else if (v instanceof double[]) {
                result += Arrays.hashCode((double[]) v);
            } else if (v instanceof Object[]) {
                result += Arrays.hashCode((Object[]) v);
            } else if (v.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(v); ++i) {
                    result += hash(Array.get(v, i));
                }
            } else {
                result += v.hashCode();
            }
        }
        return result;
    }

    private HashCodeHelper() {
        throw new UnsupportedOperationException();
    }
}
