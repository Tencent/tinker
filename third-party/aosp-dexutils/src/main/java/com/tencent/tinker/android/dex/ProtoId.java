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
public final class ProtoId extends SectionItem<ProtoId> {
    private final Dex dex;
    public        int shortyIndex;
    public        int returnTypeIndex;
    public        int parametersOffset;

    public ProtoId(Section owner, int offset, int shortyIndex, int returnTypeIndex, int parametersOffset) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.shortyIndex = shortyIndex;
        this.returnTypeIndex = returnTypeIndex;
        this.parametersOffset = parametersOffset;
    }

    @Override
    public ProtoId clone(Section newOwner, int newOffset) {
        return new ProtoId(newOwner, newOffset, shortyIndex, returnTypeIndex, parametersOffset);
    }

    public int compareTo(ProtoId other) {
        if (shortyIndex != other.shortyIndex) {
            return Unsigned.compare(shortyIndex, other.shortyIndex);
        }
        if (returnTypeIndex != other.returnTypeIndex) {
            return Unsigned.compare(returnTypeIndex, other.returnTypeIndex);
        }
        return Unsigned.compare(parametersOffset, other.parametersOffset);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((ProtoId) obj) == 0;
    }

    @Override
    public String toString() {
        if (dex == null) {
            return shortyIndex + " " + returnTypeIndex + " " + parametersOffset;
        }

        return dex.strings().get(shortyIndex)
            + ": " + dex.typeNames().get(returnTypeIndex)
            + " " + dex.readTypeList(parametersOffset);
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.PROTO_ID_ITEM;
    }
}
