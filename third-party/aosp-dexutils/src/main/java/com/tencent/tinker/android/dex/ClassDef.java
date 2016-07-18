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

/**
 * A type definition.
 */
public final class ClassDef extends SectionItem<ClassDef> {
    public static final int NO_INDEX = -1;
    public final  int offset;
    private final Dex buffer;
    public        int typeIndex;
    public        int accessFlags;
    public        int supertypeIndex;
    public        int interfacesOffset;
    public        int sourceFileIndex;
    public        int annotationsOffset;
    public        int classDataOffset;
    public        int staticValuesOffset;

    public ClassDef(Section owner, int offset, int typeIndex, int accessFlags,
                    int supertypeIndex, int interfacesOffset, int sourceFileIndex,
                    int annotationsOffset, int classDataOffset, int staticValuesOffset) {
        super(owner, offset);
        this.buffer = (owner != null ? owner.owner : null);
        this.offset = offset;
        this.typeIndex = typeIndex;
        this.accessFlags = accessFlags;
        this.supertypeIndex = supertypeIndex;
        this.interfacesOffset = interfacesOffset;
        this.sourceFileIndex = sourceFileIndex;
        this.annotationsOffset = annotationsOffset;
        this.classDataOffset = classDataOffset;
        this.staticValuesOffset = staticValuesOffset;
    }

    public short[] getInterfaces() {
        return buffer.readTypeList(interfacesOffset).types;
    }

    @Override
    public ClassDef clone(Section newOwner, int newOffset) {
        return new ClassDef(newOwner, newOffset, typeIndex, accessFlags, supertypeIndex, interfacesOffset,
            sourceFileIndex, annotationsOffset, classDataOffset, staticValuesOffset);
    }

    @Override
    public String toString() {
        if (buffer == null) {
            return typeIndex + " " + supertypeIndex;
        }

        StringBuilder result = new StringBuilder();
        result.append(buffer.typeNames().get(typeIndex));
        if (supertypeIndex != NO_INDEX) {
            result.append(" extends ").append(buffer.typeNames().get(supertypeIndex));
        }
        return result.toString();
    }

    @Override
    public int compareTo(ClassDef o) {
        if (typeIndex != o.typeIndex) {
            return typeIndex - o.typeIndex;
        }
        if (accessFlags != o.accessFlags) {
            return accessFlags - o.accessFlags;
        }
        if (supertypeIndex != o.supertypeIndex) {
            return supertypeIndex - o.supertypeIndex;
        }
        if (interfacesOffset != o.interfacesOffset) {
            return interfacesOffset - o.interfacesOffset;
        }
        if (sourceFileIndex != o.sourceFileIndex) {
            return sourceFileIndex - o.sourceFileIndex;
        }
        if (annotationsOffset != o.annotationsOffset) {
            return annotationsOffset - o.annotationsOffset;
        }
        if (classDataOffset != o.classDataOffset) {
            return classDataOffset - o.classDataOffset;
        }
        if (staticValuesOffset != o.staticValuesOffset) {
            return staticValuesOffset - o.staticValuesOffset;
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((ClassDef) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.CLASS_DEF_ITEM;
    }
}
