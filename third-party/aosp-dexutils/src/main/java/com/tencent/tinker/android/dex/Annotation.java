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

import static com.tencent.tinker.android.dex.EncodedValueReader.ENCODED_ANNOTATION;

/**
 * An annotation.
 */
public final class Annotation extends Item<Annotation> {
    public byte visibility;
    public EncodedValue encodedAnnotation;

    public Annotation(int off, byte visibility, EncodedValue encodedAnnotation) {
        super(off);
        this.visibility = visibility;
        this.encodedAnnotation = encodedAnnotation;
    }

    public EncodedValueReader getReader() {
        return new EncodedValueReader(encodedAnnotation, ENCODED_ANNOTATION);
    }

    public int getTypeIndex() {
        EncodedValueReader reader = getReader();
        reader.readAnnotation();
        return reader.getAnnotationType();
    }

    @Override public int compareTo(Annotation other) {
        int cmpRes = encodedAnnotation.compareTo(other.encodedAnnotation);
        if (cmpRes != 0) return cmpRes;
        return CompareUtils.uCompare(visibility, other.visibility);
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(visibility, encodedAnnotation);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Annotation)) {
            return false;
        }
        return this.compareTo((Annotation) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UBYTE + encodedAnnotation.byteCountInDex();
    }
}
