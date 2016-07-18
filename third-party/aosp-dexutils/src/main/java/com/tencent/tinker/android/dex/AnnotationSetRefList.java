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
 * Structure of AnnotationSetRefList element in Dex file.
 */
public class AnnotationSetRefList extends SectionItem<AnnotationSetRefList> {
    @SuppressWarnings("unused")
    private final Dex   dex;
    public        int[] annotationSetRefItems;

    public AnnotationSetRefList(Section owner, int offset, int[] annotationSetRefItems) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.annotationSetRefItems = annotationSetRefItems;
    }

    @Override
    public AnnotationSetRefList clone(Section newOwner, int newOffset) {
        return new AnnotationSetRefList(newOwner, newOffset, annotationSetRefItems);
    }

    @Override
    public int compareTo(AnnotationSetRefList o) {
        int size = annotationSetRefItems.length;
        int oSize = o.annotationSetRefItems.length;

        if (size != oSize) {
            return Unsigned.compare(size, oSize);
        }

        for (int i = 0; i < size; ++i) {
            if (annotationSetRefItems[i] != o.annotationSetRefItems[i]) {
                return Unsigned.compare(annotationSetRefItems[i], o.annotationSetRefItems[i]);
            }
        }

        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((AnnotationSetRefList) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.UINT * (1 + annotationSetRefItems.length);
    }
}
