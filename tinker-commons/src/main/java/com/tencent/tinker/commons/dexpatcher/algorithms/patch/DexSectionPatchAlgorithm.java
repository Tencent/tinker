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

import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by tomystang on 2016/6/29.
 */
public abstract class DexSectionPatchAlgorithm<T extends Comparable<T>> {
    protected final DexPatchFile patchFile;

    protected final Dex oldDex;

    /**
     * IndexMap for mapping moved old items
     */
    private final IndexMap selfIndexMapForInsert;

    public DexSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            IndexMap selfIndexMapForInsert
    ) {
        this.patchFile = patchFile;
        this.oldDex = oldDex;
        this.selfIndexMapForInsert = selfIndexMapForInsert;
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
     * Adjust {@code item} using specific {@code indexMap}
     */
    protected T adjustItem(IndexMap indexMap, T item) {
        return item;
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
        if (item instanceof TableOfContents.Section.Item) {
            return ((TableOfContents.Section.Item<?>) item).off;
        } else {
            return index;
        }
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

    public void execute() {
        TableOfContents.Section tocSec = getTocSection(this.oldDex);
        Dex.Section oldSection = null;
        int oldItemCount = 0;
        if (tocSec.exists()) {
            oldSection = this.oldDex.openSection(tocSec);
            oldItemCount = tocSec.size;
        }

        int deletedItemCount = patchFile.getBuffer().readUleb128();
        int[] deletedIndices = readDeltaIndiciesOrOffsets(deletedItemCount);
        int deletedItemCounter = 0;

        int addedItemCount = patchFile.getBuffer().readUleb128();
        int[] addedIndices = readDeltaIndiciesOrOffsets(addedItemCount);
        int addActionCursor = 0;

        int replacedItemCount = patchFile.getBuffer().readUleb128();
        int[] replacedIndices = readDeltaIndiciesOrOffsets(replacedItemCount);
        int replaceActionCursor = 0;

        // Now rest data are added and replaced items arranged in the order of
        // added indices and replaced indices.
        int writtenItemCursor = 0;
        int oldItemCursor = 0;
        List<Integer> deletedOffsetList = new ArrayList<>(deletedItemCount);
        while (oldItemCursor < oldItemCount) {
            if (addActionCursor < addedItemCount && addedIndices[addActionCursor] == writtenItemCursor) {
                T addedItem = nextItem(patchFile.getBuffer());
                writePatchedItem(addedItem);
                ++addActionCursor;
                ++writtenItemCursor;
            } else if (replaceActionCursor < replacedItemCount && replacedIndices[replaceActionCursor] == writtenItemCursor) {
                T replacedItem = nextItem(patchFile.getBuffer());
                writePatchedItem(replacedItem);
                ++replaceActionCursor;
                ++writtenItemCursor;
            } else
            if (Arrays.binarySearch(deletedIndices, oldItemCursor) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                deletedOffsetList.add(getItemOffsetOrIndex(oldItemCursor, skippedOldItem));
                ++oldItemCursor;
                ++deletedItemCounter;
            } else
            if (Arrays.binarySearch(replacedIndices, oldItemCursor) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                deletedOffsetList.add(getItemOffsetOrIndex(oldItemCursor, skippedOldItem));
                ++oldItemCursor;
            } else {
                T oldItem = adjustItem(this.selfIndexMapForInsert, nextItem(oldSection));

                int currOldItemOffset = writePatchedItem(oldItem);

                updateIndexOrOffset(
                        this.selfIndexMapForInsert,
                        oldItemCursor,
                        getItemOffsetOrIndex(oldItemCursor, oldItem),
                        writtenItemCursor,
                        currOldItemOffset
                );

                if (!deletedOffsetList.isEmpty()) {
                    int deletedOffset = deletedOffsetList.remove(0);
                    updateIndexOrOffset(
                            this.selfIndexMapForInsert,
                            0,
                            deletedOffset,
                            0,
                            currOldItemOffset
                    );
                }

                ++oldItemCursor;
                ++writtenItemCursor;
            }
        }

        if (deletedItemCounter != deletedItemCount || replaceActionCursor != replacedItemCount) {
            throw new IllegalStateException("bad patch operation sequence.");
        }

        while (addActionCursor < addedItemCount) {
            T addedItem = nextItem(patchFile.getBuffer());
            writePatchedItem(addedItem);
            ++addActionCursor;
            ++writtenItemCursor;
        }
    }
}
