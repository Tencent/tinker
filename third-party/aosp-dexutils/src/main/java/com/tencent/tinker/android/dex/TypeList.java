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

import com.tencent.tinker.android.dex.TableOfContents.Section.Item;
import com.tencent.tinker.android.dex.util.CompareUtils;

import java.util.Arrays;

public final class TypeList extends Item<TypeList> {
    public static final TypeList EMPTY = new TypeList(0, Dex.EMPTY_SHORT_ARRAY);

    public short[] types;

    public TypeList(int off, short[] types) {
        super(off);
        this.types = types;
    }

    @Override public int compareTo(TypeList other) {
        return CompareUtils.uArrCompare(types, other.types);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(types);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TypeList)) {
            return false;
        }
        return this.compareTo((TypeList) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UINT + types.length * SizeOf.USHORT;
    }
}
