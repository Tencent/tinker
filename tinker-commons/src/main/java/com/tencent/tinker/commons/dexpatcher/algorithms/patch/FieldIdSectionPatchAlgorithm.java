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
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tomystang on 2016/7/4.
 */
public class FieldIdSectionPatchAlgorithm extends DexSectionPatchAlgorithm<FieldId> {
    private final TableOfContents.Section patchedFieldIdTocSec;
    private final Dex.Section patchedFieldIdSec;

    public FieldIdSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap selfIndexMapForInsert
    ) {
        super(patchFile, oldDex, selfIndexMapForInsert);
        this.patchedFieldIdTocSec = patchedDex.getTableOfContents().fieldIds;
        this.patchedFieldIdSec = patchedDex.openSection(this.patchedFieldIdTocSec);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().fieldIds;
    }

    @Override
    protected FieldId nextItem(DexDataBuffer section) {
        return section.readFieldId();
    }

    @Override
    protected FieldId adjustItem(IndexMap indexMap, FieldId item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(FieldId patchedItem) {
        ++this.patchedFieldIdTocSec.size;
        return this.patchedFieldIdSec.writeFieldId(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldIndex != newIndex) {
            indexMap.mapFieldIds(oldIndex, newIndex);
        }
    }
}
