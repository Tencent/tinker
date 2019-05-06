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

/**
 * A type definition.
 */
public final class ClassDef extends TableOfContents.Section.Item<ClassDef> {
    public static final int NO_INDEX = -1;
    public static final int NO_OFFSET = 0;

    public int typeIndex;
    public int accessFlags;
    public int supertypeIndex;
    public int interfacesOffset;
    public int sourceFileIndex;
    public int annotationsOffset;
    public int classDataOffset;
    public int staticValuesOffset;

    public ClassDef(int off, int typeIndex, int accessFlags,
            int supertypeIndex, int interfacesOffset, int sourceFileIndex,
            int annotationsOffset, int classDataOffset, int staticValuesOffset) {
        super(off);
        this.typeIndex = typeIndex;
        this.accessFlags = accessFlags;
        this.supertypeIndex = supertypeIndex;
        this.interfacesOffset = interfacesOffset;
        this.sourceFileIndex = sourceFileIndex;
        this.annotationsOffset = annotationsOffset;
        this.classDataOffset = classDataOffset;
        this.staticValuesOffset = staticValuesOffset;
    }

    @Override
    public int compareTo(ClassDef other) {
        int res = CompareUtils.uCompare(typeIndex, other.typeIndex);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(accessFlags, other.accessFlags);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.uCompare(supertypeIndex, other.supertypeIndex);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(interfacesOffset, other.interfacesOffset);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.uCompare(sourceFileIndex, other.sourceFileIndex);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(annotationsOffset, other.annotationsOffset);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(classDataOffset, other.classDataOffset);
        if (res != 0) {
            return res;
        }
        return CompareUtils.sCompare(staticValuesOffset, other.staticValuesOffset);
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(typeIndex, accessFlags, supertypeIndex, interfacesOffset,
                sourceFileIndex, annotationsOffset, classDataOffset, staticValuesOffset);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ClassDef)) {
            return false;
        }
        return this.compareTo((ClassDef) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.CLASS_DEF_ITEM;
    }
}
