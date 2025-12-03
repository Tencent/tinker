package com.tencent.tinker.build.dexpatcher.algorithms.diff;

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.MethodHandle;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

public class MethodHandleSectionDiffAlgorithm extends DexSectionDiffAlgorithm<MethodHandle> {
    public MethodHandleSectionDiffAlgorithm(Dex oldDex, Dex newDex, SparseIndexMap oldToNewIndexMap,
                                            SparseIndexMap oldToPatchedIndexMap, SparseIndexMap newToPatchedIndexMap,
                                            SparseIndexMap selfIndexMapForSkip) {
        super(oldDex, newDex, oldToNewIndexMap, oldToPatchedIndexMap, newToPatchedIndexMap, selfIndexMapForSkip);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().methodHandles;
    }

    @Override
    protected MethodHandle nextItem(DexDataBuffer section) {
        return section.readMethodHandle();
    }

    @Override
    protected int getItemSize(MethodHandle item) {
        return item.byteCountInDex();
    }

    @Override
    protected MethodHandle adjustItem(AbstractIndexMap indexMap, MethodHandle item) {
        return indexMap.adjust(item);
    }

    @Override
    protected void updateIndexOrOffset(SparseIndexMap sparseIndexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        sparseIndexMap.mapMethodHandleIds(oldIndex, newIndex);
    }

    @Override
    protected void markDeletedIndexOrOffset(SparseIndexMap sparseIndexMap, int deletedIndex, int deletedOffset) {
        sparseIndexMap.markMethodHandleDeleted(deletedIndex);
    }
}
