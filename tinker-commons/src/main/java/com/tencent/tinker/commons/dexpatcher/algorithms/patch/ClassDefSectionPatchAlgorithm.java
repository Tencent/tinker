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
import com.tencent.tinker.android.utils.SparseIntArray;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class ClassDefSectionPatchAlgorithm extends DexSectionPatchAlgorithm<ClassDef> {
    private Dex patchedDex = null;
    private TableOfContents.Section patchedClassDefTocSec = null;
    private Dex.Section patchedClassDefSec = null;
    private List<ClassDef> patchedClassDefs = null;

    public ClassDefSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            SparseIndexMap oldToPatchedIndexMap
    ) {
        super(patchFile, oldDex, oldToPatchedIndexMap);

        if (patchedDex != null) {
            this.patchedDex = patchedDex;
            this.patchedClassDefTocSec = patchedDex.getTableOfContents().classDefs;
            this.patchedClassDefSec = patchedDex.openSection(this.patchedClassDefTocSec);
            this.patchedClassDefs = new ArrayList<>(512);
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
    protected ClassDef adjustItem(AbstractIndexMap indexMap, ClassDef item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(ClassDef patchedItem) {
        ++this.patchedClassDefTocSec.size;
        this.patchedClassDefs.add(patchedItem);
        // Since no other sections concern about offset of ClassDef item, we can simply return 0 here.
        return 0;
    }

    @Override
    protected void onPatchAlgorithmEnd() {
        this.patchedClassDefs = topologicalSort(this.patchedClassDefs);
        for (ClassDef patchedItem : this.patchedClassDefs) {
            this.patchedClassDefSec.writeClassDef(patchedItem);
        }
    }

    private List<ClassDef> topologicalSort(List<ClassDef> elements) {
        final Map<Integer, ClassDef> typeIdToClassDefMap = new HashMap<>(elements.size() + 8);
        final Map<Integer, List<Integer>> typeGraph = new HashMap<>(elements.size() + 8);
        final SparseIntArray inDegrees = new SparseIntArray(elements.size() + 8);
        for (ClassDef elem : elements) {
            typeIdToClassDefMap.put(elem.typeIndex, elem);
            typeGraph.put(elem.typeIndex, new ArrayList<>(8));
            inDegrees.put(elem.typeIndex, 0);
        }

        for (ClassDef elem : elements) {
            if (typeIdToClassDefMap.containsKey(elem.supertypeIndex)) {
                typeGraph.get(elem.supertypeIndex).add(elem.typeIndex);
                inDegrees.put(elem.typeIndex, inDegrees.get(elem.typeIndex) + 1);
            }
            for (int implTypeId : patchedDex.interfaceTypeIndicesFromClassDef(elem)) {
                if (typeIdToClassDefMap.containsKey(implTypeId)) {
                    typeGraph.get(implTypeId).add(elem.typeIndex);
                    inDegrees.put(elem.typeIndex, inDegrees.get(elem.typeIndex) + 1);
                }
            }
        }

        final Queue<Integer> typeIdWithZeroInDegrees = new ArrayDeque<>(64);
        for (int i = 0; i < inDegrees.size(); ++i) {
            final int typeId = inDegrees.keyAt(i);
            final int inDegree = inDegrees.valueAt(i);
            if (inDegree == 0) {
                typeIdWithZeroInDegrees.offer(typeId);
            }
        }

        final List<ClassDef> result = new ArrayList<>();
        while (!typeIdWithZeroInDegrees.isEmpty()) {
            final int currentTypeId = typeIdWithZeroInDegrees.poll();
            result.add(typeIdToClassDefMap.get(currentTypeId));

            for (int nextTypeId : typeGraph.get(currentTypeId)) {
                final int newInDegree = inDegrees.get(nextTypeId) - 1;
                inDegrees.put(nextTypeId, newInDegree);
                if (newInDegree == 0) {
                    typeIdWithZeroInDegrees.offer(nextTypeId);
                }
            }
        }

        // Check if type graph contains loop.
        if (result.size() != elements.size()) {
            throw new IllegalStateException("Illegal dex format, there's at least one loop in class inheritance graph.");
        }

        return result;
    }
}
