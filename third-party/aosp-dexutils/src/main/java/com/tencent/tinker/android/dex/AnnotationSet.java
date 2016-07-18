/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
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
 * *** This file is NOT a part of AOSP. ***
 *
 * Structure of AnnotationSet element in Dex file.
 */
public class AnnotationSet extends SectionItem<AnnotationSet> {
    @SuppressWarnings("unused")
    private final Dex   dex;
    public        int[] annotationOffsets;

    public AnnotationSet(Section owner, int offset, int[] annotationOffsets) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.annotationOffsets = annotationOffsets;
    }

    @Override
    public AnnotationSet clone(Section newOwner, int newOffset) {
        return new AnnotationSet(newOwner, newOffset, annotationOffsets);
    }

    @Override
    public int compareTo(AnnotationSet o) {
        int size = annotationOffsets.length;
        int oSize = o.annotationOffsets.length;

        if (size != oSize) {
            return Unsigned.compare(size, oSize);
        }

        for (int i = 0; i < size; ++i) {
            if (annotationOffsets[i] != o.annotationOffsets[i]) {
                return Unsigned.compare(annotationOffsets[i], o.annotationOffsets[i]);
            }
        }

        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((AnnotationSet) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.UINT * (1 + annotationOffsets.length);
    }
}
