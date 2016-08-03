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

import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class CodeSectionPatchAlgorithm extends DexSectionPatchAlgorithm<Code> {
    private final TableOfContents.Section patchedCodeTocSec;
    private final Dex.Section patchedCodeSec;

    public CodeSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap selfIndexMapForInsert
    ) {
        super(patchFile, oldDex, selfIndexMapForInsert);
        this.patchedCodeTocSec = patchedDex.getTableOfContents().codes;
        this.patchedCodeSec = patchedDex.openSection(this.patchedCodeTocSec);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().codes;
    }

    @Override
    protected Code nextItem(DexDataBuffer section) {
        return section.readCode();
    }

    @Override
    protected Code adjustItem(IndexMap indexMap, Code item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(Code patchedItem) {
        ++this.patchedCodeTocSec.size;
        return this.patchedCodeSec.writeCode(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldOffset != newOffset) {
            indexMap.mapCodeOffset(oldOffset, newOffset);
        }
    }
}
