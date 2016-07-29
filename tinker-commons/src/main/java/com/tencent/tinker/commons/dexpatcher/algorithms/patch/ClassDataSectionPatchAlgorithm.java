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

import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tomystang on 2016/7/4.
 */
public class ClassDataSectionPatchAlgorithm extends DexSectionPatchAlgorithm<ClassData> {
    private final TableOfContents.Section patchedClassDataTocSec;
    private final Dex.Section patchedClassDataSec;

    public ClassDataSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap selfIndexMapForInsert
    ) {
        super(patchFile, oldDex, selfIndexMapForInsert);
        this.patchedClassDataTocSec = patchedDex.getTableOfContents().classDatas;
        this.patchedClassDataSec = patchedDex.openSection(this.patchedClassDataTocSec);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().classDatas;
    }

    @Override
    protected ClassData nextItem(DexDataBuffer section) {
        return section.readClassData();
    }

    @Override
    protected ClassData adjustItem(IndexMap indexMap, ClassData item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(ClassData patchedItem) {
        ++this.patchedClassDataTocSec.size;
        return this.patchedClassDataSec.writeClassData(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldOffset != newOffset) {
            indexMap.mapClassDataOffset(oldOffset, newOffset);
        }
    }
}
