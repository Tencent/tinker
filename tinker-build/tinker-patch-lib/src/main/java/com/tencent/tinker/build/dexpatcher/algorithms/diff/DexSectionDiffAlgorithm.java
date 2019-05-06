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

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TableOfContents.Section.Item;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.commons.dexpatcher.struct.PatchOperation;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by tangyinsheng on 2016/6/29.
 */
public abstract class DexSectionDiffAlgorithm<T extends Comparable<T>> {
    private static final AbstractMap.SimpleEntry[] EMPTY_ENTRY_ARRAY = new AbstractMap.SimpleEntry[0];
    protected final Dex oldDex;
    protected final Dex newDex;
    /**
     * SparseIndexMap for mapping items between old dex and new dex.
     * e.g. item.oldIndex => item.newIndex
     */
    private final SparseIndexMap oldToNewIndexMap;
    /**
     * SparseIndexMap for mapping items between old dex and patched dex.
     * e.g. item.oldIndex => item.patchedIndex
     */
    private final SparseIndexMap oldToPatchedIndexMap;
    /**
     * SparseIndexMap for mapping items between new dex and patched dex.
     * e.g. item.newIndex => item.newIndexInPatchedDex
     */
    private final SparseIndexMap newToPatchedIndexMap;
    /**
     * SparseIndexMap for mapping items in new dex when skip items.
     */
    private final SparseIndexMap selfIndexMapForSkip;
    private final List<PatchOperation<T>> patchOperationList;
    private final Map<Integer, PatchOperation<T>> indexToDelOperationMap = new HashMap<>();
    private final Map<Integer, PatchOperation<T>> indexToAddOperationMap = new HashMap<>();
    private final Map<Integer, PatchOperation<T>> indexToReplaceOperationMap = new HashMap<>();
    private final Map<Integer, Integer> oldIndexToNewIndexMap = new HashMap<>();
    private final Map<Integer, Integer> oldOffsetToNewOffsetMap = new HashMap<>();
    private int patchedSectionSize;
    private Comparator<AbstractMap.SimpleEntry<Integer, T>> comparatorForItemDiff = new Comparator<AbstractMap.SimpleEntry<Integer, T>>() {
        @Override
        public int compare(AbstractMap.SimpleEntry<Integer, T> o1, AbstractMap.SimpleEntry<Integer, T> o2) {
            return o1.getValue().compareTo(o2.getValue());
        }
    };
    private Comparator<PatchOperation<T>> comparatorForPatchOperationOpt = new Comparator<PatchOperation<T>>() {
        @Override
        public int compare(PatchOperation<T> o1, PatchOperation<T> o2) {
            if (o1.index != o2.index) {
                return CompareUtils.sCompare(o1.index, o2.index);
            }
            int o1OrderId;
            switch (o1.op) {
                case PatchOperation.OP_DEL:
                    o1OrderId = 0;
                    break;
                case PatchOperation.OP_ADD:
                    o1OrderId = 1;
                    break;
                case PatchOperation.OP_REPLACE:
                    o1OrderId = 2;
                    break;
                default:
                    throw new IllegalStateException("unexpected patch operation code: " + o1.op);
            }
            int o2OrderId;
            switch (o2.op) {
                case PatchOperation.OP_DEL:
                    o2OrderId = 0;
                    break;
                case PatchOperation.OP_ADD:
                    o2OrderId = 1;
                    break;
                case PatchOperation.OP_REPLACE:
                    o2OrderId = 2;
                    break;
                default:
                    throw new IllegalStateException("unexpected patch operation code: " + o2.op);
            }
            return CompareUtils.sCompare(o1OrderId, o2OrderId);
        }
    };
    private AbstractMap.SimpleEntry<Integer, T>[] adjustedOldIndexedItemsWithOrigOrder = null;
    private int oldItemCount = 0;
    private int newItemCount = 0;

    public DexSectionDiffAlgorithm(
            Dex oldDex,
            Dex newDex,
            SparseIndexMap oldToNewIndexMap,
            SparseIndexMap oldToPatchedIndexMap,
            SparseIndexMap newToPatchedIndexMap,
            SparseIndexMap selfIndexMapForSkip
    ) {
        this.oldDex = oldDex;
        this.newDex = newDex;
        this.oldToNewIndexMap = oldToNewIndexMap;
        this.oldToPatchedIndexMap = oldToPatchedIndexMap;
        this.newToPatchedIndexMap = newToPatchedIndexMap;
        this.selfIndexMapForSkip = selfIndexMapForSkip;
        this.patchOperationList = new ArrayList<>();
        this.patchedSectionSize = 0;
    }

    public List<PatchOperation<T>> getPatchOperationList() {
        return this.patchOperationList;
    }

    public int getPatchedSectionSize() {
        return this.patchedSectionSize;
    }

    /**
     * Get {@code Section} in {@code TableOfContents}.
     */
    protected abstract TableOfContents.Section getTocSection(Dex dex);

    /**
     * Get next item in {@code section}.
     */
    protected abstract T nextItem(DexDataBuffer section);

    /**
     * Get item size.
     */
    protected abstract int getItemSize(T item);

    /**
     * Adjust {@code item} using specific {@code sparseIndexMap}
     */
    protected T adjustItem(AbstractIndexMap indexMap, T item) {
        return item;
    }

    /**
     * Indicate if {@code item} should be skipped in new dex.
     */
    protected boolean shouldSkipInNewDex(T newItem) {
        return false;
    }

    /**
     * Update index or offset mapping in {@code sparseIndexMap}.
     */
    protected void updateIndexOrOffset(SparseIndexMap sparseIndexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        // Should override by subclass if needed.
    }

    /**
     * Mark deleted index or offset in {@code sparseIndexMap}.
     *
     * Here we mark deleted item for such a case like this:
     *   Item in DebugInfo section reference a string in StringData section
     *   by index X, while in patched dex, the referenced string is removed.
     *
     * The {@code sparseIndexMap} must be aware of this case and return -1
     * instead of the original value X.
     *
     * Further more, the special value -1 is not chosen by our inspiration but
     * the definition of NO_INDEX in document of dex file format.
     */
    protected void markDeletedIndexOrOffset(SparseIndexMap sparseIndexMap, int deletedIndex, int deletedOffset) {
        // Should override by subclass if needed.
    }

    /**
     * Adapter method for item's offset fetching, if an item is not
     * inherited from {@code Item} (which means it is a simple item in dex section
     * that doesn't need multiple members to describe), this method
     * return {@code index} instead.
     */
    private int getItemOffsetOrIndex(int index, T item) {
        if (item instanceof Item) {
            return ((Item<?>) item).off;
        } else {
            return index;
        }
    }

    @SuppressWarnings("unchecked,NewApi")
    private AbstractMap.SimpleEntry<Integer, T>[] collectSectionItems(Dex dex, boolean isOldDex) {
        TableOfContents.Section tocSec = getTocSection(dex);
        if (!tocSec.exists()) {
            return EMPTY_ENTRY_ARRAY;
        }
        Dex.Section dexSec = dex.openSection(tocSec);
        int itemCount = tocSec.size;
        List<AbstractMap.SimpleEntry<Integer, T>> result = new ArrayList<>(itemCount);
        if (isOldDex) {
            for (int i = 0; i < itemCount; ++i) {
                T nextItem = nextItem(dexSec);
                T adjustedItem = adjustItem(oldToPatchedIndexMap, nextItem);
                result.add(new AbstractMap.SimpleEntry<>(i, adjustedItem));
            }
        } else {
            int i = 0;
            while (i < itemCount) {
                T nextItem = nextItem(dexSec);
                int indexBeforeSkip = i;
                int offsetBeforeSkip = getItemOffsetOrIndex(indexBeforeSkip, nextItem);
                int indexAfterSkip = indexBeforeSkip;
                while (indexAfterSkip < itemCount && shouldSkipInNewDex(nextItem)) {
                    if (indexAfterSkip + 1 >= itemCount) {
                        // after skipping last item, nextItem will be null.
                        nextItem = null;
                    } else {
                        nextItem = nextItem(dexSec);
                    }
                    ++indexAfterSkip;
                }
                if (nextItem != null) {
                    int offsetAfterSkip = getItemOffsetOrIndex(indexAfterSkip, nextItem);
                    T adjustedItem = adjustItem(newToPatchedIndexMap, adjustItem(selfIndexMapForSkip, nextItem));
                    int currentOutIndex = result.size();
                    result.add(new AbstractMap.SimpleEntry<>(currentOutIndex, adjustedItem));
                    updateIndexOrOffset(selfIndexMapForSkip, indexBeforeSkip, offsetBeforeSkip, indexAfterSkip, offsetAfterSkip);
                }
                i = indexAfterSkip;
                ++i;
            }
        }
        return result.toArray(new AbstractMap.SimpleEntry[0]);
    }

    public void execute() {
        this.patchOperationList.clear();

        this.adjustedOldIndexedItemsWithOrigOrder = collectSectionItems(this.oldDex, true);
        this.oldItemCount = this.adjustedOldIndexedItemsWithOrigOrder.length;

        AbstractMap.SimpleEntry<Integer, T>[] adjustedOldIndexedItems = new AbstractMap.SimpleEntry[this.oldItemCount];
        System.arraycopy(this.adjustedOldIndexedItemsWithOrigOrder, 0, adjustedOldIndexedItems, 0, this.oldItemCount);
        Arrays.sort(adjustedOldIndexedItems, this.comparatorForItemDiff);

        AbstractMap.SimpleEntry<Integer, T>[] adjustedNewIndexedItems = collectSectionItems(this.newDex, false);
        this.newItemCount = adjustedNewIndexedItems.length;
        Arrays.sort(adjustedNewIndexedItems, this.comparatorForItemDiff);

        int oldCursor = 0;
        int newCursor = 0;
        while (oldCursor < this.oldItemCount || newCursor < this.newItemCount) {
            if (oldCursor >= this.oldItemCount) {
                // rest item are all newItem.
                while (newCursor < this.newItemCount) {
                    AbstractMap.SimpleEntry<Integer, T> newIndexedItem = adjustedNewIndexedItems[newCursor++];
                    this.patchOperationList.add(new PatchOperation<>(PatchOperation.OP_ADD, newIndexedItem.getKey(), newIndexedItem.getValue()));
                }
            } else
            if (newCursor >= newItemCount) {
                // rest item are all oldItem.
                while (oldCursor < oldItemCount) {
                    AbstractMap.SimpleEntry<Integer, T> oldIndexedItem = adjustedOldIndexedItems[oldCursor++];
                    int deletedIndex = oldIndexedItem.getKey();
                    int deletedOffset = getItemOffsetOrIndex(deletedIndex, oldIndexedItem.getValue());
                    this.patchOperationList.add(new PatchOperation<T>(PatchOperation.OP_DEL, deletedIndex));
                    markDeletedIndexOrOffset(this.oldToPatchedIndexMap, deletedIndex, deletedOffset);
                }
            } else {
                AbstractMap.SimpleEntry<Integer, T> oldIndexedItem = adjustedOldIndexedItems[oldCursor];
                AbstractMap.SimpleEntry<Integer, T> newIndexedItem = adjustedNewIndexedItems[newCursor];
                int cmpRes = oldIndexedItem.getValue().compareTo(newIndexedItem.getValue());
                if (cmpRes < 0) {
                    int deletedIndex = oldIndexedItem.getKey();
                    int deletedOffset = getItemOffsetOrIndex(deletedIndex, oldIndexedItem.getValue());
                    this.patchOperationList.add(new PatchOperation<T>(PatchOperation.OP_DEL, deletedIndex));
                    markDeletedIndexOrOffset(this.oldToPatchedIndexMap, deletedIndex, deletedOffset);
                    ++oldCursor;
                } else
                if (cmpRes > 0) {
                    this.patchOperationList.add(new PatchOperation<>(PatchOperation.OP_ADD, newIndexedItem.getKey(), newIndexedItem.getValue()));
                    ++newCursor;
                } else {
                    int oldIndex = oldIndexedItem.getKey();
                    int newIndex = newIndexedItem.getKey();
                    int oldOffset = getItemOffsetOrIndex(oldIndexedItem.getKey(), oldIndexedItem.getValue());
                    int newOffset = getItemOffsetOrIndex(newIndexedItem.getKey(), newIndexedItem.getValue());

                    if (oldIndex != newIndex) {
                        this.oldIndexToNewIndexMap.put(oldIndex, newIndex);
                    }

                    if (oldOffset != newOffset) {
                        this.oldOffsetToNewOffsetMap.put(oldOffset, newOffset);
                    }

                    ++oldCursor;
                    ++newCursor;
                }
            }
        }

        // So far all diff works are done. Then we perform some optimize works.
        // detail: {OP_DEL idx} followed by {OP_ADD the_same_idx newItem}
        // will be replaced by {OP_REPLACE idx newItem}
        Collections.sort(this.patchOperationList, comparatorForPatchOperationOpt);

        Iterator<PatchOperation<T>> patchOperationIt = this.patchOperationList.iterator();
        PatchOperation<T> prevPatchOperation = null;
        while (patchOperationIt.hasNext()) {
            PatchOperation<T> patchOperation = patchOperationIt.next();
            if (prevPatchOperation != null
                && prevPatchOperation.op == PatchOperation.OP_DEL
                && patchOperation.op == PatchOperation.OP_ADD
            ) {
                if (prevPatchOperation.index == patchOperation.index) {
                    prevPatchOperation.op = PatchOperation.OP_REPLACE;
                    prevPatchOperation.newItem = patchOperation.newItem;
                    patchOperationIt.remove();
                    prevPatchOperation = null;
                } else {
                    prevPatchOperation = patchOperation;
                }
            } else {
                prevPatchOperation = patchOperation;
            }
        }

        // Finally we record some information for the final calculations.
        patchOperationIt = this.patchOperationList.iterator();
        while (patchOperationIt.hasNext()) {
            PatchOperation<T> patchOperation = patchOperationIt.next();
            switch (patchOperation.op) {
                case PatchOperation.OP_DEL: {
                    indexToDelOperationMap.put(patchOperation.index, patchOperation);
                    break;
                }
                case PatchOperation.OP_ADD: {
                    indexToAddOperationMap.put(patchOperation.index, patchOperation);
                    break;
                }
                case PatchOperation.OP_REPLACE: {
                    indexToReplaceOperationMap.put(patchOperation.index, patchOperation);
                    break;
                }
                default: {
                    break;
                }
            }
        }
    }

    public void simulatePatchOperation(int baseOffset) {
        boolean isNeedToMakeAlign = getTocSection(this.oldDex).isElementFourByteAligned;
        int oldIndex = 0;
        int patchedIndex = 0;
        int patchedOffset = baseOffset;
        while (oldIndex < this.oldItemCount || patchedIndex < this.newItemCount) {
            if (this.indexToAddOperationMap.containsKey(patchedIndex)) {
                PatchOperation<T> patchOperation = this.indexToAddOperationMap.get(patchedIndex);
                if (isNeedToMakeAlign) {
                    patchedOffset = SizeOf.roundToTimesOfFour(patchedOffset);
                }
                T newItem = patchOperation.newItem;
                int itemSize = getItemSize(newItem);
                updateIndexOrOffset(
                        this.newToPatchedIndexMap,
                        0,
                        getItemOffsetOrIndex(patchOperation.index, newItem),
                        0,
                        patchedOffset
                );
                ++patchedIndex;
                patchedOffset += itemSize;
            } else
            if (this.indexToReplaceOperationMap.containsKey(patchedIndex)) {
                PatchOperation<T> patchOperation = this.indexToReplaceOperationMap.get(patchedIndex);
                if (isNeedToMakeAlign) {
                    patchedOffset = SizeOf.roundToTimesOfFour(patchedOffset);
                }
                T newItem = patchOperation.newItem;
                int itemSize = getItemSize(newItem);
                updateIndexOrOffset(
                        this.newToPatchedIndexMap,
                        0,
                        getItemOffsetOrIndex(patchOperation.index, newItem),
                        0,
                        patchedOffset
                );
                ++patchedIndex;
                patchedOffset += itemSize;
            } else
            if (this.indexToDelOperationMap.containsKey(oldIndex)) {
                ++oldIndex;
            } else
            if (this.indexToReplaceOperationMap.containsKey(oldIndex)) {
                ++oldIndex;
            } else
            if (oldIndex < this.oldItemCount) {
                if (isNeedToMakeAlign) {
                    patchedOffset = SizeOf.roundToTimesOfFour(patchedOffset);
                }

                T oldItem = this.adjustedOldIndexedItemsWithOrigOrder[oldIndex].getValue();
                int itemSize = getItemSize(oldItem);

                int oldOffset = getItemOffsetOrIndex(oldIndex, oldItem);

                updateIndexOrOffset(
                        this.oldToPatchedIndexMap,
                        oldIndex,
                        oldOffset,
                        patchedIndex,
                        patchedOffset
                );

                int newIndex = oldIndex;
                if (this.oldIndexToNewIndexMap.containsKey(oldIndex)) {
                    newIndex = this.oldIndexToNewIndexMap.get(oldIndex);
                }

                int newOffset = oldOffset;
                if (this.oldOffsetToNewOffsetMap.containsKey(oldOffset)) {
                    newOffset = this.oldOffsetToNewOffsetMap.get(oldOffset);
                }

                updateIndexOrOffset(
                        this.newToPatchedIndexMap,
                        newIndex,
                        newOffset,
                        patchedIndex,
                        patchedOffset
                );

                ++oldIndex;
                ++patchedIndex;
                patchedOffset += itemSize;
            }
        }

        this.patchedSectionSize = SizeOf.roundToTimesOfFour(patchedOffset - baseOffset);
    }
}
