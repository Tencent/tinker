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
import com.tencent.tinker.android.dex.util.HashCodeHelper;

public final class FieldId extends Item<FieldId> {

    public int declaringClassIndex;

    public int typeIndex;

    public int nameIndex;

    public FieldId(int off, int declaringClassIndex, int typeIndex, int nameIndex) {
        super(off);
        this.declaringClassIndex = declaringClassIndex;
        this.typeIndex = typeIndex;
        this.nameIndex = nameIndex;
    }

    public int compareTo(FieldId other) {
        if (declaringClassIndex != other.declaringClassIndex) {
            return CompareUtils.uCompare(declaringClassIndex, other.declaringClassIndex);
        }
        if (nameIndex != other.nameIndex) {
            return CompareUtils.uCompare(nameIndex, other.nameIndex);
        }
        // should always be 0
        return CompareUtils.uCompare(typeIndex, other.typeIndex);
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(declaringClassIndex, typeIndex, nameIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FieldId)) {
            return false;
        }
        return this.compareTo((FieldId) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.MEMBER_ID_ITEM;
    }
}
