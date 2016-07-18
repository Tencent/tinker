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
public final class MethodId extends SectionItem<MethodId> {
    private final Dex dex;
    public        int declaringClassIndex;
    public        int protoIndex;
    public        int nameIndex;

    public MethodId(Section owner, int offset, int declaringClassIndex, int protoIndex, int nameIndex) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.declaringClassIndex = declaringClassIndex;
        this.protoIndex = protoIndex;
        this.nameIndex = nameIndex;
    }

    @Override
    public MethodId clone(Section newOwner, int newOffset) {
        return new MethodId(newOwner, newOffset, declaringClassIndex, protoIndex, nameIndex);
    }

    public int compareTo(MethodId other) {
        if (declaringClassIndex != other.declaringClassIndex) {
            return Unsigned.compare(declaringClassIndex, other.declaringClassIndex);
        }
        if (nameIndex != other.nameIndex) {
            return Unsigned.compare(nameIndex, other.nameIndex);
        }
        return Unsigned.compare(protoIndex, other.protoIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((MethodId) obj) == 0;
    }

    @Override
    public String toString() {
        if (dex == null) {
            return "declaring_class_index:" + declaringClassIndex + "->name_index:" + nameIndex + "(proto_index:" + protoIndex + ")";
        }

        ProtoId protoId = dex.protoIds().get(protoIndex);
        return dex.typeNames().get(protoId.returnTypeIndex)
            + " "
            + dex.typeNames().get(declaringClassIndex)
            + "->"
            + dex.strings().get(nameIndex)
            + dex.readTypeList(protoId.parametersOffset);
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.MEMBER_ID_ITEM;
    }
}
