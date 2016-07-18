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

package com.tencent.tinker.commons.dexdifflib.algorithm;

import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.commons.dexdifflib.struct.IndexedItem;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecord;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;

public abstract class ItemPatchAlgorithm<T extends Comparable<T>> extends AbstractAlgorithm {
    private final PatchOpRecordList<T> patchOpList;
    private int oldItemCount = 0;
    private int newItemCount = 0;

    protected ItemPatchAlgorithm(PatchOpRecordList<T> patchOpList) {
        this.patchOpList = (patchOpList);
    }

    protected abstract T getOldItem(int index);

    protected abstract int getOldItemCount();

    protected abstract void beforePatchItemConstruct(int newIndex);

    protected abstract void updateIndex(int oldOffset, int oldIndex, int newIndex);

    protected abstract void constructPatchedItem(int newIndex, T newItem);


    @SuppressWarnings("unchecked")
    @Override
    public ItemPatchAlgorithm<T> prepare() {
        oldItemCount = getOldItemCount();
        newItemCount = oldItemCount;
        for (PatchOpRecord<T> patchOp : patchOpList) {
            if (patchOp.op == PatchOpRecord.OP_DEL) {
                --newItemCount;
            } else if (patchOp.op == PatchOpRecord.OP_ADD) {
                ++newItemCount;
            }
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ItemPatchAlgorithm<T> process() {
        Object[] oldItems = new Object[oldItemCount];
        for (int idx = 0; idx < oldItemCount; ++idx) {
            oldItems[idx] = getOldItem(idx);
        }

        int[] newItemIndices = new int[newItemCount];
        Object[] newItems = new Object[newItemCount];

        for (PatchOpRecord<T> patchOp : patchOpList) {
            switch (patchOp.op) {
                case PatchOpRecord.OP_ADD: {
                    newItemIndices[patchOp.newIndex] = IndexedItem.INDEX_UNSET;
                    newItems[patchOp.newIndex] = patchOp.newItem;
                    break;
                }
                case PatchOpRecord.OP_MOVE: {
                    newItemIndices[patchOp.newIndex] = patchOp.oldIndex;
                    newItems[patchOp.newIndex] = oldItems[patchOp.oldIndex];
                    oldItems[patchOp.oldIndex] = null;
                    break;
                }
                case PatchOpRecord.OP_REPLACE: {
                    newItemIndices[patchOp.newIndex] = IndexedItem.INDEX_UNSET;
                    newItems[patchOp.newIndex] = patchOp.newItem;
                    oldItems[patchOp.oldIndex] = null;
                    break;
                }
                case PatchOpRecord.OP_DEL: {
                    oldItems[patchOp.oldIndex] = null;
                    break;
                }
                default: {
                    break;
                }
            }
        }

        int currNewItemIdx = 0;
        for (int i = 0; i < oldItemCount; ++i) {
            if (oldItems[i] != null) {
                while (currNewItemIdx < newItemCount && newItems[currNewItemIdx] != null) {
                    ++currNewItemIdx;
                }
                if (currNewItemIdx >= newItemCount) break;
                newItemIndices[currNewItemIdx] = i;
                newItems[currNewItemIdx] = oldItems[i];
                oldItems[i] = null;
            }
        }

        for (int i = 0; i < newItemCount; ++i) {
            beforePatchItemConstruct(i);

            if (newItemIndices[i] != IndexedItem.INDEX_UNSET) {
                Object item = newItems[i];
                if (item instanceof SectionItem<?>) {
                    updateIndex(((SectionItem<?>) item).off, newItemIndices[i], i);
                } else {
                    updateIndex(newItemIndices[i], newItemIndices[i], i);
                }
            }

            constructPatchedItem(i, (T) newItems[i]);
        }

        return this;
    }
}
