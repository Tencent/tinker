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

package com.tencent.tinker.build.dexpatcher.algorithms.diff;

import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tangyinsheng on 2016/6/30.
 */
public class ClassDefSectionDiffAlgorithm extends DexSectionDiffAlgorithm<ClassDef> {
    private Set<Integer> typeIdOfClassDefToRemoveSet = new HashSet<>();

    public ClassDefSectionDiffAlgorithm(Dex oldDex, Dex newDex, IndexMap oldToNewIndexMap, IndexMap oldToPatchedIndexMap, IndexMap newToPatchedIndexMap, IndexMap selfIndexMapForSkip) {
        super(oldDex, newDex, oldToNewIndexMap, oldToPatchedIndexMap, newToPatchedIndexMap, selfIndexMapForSkip);
    }

    public void setTypeIdOfClassDefsToRemove(Collection<Integer> typeIdOfClassDefsToRemove) {
        this.typeIdOfClassDefToRemoveSet.clear();
        this.typeIdOfClassDefToRemoveSet.addAll(typeIdOfClassDefsToRemove);
    }

    public void clearTypeIdOfClassDefsToRemove() {
        this.typeIdOfClassDefToRemoveSet.clear();
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
    protected boolean shouldSkipInNewDex(ClassDef newItem) {
        return this.typeIdOfClassDefToRemoveSet.contains(newItem.typeIndex);
    }

    @Override
    protected int getItemSize(ClassDef item) {
        return SizeOf.CLASS_DEF_ITEM;
    }

    @Override
    protected ClassDef adjustItem(IndexMap indexMap, ClassDef item) {
        return indexMap.adjust(item);
    }
}
