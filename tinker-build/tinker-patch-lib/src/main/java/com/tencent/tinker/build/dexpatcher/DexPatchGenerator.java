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

package com.tencent.tinker.build.dexpatcher;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.AnnotationsDirectory;
import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.AnnotationSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.AnnotationSetRefListSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.AnnotationSetSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.AnnotationsDirectorySectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.ClassDataSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.ClassDefSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.CodeSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.DebugInfoItemSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.DexSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.FieldIdSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.MethodIdSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.ProtoIdSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.StaticValueSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.StringDataSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.TypeIdSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.algorithms.diff.TypeListSectionDiffAlgorithm;
import com.tencent.tinker.build.dexpatcher.util.PatternUtils;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.struct.PatchOperation;
import com.tencent.tinker.commons.dexpatcher.util.SparseIndexMap;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * Created by tangyinsheng on 2016/6/30.
 */
public class DexPatchGenerator {
    private static final String TAG = "DexPatchGenerator";

    private final Dex oldDex;
    private final Dex newDex;
    private final DexPatcherLogger logger = new DexPatcherLogger();
    private DexSectionDiffAlgorithm<StringData> stringDataSectionDiffAlg;
    private DexSectionDiffAlgorithm<Integer> typeIdSectionDiffAlg;
    private DexSectionDiffAlgorithm<ProtoId> protoIdSectionDiffAlg;
    private DexSectionDiffAlgorithm<FieldId> fieldIdSectionDiffAlg;
    private DexSectionDiffAlgorithm<MethodId> methodIdSectionDiffAlg;
    private DexSectionDiffAlgorithm<ClassDef> classDefSectionDiffAlg;
    private DexSectionDiffAlgorithm<TypeList> typeListSectionDiffAlg;
    private DexSectionDiffAlgorithm<AnnotationSetRefList> annotationSetRefListSectionDiffAlg;
    private DexSectionDiffAlgorithm<AnnotationSet> annotationSetSectionDiffAlg;
    private DexSectionDiffAlgorithm<ClassData> classDataSectionDiffAlg;
    private DexSectionDiffAlgorithm<Code> codeSectionDiffAlg;
    private DexSectionDiffAlgorithm<DebugInfoItem> debugInfoSectionDiffAlg;
    private DexSectionDiffAlgorithm<Annotation> annotationSectionDiffAlg;
    private DexSectionDiffAlgorithm<EncodedValue> encodedArraySectionDiffAlg;
    private DexSectionDiffAlgorithm<AnnotationsDirectory> annotationsDirectorySectionDiffAlg;
    private Set<String> additionalRemovingClassPatternSet;
    private int patchedHeaderOffset = 0;
    private int patchedStringIdsOffset = 0;
    private int patchedTypeIdsOffset = 0;
    private int patchedProtoIdsOffset = 0;
    private int patchedFieldIdsOffset = 0;
    private int patchedMethodIdsOffset = 0;
    private int patchedClassDefsOffset = 0;
    private int patchedTypeListsOffset = 0;
    private int patchedAnnotationItemsOffset = 0;
    private int patchedAnnotationSetItemsOffset = 0;
    private int patchedAnnotationSetRefListItemsOffset = 0;
    private int patchedAnnotationsDirectoryItemsOffset = 0;
    private int patchedDebugInfoItemsOffset = 0;
    private int patchedCodeItemsOffset = 0;
    private int patchedClassDataItemsOffset = 0;
    private int patchedStringDataItemsOffset = 0;
    private int patchedEncodedArrayItemsOffset = 0;
    private int patchedMapListOffset = 0;
    private int patchedDexSize = 0;

    public DexPatchGenerator(File oldDexFile, File newDexFile) throws IOException {
        this(new Dex(oldDexFile), new Dex(newDexFile));
    }

    /**
     * Notice: you should close inputstream manually.
     */
    public DexPatchGenerator(File oldDexFile, InputStream newDexStream) throws IOException {
        this(new Dex(oldDexFile), new Dex(newDexStream));
    }

    /**
     * Notice: you should close inputstream manually.
     */
    public DexPatchGenerator(InputStream oldDexStream, InputStream newDexStream) throws IOException {
        this(new Dex(oldDexStream), new Dex(newDexStream));
    }

    public DexPatchGenerator(Dex oldDex, Dex newDex) {
        this.oldDex = oldDex;
        this.newDex = newDex;

        SparseIndexMap oldToNewIndexMap = new SparseIndexMap();
        SparseIndexMap oldToPatchedIndexMap = new SparseIndexMap();
        SparseIndexMap newToPatchedIndexMap = new SparseIndexMap();
        SparseIndexMap selfIndexMapForSkip = new SparseIndexMap();

        additionalRemovingClassPatternSet = new HashSet<>();

        this.stringDataSectionDiffAlg = new StringDataSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.typeIdSectionDiffAlg = new TypeIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.protoIdSectionDiffAlg = new ProtoIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.fieldIdSectionDiffAlg = new FieldIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.methodIdSectionDiffAlg = new MethodIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.classDefSectionDiffAlg = new ClassDefSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.typeListSectionDiffAlg = new TypeListSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationSetRefListSectionDiffAlg = new AnnotationSetRefListSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationSetSectionDiffAlg = new AnnotationSetSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.classDataSectionDiffAlg = new ClassDataSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.codeSectionDiffAlg = new CodeSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.debugInfoSectionDiffAlg = new DebugInfoItemSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationSectionDiffAlg = new AnnotationSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.encodedArraySectionDiffAlg = new StaticValueSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationsDirectorySectionDiffAlg = new AnnotationsDirectorySectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                newToPatchedIndexMap,
                selfIndexMapForSkip
        );
    }

    public void addAdditionalRemovingClassPattern(String pattern) {
        this.additionalRemovingClassPatternSet.add(
                PatternUtils.dotClassNamePatternToDescriptorRegEx(pattern)
        );
    }

    public void setAdditionalRemovingClassPatterns(Collection<String> patterns) {
        for (String pattern : patterns) {
            this.additionalRemovingClassPatternSet.add(
                    PatternUtils.dotClassNamePatternToDescriptorRegEx(pattern)
            );
        }
    }

    public void clearAdditionalRemovingClassPatterns() {
        this.additionalRemovingClassPatternSet.clear();
    }

    public void setLogger(DexPatcherLogger.IDexPatcherLogger logger) {
        this.logger.setLoggerImpl(logger);
    }

    public void executeAndSaveTo(File file) throws IOException {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            executeAndSaveTo(os);
        } finally {
            IOHelper.closeQuietly(os);
        }
    }

    public void executeAndSaveTo(OutputStream out) throws IOException {
        // Firstly, collect information of items we want to remove additionally
        // in new dex and set them to corresponding diff algorithm implementations.
        Pattern[] classNamePatterns = new Pattern[this.additionalRemovingClassPatternSet.size()];
        int classNamePatternCount = 0;
        for (String regExStr : this.additionalRemovingClassPatternSet) {
            classNamePatterns[classNamePatternCount++] = Pattern.compile(regExStr);
        }

        List<Integer> typeIdOfClassDefsToRemove = new ArrayList<>(classNamePatternCount);
        List<Integer> offsetOfClassDatasToRemove = new ArrayList<>(classNamePatternCount);
        for (ClassDef classDef : this.newDex.classDefs()) {
            String typeName = this.newDex.typeNames().get(classDef.typeIndex);
            for (Pattern pattern : classNamePatterns) {
                if (pattern.matcher(typeName).matches()) {
                    typeIdOfClassDefsToRemove.add(classDef.typeIndex);
                    offsetOfClassDatasToRemove.add(classDef.classDataOffset);
                    break;
                }
            }
        }

        ((ClassDefSectionDiffAlgorithm) this.classDefSectionDiffAlg)
                .setTypeIdOfClassDefsToRemove(typeIdOfClassDefsToRemove);
        ((ClassDataSectionDiffAlgorithm) this.classDataSectionDiffAlg)
                .setOffsetOfClassDatasToRemove(offsetOfClassDatasToRemove);

        // Then, run diff algorithms according to sections' dependencies.

        // Use size calculated by algorithms above or from dex file definition to
        // calculate sections' offset and patched dex size.

        // Calculate header and id sections size, so that we can work out
        // the base offset of typeLists Section.
        int patchedheaderSize = SizeOf.HEADER_ITEM;
        int patchedStringIdsSize = newDex.getTableOfContents().stringIds.size * SizeOf.STRING_ID_ITEM;
        int patchedTypeIdsSize = newDex.getTableOfContents().typeIds.size * SizeOf.TYPE_ID_ITEM;

        // Although simulatePatchOperation can calculate this value, since protoIds section
        // depends on typeLists section, we can't run protoIds Section's simulatePatchOperation
        // method so far. Instead we calculate protoIds section's size using information in newDex
        // directly.
        int patchedProtoIdsSize = newDex.getTableOfContents().protoIds.size * SizeOf.PROTO_ID_ITEM;

        int patchedFieldIdsSize = newDex.getTableOfContents().fieldIds.size * SizeOf.MEMBER_ID_ITEM;
        int patchedMethodIdsSize = newDex.getTableOfContents().methodIds.size * SizeOf.MEMBER_ID_ITEM;
        int patchedClassDefsSize = newDex.getTableOfContents().classDefs.size * SizeOf.CLASS_DEF_ITEM;

        int patchedIdSectionSize =
                patchedStringIdsSize
                        + patchedTypeIdsSize
                        + patchedProtoIdsSize
                        + patchedFieldIdsSize
                        + patchedMethodIdsSize
                        + patchedClassDefsSize;

        this.patchedHeaderOffset = 0;

        // The diff works on each sections obey such procedure:
        //  1. Execute diff algorithms to calculate indices of items we need to add, del and replace.
        //  2. Execute patch algorithm simulation to calculate indices and offsets mappings that is
        //  necessary to next section's diff works.

        // Immediately do the patch simulation so that we can know:
        //  1. Indices and offsets mapping between old dex and patched dex.
        //  2. Indices and offsets mapping between new dex and patched dex.
        // These information will be used to do next diff works.
        this.patchedStringIdsOffset = patchedHeaderOffset + patchedheaderSize;
        if (this.oldDex.getTableOfContents().stringIds.isElementFourByteAligned) {
            this.patchedStringIdsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedStringIdsOffset);
        }
        this.stringDataSectionDiffAlg.execute();
        this.patchedStringDataItemsOffset = patchedheaderSize + patchedIdSectionSize;
        if (this.oldDex.getTableOfContents().stringDatas.isElementFourByteAligned) {
            this.patchedStringDataItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedStringDataItemsOffset);
        }
        this.stringDataSectionDiffAlg.simulatePatchOperation(this.patchedStringDataItemsOffset);

        this.typeIdSectionDiffAlg.execute();
        this.patchedTypeIdsOffset = this.patchedStringIdsOffset + patchedStringIdsSize;
        if (this.oldDex.getTableOfContents().typeIds.isElementFourByteAligned) {
            this.patchedTypeIdsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedTypeIdsOffset);
        }
        this.typeIdSectionDiffAlg.simulatePatchOperation(this.patchedTypeIdsOffset);

        this.typeListSectionDiffAlg.execute();
        this.patchedTypeListsOffset
                = patchedheaderSize
                + patchedIdSectionSize
                + this.stringDataSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().typeLists.isElementFourByteAligned) {
            this.patchedTypeListsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedTypeListsOffset);
        }
        this.typeListSectionDiffAlg.simulatePatchOperation(this.patchedTypeListsOffset);

        this.protoIdSectionDiffAlg.execute();
        this.patchedProtoIdsOffset = this.patchedTypeIdsOffset + patchedTypeIdsSize;
        if (this.oldDex.getTableOfContents().protoIds.isElementFourByteAligned) {
            this.patchedProtoIdsOffset = SizeOf.roundToTimesOfFour(this.patchedProtoIdsOffset);
        }
        this.protoIdSectionDiffAlg.simulatePatchOperation(this.patchedProtoIdsOffset);

        this.fieldIdSectionDiffAlg.execute();
        this.patchedFieldIdsOffset = this.patchedProtoIdsOffset + patchedProtoIdsSize;
        if (this.oldDex.getTableOfContents().fieldIds.isElementFourByteAligned) {
            this.patchedFieldIdsOffset = SizeOf.roundToTimesOfFour(this.patchedFieldIdsOffset);
        }
        this.fieldIdSectionDiffAlg.simulatePatchOperation(this.patchedFieldIdsOffset);

        this.methodIdSectionDiffAlg.execute();
        this.patchedMethodIdsOffset = this.patchedFieldIdsOffset + patchedFieldIdsSize;
        if (this.oldDex.getTableOfContents().methodIds.isElementFourByteAligned) {
            this.patchedMethodIdsOffset = SizeOf.roundToTimesOfFour(this.patchedMethodIdsOffset);
        }
        this.methodIdSectionDiffAlg.simulatePatchOperation(this.patchedMethodIdsOffset);

        this.annotationSectionDiffAlg.execute();
        this.patchedAnnotationItemsOffset
                = this.patchedTypeListsOffset
                + this.typeListSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().annotations.isElementFourByteAligned) {
            this.patchedAnnotationItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedAnnotationItemsOffset);
        }
        this.annotationSectionDiffAlg.simulatePatchOperation(this.patchedAnnotationItemsOffset);

        this.annotationSetSectionDiffAlg.execute();
        this.patchedAnnotationSetItemsOffset
                = this.patchedAnnotationItemsOffset
                + this.annotationSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().annotationSets.isElementFourByteAligned) {
            this.patchedAnnotationSetItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedAnnotationSetItemsOffset);
        }
        this.annotationSetSectionDiffAlg.simulatePatchOperation(
                this.patchedAnnotationSetItemsOffset
        );

        this.annotationSetRefListSectionDiffAlg.execute();
        this.patchedAnnotationSetRefListItemsOffset
                = this.patchedAnnotationSetItemsOffset
                + this.annotationSetSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().annotationSetRefLists.isElementFourByteAligned) {
            this.patchedAnnotationSetRefListItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedAnnotationSetRefListItemsOffset);
        }
        this.annotationSetRefListSectionDiffAlg.simulatePatchOperation(
                this.patchedAnnotationSetRefListItemsOffset
        );

        this.annotationsDirectorySectionDiffAlg.execute();
        this.patchedAnnotationsDirectoryItemsOffset
                = this.patchedAnnotationSetRefListItemsOffset
                + this.annotationSetRefListSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().annotationsDirectories.isElementFourByteAligned) {
            this.patchedAnnotationsDirectoryItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedAnnotationsDirectoryItemsOffset);
        }
        this.annotationsDirectorySectionDiffAlg.simulatePatchOperation(
                this.patchedAnnotationsDirectoryItemsOffset
        );

        this.debugInfoSectionDiffAlg.execute();
        this.patchedDebugInfoItemsOffset
                = this.patchedAnnotationsDirectoryItemsOffset
                + this.annotationsDirectorySectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().debugInfos.isElementFourByteAligned) {
            this.patchedDebugInfoItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedDebugInfoItemsOffset);
        }
        this.debugInfoSectionDiffAlg.simulatePatchOperation(this.patchedDebugInfoItemsOffset);

        this.codeSectionDiffAlg.execute();
        this.patchedCodeItemsOffset
                = this.patchedDebugInfoItemsOffset
                + this.debugInfoSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().codes.isElementFourByteAligned) {
            this.patchedCodeItemsOffset = SizeOf.roundToTimesOfFour(this.patchedCodeItemsOffset);
        }
        this.codeSectionDiffAlg.simulatePatchOperation(this.patchedCodeItemsOffset);

        this.classDataSectionDiffAlg.execute();
        this.patchedClassDataItemsOffset
                = this.patchedCodeItemsOffset
                + this.codeSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().classDatas.isElementFourByteAligned) {
            this.patchedClassDataItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedClassDataItemsOffset);
        }
        this.classDataSectionDiffAlg.simulatePatchOperation(this.patchedClassDataItemsOffset);

        this.encodedArraySectionDiffAlg.execute();
        this.patchedEncodedArrayItemsOffset
                = this.patchedClassDataItemsOffset
                + this.classDataSectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().encodedArrays.isElementFourByteAligned) {
            this.patchedEncodedArrayItemsOffset
                    = SizeOf.roundToTimesOfFour(this.patchedEncodedArrayItemsOffset);
        }
        this.encodedArraySectionDiffAlg.simulatePatchOperation(this.patchedEncodedArrayItemsOffset);

        this.classDefSectionDiffAlg.execute();
        this.patchedClassDefsOffset = this.patchedMethodIdsOffset + patchedMethodIdsSize;
        if (this.oldDex.getTableOfContents().classDefs.isElementFourByteAligned) {
            this.patchedClassDefsOffset = SizeOf.roundToTimesOfFour(this.patchedClassDefsOffset);
        }

        // Calculate any values we still know nothing about them.
        this.patchedMapListOffset
                = this.patchedEncodedArrayItemsOffset
                + this.encodedArraySectionDiffAlg.getPatchedSectionSize();
        if (this.oldDex.getTableOfContents().mapList.isElementFourByteAligned) {
            this.patchedMapListOffset = SizeOf.roundToTimesOfFour(this.patchedMapListOffset);
        }
        int patchedMapListSize = newDex.getTableOfContents().mapList.byteCount;

        this.patchedDexSize
                = this.patchedMapListOffset
                + patchedMapListSize;

        // Finally, write results to patch file.
        writeResultToStream(out);
    }

    private void writeResultToStream(OutputStream os) throws IOException {
        DexDataBuffer buffer = new DexDataBuffer();
        buffer.write(DexPatchFile.MAGIC);
        buffer.writeShort(DexPatchFile.CURRENT_VERSION);
        buffer.writeInt(this.patchedDexSize);
        // we will return here to write firstChunkOffset later.
        int posOfFirstChunkOffsetField = buffer.position();
        buffer.writeInt(0);
        buffer.writeInt(this.patchedStringIdsOffset);
        buffer.writeInt(this.patchedTypeIdsOffset);
        buffer.writeInt(this.patchedProtoIdsOffset);
        buffer.writeInt(this.patchedFieldIdsOffset);
        buffer.writeInt(this.patchedMethodIdsOffset);
        buffer.writeInt(this.patchedClassDefsOffset);
        buffer.writeInt(this.patchedMapListOffset);
        buffer.writeInt(this.patchedTypeListsOffset);
        buffer.writeInt(this.patchedAnnotationSetRefListItemsOffset);
        buffer.writeInt(this.patchedAnnotationSetItemsOffset);
        buffer.writeInt(this.patchedClassDataItemsOffset);
        buffer.writeInt(this.patchedCodeItemsOffset);
        buffer.writeInt(this.patchedStringDataItemsOffset);
        buffer.writeInt(this.patchedDebugInfoItemsOffset);
        buffer.writeInt(this.patchedAnnotationItemsOffset);
        buffer.writeInt(this.patchedEncodedArrayItemsOffset);
        buffer.writeInt(this.patchedAnnotationsDirectoryItemsOffset);
        buffer.write(this.oldDex.computeSignature(false));
        int firstChunkOffset = buffer.position();
        buffer.position(posOfFirstChunkOffsetField);
        buffer.writeInt(firstChunkOffset);
        buffer.position(firstChunkOffset);

        writePatchOperations(buffer, this.stringDataSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.typeIdSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.typeListSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.protoIdSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.fieldIdSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.methodIdSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.annotationSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.annotationSetSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.annotationSetRefListSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.annotationsDirectorySectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.debugInfoSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.codeSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.classDataSectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.encodedArraySectionDiffAlg.getPatchOperationList());
        writePatchOperations(buffer, this.classDefSectionDiffAlg.getPatchOperationList());

        byte[] bufferData = buffer.array();
        os.write(bufferData);
        os.flush();
    }

    private <T extends Comparable<T>> void writePatchOperations(
            DexDataBuffer buffer, List<PatchOperation<T>> patchOperationList
    ) {
        List<Integer> delOpIndexList = new ArrayList<>(patchOperationList.size());
        List<Integer> addOpIndexList = new ArrayList<>(patchOperationList.size());
        List<Integer> replaceOpIndexList = new ArrayList<>(patchOperationList.size());
        List<T> newItemList = new ArrayList<>(patchOperationList.size());

        for (PatchOperation<T> patchOperation : patchOperationList) {
            switch (patchOperation.op) {
                case PatchOperation.OP_DEL: {
                    delOpIndexList.add(patchOperation.index);
                    break;
                }
                case PatchOperation.OP_ADD: {
                    addOpIndexList.add(patchOperation.index);
                    newItemList.add(patchOperation.newItem);
                    break;
                }
                case PatchOperation.OP_REPLACE: {
                    replaceOpIndexList.add(patchOperation.index);
                    newItemList.add(patchOperation.newItem);
                    break;
                }
                default:
                    break;
            }
        }

        buffer.writeUleb128(delOpIndexList.size());
        int lastIndex = 0;
        for (Integer index : delOpIndexList) {
            buffer.writeSleb128(index - lastIndex);
            lastIndex = index;
        }

        buffer.writeUleb128(addOpIndexList.size());
        lastIndex = 0;
        for (Integer index : addOpIndexList) {
            buffer.writeSleb128(index - lastIndex);
            lastIndex = index;
        }

        buffer.writeUleb128(replaceOpIndexList.size());
        lastIndex = 0;
        for (Integer index : replaceOpIndexList) {
            buffer.writeSleb128(index - lastIndex);
            lastIndex = index;
        }

        for (T newItem : newItemList) {
            if (newItem instanceof StringData) {
                buffer.writeStringData((StringData) newItem);
            } else
            if (newItem instanceof Integer) {
                // TypeId item.
                buffer.writeInt((Integer) newItem);
            } else
            if (newItem instanceof TypeList) {
                buffer.writeTypeList((TypeList) newItem);
            } else
            if (newItem instanceof ProtoId) {
                buffer.writeProtoId((ProtoId) newItem);
            } else
            if (newItem instanceof FieldId) {
                buffer.writeFieldId((FieldId) newItem);
            } else
            if (newItem instanceof MethodId) {
                buffer.writeMethodId((MethodId) newItem);
            } else
            if (newItem instanceof Annotation) {
                buffer.writeAnnotation((Annotation) newItem);
            } else
            if (newItem instanceof AnnotationSet) {
                buffer.writeAnnotationSet((AnnotationSet) newItem);
            } else
            if (newItem instanceof AnnotationSetRefList) {
                buffer.writeAnnotationSetRefList(
                        (AnnotationSetRefList) newItem
                );
            } else
            if (newItem instanceof AnnotationsDirectory) {
                buffer.writeAnnotationsDirectory(
                        (AnnotationsDirectory) newItem
                );
            } else
            if (newItem instanceof DebugInfoItem) {
                buffer.writeDebugInfoItem((DebugInfoItem) newItem);
            } else
            if (newItem instanceof Code) {
                buffer.writeCode((Code) newItem);
            } else
            if (newItem instanceof ClassData) {
                buffer.writeClassData((ClassData) newItem);
            } else
            if (newItem instanceof EncodedValue) {
                buffer.writeEncodedArray((EncodedValue) newItem);
            } else
            if (newItem instanceof ClassDef) {
                buffer.writeClassDef((ClassDef) newItem);
            } else {
                throw new IllegalStateException(
                        "Unknown item type: " + newItem.getClass()
                );
            }
        }
    }
}
