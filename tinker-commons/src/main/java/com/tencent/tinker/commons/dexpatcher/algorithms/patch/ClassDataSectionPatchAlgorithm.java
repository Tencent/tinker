/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.commons.dexpatcher.algorithms.patch;

import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class ClassDataSectionPatchAlgorithm extends DexSectionPatchAlgorithm<ClassData> {
    private TableOfContents.Section patchedClassDataTocSec = null;
    private Dex.Section patchedClassDataSec = null;

    public ClassDataSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            SparseIndexMap oldToPatchedIndexMap
    ) {
        super(patchFile, oldDex, oldToPatchedIndexMap);

        if (patchedDex != null) {
            this.patchedClassDataTocSec = patchedDex.getTableOfContents().classDatas;
            this.patchedClassDataSec = patchedDex.openSection(this.patchedClassDataTocSec);
        }
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
    protected int getItemSize(ClassData item) {
        return item.byteCountInDex();
    }

    @Override
    protected ClassData adjustItem(AbstractIndexMap indexMap, ClassData item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(ClassData patchedItem) {
        ++this.patchedClassDataTocSec.size;
        return this.patchedClassDataSec.writeClassData(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(SparseIndexMap sparseIndexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldOffset != newOffset) {
            sparseIndexMap.mapClassDataOffset(oldOffset, newOffset);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(SparseIndexMap sparseIndexMap, int deletedIndex, int deletedOffset) {
        sparseIndexMap.markClassDataDeleted(deletedOffset);
    }
}
