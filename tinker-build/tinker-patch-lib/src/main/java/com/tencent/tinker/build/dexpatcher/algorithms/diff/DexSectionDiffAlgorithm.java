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

package com.tencent.tinker.build.dexpatcher.algorithms.diff;

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TableOfContents.Section.Item;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.PatchOperation;

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
 * Created by tomystang on 2016/6/29.
 */
public abstract class DexSectionDiffAlgorithm<T extends Comparable<T>> {
    protected final Dex oldDex;

    protected final Dex newDex;

    /**
     * IndexMap for mapping items between old dex and new dex.
     * e.g. item.oldIndex => item.newIndex
     */
    private final IndexMap oldToNewIndexMap;

    /**
     * IndexMap for mapping items between old dex and patched dex.
     * e.g. item.oldIndex => item.patchedIndex
     */
    private final IndexMap oldToPatchedIndexMap;

    /**
     * IndexMap for mapping items in new dex when skip items.
     */
    private final IndexMap selfIndexMapForSkip;


    private final List<PatchOperation<T>> tempPatchOperationList;

    private final List<PatchOperation<T>> patchOperationList;

    private int patchedSectionSize;

    public DexSectionDiffAlgorithm(
            Dex oldDex,
            Dex newDex,
            IndexMap oldToNewIndexMap,
            IndexMap oldToPatchedIndexMap,
            IndexMap selfIndexMapForSkip
    ) {
        this.oldDex = oldDex;
        this.newDex = newDex;
        this.oldToNewIndexMap = oldToNewIndexMap;
        this.oldToPatchedIndexMap = oldToPatchedIndexMap;
        this.selfIndexMapForSkip = selfIndexMapForSkip;
        this.tempPatchOperationList = new ArrayList<>();
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
     * Adjust {@code item} using specific {@code indexMap}
     */
    protected T adjustItem(IndexMap indexMap, T item) {
        return item;
    }

    /**
     * Indicate if {@code item} should be skipped in new dex.
     */
    protected boolean shouldSkipInNewDex(T newItem) {
        return false;
    }

    /**
     * Update index or offset mapping in {@code indexMap}.
     */
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
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

    private static final AbstractMap.SimpleEntry[] EMPTY_ENTRY_ARRAY = new AbstractMap.SimpleEntry[0];

    // FIXME notice about this method.
    @SuppressWarnings("unchecked")
    private AbstractMap.SimpleEntry<Integer, T>[] collectSectionItems(Dex dex) {
        TableOfContents.Section tocSec = getTocSection(dex);
        if (!tocSec.exists()) {
            return EMPTY_ENTRY_ARRAY;
        }
        Dex.Section dexSec = dex.openSection(tocSec);
        int itemCount = tocSec.size;
        boolean isOldDex = (dex == oldDex);
        List<AbstractMap.SimpleEntry<Integer, T>> result = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            T nextItem = nextItem(dexSec);
            int indexBeforeSkip = i;
            int indexAfterSkip = indexBeforeSkip;
            if (isOldDex) {
                nextItem = adjustItem(oldToNewIndexMap, nextItem);
            } else {
                int offsetBeforeSkip = getItemOffsetOrIndex(indexBeforeSkip, nextItem);
                while (i < itemCount && shouldSkipInNewDex(nextItem)) {
                    if (i + 1 >= itemCount) {
                        // after skipping last item, nextItem will be null.
                        nextItem = null;
                    } else {
                        nextItem = nextItem(dexSec);
                    }
                    --itemCount;
                    ++indexAfterSkip;
                }
                if (nextItem != null) {
                    int offsetAfterSkip = getItemOffsetOrIndex(indexAfterSkip, nextItem);
                    nextItem = adjustItem(selfIndexMapForSkip, nextItem);
                    updateIndexOrOffset(selfIndexMapForSkip, indexBeforeSkip, offsetBeforeSkip, indexAfterSkip, offsetAfterSkip);
                }
            }
            if (nextItem != null) {
                result.add(new AbstractMap.SimpleEntry<>(indexAfterSkip, nextItem));
            }
        }
        return result.toArray(new AbstractMap.SimpleEntry[itemCount]);
    }

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

    private AbstractMap.SimpleEntry<Integer, T>[] adjuestedOldIndexedItemsWithOrigOrder = null;
    private AbstractMap.SimpleEntry<Integer, T>[] newIndexedItems = null;

    private final Map<Integer, PatchOperation<T>> indexToDelOperationMap = new HashMap<>();
    private final Map<Integer, PatchOperation<T>> indexToAddOperationMap = new HashMap<>();
    private final Map<Integer, PatchOperation<T>> indexToReplaceOperationMap = new HashMap<>();

    private final Map<Integer, Integer> oldIndexToNewIndexMap = new HashMap<>();
    private final Map<Integer, Integer> oldOffsetToNewOffsetMap = new HashMap<>();

    public void execute() {
        this.tempPatchOperationList.clear();
        adjuestedOldIndexedItemsWithOrigOrder = collectSectionItems(oldDex);

        AbstractMap.SimpleEntry<Integer, T>[] adjustedOldIndexedItems = new AbstractMap.SimpleEntry[adjuestedOldIndexedItemsWithOrigOrder.length];
        System.arraycopy(adjuestedOldIndexedItemsWithOrigOrder, 0, adjustedOldIndexedItems, 0, adjuestedOldIndexedItemsWithOrigOrder.length);
        Arrays.sort(adjustedOldIndexedItems, comparatorForItemDiff);

        newIndexedItems = collectSectionItems(newDex);
        Arrays.sort(newIndexedItems, comparatorForItemDiff);

        int oldItemCount = adjustedOldIndexedItems.length;
        int newItemCount = newIndexedItems.length;
        int oldCursor = 0;
        int newCursor = 0;
        while (oldCursor < oldItemCount || newCursor < newItemCount) {
            if (oldCursor >= oldItemCount) {
                // rest item are all newItem.
                while (newCursor < newItemCount) {
                    AbstractMap.SimpleEntry<Integer, T> newIndexedItem = newIndexedItems[newCursor++];
                    tempPatchOperationList.add(new PatchOperation<>(PatchOperation.OP_ADD, newIndexedItem.getKey(), newIndexedItem.getValue()));
                }
            } else
            if (newCursor >= newItemCount) {
                // rest item are all oldItem.
                while (oldCursor < oldItemCount) {
                    AbstractMap.SimpleEntry<Integer, T> oldIndexedItem = adjustedOldIndexedItems[oldCursor++];
                    tempPatchOperationList.add(new PatchOperation<T>(PatchOperation.OP_DEL, oldIndexedItem.getKey()));
                }
            } else {
                AbstractMap.SimpleEntry<Integer, T> oldIndexedItem = adjustedOldIndexedItems[oldCursor];
                AbstractMap.SimpleEntry<Integer, T> newIndexedItem = newIndexedItems[newCursor];
                int cmpRes = oldIndexedItem.getValue().compareTo(newIndexedItem.getValue());
                if (cmpRes < 0) {
                    tempPatchOperationList.add(new PatchOperation<T>(PatchOperation.OP_DEL, oldIndexedItem.getKey()));
                    ++oldCursor;
                } else
                if (cmpRes > 0) {
                    tempPatchOperationList.add(new PatchOperation<>(PatchOperation.OP_ADD, newIndexedItem.getKey(), newIndexedItem.getValue()));
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

                    updateIndexOrOffset(oldToNewIndexMap, oldIndex, oldOffset, newIndex, newOffset);

                    ++oldCursor;
                    ++newCursor;
                }
            }
        }

        // So far all diff works are done. Then we perform some optimize works.
        // detail: {OP_DEL idx} followed by {OP_ADD the_same_idx newItem}
        // will be replaced by {OP_REPLACE idx newItem}
        Collections.sort(tempPatchOperationList, comparatorForPatchOperationOpt);
        Iterator<PatchOperation<T>> patchOperationIt = tempPatchOperationList.iterator();

        PatchOperation<T> prevPatchOperation = null;
        while (patchOperationIt.hasNext()) {
            PatchOperation<T> patchOperation = patchOperationIt.next();
            if (prevPatchOperation != null &&
                prevPatchOperation.op == PatchOperation.OP_DEL &&
                patchOperation.op == PatchOperation.OP_ADD
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
        for (PatchOperation<T> patchOperation : this.tempPatchOperationList) {
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
            }
        }
    }

    public void simulatePatchOperation(int baseOffset) {
        this.patchOperationList.clear();
        int offsetAfterWritten = baseOffset;
        int oldItemCount = adjuestedOldIndexedItemsWithOrigOrder.length;
        int newItemCount = newIndexedItems.length;

        boolean isNeedToMakeAlign = getTocSection(oldDex).isElementFourByteAligned;
        int oldItemCursor = 0;
        int countedItemCursor = 0;
        while (oldItemCursor < oldItemCount) {
            if (indexToAddOperationMap.containsKey(countedItemCursor)) {
                PatchOperation<T> patchOperation = indexToAddOperationMap.get(countedItemCursor);
                if (isNeedToMakeAlign) {
                    offsetAfterWritten = SizeOf.roundToTimesOfFour(offsetAfterWritten);
                }
                T adjustedItem = adjustItem(oldToPatchedIndexMap, patchOperation.newItem);
                this.patchOperationList.add(
                        new PatchOperation<>(patchOperation.op, patchOperation.index, adjustedItem)
                );
                int itemSize = getItemSize(adjustedItem);
                updateIndexOrOffset(
                        oldToPatchedIndexMap,
                        patchOperation.index,
                        getItemOffsetOrIndex(patchOperation.index, patchOperation.newItem),
                        countedItemCursor,
                        offsetAfterWritten
                );
                offsetAfterWritten += itemSize;
                ++countedItemCursor;
            } else
            if (indexToReplaceOperationMap.containsKey(countedItemCursor)) {
                PatchOperation<T> patchOperation = indexToReplaceOperationMap.get(countedItemCursor);
                if (isNeedToMakeAlign) {
                    offsetAfterWritten = SizeOf.roundToTimesOfFour(offsetAfterWritten);
                }
                T adjustedItem = adjustItem(oldToPatchedIndexMap, patchOperation.newItem);
                this.patchOperationList.add(
                        new PatchOperation<>(patchOperation.op, patchOperation.index, adjustedItem)
                );
                int itemSize = getItemSize(adjustedItem);
                updateIndexOrOffset(
                        oldToPatchedIndexMap,
                        patchOperation.index,
                        getItemOffsetOrIndex(patchOperation.index, patchOperation.newItem),
                        countedItemCursor,
                        offsetAfterWritten
                );
                offsetAfterWritten += itemSize;
                ++countedItemCursor;
            } else
            if (indexToDelOperationMap.containsKey(oldItemCursor)) {
                this.patchOperationList.add(
                        indexToDelOperationMap.get(oldItemCursor)
                );
                ++oldItemCursor;
            } else
            if (indexToReplaceOperationMap.containsKey(oldItemCursor)) {
                ++oldItemCursor;
            } else {
                if (isNeedToMakeAlign) {
                    offsetAfterWritten = SizeOf.roundToTimesOfFour(offsetAfterWritten);
                }
                T adjustedOldItem = adjustItem(oldToPatchedIndexMap, adjuestedOldIndexedItemsWithOrigOrder[oldItemCursor].getValue());
                int itemSize = getItemSize(adjustedOldItem);

                int adjustedOldItemCursor = oldItemCursor;
                if (this.oldIndexToNewIndexMap.containsKey(adjustedOldItemCursor)) {
                    adjustedOldItemCursor = this.oldIndexToNewIndexMap.get(adjustedOldItemCursor);
                }

                int adjustedOldItemOffset = getItemOffsetOrIndex(oldItemCursor, adjustedOldItem);
                if (this.oldOffsetToNewOffsetMap.containsKey(adjustedOldItemOffset)) {
                    adjustedOldItemOffset = this.oldOffsetToNewOffsetMap.get(adjustedOldItemOffset);
                }

                updateIndexOrOffset(
                        oldToPatchedIndexMap,
                        adjustedOldItemCursor,
                        adjustedOldItemOffset,
                        countedItemCursor,
                        offsetAfterWritten
                );

                offsetAfterWritten += itemSize;
                ++oldItemCursor;
                ++countedItemCursor;
            }
        }

        if (oldItemCursor != oldItemCount) {
            throw new IllegalStateException("bad patch operation sequence.");
        }

        while (countedItemCursor < newItemCount) {
            PatchOperation<T> patchOperation = indexToAddOperationMap.get(countedItemCursor);
            if (patchOperation != null) {
                if (isNeedToMakeAlign) {
                    offsetAfterWritten = SizeOf.roundToTimesOfFour(offsetAfterWritten);
                }
                T adjustedItem = adjustItem(oldToPatchedIndexMap, patchOperation.newItem);
                this.patchOperationList.add(
                        new PatchOperation<>(patchOperation.op, patchOperation.index, adjustedItem)
                );
                int itemSize = getItemSize(adjustedItem);
                updateIndexOrOffset(
                        oldToPatchedIndexMap,
                        patchOperation.index,
                        getItemOffsetOrIndex(patchOperation.index, patchOperation.newItem),
                        countedItemCursor,
                        offsetAfterWritten
                );
                offsetAfterWritten += itemSize;
            } else {
                throw new IllegalStateException("expected OP_ADD patch operation, but null was found.");
            }
            ++countedItemCursor;
        }

        this.patchedSectionSize = SizeOf.roundToTimesOfFour(offsetAfterWritten - baseOffset);
    }
}
