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

import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class ClassDefSectionPatchAlgorithm extends DexSectionPatchAlgorithm<ClassDef> {
    private TableOfContents.Section patchedClassDefTocSec = null;
    private Dex.Section patchedClassDefSec = null;

    public ClassDefSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap oldToFullPatchedIndexMap
    ) {
        super(patchFile, oldDex, oldToFullPatchedIndexMap);

        if (patchedDex != null) {
            this.patchedClassDefTocSec = patchedDex.getTableOfContents().classDefs;
            this.patchedClassDefSec = patchedDex.openSection(this.patchedClassDefTocSec);
        }
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().classDefs;
    }

    @Override
    protected ClassDef nextItem(DexDataBuffer section) {
        return section.readClassDef();
    }


    @Override
    protected int getItemSize(ClassDef item) {
        return item.byteCountInDex();
    }

    @Override
    protected ClassDef adjustItem(IndexMap indexMap, ClassDef item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(ClassDef patchedItem) {
        ++this.patchedClassDefTocSec.size;
        return this.patchedClassDefSec.writeClassDef(patchedItem);
    }
}
