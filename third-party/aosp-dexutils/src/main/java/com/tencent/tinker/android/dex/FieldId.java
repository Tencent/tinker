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
public final class FieldId extends SectionItem<FieldId> {
    private final Dex dex;
    public        int declaringClassIndex;
    public        int typeIndex;
    public        int nameIndex;

    public FieldId(Section owner, int offset, int declaringClassIndex, int typeIndex, int nameIndex) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.declaringClassIndex = declaringClassIndex;
        this.typeIndex = typeIndex;
        this.nameIndex = nameIndex;
    }

    @Override
    public FieldId clone(Section newOwner, int newOffset) {
        return new FieldId(newOwner, newOffset, declaringClassIndex, typeIndex, nameIndex);
    }

    public int compareTo(FieldId other) {
        if (declaringClassIndex != other.declaringClassIndex) {
            return Unsigned.compare(declaringClassIndex, other.declaringClassIndex);
        }
        if (nameIndex != other.nameIndex) {
            return Unsigned.compare(nameIndex, other.nameIndex);
        }
        return Unsigned.compare(typeIndex, other.typeIndex); // should always be 0
    }

    @Override
    public int hashCode() {
        return (declaringClassIndex << 28) | (typeIndex << 20) | (nameIndex << 16);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((FieldId) obj) == 0;
    }

    @Override
    public String toString() {
        if (dex == null) {
            return "type_index:" + typeIndex + " declaring_class_index:" + declaringClassIndex + "->name_index:" + nameIndex;
        }
        return dex.typeNames().get(typeIndex) + " " + dex.typeNames().get(declaringClassIndex) + "->" + dex.strings().get(nameIndex);
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.MEMBER_ID_ITEM;
    }
}
