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

package com.tencent.tinker.build.dexdifflib.algorithms;

import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.commons.dexdifflib.algorithm.AbstractAlgorithm;
import com.tencent.tinker.commons.dexdifflib.struct.IndexedItem;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecord;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ItemDiffAlgorithm<T extends Comparable<T>> extends AbstractAlgorithm {
    private static ComparatorForPatchOpOptimize<PatchOpRecord<?>> comparatorForPatchOpOptimize = new ComparatorForPatchOpOptimize<>();
    private final PatchOpRecordList<T> result;
    private       IndexedItem<T>[]     oldIndexedItems;
    private       IndexedItem<T>[]     newIndexedItems;

    public ItemDiffAlgorithm(PatchOpRecordList<T> result) {
        this.result = result;
    }

    protected abstract T getOldItem(int index);

    protected abstract int getOldItemCount();

    protected abstract T getNewItem(int index);

    protected abstract int getNewItemCount();

    protected abstract int compareItem(T oldItem, T newItem);

    protected abstract void updateIndexedItem(IndexedItem<T> oldIndexedItem, IndexedItem<T> newIndexedItem);

    @SuppressWarnings("unchecked")
    @Override
    public ItemDiffAlgorithm prepare() {
        int oldItemCount = getOldItemCount();
        this.oldIndexedItems = (IndexedItem<T>[]) Array.newInstance(IndexedItem.class, oldItemCount);
        for (int i = 0; i < oldItemCount; ++i) {
            this.oldIndexedItems[i] = new IndexedItem<>(i, getOldItem(i));
        }

        Arrays.sort(this.oldIndexedItems);

        int newItemCount = getNewItemCount();
        this.newIndexedItems = (IndexedItem<T>[]) Array.newInstance(IndexedItem.class, newItemCount);
        for (int i = 0; i < newItemCount; ++i) {
            this.newIndexedItems[i] = new IndexedItem<>(i, getNewItem(i));
        }
        Arrays.sort(this.newIndexedItems);

        return this;
    }

    @Override
    public ItemDiffAlgorithm process() {
        PatchOpRecordList<T> rawResult = new PatchOpRecordList<>();

        generateRawPatchOpList(rawResult);
        optimizePatchOpsStage1(rawResult);
        optimizePatchOpsStage2(rawResult);

        this.result.clear();
        this.result.addAll(rawResult);

        return this;
    }

    private void generateRawPatchOpList(PatchOpRecordList<T> rawResult) {
        int oldIndexedItemCount = oldIndexedItems.length;
        int newIndexedItemCount = newIndexedItems.length;

        int oldCurrPos = 0;
        int newCurrPos = 0;
        while (oldCurrPos < oldIndexedItemCount || newCurrPos < newIndexedItemCount) {
            if (oldCurrPos >= oldIndexedItemCount) {
                // rest newValues
                IndexedItem<T> newIndexedItem = this.newIndexedItems[newCurrPos];
                rawResult.add(PatchOpRecord.createAddOpRecord(newIndexedItem.index, newIndexedItem.item));
                ++newCurrPos;
            } else if (newCurrPos >= newIndexedItemCount) {
                // rest oldItemues
                IndexedItem<T> oldIndexedItem = this.oldIndexedItems[oldCurrPos];
                rawResult.add(PatchOpRecord.<T>createDelOpRecord(oldIndexedItem.index));
                ++oldCurrPos;
            } else {
                IndexedItem<T> oldIndexedItem = this.oldIndexedItems[oldCurrPos];
                IndexedItem<T> newIndexedItem = this.newIndexedItems[newCurrPos];
                int cmpRes = compareItem(oldIndexedItem.item, newIndexedItem.item);
                if (cmpRes < 0) {
                    // del until oldIndexedItem is equal to newIndexedItem
                    rawResult.add(PatchOpRecord.<T>createDelOpRecord(oldIndexedItem.index));
                    ++oldCurrPos;
                } else if (cmpRes > 0) {
                    // add until newIndexedItem is equal to oldIndexedItem
                    rawResult.add(PatchOpRecord.createAddOpRecord(newIndexedItem.index, newIndexedItem.item));
                    ++newCurrPos;
                } else {
                    if ((oldIndexedItem.item instanceof SectionItem) && (newIndexedItem.item instanceof SectionItem)) {
                        if (((SectionItem<T>) oldIndexedItem.item).off != ((SectionItem<T>) newIndexedItem.item).off) {
                            updateIndexedItem(oldIndexedItem, newIndexedItem);
                        } else if (oldIndexedItem.index != newIndexedItem.index) {
                            updateIndexedItem(oldIndexedItem, newIndexedItem);
                        }
                    } else if (oldIndexedItem.index != newIndexedItem.index) {
                        updateIndexedItem(oldIndexedItem, newIndexedItem);
                    }

                    if (oldIndexedItem.index != newIndexedItem.index) {
                        rawResult.add(PatchOpRecord.<T>createMoveOpRecord(oldIndexedItem.index, newIndexedItem.index));
                    }

                    ++oldCurrPos;
                    ++newCurrPos;
                }
            }
        }
    }

    private void optimizePatchOpsStage1(PatchOpRecordList<T> rawResult) {
        PatchOpRecordList<T> tmpResStorage = new PatchOpRecordList<>(rawResult);
        Collections.sort(tmpResStorage, comparatorForPatchOpOptimize);
        int currPos = 0;
        int rawDiffOpsCount = rawResult.size();

        rawResult.clear();

        while (currPos < rawDiffOpsCount) {
            PatchOpRecord<T> currOp = tmpResStorage.get(currPos);
            switch (currOp.op) {
                case PatchOpRecord.OP_ADD: {
                    rawResult.add(currOp);
                    ++currPos;
                    break;
                }
                case PatchOpRecord.OP_DEL: {
                    if (currPos < rawDiffOpsCount - 1) {
                        PatchOpRecord<T> nextOp = tmpResStorage.get(currPos + 1);
                        if ((nextOp.op == PatchOpRecord.OP_ADD) && nextOp.newIndex == currOp.newIndex) {
                            rawResult.add(PatchOpRecord.createReplaceOpRecord(currOp.oldIndex, nextOp.newItem));
                            currPos += 2;
                            continue;
                        }
                    }
                    rawResult.add(currOp);
                    ++currPos;
                    break;
                }
                case PatchOpRecord.OP_MOVE: {
                    rawResult.add(currOp);
                    ++currPos;
                    break;
                }
            }
        }
    }

    private void optimizePatchOpsStage2(PatchOpRecordList<T> rawResult) {
        PatchOpRecordList<T> tmpResStorage = new PatchOpRecordList<>(rawResult);

        Set<Integer> deletedOldIndexSet = new HashSet<>();
        Set<Integer> occupiedNewIndexSet = new HashSet<>();
        Map<Integer, PatchOpRecord<T>> movedIndexMap = new HashMap<>();

        rawResult.clear();

        for (PatchOpRecord<T> currOp : tmpResStorage) {
            switch (currOp.op) {
                case PatchOpRecord.OP_ADD: {
                    rawResult.add(currOp);
                    occupiedNewIndexSet.add(currOp.newIndex);
                    break;
                }
                case PatchOpRecord.OP_DEL: {
                    rawResult.add(currOp);
                    deletedOldIndexSet.add(currOp.oldIndex);
                    break;
                }
                case PatchOpRecord.OP_MOVE: {
                    movedIndexMap.put(currOp.oldIndex, currOp);
                    break;
                }
                case PatchOpRecord.OP_REPLACE: {
                    rawResult.add(currOp);
                    deletedOldIndexSet.add(currOp.oldIndex);
                    occupiedNewIndexSet.add(currOp.newIndex);
                    break;
                }
                default: {
                    break;
                }
            }
        }

        int currNewIndex = 0;
        for (int i = 0; i < oldIndexedItems.length; ++i) {
            if (deletedOldIndexSet.contains(i)) continue;
            while (currNewIndex < newIndexedItems.length && occupiedNewIndexSet.contains(currNewIndex)) {
                ++currNewIndex;
            }
            if (currNewIndex >= newIndexedItems.length) break;

            if (movedIndexMap.containsKey(i)) {
                int newIndexByMoving = movedIndexMap.get(i).newIndex;
                boolean shouldAddMoveOp = currNewIndex != newIndexByMoving;
                if (!shouldAddMoveOp) {
                    for (int j = i - 1; j >= newIndexByMoving; --j) {
                        if (!deletedOldIndexSet.contains(j)) {
                            shouldAddMoveOp = true;
                            break;
                        }
                    }
                }
                if (shouldAddMoveOp) {
                    rawResult.add(movedIndexMap.get(i));
                }
                deletedOldIndexSet.add(i);
                occupiedNewIndexSet.add(newIndexByMoving);
            } else {
                occupiedNewIndexSet.add(currNewIndex);
            }
        }
    }

    private static class ComparatorForPatchOpOptimize<U extends PatchOpRecord<?>> implements Comparator<U> {
        @Override
        public int compare(U o1, U o2) {
            if (o1.newIndex != o2.newIndex) {
                return o1.newIndex - o2.newIndex;
            }
            if (o1.op != o2.op) {
                return o1.op - o2.op;
            }
            return o1.oldIndex - o2.oldIndex;
        }
    }
}
