package com.tencent.tinker.build.dexpatcher.algorithms.diff;

import com.tencent.tinker.android.dex.CallSiteId;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

public class CallSiteIdSectionDiffAlgorithm extends DexSectionDiffAlgorithm<CallSiteId> {
    public CallSiteIdSectionDiffAlgorithm(Dex oldDex, Dex newDex, SparseIndexMap oldToNewIndexMap,
                                          SparseIndexMap oldToPatchedIndexMap, SparseIndexMap newToPatchedIndexMap,
                                          SparseIndexMap selfIndexMapForSkip) {
        super(oldDex, newDex, oldToNewIndexMap, oldToPatchedIndexMap, newToPatchedIndexMap, selfIndexMapForSkip);
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().callSiteIds;
    }

    @Override
    protected CallSiteId nextItem(DexDataBuffer section) {
        return section.readCallSiteId();
    }

    @Override
    protected int getItemSize(CallSiteId item) {
        return item.byteCountInDex();
    }

    @Override
    protected CallSiteId adjustItem(AbstractIndexMap indexMap, CallSiteId item) {
        return indexMap.adjust(item);
    }

    @Override
    protected void updateIndexOrOffset(SparseIndexMap sparseIndexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        sparseIndexMap.mapCallsiteIds(oldIndex, newIndex);
    }

    @Override
    protected void markDeletedIndexOrOffset(SparseIndexMap sparseIndexMap, int deletedIndex, int deletedOffset) {
        sparseIndexMap.markCallsiteDeleted(deletedIndex);
    }
}
