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

import com.tencent.tinker.android.dex.TableOfContents.Section;
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.android.dex.util.Unsigned;

/**
 * Modifications by tomystang:
 * Make this class derived from {@code SectionItem} so that
 * we can trace dex section this element belongs to easily.
 */
public final class TypeList extends SectionItem<TypeList> {

    public static final TypeList EMPTY = new TypeList(null, 0, Dex.EMPTY_SHORT_ARRAY);

    private final Dex     dex;
    public        short[] types;

    public TypeList(Section owner, int offset, short[] types) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.types = types;
    }

    @Override
    public TypeList clone(Section newOwner, int newOffset) {
        return new TypeList(newOwner, newOffset, types);
    }

    @Override
    public int compareTo(TypeList other) {
        for (int i = 0; i < types.length && i < other.types.length; i++) {
            if (types[i] != other.types[i]) {
                return Unsigned.compare(types[i], other.types[i]);
            }
        }
        return Unsigned.compare(types.length, other.types.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((TypeList) obj) == 0;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("(");
        for (int i = 0, typesLength = types.length; i < typesLength; i++) {
            result.append(dex != null ? dex.typeNames().get(types[i]) : types[i]);
        }
        result.append(")");
        return result.toString();
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.TYPE_ITEM;
    }
}
