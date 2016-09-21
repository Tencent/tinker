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
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.struct.SmallPatchedDexItemFile;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class StringDataSectionPatchAlgorithm extends DexSectionPatchAlgorithm<StringData> {
    private TableOfContents.Section patchedStringDataTocSec = null;
    private TableOfContents.Section patchedStringIdTocSec = null;
    private Dex.Section patchedStringDataSec = null;
    private Dex.Section patchedStringIdSec = null;

    public StringDataSectionPatchAlgorithm(
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
                        return extraInfoFile.isStringInSmallPatchedDex(
                                oldDexSign, patchedItemIndex
                        );
                    }
                }
        );
    }

    public StringDataSectionPatchAlgorithm(
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
            this.patchedStringDataTocSec = patchedDex.getTableOfContents().stringDatas;
            this.patchedStringIdTocSec = patchedDex.getTableOfContents().stringIds;
            this.patchedStringDataSec = patchedDex.openSection(this.patchedStringDataTocSec);
            this.patchedStringIdSec = patchedDex.openSection(this.patchedStringIdTocSec);
        }
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().stringDatas;
    }

    @Override
    protected StringData nextItem(DexDataBuffer section) {
        return section.readStringData();
    }

    @Override
    protected int getItemSize(StringData item) {
        return item.byteCountInDex();
    }

    @Override
    protected int getFullPatchSectionBase() {
        return this.patchFile.getPatchedStringDataSectionOffset();
    }

    @Override
    protected int writePatchedItem(StringData patchedItem) {
        int off = this.patchedStringDataSec.writeStringData(patchedItem);
        this.patchedStringIdSec.writeInt(off);
        ++this.patchedStringDataTocSec.size;
        ++this.patchedStringIdTocSec.size;
        return off;
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldIndex != newIndex) {
            indexMap.mapStringIds(oldIndex, newIndex);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(IndexMap indexMap, int deletedIndex, int deletedOffset) {
        indexMap.markStringIdDeleted(deletedIndex);
    }
}
