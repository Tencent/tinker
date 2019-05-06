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
import com.tencent.tinker.android.dex.util.CompareUtils;

import java.util.Arrays;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Structure of AnnotationSet element in Dex file.
 */
public class AnnotationSet extends Section.Item<AnnotationSet> {
    public int[] annotationOffsets;

    public AnnotationSet(int off, int[] annotationOffsets) {
        super(off);
        this.annotationOffsets = annotationOffsets;
    }

    @Override
    public int compareTo(AnnotationSet other) {
        int size = annotationOffsets.length;
        int oSize = other.annotationOffsets.length;

        if (size != oSize) {
            return CompareUtils.uCompare(size, oSize);
        }

        for (int i = 0; i < size; ++i) {
            if (annotationOffsets[i] != other.annotationOffsets[i]) {
                return CompareUtils.uCompare(annotationOffsets[i], other.annotationOffsets[i]);
            }
        }

        return 0;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(annotationOffsets);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnnotationSet)) {
            return false;
        }
        return this.compareTo((AnnotationSet) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UINT * (1 + annotationOffsets.length);
    }
}
