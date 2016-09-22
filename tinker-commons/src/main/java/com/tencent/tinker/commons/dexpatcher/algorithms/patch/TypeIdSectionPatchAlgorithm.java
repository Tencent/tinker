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
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.struct.SmallPatchedDexItemFile;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class TypeIdSectionPatchAlgorithm extends DexSectionPatchAlgorithm<Integer> {
    private TableOfContents.Section patchedTypeIdTocSec = null;
    private Dex.Section patchedTypeIdSec = null;

    public TypeIdSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap oldToFullPatchedIndexMap,
            IndexMap fullPatchedToSmallPatchedIndexMap,
            final SmallPatchedDexItemFile extraInfoFile
    ) {
        this(
                patchFile,
                oldDex,
                patchedDex,
                oldToFullPatchedIndexMap,
                fullPatchedToSmallPatchedIndexMap,
                new SmallPatchedDexItemChooser() {
                    @Override
                    public boolean isPatchedItemInSmallPatchedDex(
                            String oldDexSign, int patchedItemIndex
                    ) {
                        return extraInfoFile.isTypeIdInSmallPatchedDex(
                                oldDexSign, patchedItemIndex
                        );
                    }
                }
        );
    }

    public TypeIdSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap oldToFullPatchedIndexMap,
            IndexMap fullPatchedToSmallPatchedIndexMap,
            SmallPatchedDexItemChooser spdItemChooser
    ) {
        super(
                patchFile,
                oldDex,
                oldToFullPatchedIndexMap,
                fullPatchedToSmallPatchedIndexMap,
                spdItemChooser
        );

        if (patchedDex != null) {
            this.patchedTypeIdTocSec = patchedDex.getTableOfContents().typeIds;
            this.patchedTypeIdSec = patchedDex.openSection(this.patchedTypeIdTocSec);
        }
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().typeIds;
    }

    @Override
    protected Integer nextItem(DexDataBuffer section) {
        return section.readInt();
    }

    @Override
    protected int getItemSize(Integer item) {
        return SizeOf.UINT;
    }

    @Override
    protected int getFullPatchSectionBase() {
        if (this.patchFile != null) {
            return this.patchFile.getPatchedTypeIdSectionOffset();
        } else {
            return getTocSection(this.oldDex).off;
        }
    }

    @Override
    protected Integer adjustItem(IndexMap indexMap, Integer item) {
        return indexMap.adjustStringIndex(item);
    }

    @Override
    protected int writePatchedItem(Integer patchedItem) {
        int off = this.patchedTypeIdSec.position();
        this.patchedTypeIdSec.writeInt(patchedItem);
        ++this.patchedTypeIdTocSec.size;
        return off;
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldIndex != newIndex) {
            indexMap.mapTypeIds(oldIndex, newIndex);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(IndexMap indexMap, int deletedIndex, int deletedOffset) {
        indexMap.markTypeIdDeleted(deletedIndex);
    }
}
