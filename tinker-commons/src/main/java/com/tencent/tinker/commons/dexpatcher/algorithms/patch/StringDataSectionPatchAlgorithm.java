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

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tomystang on 2016/7/4.
 */
public class StringDataSectionPatchAlgorithm extends DexSectionPatchAlgorithm<StringData> {
    private final Dex patchedDex;
    private final TableOfContents.Section patchedStringDataTocSec;
    private final TableOfContents.Section patchedStringIdTocSec;
    private final Dex.Section patchedStringDataSec;
    private final Dex.Section patchedStringIdSec;

    public StringDataSectionPatchAlgorithm(DexPatchFile patchFile,
                                           Dex oldDex,
                                           Dex patchedDex,
                                           IndexMap selfIndexMapForInsert
    ) {
        super(patchFile, oldDex, selfIndexMapForInsert);
        this.patchedDex = patchedDex;
        this.patchedStringDataTocSec = this.patchedDex.getTableOfContents().stringDatas;
        this.patchedStringIdTocSec = this.patchedDex.getTableOfContents().stringIds;
        this.patchedStringDataSec = this.patchedDex.openSection(this.patchedStringDataTocSec);
        this.patchedStringIdSec = this.patchedDex.openSection(this.patchedStringIdTocSec);
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
}
