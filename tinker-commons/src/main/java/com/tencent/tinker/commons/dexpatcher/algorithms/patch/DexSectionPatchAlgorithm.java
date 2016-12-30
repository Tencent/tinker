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

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.util.AbstractIndexMap;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;

import java.util.Arrays;

/**
 * Created by tangyinsheng on 2016/6/29.
 */
public abstract class DexSectionPatchAlgorithm<T extends Comparable<T>> {
    protected final DexPatchFile patchFile;

    protected final Dex oldDex;

    /**
     * SparseIndexMap for mapping old item to corresponding one in patched item.
     */
    private final SparseIndexMap oldToPatchedIndexMap;

    public DexSectionPatchAlgorithm(DexPatchFile patchFile, Dex oldDex, SparseIndexMap oldToPatchedIndexMap) {
        this.patchFile = patchFile;
        this.oldDex = oldDex;
        this.oldToPatchedIndexMap = oldToPatchedIndexMap;
    }

    /**
     * Get {@link TableOfContents.Section} from {@code dex}.
     */
    protected abstract TableOfContents.Section getTocSection(Dex dex);

    /**
     * Get next item in {@code section}.
     */
    protected abstract T nextItem(DexDataBuffer section);

    /**
     * Get size of {@code item}.
     */
    protected abstract int getItemSize(T item);

    /**
     * Adjust {@code item} using specific {@code sparseIndexMap}
     */
    protected T adjustItem(AbstractIndexMap indexMap, T item) {
        return item;
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
     * Output patched item. This method should be overrided by subclass
     * so that patched item can be written to right place.
     * <p/>
     * Returns the offset where {@code patchedItem} is written. (<b>Must be valid.</b>)
     */
    protected abstract int writePatchedItem(T patchedItem);

    private int[] readDeltaIndiciesOrOffsets(int count) {
        int[] result = new int[count];
        int lastVal = 0;
        for (int i = 0; i < count; ++i) {
            int delta = patchFile.getBuffer().readSleb128();
            lastVal = lastVal + delta;
            result[i] = lastVal;
        }
        return result;
    }

    /**
     * Adapter method for item's offset fetching, if an item is not
     * inherited from {@link TableOfContents.Section.Item} (which means it is a simple item in dex section
     * that doesn't need multiple members to describe), this method
     * return {@code index} instead.
     */
    private int getItemOffsetOrIndex(int index, T item) {
        if (item instanceof TableOfContents.Section.Item) {
            return ((TableOfContents.Section.Item<?>) item).off;
        } else {
            return index;
        }
    }

    public void execute() {
        final int deletedItemCount = patchFile.getBuffer().readUleb128();
        final int[] deletedIndices = readDeltaIndiciesOrOffsets(deletedItemCount);

        final int addedItemCount = patchFile.getBuffer().readUleb128();
        final int[] addedIndices = readDeltaIndiciesOrOffsets(addedItemCount);

        final int replacedItemCount = patchFile.getBuffer().readUleb128();
        final int[] replacedIndices = readDeltaIndiciesOrOffsets(replacedItemCount);

        final TableOfContents.Section tocSec = getTocSection(this.oldDex);
        Dex.Section oldSection = null;

        int oldItemCount = 0;
        if (tocSec.exists()) {
            oldSection = this.oldDex.openSection(tocSec);
            oldItemCount = tocSec.size;
        }

        // Now rest data are added and replaced items arranged in the order of
        // added indices and replaced indices.
        doFullPatch(
                oldSection, oldItemCount, deletedIndices, addedIndices, replacedIndices
        );
    }

    private void doFullPatch(
            Dex.Section oldSection,
            int oldItemCount,
            int[] deletedIndices,
            int[] addedIndices,
            int[] replacedIndices
    ) {
        int deletedItemCount = deletedIndices.length;
        int addedItemCount = addedIndices.length;
        int replacedItemCount = replacedIndices.length;
        int newItemCount = oldItemCount + addedItemCount - deletedItemCount;

        int deletedItemCounter = 0;
        int addActionCursor = 0;
        int replaceActionCursor = 0;

        int oldIndex = 0;
        int patchedIndex = 0;
        while (oldIndex < oldItemCount || patchedIndex < newItemCount) {
            if (addActionCursor < addedItemCount && addedIndices[addActionCursor] == patchedIndex) {
                T addedItem = nextItem(patchFile.getBuffer());
                int patchedOffset = writePatchedItem(addedItem);
                ++addActionCursor;
                ++patchedIndex;
            } else
            if (replaceActionCursor < replacedItemCount && replacedIndices[replaceActionCursor] == patchedIndex) {
                T replacedItem = nextItem(patchFile.getBuffer());
                int patchedOffset = writePatchedItem(replacedItem);
                ++replaceActionCursor;
                ++patchedIndex;
            } else
            if (Arrays.binarySearch(deletedIndices, oldIndex) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                markDeletedIndexOrOffset(
                        oldToPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, skippedOldItem)
                );
                ++oldIndex;
                ++deletedItemCounter;
            } else
            if (Arrays.binarySearch(replacedIndices, oldIndex) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                markDeletedIndexOrOffset(
                        oldToPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, skippedOldItem)
                );
                ++oldIndex;
            } else
            if (oldIndex < oldItemCount) {
                T oldItem = adjustItem(this.oldToPatchedIndexMap, nextItem(oldSection));

                int patchedOffset = writePatchedItem(oldItem);

                updateIndexOrOffset(
                        this.oldToPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, oldItem),
                        patchedIndex,
                        patchedOffset
                );

                ++oldIndex;
                ++patchedIndex;
            }
        }

        if (addActionCursor != addedItemCount || deletedItemCounter != deletedItemCount
                || replaceActionCursor != replacedItemCount
        ) {
            throw new IllegalStateException(
                    String.format(
                            "bad patch operation sequence. addCounter: %d, addCount: %d, "
                                    + "delCounter: %d, delCount: %d, "
                                    + "replaceCounter: %d, replaceCount:%d",
                            addActionCursor,
                            addedItemCount,
                            deletedItemCounter,
                            deletedItemCount,
                            replaceActionCursor,
                            replacedItemCount
                    )
            );
        }
    }
}
