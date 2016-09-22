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
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.struct.SmallPatchedDexItemFile;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class ProtoIdSectionPatchAlgorithm extends DexSectionPatchAlgorithm<ProtoId> {
    private TableOfContents.Section patchedProtoIdTocSec = null;
    private Dex.Section patchedProtoIdSec = null;

    public ProtoIdSectionPatchAlgorithm(
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
                        return extraInfoFile.isProtoIdInSmallPatchedDex(
                                oldDexSign, patchedItemIndex
                        );
                    }
                }
        );
    }

    public ProtoIdSectionPatchAlgorithm(
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
            this.patchedProtoIdTocSec = patchedDex.getTableOfContents().protoIds;
            this.patchedProtoIdSec = patchedDex.openSection(this.patchedProtoIdTocSec);
        }
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().protoIds;
    }

    @Override
    protected ProtoId nextItem(DexDataBuffer section) {
        return section.readProtoId();
    }

    @Override
    protected int getItemSize(ProtoId item) {
        return item.byteCountInDex();
    }

    @Override
    protected int getFullPatchSectionBase() {
        if (this.patchFile != null) {
            return this.patchFile.getPatchedProtoIdSectionOffset();
        } else {
            return getTocSection(this.oldDex).off;
        }
    }

    @Override
    protected ProtoId adjustItem(IndexMap indexMap, ProtoId item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(ProtoId patchedItem) {
        ++this.patchedProtoIdTocSec.size;
        return this.patchedProtoIdSec.writeProtoId(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldIndex != newIndex) {
            indexMap.mapProtoIds(oldIndex, newIndex);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(IndexMap indexMap, int deletedIndex, int deletedOffset) {
        indexMap.markProtoIdDeleted(deletedIndex);
    }
}
