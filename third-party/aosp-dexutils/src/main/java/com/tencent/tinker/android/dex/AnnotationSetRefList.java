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
 * Structure of AnnotationSetRefList element in Dex file.
 */
public class AnnotationSetRefList extends Section.Item<AnnotationSetRefList> {
    public int[] annotationSetRefItems;

    public AnnotationSetRefList(int off, int[] annotationSetRefItems) {
        super(off);
        this.annotationSetRefItems = annotationSetRefItems;
    }

    @Override
    public int compareTo(AnnotationSetRefList other) {
        int size = annotationSetRefItems.length;
        int oSize = other.annotationSetRefItems.length;

        if (size != oSize) {
            return CompareUtils.uCompare(size, oSize);
        }

        for (int i = 0; i < size; ++i) {
            if (annotationSetRefItems[i] != other.annotationSetRefItems[i]) {
                return CompareUtils.uCompare(annotationSetRefItems[i], other.annotationSetRefItems[i]);
            }
        }

        return 0;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(annotationSetRefItems);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnnotationSetRefList)) {
            return false;
        }
        return this.compareTo((AnnotationSetRefList) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UINT * (1 + annotationSetRefItems.length);
    }
}
