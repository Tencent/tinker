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

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.MethodHandle;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

/**
 * Created by tangyinsheng on 2021/6/22.
 */
public class MethodHandleSectionPatchAlgorithm extends DexSectionPatchAlgorithm<MethodHandle> {
    private TableOfContents.Section patchedMethodHandleTocSec = null;
    private Dex.Section patchedMethodHandleSec = null;

    public MethodHandleSectionPatchAlgorithm(DexPatchFile patchFile, Dex oldDex, Dex patchedDex,
                                             SparseIndexMap oldToPatchedIndexMap) {
        super(patchFile, oldDex, oldToPatchedIndexMap);
        patchedMethodHandleTocSec = patchedDex.getTableOfContents().methodHandles;
        patchedMethodHandleSec = patchedDex.openSection(this.patchedMethodHandleTocSec);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().methodHandles;
    }

    @Override
    protected MethodHandle nextItem(DexDataBuffer section) {
        return section.readMethodHandle();
    }

    @Override
    protected int getItemSize(MethodHandle item) {
        return item.byteCountInDex();
    }

    @Override
    protected MethodHandle adjustItem(AbstractIndexMap indexMap, MethodHandle item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(MethodHandle patchedItem) {
        ++this.patchedMethodHandleTocSec.size;
        return this.patchedMethodHandleSec.writeMethodHandle(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(SparseIndexMap sparseIndexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldIndex != newIndex) {
            sparseIndexMap.mapMethodHandleIds(oldIndex, newIndex);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(SparseIndexMap sparseIndexMap, int deletedIndex, int deletedOffset) {
        sparseIndexMap.markMethodHandleDeleted(deletedIndex);
    }
}
