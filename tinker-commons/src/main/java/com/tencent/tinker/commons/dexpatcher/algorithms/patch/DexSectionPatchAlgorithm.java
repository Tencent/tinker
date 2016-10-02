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
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.util.Hex;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;

import java.util.Arrays;

/**
 * Created by tangyinsheng on 2016/6/29.
 */
public abstract class DexSectionPatchAlgorithm<T extends Comparable<T>> {
    protected final DexPatchFile patchFile;

    protected final Dex oldDex;

    /**
     * IndexMap for mapping old item to corresponding one in full patch.
     */
    private final IndexMap oldToFullPatchedIndexMap;

    /**
     * IndexMap for mapping item in full patch to corresponding one in small patch.
     */
    private final IndexMap fullPatchedToSmallPatchedIndexMap;

    /**
     * Signature string of dex we're processing. For extra info file usage.
     */
    private final String oldDexSignStr;
    private SmallPatchedDexItemChooser smallPatchedDexItemChooser = null;

    public DexSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            IndexMap oldToFullPatchedIndexMap,
            IndexMap fullPatchedToSmallPatchedIndexMap
    ) {
        this(patchFile, oldDex, oldToFullPatchedIndexMap, fullPatchedToSmallPatchedIndexMap, null);
    }

    public DexSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            IndexMap oldToFullPatchedIndexMap,
            IndexMap fullPatchedToSmallPatchedIndexMap,
            SmallPatchedDexItemChooser smallPatchedDexItemChooser
    ) {
        this.patchFile = patchFile;
        this.oldDex = oldDex;
        this.oldToFullPatchedIndexMap = oldToFullPatchedIndexMap;
        this.fullPatchedToSmallPatchedIndexMap = fullPatchedToSmallPatchedIndexMap;
        this.oldDexSignStr = Hex.toHexString(oldDex.computeSignature(false));
        this.smallPatchedDexItemChooser = smallPatchedDexItemChooser;
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
     * Mark deleted index or offset in {@code indexMap}.
     *
     * Here we mark deleted item for such a case like this:
     *   Item in DebugInfo section reference a string in StringData section
     *   by index X, while in patched dex, the referenced string is removed.
     *
     * The {@code indexMap} must be aware of this case and return -1
     * instead of the original value X.
     *
     * Further more, the special value -1 is not chosen by our inspiration but
     * the definition of NO_INDEX in document of dex file format.
     */
    protected void markDeletedIndexOrOffset(IndexMap indexMap, int deletedIndex, int deletedOffset) {
        // Should override by subclass if needed.
    }

    /**
     * Judge if item on index {@code patchedIndex} should be kept in small dex.
     */
    protected final boolean isPatchedItemInSmallPatchedDex(String oldDexSignStr, int patchedIndex) {
        if (this.smallPatchedDexItemChooser != null) {
            return this.smallPatchedDexItemChooser
                    .isPatchedItemInSmallPatchedDex(oldDexSignStr, patchedIndex);
        } else {
            return true;
        }
    }

    /**
     * Return base offset of current section in full patched dex.
     */
    protected abstract int getFullPatchSectionBase();

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
        int deletedItemCount;
        int[] deletedIndices;

        int addedItemCount;
        int[] addedIndices;

        int replacedItemCount;
        int[] replacedIndices;

        if (patchFile != null) {
            deletedItemCount = patchFile.getBuffer().readUleb128();
            deletedIndices = readDeltaIndiciesOrOffsets(deletedItemCount);

            addedItemCount = patchFile.getBuffer().readUleb128();
            addedIndices = readDeltaIndiciesOrOffsets(addedItemCount);

            replacedItemCount = patchFile.getBuffer().readUleb128();
            replacedIndices = readDeltaIndiciesOrOffsets(replacedItemCount);
        } else {
            deletedItemCount = 0;
            deletedIndices = new int[deletedItemCount];

            addedItemCount = 0;
            addedIndices = new int[addedItemCount];

            replacedItemCount = 0;
            replacedIndices = new int[replacedItemCount];
        }

        TableOfContents.Section tocSec = getTocSection(this.oldDex);
        Dex.Section oldSection = null;

        int oldItemCount = 0;
        if (tocSec.exists()) {
            oldSection = this.oldDex.openSection(tocSec);
            oldItemCount = tocSec.size;
        }

        // Now rest data are added and replaced items arranged in the order of
        // added indices and replaced indices.
        boolean genFullPatchDex = (fullPatchedToSmallPatchedIndexMap == null);

        if (genFullPatchDex) {
            doFullPatch(
                    oldSection, oldItemCount, deletedIndices, addedIndices, replacedIndices
            );
        } else {
            doSmallPatch(
                    oldSection, oldItemCount, deletedIndices, addedIndices, replacedIndices
            );
        }
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
                        oldToFullPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, skippedOldItem)
                );
                ++oldIndex;
                ++deletedItemCounter;
            } else
            if (Arrays.binarySearch(replacedIndices, oldIndex) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                markDeletedIndexOrOffset(
                        oldToFullPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, skippedOldItem)
                );
                ++oldIndex;
            } else
            if (oldIndex < oldItemCount) {
                T oldItem = adjustItem(this.oldToFullPatchedIndexMap, nextItem(oldSection));

                int patchedOffset = writePatchedItem(oldItem);

                updateIndexOrOffset(
                        this.oldToFullPatchedIndexMap,
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

    private void doSmallPatch(
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
        int fullPatchedIndex = 0;
        int fullPatchedOffset = getFullPatchSectionBase();
        int smallPatchedIndex = 0;
        while (oldIndex < oldItemCount || fullPatchedIndex < newItemCount) {
            if (addActionCursor < addedItemCount && addedIndices[addActionCursor] == fullPatchedIndex) {
                T addedItem = nextItem(patchFile.getBuffer());
                ++addActionCursor;

                if (getTocSection(oldDex).isElementFourByteAligned) {
                    fullPatchedOffset = SizeOf.roundToTimesOfFour(fullPatchedOffset);
                }

                if (isPatchedItemInSmallPatchedDex(this.oldDexSignStr, fullPatchedIndex)) {
                    T adjustedItem = adjustItem(fullPatchedToSmallPatchedIndexMap, addedItem);
                    int smallPatchedOffset = writePatchedItem(adjustedItem);
                    updateIndexOrOffset(
                            fullPatchedToSmallPatchedIndexMap,
                            fullPatchedIndex,
                            fullPatchedOffset,
                            smallPatchedIndex,
                            smallPatchedOffset
                    );
                    ++smallPatchedIndex;
                }

                ++fullPatchedIndex;
                fullPatchedOffset += getItemSize(addedItem);
            } else
            if (replaceActionCursor < replacedItemCount && replacedIndices[replaceActionCursor] == fullPatchedIndex) {
                T replacedItem = nextItem(patchFile.getBuffer());
                ++replaceActionCursor;

                if (getTocSection(oldDex).isElementFourByteAligned) {
                    fullPatchedOffset = SizeOf.roundToTimesOfFour(fullPatchedOffset);
                }

                if (isPatchedItemInSmallPatchedDex(this.oldDexSignStr, fullPatchedIndex)) {
                    T adjustedItem = adjustItem(fullPatchedToSmallPatchedIndexMap, replacedItem);
                    int smallPatchedOffset = writePatchedItem(adjustedItem);
                    updateIndexOrOffset(
                            fullPatchedToSmallPatchedIndexMap,
                            fullPatchedIndex,
                            fullPatchedOffset,
                            smallPatchedIndex,
                            smallPatchedOffset
                    );
                    ++smallPatchedIndex;
                }

                ++fullPatchedIndex;
                fullPatchedOffset += getItemSize(replacedItem);
            } else
            if (Arrays.binarySearch(deletedIndices, oldIndex) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                markDeletedIndexOrOffset(
                        oldToFullPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, skippedOldItem)
                );
                ++oldIndex;
                ++deletedItemCounter;
            } else
            if (Arrays.binarySearch(replacedIndices, oldIndex) >= 0) {
                T skippedOldItem = nextItem(oldSection); // skip old item.
                markDeletedIndexOrOffset(
                        oldToFullPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, skippedOldItem)
                );
                ++oldIndex;
            } else
            if (oldIndex < oldItemCount) {
                T oldItem = nextItem(oldSection);
                T oldItemInFullPatch = adjustItem(this.oldToFullPatchedIndexMap, oldItem);

                if (getTocSection(oldDex).isElementFourByteAligned) {
                    fullPatchedOffset = SizeOf.roundToTimesOfFour(fullPatchedOffset);
                }

                if (isPatchedItemInSmallPatchedDex(this.oldDexSignStr, fullPatchedIndex)) {
                    T patchedItemInSmallPatch = adjustItem(
                            this.fullPatchedToSmallPatchedIndexMap, oldItemInFullPatch
                    );
                    int smallPatchedOffset = writePatchedItem(patchedItemInSmallPatch);
                    updateIndexOrOffset(
                            fullPatchedToSmallPatchedIndexMap,
                            fullPatchedIndex,
                            fullPatchedOffset,
                            smallPatchedIndex,
                            smallPatchedOffset
                    );
                    ++smallPatchedIndex;
                }

                updateIndexOrOffset(
                        oldToFullPatchedIndexMap,
                        oldIndex,
                        getItemOffsetOrIndex(oldIndex, oldItem),
                        fullPatchedIndex,
                        fullPatchedOffset
                );

                ++fullPatchedIndex;
                fullPatchedOffset += getItemSize(oldItemInFullPatch);

                ++oldIndex;
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

    /**
     * Indicates if an item in full patched dex with specific index
     * should be kept in small patched dex of current old dex.
     */
    public interface SmallPatchedDexItemChooser {
        boolean isPatchedItemInSmallPatchedDex(String oldDexSign, int patchedItemIndex);
    }
}
