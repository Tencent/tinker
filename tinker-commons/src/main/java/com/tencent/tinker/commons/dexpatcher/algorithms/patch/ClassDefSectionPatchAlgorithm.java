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

import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

/**
 * Created by tomystang on 2016/7/4.
 */
public class ClassDefSectionPatchAlgorithm extends DexSectionPatchAlgorithm<ClassDef> {
    private final TableOfContents.Section patchedClassDefTocSec;
    private final Dex.Section patchedClassDefSec;

    public ClassDefSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap selfIndexMapForInsert
    ) {
        super(patchFile, oldDex, selfIndexMapForInsert);
        this.patchedClassDefTocSec = patchedDex.getTableOfContents().classDefs;
        this.patchedClassDefSec = patchedDex.openSection(this.patchedClassDefTocSec);
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
    protected ClassDef adjustItem(IndexMap indexMap, ClassDef item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(ClassDef patchedItem) {
        ++this.patchedClassDefTocSec.size;
        return this.patchedClassDefSec.writeClassDef(patchedItem);
    }
}
