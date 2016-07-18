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
 * An annotation.
 *
 * Modifications by tomystang:
 * Make this class derived from {@code SectionItem} so that
 * we can trace dex section this element belongs to easily.
 */
public final class Annotation extends SectionItem<Annotation> {
    private final Dex          dex;
    public        byte         visibility;
    public        EncodedValue encodedAnnotation;

    public Annotation(Section owner, int offset, byte visibility, EncodedValue encodedAnnotation) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.visibility = visibility;
        this.encodedAnnotation = encodedAnnotation;
    }

    public EncodedValueReader getReader() {
        return new EncodedValueReader(encodedAnnotation, EncodedValueReader.ENCODED_ANNOTATION);
    }

    public int getTypeIndex() {
        EncodedValueReader reader = getReader();
        reader.readAnnotation();
        return reader.getAnnotationType();
    }

    @Override
    public Annotation clone(Section newOwner, int newOffset) {
        return new Annotation(newOwner, newOffset, visibility, encodedAnnotation);
    }

    @Override
    public int compareTo(Annotation other) {
        return encodedAnnotation.compareTo(other.encodedAnnotation);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((Annotation) obj) == 0;
    }

    @Override
    public String toString() {
        return dex == null
            ? visibility + " " + getTypeIndex()
            : visibility + " " + dex.typeNames().get(getTypeIndex());
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.UBYTE + encodedAnnotation.getByteCountInDex();
    }
}
