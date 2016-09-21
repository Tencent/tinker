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

package com.tencent.tinker.build.dexpatcher.util;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.AnnotationsDirectory;
import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.EncodedValueReader;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.Leb128;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dex.util.ByteInput;
import com.tencent.tinker.android.dx.instruction.InstructionCodec;
import com.tencent.tinker.android.dx.instruction.InstructionReader;
import com.tencent.tinker.android.dx.instruction.InstructionVisitor;
import com.tencent.tinker.android.dx.instruction.ShortArrayCodeInput;
import com.tencent.tinker.android.dx.util.Hex;
import com.tencent.tinker.android.dx.util.IndexMap;
import com.tencent.tinker.build.util.DexClassesComparator;
import com.tencent.tinker.build.util.DexClassesComparator.DexClassInfo;
import com.tencent.tinker.build.util.DexClassesComparator.DexGroup;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger.IDexPatcherLogger;
import com.tencent.tinker.commons.dexpatcher.struct.SmallPatchedDexItemFile;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by tangyinsheng on 2016/8/8.
 */
public final class SmallDexPatchGenerator {
    private static final String TAG = "SmallDexPatchGenerator";

    private final List<DexGroup> oldDexGroups = new ArrayList<>();
    private final List<DexGroup> patchedDexGroups = new ArrayList<>();

    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedStringIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedTypeIdIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedTypeListIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedProtoIdIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedFieldIdIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedMethodIdIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedAnnotationIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedAnnotationSetIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedAnnotationSetRefListIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedAnnotationsDirectoryIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedEncodedArrayIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedDebugInfoIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedCodeIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedClassDataIndicesMap = new HashMap<>();
    private final Map<Dex, Set<Integer>>
            patchedDexToCollectedClassDefIndicesMap = new HashMap<>();

    private final Map<Dex, Integer>
            patchedDexToSmallPatchedStringIdOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedTypeIdOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedProtoIdOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedFieldIdOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedMethodIdOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedClassDefOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedMapListOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedTypeListOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedAnnotationSetRefListOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedAnnotationSetOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedClassDataOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedCodeOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedStringDataOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedDebugInfoOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedAnnotationOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedEncodedArrayOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedAnnotationsDirectoryOffsetMap = new HashMap<>();
    private final Map<Dex, Integer>
            patchedDexToSmallPatchedDexSizeMap = new HashMap<>();

    private final Set<String> loaderClassPatterns = new HashSet<>();

    private final DexPatcherLogger logger = new DexPatcherLogger();

    public void addLoaderClassPattern(String pattern) {
        this.loaderClassPatterns.add(pattern);
    }

    public void setLoaderClassPatterns(Collection<String> patterns) {
        this.loaderClassPatterns.clear();
        this.loaderClassPatterns.addAll(patterns);
    }

    public void clearLoaderClassPatterns() {
        this.loaderClassPatterns.clear();
    }

    public void setLogger(IDexPatcherLogger logger) {
        this.logger.setLoggerImpl(logger);
    }

    public SmallDexPatchGenerator appendDexGroup(DexGroup oldDexGroup, DexGroup patchedDexGroup) {
        if (oldDexGroup == null) {
            throw new IllegalArgumentException("oldDexGroup is null.");
        }
        if (patchedDexGroup == null) {
            throw new IllegalArgumentException("patchedDexGroup is null.");
        }

        this.oldDexGroups.add(oldDexGroup);
        this.patchedDexGroups.add(patchedDexGroup);

        // Build map between patched dex and old dex, which is used in next logic.
        if (oldDexGroup.dexes.length != patchedDexGroup.dexes.length) {
            throw new IllegalArgumentException(
                    "dex count in oldDexGroup is not matched to dex count in patchedDexGroup."
            );
        }

        return this;
    }

    public void executeAndSaveTo(File out) throws IOException {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(out));
            executeAndSaveTo(os);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception e) {
                    // ignored.
                }
            }
        }
    }

    public void executeAndSaveTo(OutputStream os) throws IOException {
        int dexGroupCount = this.oldDexGroups.size();

        // Collect all items that should be exist in small patched dex.
        for (int i = 0; i < dexGroupCount; ++i) {
            DexGroup oldDexGroup = oldDexGroups.get(i);
            DexGroup patchedDexGroup = patchedDexGroups.get(i);

            collectItemIndicesFromDexGroup(oldDexGroup, patchedDexGroup);
            calculateSmallPatchedSectionOffsets(oldDexGroup, patchedDexGroup);
        }

        saveToStream(os);
    }

    private void calculateSmallPatchedSectionOffsets(
            DexGroup oldDexGroup, DexGroup patchedDexGroup
    ) {
        if (oldDexGroup.dexes.length != patchedDexGroup.dexes.length) {
            throw new IllegalStateException("dex group contains different amount of dexes.");
        }
        int dexCount = oldDexGroup.dexes.length;
        for (int dexId = 0; dexId < dexCount; ++dexId) {
            Dex oldDex = oldDexGroup.dexes[dexId];
            Dex patchedDex = patchedDexGroup.dexes[dexId];

            final String currOldDexSignStr = Hex.toHexString(oldDex.computeSignature(false));

            IndexMap fullToSmallPatchIndexMap = new IndexMap();

            // For calculating size of mapList soon.
            // Initialize it to 2 means a dex must contains two sections: header
            // and mapList.
            int smallPatchedSectionCount = 2;

            // In next steps we do a bunch of simulations to calculate actual sizes of
            // each section in small patched dex.

            // First, calculate header and id sections size, so that we can work out
            // base offsets of data sections soon.
            int smallPatchedHeaderSize = SizeOf.HEADER_ITEM;
            int collectedStringIndicesCount = getCollectedIndicesCountSafely(
                    patchedDexToCollectedStringIndicesMap, patchedDex
            );
            int smallPatchedStringIdsSize = collectedStringIndicesCount * SizeOf.STRING_ID_ITEM;
            if (smallPatchedHeaderSize > 0) {
                ++smallPatchedSectionCount;
            }
            int collectedTypeIndicesCount = getCollectedIndicesCountSafely(
                    patchedDexToCollectedTypeIdIndicesMap, patchedDex
            );
            int smallPatchedTypeIdsSize = collectedTypeIndicesCount * SizeOf.TYPE_ID_ITEM;
            if (smallPatchedTypeIdsSize > 0) {
                ++smallPatchedSectionCount;
            }

            // Although simulatePatchOperation can calculate this value, since protoIds section
            // depends on typeLists section, we can't run protoIds Section's simulatePatchOperation
            // method so far. Instead we calculate protoIds section's size using information we known
            // directly.
            int collectedProtoIdsIndicesCount = getCollectedIndicesCountSafely(
                    patchedDexToCollectedProtoIdIndicesMap, patchedDex
            );
            int smallPatchedProtoIdsSize = collectedProtoIdsIndicesCount * SizeOf.PROTO_ID_ITEM;
            if (smallPatchedProtoIdsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int collectedFieldIdsIndicesCount = getCollectedIndicesCountSafely(
                    patchedDexToCollectedFieldIdIndicesMap, patchedDex
            );
            int smallPatchedFieldIdsSize = collectedFieldIdsIndicesCount * SizeOf.MEMBER_ID_ITEM;
            if (smallPatchedFieldIdsSize > 0) {
                ++smallPatchedSectionCount;
            }
            int collectedMethodIdsIndicesCount = getCollectedIndicesCountSafely(
                    patchedDexToCollectedMethodIdIndicesMap, patchedDex
            );
            int smallPatchedMethodIdsSize = collectedMethodIdsIndicesCount * SizeOf.MEMBER_ID_ITEM;
            if (smallPatchedMethodIdsSize > 0) {
                ++smallPatchedSectionCount;
            }
            int collectedClassDefsIndicesCount = getCollectedIndicesCountSafely(
                    patchedDexToCollectedClassDefIndicesMap, patchedDex
            );
            int smallPatchedClassDefsSize = collectedClassDefsIndicesCount * SizeOf.CLASS_DEF_ITEM;
            if (smallPatchedClassDefsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedIdSectionSize =
                    smallPatchedStringIdsSize
                            + smallPatchedTypeIdsSize
                            + smallPatchedProtoIdsSize
                            + smallPatchedFieldIdsSize
                            + smallPatchedMethodIdsSize
                            + smallPatchedClassDefsSize;

            int smallPatchedHeaderOffset = 0;

            int smallPatchedStringIdsOffset = smallPatchedHeaderOffset + smallPatchedHeaderSize;
            if (oldDex.getTableOfContents().stringIds.isElementFourByteAligned) {
                smallPatchedStringIdsOffset = SizeOf.roundToTimesOfFour(smallPatchedStringIdsOffset);
            }
            patchedDexToSmallPatchedStringIdOffsetMap.put(patchedDex, smallPatchedStringIdsOffset);

            int smallPatchedStringDatasOffset = smallPatchedHeaderSize + smallPatchedIdSectionSize;
            if (oldDex.getTableOfContents().stringDatas.isElementFourByteAligned) {
                smallPatchedStringDatasOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedStringDatasOffset);
            }
            patchedDexToSmallPatchedStringDataOffsetMap.put(patchedDex, smallPatchedStringDatasOffset);
            int smallPatchedStringDataItemsSize = new SmallPatchSimulator<StringData>(
                    patchedDex,
                    patchedDex.getTableOfContents().stringDatas,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedStringIndicesMap.get(patchedDex)
            ).simulate(smallPatchedStringDatasOffset);
            if (smallPatchedStringDataItemsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedTypeIdsOffset
                    = smallPatchedStringIdsOffset + smallPatchedStringIdsSize;
            if (oldDex.getTableOfContents().typeIds.isElementFourByteAligned) {
                smallPatchedTypeIdsOffset = SizeOf.roundToTimesOfFour(smallPatchedTypeIdsOffset);
            }
            patchedDexToSmallPatchedTypeIdOffsetMap.put(patchedDex, smallPatchedTypeIdsOffset);

            int smallPatchedTypeListsOffset
                    = smallPatchedHeaderSize
                    + smallPatchedIdSectionSize
                    + smallPatchedStringDataItemsSize;
            if (oldDex.getTableOfContents().typeLists.isElementFourByteAligned) {
                smallPatchedTypeListsOffset = SizeOf.roundToTimesOfFour(smallPatchedTypeListsOffset);
            }
            patchedDexToSmallPatchedTypeListOffsetMap.put(
                    patchedDex, smallPatchedTypeListsOffset
            );
            int smallPatchedTypeListsSize = new SmallPatchSimulator<TypeList>(
                    patchedDex,
                    patchedDex.getTableOfContents().typeLists,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedTypeListIndicesMap.get(patchedDex)
            ).simulate(smallPatchedTypeListsOffset);
            if (smallPatchedTypeListsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedProtoIdsOffset
                    = smallPatchedTypeIdsOffset + smallPatchedTypeIdsSize;
            if (oldDex.getTableOfContents().protoIds.isElementFourByteAligned) {
                smallPatchedProtoIdsOffset = SizeOf.roundToTimesOfFour(smallPatchedProtoIdsOffset);
            }
            patchedDexToSmallPatchedProtoIdOffsetMap.put(
                    patchedDex, smallPatchedProtoIdsOffset
            );

            int smallPatchedFieldIdsOffset
                    = smallPatchedProtoIdsOffset + smallPatchedProtoIdsSize;
            if (oldDex.getTableOfContents().fieldIds.isElementFourByteAligned) {
                smallPatchedFieldIdsOffset = SizeOf.roundToTimesOfFour(smallPatchedFieldIdsOffset);
            }
            patchedDexToSmallPatchedFieldIdOffsetMap.put(
                    patchedDex, smallPatchedFieldIdsOffset
            );

            int smallPatchedMethodIdsOffset
                    = smallPatchedFieldIdsOffset + smallPatchedFieldIdsSize;
            if (oldDex.getTableOfContents().methodIds.isElementFourByteAligned) {
                smallPatchedMethodIdsOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedMethodIdsOffset);
            }
            patchedDexToSmallPatchedMethodIdOffsetMap.put(
                    patchedDex, smallPatchedMethodIdsOffset
            );

            int smallPatchedAnnotationsOffset
                    = smallPatchedTypeListsOffset + smallPatchedTypeListsSize;
            if (oldDex.getTableOfContents().annotations.isElementFourByteAligned) {
                smallPatchedAnnotationsOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedAnnotationsOffset);
            }
            patchedDexToSmallPatchedAnnotationOffsetMap.put(
                    patchedDex, smallPatchedAnnotationsOffset
            );
            int smallPatchedAnnotationsSize = new SmallPatchSimulator<Annotation>(
                    patchedDex,
                    patchedDex.getTableOfContents().annotations,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedAnnotationIndicesMap.get(patchedDex)
            ).simulate(smallPatchedAnnotationsOffset);
            if (smallPatchedAnnotationsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedAnnotationSetsOffset
                    = smallPatchedAnnotationsOffset + smallPatchedAnnotationsSize;
            if (oldDex.getTableOfContents().annotationSets.isElementFourByteAligned) {
                smallPatchedAnnotationSetsOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedAnnotationSetsOffset);
            }
            patchedDexToSmallPatchedAnnotationSetOffsetMap.put(
                    patchedDex, smallPatchedAnnotationSetsOffset
            );
            int smallPatchedAnnotationSetsSize = new SmallPatchSimulator<AnnotationSet>(
                    patchedDex,
                    patchedDex.getTableOfContents().annotationSets,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedAnnotationSetIndicesMap.get(patchedDex)
            ).simulate(smallPatchedAnnotationSetsOffset);
            if (smallPatchedAnnotationSetsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedAnnotationSetRefListsOffset
                    = smallPatchedAnnotationSetsOffset
                    + smallPatchedAnnotationSetsSize;
            if (oldDex.getTableOfContents().annotationSetRefLists.isElementFourByteAligned) {
                smallPatchedAnnotationSetRefListsOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedAnnotationSetRefListsOffset);
            }
            patchedDexToSmallPatchedAnnotationSetRefListOffsetMap.put(
                    patchedDex, smallPatchedAnnotationSetRefListsOffset
            );
            int smallPatchedAnnotationSetRefListsSize
                    = new SmallPatchSimulator<AnnotationSetRefList>(
                    patchedDex,
                    patchedDex.getTableOfContents().annotationSetRefLists,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedAnnotationSetRefListIndicesMap.get(patchedDex)
            ).simulate(smallPatchedAnnotationSetRefListsOffset);
            if (smallPatchedAnnotationSetRefListsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedAnnotationsDirectoriesOffset
                    = smallPatchedAnnotationSetRefListsOffset
                    + smallPatchedAnnotationSetRefListsSize;
            if (oldDex.getTableOfContents().annotationsDirectories.isElementFourByteAligned) {
                smallPatchedAnnotationsDirectoriesOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedAnnotationsDirectoriesOffset);
            }
            patchedDexToSmallPatchedAnnotationsDirectoryOffsetMap.put(
                    patchedDex, smallPatchedAnnotationsDirectoriesOffset
            );
            int smallPatchedAnnotationsDirectoriesSize
                    = new SmallPatchSimulator<AnnotationsDirectory>(
                    patchedDex,
                    patchedDex.getTableOfContents().annotationsDirectories,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedAnnotationsDirectoryIndicesMap.get(patchedDex)
            ).simulate(smallPatchedAnnotationsDirectoriesOffset);
            if (smallPatchedAnnotationsDirectoriesSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedDebugInfoItemsOffset
                    = smallPatchedAnnotationsDirectoriesOffset
                    + smallPatchedAnnotationsDirectoriesSize;
            if (oldDex.getTableOfContents().debugInfos.isElementFourByteAligned) {
                smallPatchedDebugInfoItemsOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedDebugInfoItemsOffset);
            }
            patchedDexToSmallPatchedDebugInfoOffsetMap.put(
                    patchedDex, smallPatchedDebugInfoItemsOffset
            );
            int smallPatchedDebugInfoItemsSize = new SmallPatchSimulator<DebugInfoItem>(
                    patchedDex,
                    patchedDex.getTableOfContents().debugInfos,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedDebugInfoIndicesMap.get(patchedDex)
            ).simulate(smallPatchedDebugInfoItemsOffset);
            if (smallPatchedDebugInfoItemsSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedCodesOffset
                    = smallPatchedDebugInfoItemsOffset
                    + smallPatchedDebugInfoItemsSize;
            if (oldDex.getTableOfContents().codes.isElementFourByteAligned) {
                smallPatchedCodesOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedCodesOffset);
            }
            patchedDexToSmallPatchedCodeOffsetMap.put(
                    patchedDex, smallPatchedCodesOffset
            );
            int smallPatchedCodesSize = new SmallPatchSimulator<Code>(
                    patchedDex,
                    patchedDex.getTableOfContents().codes,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedCodeIndicesMap.get(patchedDex)
            ).simulate(smallPatchedCodesOffset);
            if (smallPatchedCodesSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedClassDatasOffset
                    = smallPatchedCodesOffset
                    + smallPatchedCodesSize;
            if (oldDex.getTableOfContents().classDatas.isElementFourByteAligned) {
                smallPatchedClassDatasOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedClassDatasOffset);
            }
            patchedDexToSmallPatchedClassDataOffsetMap.put(
                    patchedDex, smallPatchedClassDatasOffset
            );
            int smallPatchedClassDatasSize = new SmallPatchSimulator<ClassData>(
                    patchedDex,
                    patchedDex.getTableOfContents().classDatas,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedClassDataIndicesMap.get(patchedDex)
            ).simulate(smallPatchedClassDatasOffset);
            if (smallPatchedClassDatasSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedEncodedArraysOffset
                    = smallPatchedClassDatasOffset
                    + smallPatchedClassDatasSize;
            if (oldDex.getTableOfContents().encodedArrays.isElementFourByteAligned) {
                smallPatchedEncodedArraysOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedEncodedArraysOffset);
            }
            patchedDexToSmallPatchedEncodedArrayOffsetMap.put(
                    patchedDex, smallPatchedEncodedArraysOffset
            );
            int smallPatchedEncodedArraysSize = new SmallPatchSimulator<EncodedValue>(
                    patchedDex,
                    patchedDex.getTableOfContents().encodedArrays,
                    fullToSmallPatchIndexMap,
                    patchedDexToCollectedEncodedArrayIndicesMap.get(patchedDex)
            ).simulate(smallPatchedEncodedArraysOffset);
            if (smallPatchedEncodedArraysSize > 0) {
                ++smallPatchedSectionCount;
            }

            int smallPatchedClassDefsOffset
                    = smallPatchedMethodIdsOffset
                    + smallPatchedMethodIdsSize;
            if (oldDex.getTableOfContents().classDefs.isElementFourByteAligned) {
                smallPatchedClassDefsOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedClassDefsOffset);
            }
            patchedDexToSmallPatchedClassDefOffsetMap.put(
                    patchedDex, smallPatchedClassDefsOffset
            );

            // Calculate any values we still know nothing about them.
            int smallPatchedMapListOffset
                    = smallPatchedEncodedArraysOffset
                    + smallPatchedEncodedArraysSize;
            if (oldDex.getTableOfContents().mapList.isElementFourByteAligned) {
                smallPatchedMapListOffset
                        = SizeOf.roundToTimesOfFour(smallPatchedMapListOffset);
            }
            patchedDexToSmallPatchedMapListOffsetMap.put(
                    patchedDex, smallPatchedMapListOffset
            );
            int smallPatchedMapListSize
                    = SizeOf.UINT + SizeOf.MAP_ITEM * smallPatchedSectionCount;

            int smallPatchedDexSize
                    = smallPatchedMapListOffset
                    + smallPatchedMapListSize;
            patchedDexToSmallPatchedDexSizeMap.put(patchedDex, smallPatchedDexSize);
        }
    }

    private int getCollectedIndicesCountSafely(
            Map<Dex, Set<Integer>> collectedIndicesMap, Dex patchedDex
    ) {
        Set<Integer> indices = collectedIndicesMap.get(patchedDex);
        if (indices == null) {
            return 0;
        } else {
            return indices.size();
        }
    }

    private void saveToStream(OutputStream os) throws IOException {
        DexDataBuffer buffer = new DexDataBuffer();

        // Write header
        buffer.write(SmallPatchedDexItemFile.MAGIC);
        buffer.writeShort(SmallPatchedDexItemFile.CURRENT_VERSION);
        // Take the field 'firstChunkOffset' into header's size account.
        buffer.writeInt(buffer.position() + SizeOf.UINT);

        // Gather old dexes
        List<Dex> oldDexes = new ArrayList<>();
        int oldDexGroupCount = this.oldDexGroups.size();
        for (int i = 0; i < oldDexGroupCount; ++i) {
            DexGroup oldDexGroup = oldDexGroups.get(i);
            for (Dex oldDex : oldDexGroup.dexes) {
                oldDexes.add(oldDex);
            }
        }

        // Gather patched dexes
        List<Dex> patchedDexes = new ArrayList<>();
        int patchedDexGroupCount = this.patchedDexGroups.size();
        for (int i = 0; i < patchedDexGroupCount; ++i) {
            DexGroup patchedDexGroup = patchedDexGroups.get(i);
            for (Dex patchedDex : patchedDexGroup.dexes) {
                patchedDexes.add(patchedDex);
            }
        }

        // Dex sign chunk
        int oldDexSignCount = oldDexes.size();
        buffer.writeUleb128(oldDexSignCount);

        Map<String, Integer> oldDexSignToIdxInSignList = new HashMap<>();
        for (int i = 0; i < oldDexSignCount; ++i) {
            final byte[] signBytes = oldDexes.get(i).computeSignature(false);
            final String signStr = Hex.toHexString(signBytes);
            buffer.write(signBytes);
            oldDexSignToIdxInSignList.put(signStr, i);
        }

        for (Dex patchedDex : patchedDexes) {
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedStringIdOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedTypeIdOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedProtoIdOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedFieldIdOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedMethodIdOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedClassDefOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedStringDataOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedTypeListOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedAnnotationOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedAnnotationSetOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedAnnotationSetRefListOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedAnnotationsDirectoryOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedDebugInfoOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedCodeOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedClassDataOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedEncodedArrayOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedMapListOffsetMap
            );
            writeSmallPatchedSectionOffset(
                    buffer, patchedDex, patchedDexToSmallPatchedDexSizeMap
            );
        }

        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedStringIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedTypeIdIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedTypeListIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedProtoIdIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedFieldIdIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedMethodIdIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedAnnotationIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedAnnotationSetIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedAnnotationSetRefListIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedAnnotationsDirectoryIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedEncodedArrayIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedDebugInfoIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedCodeIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedClassDataIndicesMap);
        writeDataChunk(buffer, patchedDexes, patchedDexToCollectedClassDefIndicesMap);

        os.write(buffer.array());
        os.flush();
    }

    private void writeSmallPatchedSectionOffset(
            DexDataBuffer buffer,
            Dex patchedDex,
            Map<Dex, Integer> patchedDexToSmallPatchedSectionOffsetMap
    ) {
        Integer offset = patchedDexToSmallPatchedSectionOffsetMap.get(patchedDex);
        if (offset != null) {
            buffer.writeInt(offset);
        } else {
            throw new IllegalStateException("section offset is missing.");
        }
    }

    private void writeDataChunk(
            DexDataBuffer buffer,
            List<Dex> patchedDexList,
            Map<Dex, Set<Integer>> patchedDexToCollectedItemIndicesMap
    ) {
        for (Dex patchedDex : patchedDexList) {
            Set<Integer> itemIndices = patchedDexToCollectedItemIndicesMap.get(patchedDex);
            if (itemIndices == null) {
                buffer.writeUleb128(0);
            } else {
                int indexCount = itemIndices.size();
                Integer[] itemIndexArr = new Integer[indexCount];
                itemIndices.toArray(itemIndexArr);
                Arrays.sort(itemIndexArr);
                buffer.writeUleb128(indexCount);
                int prevIndex = 0;
                for (int j = 0; j < indexCount; ++j) {
                    buffer.writeSleb128(itemIndexArr[j] - prevIndex);
                    prevIndex = itemIndexArr[j];
                }
            }
        }
    }

    private boolean isClassMethodReferenceToRefAffectedClass(
            Dex owner,
            ClassData.Method[] methods,
            Collection<String> affectedClassDescs
    ) {
        if (affectedClassDescs.isEmpty() || methods == null || methods.length == 0) {
            return false;
        }

        for (ClassData.Method method : methods) {
            if (method.codeOffset == 0) {
                continue;
            }
            Code code = owner.readCode(method);
            RefToRefAffectedClassInsnVisitor refInsnVisitor =
                    new RefToRefAffectedClassInsnVisitor(owner, method, affectedClassDescs);
            InstructionReader insnReader =
                    new InstructionReader(new ShortArrayCodeInput(code.instructions));
            try {
                insnReader.accept(refInsnVisitor);
                if (refInsnVisitor.isMethodReferencedToRefAffectedClass) {
                    return true;
                }
            } catch (EOFException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }

    private void collectItemIndicesFromDexGroup(
            DexGroup oldDexGroup,
            DexGroup patchedDexGroup
    ) {
        DexClassesComparator dexClassesCmp = new DexClassesComparator("*");
        dexClassesCmp.setCompareMode(DexClassesComparator.COMPARE_MODE_CAUSE_REF_CHANGE_ONLY);
        dexClassesCmp.setIgnoredRemovedClassDescPattern(this.loaderClassPatterns);
        dexClassesCmp.startCheck(oldDexGroup, patchedDexGroup);

        Set<String> refAffectedClassDescs
                = dexClassesCmp.getChangedClassDescToInfosMap().keySet();

        Set<DexClassInfo> classInfosInPatchedDexGroup
                = patchedDexGroup.getClassInfosInDexesWithDuplicateCheck();

        Set<DexClassInfo> patchedClassInfosForItemIndexCollecting = new HashSet<>();

        for (DexClassInfo patchedClassInfo : classInfosInPatchedDexGroup) {
            if (patchedClassInfo.classDef.classDataOffset == 0) {
                continue;
            }
            ClassData patchedClassData
                    = patchedClassInfo.owner.readClassData(patchedClassInfo.classDef);

            boolean shouldAdd = isClassMethodReferenceToRefAffectedClass(
                    patchedClassInfo.owner,
                    patchedClassData.directMethods,
                    refAffectedClassDescs
            );

            if (!shouldAdd) {
                shouldAdd = isClassMethodReferenceToRefAffectedClass(
                        patchedClassInfo.owner,
                        patchedClassData.virtualMethods,
                        refAffectedClassDescs
                );
            }

            if (shouldAdd) {
                logger.i(TAG, "Add class %s to small patched dex.", patchedClassInfo.classDesc);
                patchedClassInfosForItemIndexCollecting.add(patchedClassInfo);
            }
        }

        // So far we get descriptors of classes we need to add additionally,
        // while we still need to do a fully compare to collect added classes
        // and replaced classes since they may use items in their owner dex which
        // is not modified.
        dexClassesCmp.setCompareMode(DexClassesComparator.COMPARE_MODE_NORMAL);
        dexClassesCmp.startCheck(oldDexGroup, patchedDexGroup);

        Collection<DexClassInfo> addedClassInfos = dexClassesCmp.getAddedClassInfos();
        for (DexClassInfo addClassInfo : addedClassInfos) {
            logger.i(TAG, "Add class %s to small patched dex.", addClassInfo.classDesc);
            patchedClassInfosForItemIndexCollecting.add(addClassInfo);
        }

        Collection<DexClassInfo[]> changedOldPatchedClassInfos =
                dexClassesCmp.getChangedClassDescToInfosMap().values();

        // changedOldPatchedClassInfo[1] means changedPatchedClassInfo
        for (DexClassInfo[] changedOldPatchedClassInfo : changedOldPatchedClassInfos) {
            logger.i(TAG, "Add class %s to small patched dex.", changedOldPatchedClassInfo[1].classDesc);
            patchedClassInfosForItemIndexCollecting.add(changedOldPatchedClassInfo[1]);
        }

        // Finally we collect all elements' indices of collected class.

        Map<Dex, OffsetToIndexConverter> dexToOffsetToIndexConverterMap = new HashMap<>();

        for (DexClassInfo classInfo : patchedClassInfosForItemIndexCollecting) {
            Dex owner = classInfo.owner;
            OffsetToIndexConverter offsetToIndexConverter =
                    dexToOffsetToIndexConverterMap.get(owner);

            if (offsetToIndexConverter == null) {
                offsetToIndexConverter = new OffsetToIndexConverter(owner);
                dexToOffsetToIndexConverterMap.put(owner, offsetToIndexConverter);
            }

            collectItemIndicesFromClassInfo(classInfo, offsetToIndexConverter);
        }
    }

    private void collectItemIndicesFromClassInfo(
            DexClassInfo classInfo,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        Dex owner = classInfo.owner;

        putValueIntoSetMap(
                patchedDexToCollectedClassDefIndicesMap,
                owner,
                classInfo.classDefIndex
        );

        collectItemIndicesFromTypeIndex(
                owner, classInfo.classDef.typeIndex, offsetToIndexConverter
        );

        collectItemIndicesFromTypeIndex(
                owner, classInfo.classDef.supertypeIndex, offsetToIndexConverter
        );

        collectItemIndicesFromTypeList(
                owner, classInfo.classDef.interfacesOffset, offsetToIndexConverter
        );

        collectItemIndicesFromStringIndex(
                owner, classInfo.classDef.sourceFileIndex, offsetToIndexConverter
        );

        collectItemIndicesFromAnnotationsDirectory(
                owner, classInfo.classDef.annotationsOffset, offsetToIndexConverter
        );

        collectItemIndicesFromClassData(
                owner, classInfo.classDef.classDataOffset, offsetToIndexConverter
        );

        collectItemIndicesFromEncodedArray(
                owner, classInfo.classDef.staticValuesOffset, offsetToIndexConverter
        );
    }

    private void collectItemIndicesFromStringIndex(
            Dex owner,
            int stringIndex,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (stringIndex == ClassDef.NO_INDEX) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedStringIndicesMap,
                owner,
                stringIndex
        );
    }

    private void collectItemIndicesFromTypeList(
            Dex owner,
            int typeListOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (typeListOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedTypeListIndicesMap,
                owner,
                offsetToIndexConverter.getTypeListIndexByOffset(typeListOffset)
        );

        TypeList typeList = owner.openSection(typeListOffset).readTypeList();
        for (int typeIndex : typeList.types) {
            collectItemIndicesFromTypeIndex(
                    owner, typeIndex, offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromTypeIndex(
            Dex owner,
            int typeIndex,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (typeIndex == ClassDef.NO_INDEX) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedTypeIdIndicesMap,
                owner,
                typeIndex
        );

        collectItemIndicesFromStringIndex(
                owner, owner.typeIds().get(typeIndex), offsetToIndexConverter
        );
    }

    private void collectItemIndicesFromFieldIndex(
            Dex owner,
            int fieldIndex,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (fieldIndex == ClassDef.NO_INDEX) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedFieldIdIndicesMap,
                owner,
                fieldIndex
        );

        FieldId fieldId = owner.fieldIds().get(fieldIndex);
        collectItemIndicesFromStringIndex(owner, fieldId.nameIndex, offsetToIndexConverter);
        collectItemIndicesFromTypeIndex(
                owner, fieldId.declaringClassIndex, offsetToIndexConverter
        );
        collectItemIndicesFromTypeIndex(owner, fieldId.typeIndex, offsetToIndexConverter);
    }

    private void collectItemIndicesFromMethodIndex(
            Dex owner,
            int methodIndex,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (methodIndex == ClassDef.NO_INDEX) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedMethodIdIndicesMap,
                owner,
                methodIndex
        );

        MethodId methodId = owner.methodIds().get(methodIndex);
        collectItemIndicesFromStringIndex(
                owner, methodId.nameIndex, offsetToIndexConverter
        );
        collectItemIndicesFromTypeIndex(
                owner, methodId.declaringClassIndex, offsetToIndexConverter
        );
        collectItemIndicesFromProtoIndex(
                owner, methodId.protoIndex, offsetToIndexConverter
        );
    }

    private void collectItemIndicesFromProtoIndex(
            Dex owner,
            int protoIndex,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (protoIndex == ClassDef.NO_INDEX) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedProtoIdIndicesMap,
                owner,
                protoIndex
        );

        ProtoId protoId = owner.protoIds().get(protoIndex);

        collectItemIndicesFromStringIndex(
                owner, protoId.shortyIndex, offsetToIndexConverter
        );
        collectItemIndicesFromTypeIndex(
                owner, protoId.returnTypeIndex, offsetToIndexConverter
        );
        collectItemIndicesFromTypeList(
                owner, protoId.parametersOffset, offsetToIndexConverter
        );
    }

    private void collectItemIndicesFromAnnotationsDirectory(
            Dex owner,
            int annotationsDirectoryOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (annotationsDirectoryOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedAnnotationsDirectoryIndicesMap,
                owner,
                offsetToIndexConverter.getAnnotationsDirectoryIndexByOffset(
                        annotationsDirectoryOffset
                )
        );

        AnnotationsDirectory annotationsDirectory =
                owner.openSection(annotationsDirectoryOffset).readAnnotationsDirectory();

        collectItemIndicesFromAnnotationSet(
                owner,
                annotationsDirectory.classAnnotationsOffset,
                offsetToIndexConverter
        );

        for (int[] fieldAnnoPair : annotationsDirectory.fieldAnnotations) {
            collectItemIndicesFromFieldIndex(
                    owner, fieldAnnoPair[0], offsetToIndexConverter
            );
            collectItemIndicesFromAnnotationSet(
                    owner, fieldAnnoPair[1], offsetToIndexConverter
            );
        }
        for (int[] methodAnnoPair : annotationsDirectory.methodAnnotations) {
            collectItemIndicesFromMethodIndex(
                    owner, methodAnnoPair[0], offsetToIndexConverter
            );
            collectItemIndicesFromAnnotationSet(
                    owner, methodAnnoPair[1], offsetToIndexConverter
            );
        }
        for (int[] paramAnnoPair : annotationsDirectory.parameterAnnotations) {
            collectItemIndicesFromMethodIndex(
                    owner, paramAnnoPair[0], offsetToIndexConverter
            );
            collectItemIndicesFromAnnotationSetRefList(
                    owner, paramAnnoPair[1], offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromAnnotationSetRefList(
            Dex owner,
            int annotationSetRefListOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (annotationSetRefListOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedAnnotationSetRefListIndicesMap,
                owner,
                offsetToIndexConverter.getAnnotationSetRefListIndexByOffset(
                        annotationSetRefListOffset
                )
        );

        AnnotationSetRefList annotationSetRefList =
                owner.openSection(annotationSetRefListOffset).readAnnotationSetRefList();

        for (int annotationSetOffset : annotationSetRefList.annotationSetRefItems) {
            collectItemIndicesFromAnnotationSet(
                    owner, annotationSetOffset, offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromAnnotationSet(
            Dex owner,
            int annotationSetOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (annotationSetOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedAnnotationSetIndicesMap,
                owner,
                offsetToIndexConverter.getAnnotationSetIndexByOffset(
                        annotationSetOffset
                )
        );

        AnnotationSet annotationSet = owner.openSection(annotationSetOffset).readAnnotationSet();

        for (int annotationOffset : annotationSet.annotationOffsets) {
            collectItemIndicesFromAnnotation(
                    owner, annotationOffset, offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromAnnotation(
            Dex owner,
            int annotationOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (annotationOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedAnnotationIndicesMap,
                owner,
                offsetToIndexConverter.getAnnotationIndexByOffset(
                        annotationOffset
                )
        );

        Annotation annotation =
                owner.openSection(annotationOffset).readAnnotation();

        EncodedValueReader annotationReader = annotation.getReader();

        collectItemIndicesFromAnnotationReader(
                owner,
                annotationReader,
                offsetToIndexConverter
        );
    }

    private void collectItemIndicesFromAnnotationReader(
            Dex owner,
            EncodedValueReader annotationReader,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        int fieldCount = annotationReader.readAnnotation();

        collectItemIndicesFromTypeIndex(
                owner, annotationReader.getAnnotationType(), offsetToIndexConverter
        );

        for (int i = 0; i < fieldCount; ++i) {
            int annotationNameIndex = annotationReader.readAnnotationName();
            collectItemIndicesFromStringIndex(
                    owner, annotationNameIndex, offsetToIndexConverter
            );
            collectItemIndicesFromEncodedValueReader(
                    owner, annotationReader, offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromEncodedArrayReader(
            Dex owner,
            EncodedValueReader arrayReader,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        int size = arrayReader.readArray();
        for (int i = 0; i < size; ++i) {
            collectItemIndicesFromEncodedValueReader(
                    owner, arrayReader, offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromEncodedValueReader(
            Dex owner,
            EncodedValueReader encodedValueReader,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        switch (encodedValueReader.peek()) {
            case EncodedValueReader.ENCODED_BYTE:
                // Skip value.
                encodedValueReader.readByte();
                break;
            case EncodedValueReader.ENCODED_SHORT:
                // Skip value.
                encodedValueReader.readShort();
                break;
            case EncodedValueReader.ENCODED_INT:
                // Skip value.
                encodedValueReader.readInt();
                break;
            case EncodedValueReader.ENCODED_LONG:
                // Skip value.
                encodedValueReader.readLong();
                break;
            case EncodedValueReader.ENCODED_CHAR:
                // Skip value.
                encodedValueReader.readChar();
                break;
            case EncodedValueReader.ENCODED_FLOAT:
                // Skip value.
                encodedValueReader.readFloat();
                break;
            case EncodedValueReader.ENCODED_DOUBLE:
                // Skip value.
                encodedValueReader.readDouble();
                break;
            case EncodedValueReader.ENCODED_STRING:
                collectItemIndicesFromStringIndex(
                        owner,
                        encodedValueReader.readString(),
                        offsetToIndexConverter
                );
                break;
            case EncodedValueReader.ENCODED_TYPE:
                collectItemIndicesFromTypeIndex(
                        owner,
                        encodedValueReader.readType(),
                        offsetToIndexConverter
                );
                break;
            case EncodedValueReader.ENCODED_FIELD:
                collectItemIndicesFromFieldIndex(
                        owner,
                        encodedValueReader.readField(),
                        offsetToIndexConverter
                );
                break;
            case EncodedValueReader.ENCODED_ENUM:
                collectItemIndicesFromFieldIndex(
                        owner,
                        encodedValueReader.readEnum(),
                        offsetToIndexConverter
                );
                break;
            case EncodedValueReader.ENCODED_METHOD:
                collectItemIndicesFromMethodIndex(
                        owner,
                        encodedValueReader.readMethod(),
                        offsetToIndexConverter
                );
                break;
            case EncodedValueReader.ENCODED_ARRAY:
                collectItemIndicesFromEncodedArrayReader(
                        owner,
                        encodedValueReader,
                        offsetToIndexConverter
                );
                break;
            case EncodedValueReader.ENCODED_ANNOTATION:
                collectItemIndicesFromAnnotationReader(
                        owner,
                        encodedValueReader,
                        offsetToIndexConverter

                );
                break;
            case EncodedValueReader.ENCODED_NULL:
                // Skip value.
                encodedValueReader.readNull();
                break;
            case EncodedValueReader.ENCODED_BOOLEAN:
                // Skip value.
                encodedValueReader.readBoolean();
                break;
            default:
                throw new DexException(
                        "Unexpected type: " + Integer.toHexString(encodedValueReader.peek())
                );
        }
    }

    private void collectItemIndicesFromClassData(
            Dex owner,
            int classDataOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (classDataOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedClassDataIndicesMap,
                owner,
                offsetToIndexConverter.getClassDataIndexByOffset(classDataOffset)
        );

        ClassData classData = owner.openSection(classDataOffset).readClassData();

        for (ClassData.Field field : classData.instanceFields) {
            collectItemIndicesFromFieldIndex(
                    owner, field.fieldIndex, offsetToIndexConverter
            );
        }

        for (ClassData.Field field : classData.staticFields) {
            collectItemIndicesFromFieldIndex(
                    owner, field.fieldIndex, offsetToIndexConverter
            );
        }

        for (ClassData.Method method : classData.directMethods) {
            collectItemIndicesFromMethodIndex(
                    owner, method.methodIndex, offsetToIndexConverter
            );
            collectItemIndicesFromCode(
                    owner, method.codeOffset, offsetToIndexConverter
            );
        }

        for (ClassData.Method method : classData.virtualMethods) {
            collectItemIndicesFromMethodIndex(
                    owner, method.methodIndex, offsetToIndexConverter
            );
            collectItemIndicesFromCode(
                    owner, method.codeOffset, offsetToIndexConverter
            );
        }
    }

    private void collectItemIndicesFromCode(
            Dex owner,
            int codeOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (codeOffset == ClassDef.NO_OFFSET) {
            return;
        }


        putValueIntoSetMap(
                patchedDexToCollectedCodeIndicesMap,
                owner,
                offsetToIndexConverter.getCodeIndexByOffset(codeOffset)
        );

        Code code = owner.openSection(codeOffset).readCode();

        collectItemIndicesFromDebugInfoItem(
                owner,
                code.debugInfoOffset,
                offsetToIndexConverter
        );

        InstructionReader ir = new InstructionReader(new ShortArrayCodeInput(code.instructions));
        try {
            ir.accept(new IndicesCollectorInsnVisitor(
                    owner, offsetToIndexConverter
            ));
        } catch (EOFException e) {
            throw new IllegalStateException(e);
        }

        for (Code.CatchHandler catchHandler : code.catchHandlers) {
            for (int typeIndex : catchHandler.typeIndexes) {
                collectItemIndicesFromTypeIndex(
                        owner,
                        typeIndex,
                        offsetToIndexConverter
                );
            }
        }
    }

    private void collectItemIndicesFromDebugInfoItem(
            Dex owner,
            int debugInfoItemOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (debugInfoItemOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedDebugInfoIndicesMap,
                owner,
                offsetToIndexConverter.getDebugInfoItemIndexByOffset(debugInfoItemOffset)
        );

        DebugInfoItem debugInfoItem = owner.openSection(debugInfoItemOffset).readDebugInfoItem();

        for (int stringIndex : debugInfoItem.parameterNames) {
            collectItemIndicesFromStringIndex(
                    owner, stringIndex, offsetToIndexConverter
            );
        }

        final ByteArrayInputStream bais = new ByteArrayInputStream(debugInfoItem.infoSTM);
        ByteInput inAdapter = new ByteInput() {
            @Override
            public byte readByte() {
                return (byte) (bais.read() & 0xFF);
            }
        };

        outside_whileloop:
        while (true) {
            int opcode = bais.read() & 0xFF;
            switch (opcode) {
                case DebugInfoItem.DBG_END_SEQUENCE: {
                    break outside_whileloop;
                }
                case DebugInfoItem.DBG_ADVANCE_PC: {
                    // Skip addrDiff.
                    int addrDiff = Leb128.readUnsignedLeb128(inAdapter);
                    break;
                }
                case DebugInfoItem.DBG_ADVANCE_LINE: {
                    // Skip lineDiff.
                    int lineDiff = Leb128.readSignedLeb128(inAdapter);
                    break;
                }
                case DebugInfoItem.DBG_START_LOCAL:
                case DebugInfoItem.DBG_START_LOCAL_EXTENDED: {
                    // Skip registerNum.
                    int registerNum = Leb128.readUnsignedLeb128(inAdapter);

                    int nameIndex = Leb128.readUnsignedLeb128p1(inAdapter);
                    collectItemIndicesFromStringIndex(
                            owner, nameIndex, offsetToIndexConverter
                    );

                    int typeIndex = Leb128.readUnsignedLeb128p1(inAdapter);
                    collectItemIndicesFromTypeIndex(
                            owner, typeIndex, offsetToIndexConverter
                    );

                    if (opcode == DebugInfoItem.DBG_START_LOCAL_EXTENDED) {
                        int sigIndex = Leb128.readUnsignedLeb128p1(inAdapter);
                        collectItemIndicesFromStringIndex(
                                owner, sigIndex, offsetToIndexConverter
                        );
                    }
                    break;
                }
                case DebugInfoItem.DBG_END_LOCAL:
                case DebugInfoItem.DBG_RESTART_LOCAL: {
                    // Skip registerNum.
                    int registerNum = Leb128.readUnsignedLeb128(inAdapter);
                    break;
                }
                case DebugInfoItem.DBG_SET_FILE: {
                    int nameIndex = Leb128.readUnsignedLeb128p1(inAdapter);
                    collectItemIndicesFromStringIndex(
                            owner, nameIndex, offsetToIndexConverter
                    );
                    break;
                }
                case DebugInfoItem.DBG_SET_PROLOGUE_END:
                case DebugInfoItem.DBG_SET_EPILOGUE_BEGIN:
                default: {
                    break;
                }
            }
        }
    }

    private void collectItemIndicesFromEncodedArray(
            Dex owner,
            int encodedArrayOffset,
            OffsetToIndexConverter offsetToIndexConverter
    ) {
        if (encodedArrayOffset == ClassDef.NO_OFFSET) {
            return;
        }

        putValueIntoSetMap(
                patchedDexToCollectedEncodedArrayIndicesMap,
                owner,
                offsetToIndexConverter.getEncodedArrayIndexByOffset(encodedArrayOffset)
        );

        EncodedValue arrayVal = owner.openSection(encodedArrayOffset).readEncodedArray();
        EncodedValueReader arrayReader =
                new EncodedValueReader(arrayVal, EncodedValueReader.ENCODED_ARRAY);

        collectItemIndicesFromEncodedArrayReader(
                owner, arrayReader, offsetToIndexConverter
        );
    }

    private <K, V> void putValueIntoSetMap(Map<K, Set<V>> map, K key, V value) {
        Set<V> valueSet = map.get(key);
        if (valueSet == null) {
            valueSet = new HashSet<>();
            map.put(key, valueSet);
        }
        valueSet.add(value);
    }

    private class SmallPatchSimulator<T extends Comparable<T>> {
        private final TableOfContents.Section tocSec;
        private final Dex.Section patchedSection;
        private final int patchedItemCount;
        private final IndexMap fullToSmallPatchMap;
        private final Set<Integer> collectedIndices;

        SmallPatchSimulator(
                Dex patchedDex,
                TableOfContents.Section tocSec,
                IndexMap fullToSmallPatchMap,
                Set<Integer> collectedIndices
        ) {
            if (tocSec.exists()) {
                this.tocSec = tocSec;
                this.patchedSection = patchedDex.openSection(tocSec);
                this.patchedItemCount = tocSec.size;
                this.fullToSmallPatchMap = fullToSmallPatchMap;
                this.collectedIndices = collectedIndices;
            } else {
                this.tocSec = null;
                this.patchedSection = null;
                this.patchedItemCount = 0;
                this.fullToSmallPatchMap = null;
                this.collectedIndices = null;
            }
        }

        private int getItemIndexOrOffset(T item, int index) {
            if (item instanceof TableOfContents.Section.Item) {
                return ((TableOfContents.Section.Item) item).off;
            } else {
                return index;
            }
        }

        @SuppressWarnings("unchecked")
        private T nextItem(DexDataBuffer buffer) {
            switch (this.tocSec.type) {
                case TableOfContents.SECTION_TYPE_TYPEIDS: {
                    return (T) (Integer) buffer.readInt();
                }
                case TableOfContents.SECTION_TYPE_PROTOIDS: {
                    return (T) buffer.readProtoId();
                }
                case TableOfContents.SECTION_TYPE_FIELDIDS: {
                    return (T) buffer.readFieldId();
                }
                case TableOfContents.SECTION_TYPE_METHODIDS: {
                    return (T) buffer.readMethodId();
                }
                case TableOfContents.SECTION_TYPE_CLASSDEFS: {
                    return (T) buffer.readClassDef();
                }
                case TableOfContents.SECTION_TYPE_STRINGDATAS: {
                    return (T) buffer.readStringData();
                }
                case TableOfContents.SECTION_TYPE_TYPELISTS: {
                    return (T) buffer.readTypeList();
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONS: {
                    return (T) buffer.readAnnotation();
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSETS: {
                    return (T) buffer.readAnnotationSet();
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSETREFLISTS: {
                    return (T) buffer.readAnnotationSetRefList();
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSDIRECTORIES: {
                    return (T) buffer.readAnnotationsDirectory();
                }
                case TableOfContents.SECTION_TYPE_DEBUGINFOS: {
                    return (T) buffer.readDebugInfoItem();
                }
                case TableOfContents.SECTION_TYPE_CODES: {
                    return (T) buffer.readCode();
                }
                case TableOfContents.SECTION_TYPE_ENCODEDARRAYS: {
                    return (T) buffer.readEncodedArray();
                }
                case TableOfContents.SECTION_TYPE_CLASSDATA: {
                    return (T) buffer.readClassData();
                }
                default:
                    throw new IllegalStateException("unknown section type: " + this.tocSec.type);
            }
        }

        private int getItemSize(T item) {
            if (item instanceof TableOfContents.Section.Item) {
                return ((TableOfContents.Section.Item) item).byteCountInDex();
            } else {
                if (item instanceof Integer) {
                    return SizeOf.UINT;
                } else {
                    throw new IllegalStateException(
                            "unexpected item type: " + item.getClass().getName()
                    );
                }
            }
        }

        @SuppressWarnings("unchecked")
        private T adjustItem(IndexMap indexMap, T item) {
            switch (this.tocSec.type) {
                case TableOfContents.SECTION_TYPE_TYPEIDS: {
                    return (T) (Integer) indexMap.adjustStringIndex((Integer) item);
                }
                case TableOfContents.SECTION_TYPE_PROTOIDS: {
                    return (T) indexMap.adjust((ProtoId) item);
                }
                case TableOfContents.SECTION_TYPE_FIELDIDS: {
                    return (T) indexMap.adjust((FieldId) item);
                }
                case TableOfContents.SECTION_TYPE_METHODIDS: {
                    return (T) indexMap.adjust((MethodId) item);
                }
                case TableOfContents.SECTION_TYPE_CLASSDEFS: {
                    return (T) indexMap.adjust((ClassDef) item);
                }
                case TableOfContents.SECTION_TYPE_STRINGDATAS: {
                    // nothing to do.
                    return item;
                }
                case TableOfContents.SECTION_TYPE_TYPELISTS: {
                    return (T) indexMap.adjust((TypeList) item);
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONS: {
                    return (T) indexMap.adjust((Annotation) item);
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSETS: {
                    return (T) indexMap.adjust((AnnotationSet) item);
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSETREFLISTS: {
                    return (T) indexMap.adjust((AnnotationSetRefList) item);
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSDIRECTORIES: {
                    return (T) indexMap.adjust((AnnotationsDirectory) item);
                }
                case TableOfContents.SECTION_TYPE_DEBUGINFOS: {
                    return (T) indexMap.adjust((DebugInfoItem) item);
                }
                case TableOfContents.SECTION_TYPE_CODES: {
                    return (T) indexMap.adjust((Code) item);
                }
                case TableOfContents.SECTION_TYPE_ENCODEDARRAYS: {
                    return (T) indexMap.adjust((EncodedValue) item);
                }
                case TableOfContents.SECTION_TYPE_CLASSDATA: {
                    return (T) indexMap.adjust((ClassData) item);
                }
                default:
                    throw new IllegalStateException("unknown section type: " + this.tocSec.type);
            }
        }

        @SuppressWarnings("unchecked")
        private void updateIndexOrOffset(
                IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset
        ) {
            switch (this.tocSec.type) {
                case TableOfContents.SECTION_TYPE_TYPEIDS: {
                    indexMap.mapTypeIds(oldIndex, newIndex);
                    break;
                }
                case TableOfContents.SECTION_TYPE_PROTOIDS: {
                    indexMap.mapProtoIds(oldIndex, newIndex);
                    break;
                }
                case TableOfContents.SECTION_TYPE_FIELDIDS: {
                    indexMap.mapFieldIds(oldIndex, newIndex);
                    break;
                }
                case TableOfContents.SECTION_TYPE_METHODIDS: {
                    indexMap.mapMethodIds(oldIndex, newIndex);
                    break;
                }
                case TableOfContents.SECTION_TYPE_CLASSDEFS: {
                    // nothing to do.
                    break;
                }
                case TableOfContents.SECTION_TYPE_STRINGDATAS: {
                    indexMap.mapStringIds(oldIndex, newIndex);
                    break;
                }
                case TableOfContents.SECTION_TYPE_TYPELISTS: {
                    indexMap.mapTypeListOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONS: {
                    indexMap.mapAnnotationOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSETS: {
                    indexMap.mapAnnotationSetOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSETREFLISTS: {
                    indexMap.mapAnnotationSetRefListOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_ANNOTATIONSDIRECTORIES: {
                    indexMap.mapAnnotationsDirectoryOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_DEBUGINFOS: {
                    indexMap.mapDebugInfoItemOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_CODES: {
                    indexMap.mapCodeOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_ENCODEDARRAYS: {
                    indexMap.mapStaticValuesOffset(oldOffset, newOffset);
                    break;
                }
                case TableOfContents.SECTION_TYPE_CLASSDATA: {
                    indexMap.mapClassDataOffset(oldOffset, newOffset);
                    break;
                }
                default:
                    throw new IllegalStateException("unknown section type: " + this.tocSec.type);
            }
        }

        public int simulate(int smallPatchBaseOffset) {
            if (patchedSection == null) {
                return 0;
            }
            if (collectedIndices == null || collectedIndices.isEmpty()) {
                return 0;
            }
            int smallPatchedIndex = 0;
            int smallPatchOffset = smallPatchBaseOffset;
            for (int fullPatchedItemIndex = 0;
                 fullPatchedItemIndex < this.patchedItemCount;
                 ++fullPatchedItemIndex
            ) {
                T fullPatchedItemInSmallPatch = adjustItem(
                        this.fullToSmallPatchMap, nextItem(this.patchedSection)
                );
                if (collectedIndices.contains(fullPatchedItemIndex)) {
                    if (this.tocSec.isElementFourByteAligned) {
                        smallPatchOffset = SizeOf.roundToTimesOfFour(smallPatchOffset);
                    }

                    int fullPatchedOffset = getItemIndexOrOffset(
                            fullPatchedItemInSmallPatch, fullPatchedItemIndex
                    );

                    if (fullPatchedItemIndex != smallPatchedIndex
                            || fullPatchedOffset != smallPatchOffset) {
                        updateIndexOrOffset(
                                this.fullToSmallPatchMap,
                                fullPatchedItemIndex,
                                fullPatchedOffset,
                                smallPatchedIndex,
                                smallPatchOffset
                        );
                    }

                    ++smallPatchedIndex;
                    smallPatchOffset += getItemSize(fullPatchedItemInSmallPatch);
                }
            }
            return smallPatchOffset - smallPatchBaseOffset;
        }
    }

    private class RefToRefAffectedClassInsnVisitor extends InstructionVisitor {
        private final Dex methodOwner;
        private final ClassData.Method method;
        private final Collection<String> refAffectedClassDefs;
        private boolean isMethodReferencedToRefAffectedClass;

        RefToRefAffectedClassInsnVisitor(Dex methodOwner, ClassData.Method method, Collection<String> refAffectedClassDefs) {
            super(null);
            this.methodOwner = methodOwner;
            this.method = method;
            this.refAffectedClassDefs = refAffectedClassDefs;
            this.isMethodReferencedToRefAffectedClass = false;
        }

        @Override
        public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
            processIndexByType(index, indexType);
        }

        private void processIndexByType(int index, int indexType) {
            String typeName = null;
            String refInfoInLog = null;
            switch (indexType) {
                case InstructionCodec.INDEX_TYPE_TYPE_REF: {
                    typeName = methodOwner.typeNames().get(index);
                    refInfoInLog = "init ref-changed class";
                    break;
                }
                case InstructionCodec.INDEX_TYPE_FIELD_REF: {
                    final FieldId fieldId = methodOwner.fieldIds().get(index);
                    typeName = methodOwner.typeNames().get(fieldId.declaringClassIndex);
                    refInfoInLog = "referencing to field: " + methodOwner.strings().get(fieldId.nameIndex);
                    break;
                }
                case InstructionCodec.INDEX_TYPE_METHOD_REF: {
                    final MethodId methodId = methodOwner.methodIds().get(index);
                    typeName = methodOwner.typeNames().get(methodId.declaringClassIndex);
                    refInfoInLog = "invoking method: " + getMethodProtoTypeStr(methodId);
                    break;
                }
            }
            if (typeName != null && refAffectedClassDefs.contains(typeName)) {
                MethodId methodId = methodOwner.methodIds().get(method.methodIndex);
                logger.i(
                        TAG,
                        "Method %s in class %s referenced ref-changed class %s by %s",
                        getMethodProtoTypeStr(methodId),
                        methodOwner.typeNames().get(methodId.declaringClassIndex),
                        typeName,
                        refInfoInLog
                );
                isMethodReferencedToRefAffectedClass = true;
            }
        }

        private String getMethodProtoTypeStr(MethodId methodId) {
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(methodOwner.strings().get(methodId.nameIndex));
            ProtoId protoId = methodOwner.protoIds().get(methodId.protoIndex);
            strBuilder.append('(');
            short[] paramTypeIds = methodOwner.parameterTypeIndicesFromMethodId(methodId);
            for (short typeId : paramTypeIds) {
                strBuilder.append(methodOwner.typeNames().get(typeId));
            }
            strBuilder.append(')').append(methodOwner.typeNames().get(protoId.returnTypeIndex));
            return strBuilder.toString();
        }
    }

    private class IndicesCollectorInsnVisitor extends InstructionVisitor {
        private final Dex ownerDex;
        private final OffsetToIndexConverter offsetToIndexConverter;

        IndicesCollectorInsnVisitor(
                Dex ownerDex, OffsetToIndexConverter offsetToIndexConverter
        ) {
            super(null);
            this.ownerDex = ownerDex;
            this.offsetToIndexConverter = offsetToIndexConverter;
        }

        @Override
        public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
            processIndexByType(index, indexType);
        }

        @Override
        public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
            processIndexByType(index, indexType);
        }

        private void processIndexByType(int index, int indexType) {
            switch (indexType) {
                case InstructionCodec.INDEX_TYPE_STRING_REF: {
                    collectItemIndicesFromStringIndex(
                            ownerDex, index, offsetToIndexConverter
                    );
                    break;
                }
                case InstructionCodec.INDEX_TYPE_TYPE_REF: {
                    collectItemIndicesFromTypeIndex(
                            ownerDex, index, offsetToIndexConverter
                    );
                    break;
                }
                case InstructionCodec.INDEX_TYPE_FIELD_REF: {
                    collectItemIndicesFromFieldIndex(
                            ownerDex, index, offsetToIndexConverter
                    );
                    break;
                }
                case InstructionCodec.INDEX_TYPE_METHOD_REF: {
                    collectItemIndicesFromMethodIndex(
                            ownerDex, index, offsetToIndexConverter
                    );
                    break;
                }
            }
        }
    }
}
