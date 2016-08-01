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

package com.tencent.tinker.commons.dexpatcher.algorithms.patch;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tomystang on 2016/7/4.
 */
public class AnnotationSectionPatchAlgorithm extends DexSectionPatchAlgorithm<Annotation> {
    private final TableOfContents.Section patchedAnnotationTocSec;
    private final Dex.Section patchedAnnotationSec;

    public AnnotationSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap selfIndexMapForInsert
    ) {
        super(patchFile, oldDex, selfIndexMapForInsert);
        this.patchedAnnotationTocSec = patchedDex.getTableOfContents().annotations;
        this.patchedAnnotationSec = patchedDex.openSection(this.patchedAnnotationTocSec);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().annotations;
    }

    @Override
    protected Annotation nextItem(DexDataBuffer section) {
        return section.readAnnotation();
    }

    @Override
    protected Annotation adjustItem(IndexMap indexMap, Annotation item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(Annotation patchedItem) {
        ++this.patchedAnnotationTocSec.size;
        return this.patchedAnnotationSec.writeAnnotation(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldOffset != newOffset) {
            indexMap.mapAnnotationOffset(oldOffset, newOffset);
        }
    }
}
