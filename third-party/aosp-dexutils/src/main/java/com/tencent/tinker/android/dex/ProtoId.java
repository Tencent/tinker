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

import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dex.util.HashCodeHelper;

public final class ProtoId extends TableOfContents.Section.Item<ProtoId> {
    public int shortyIndex;
    public int returnTypeIndex;
    public int parametersOffset;

    public ProtoId(int off, int shortyIndex, int returnTypeIndex, int parametersOffset) {
        super(off);
        this.shortyIndex = shortyIndex;
        this.returnTypeIndex = returnTypeIndex;
        this.parametersOffset = parametersOffset;
    }

    public int compareTo(ProtoId other) {
        int res = CompareUtils.uCompare(shortyIndex, other.shortyIndex);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.uCompare(returnTypeIndex, other.returnTypeIndex);
        if (res != 0) {
            return res;
        }
        return CompareUtils.sCompare(parametersOffset, other.parametersOffset);
    }


    @Override
    public int hashCode() {
        return HashCodeHelper.hash(shortyIndex, returnTypeIndex, parametersOffset);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProtoId)) {
            return false;
        }
        return this.compareTo((ProtoId) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.PROTO_ID_ITEM;
    }
}
