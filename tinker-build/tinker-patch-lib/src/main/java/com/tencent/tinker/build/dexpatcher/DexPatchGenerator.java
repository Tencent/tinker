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
import com.tencent.tinker.android.dx.util.IndexMap;
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
import com.tencent.tinker.build.dexpatcher.util.DexPatcherLogManager;
import com.tencent.tinker.build.dexpatcher.util.PatternUtils;
import com.tencent.tinker.commons.dexpatcher.struct.DexPatchFile;
import com.tencent.tinker.commons.dexpatcher.struct.PatchOperation;

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
 * Created by tomystang on 2016/6/30.
 */
public class DexPatchGenerator {
    private final Dex oldDex;
    private final Dex newDex;

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

        IndexMap oldToNewIndexMap = new IndexMap();
        IndexMap oldToPatchedIndexMap = new IndexMap();
        IndexMap selfIndexMapForSkip = new IndexMap();

        additionalRemovingClassPatternSet = new HashSet<>();

        this.stringDataSectionDiffAlg = new StringDataSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.typeIdSectionDiffAlg = new TypeIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.protoIdSectionDiffAlg = new ProtoIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.fieldIdSectionDiffAlg = new FieldIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.methodIdSectionDiffAlg = new MethodIdSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.classDefSectionDiffAlg = new ClassDefSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.typeListSectionDiffAlg = new TypeListSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationSetRefListSectionDiffAlg = new AnnotationSetRefListSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationSetSectionDiffAlg = new AnnotationSetSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.classDataSectionDiffAlg = new ClassDataSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.codeSectionDiffAlg = new CodeSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.debugInfoSectionDiffAlg = new DebugInfoItemSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationSectionDiffAlg = new AnnotationSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip);
        this.encodedArraySectionDiffAlg = new StaticValueSectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
                selfIndexMapForSkip
        );
        this.annotationsDirectorySectionDiffAlg = new AnnotationsDirectorySectionDiffAlgorithm(
                oldDex, newDex,
                oldToNewIndexMap,
                oldToPatchedIndexMap,
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

    public void executeAndSaveTo(File file) throws IOException {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
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

    public void executeAndSaveTo(OutputStream out) throws IOException {
        // Firstly, collect information of items we want to remove additionally
        // in new dex and set them to corresponding diff algorithm implementations.
        Pattern[] classNamePatterns = new Pattern[this.additionalRemovingClassPatternSet.size()];
        int i = 0;
        for (String regExStr : this.additionalRemovingClassPatternSet) {
            classNamePatterns[i++] = Pattern.compile(regExStr);
        }

        List<Integer> typeIdOfClassDefsToRemove = new ArrayList<>();
        List<Integer> offsetOfClassDatasToRemove = new ArrayList<>();
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

        ((ClassDefSectionDiffAlgorithm) this.classDefSectionDiffAlg).setTypeIdOfClassDefsToRemove(typeIdOfClassDefsToRemove);
        ((ClassDataSectionDiffAlgorithm) this.classDataSectionDiffAlg).setOffsetOfClassDatasToRemove(offsetOfClassDatasToRemove);

        // Then, run diff algorithms according to sections' dependencies.
        this.stringDataSectionDiffAlg.execute();
        this.typeIdSectionDiffAlg.execute();
        this.typeListSectionDiffAlg.execute();
        this.protoIdSectionDiffAlg.execute();
        this.fieldIdSectionDiffAlg.execute();
        this.methodIdSectionDiffAlg.execute();
        this.annotationSectionDiffAlg.execute();
        this.annotationSetSectionDiffAlg.execute();
        this.annotationSetRefListSectionDiffAlg.execute();
        this.annotationsDirectorySectionDiffAlg.execute();
        this.encodedArraySectionDiffAlg.execute();
        this.debugInfoSectionDiffAlg.execute();
        this.codeSectionDiffAlg.execute();
        this.classDataSectionDiffAlg.execute();
        this.classDefSectionDiffAlg.execute();

        // Secondly, use size calculated by algorithms above or from dex file definition to
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
        this.patchedStringIdsOffset = patchedHeaderOffset + patchedheaderSize;
        this.patchedStringDataItemsOffset = patchedheaderSize + patchedIdSectionSize;
        this.stringDataSectionDiffAlg.simulatePatchOperation(this.patchedStringDataItemsOffset);

        this.patchedTypeIdsOffset = this.patchedStringIdsOffset + patchedStringIdsSize;
        this.typeIdSectionDiffAlg.simulatePatchOperation(this.patchedTypeIdsOffset);

        this.patchedTypeListsOffset = patchedheaderSize + patchedIdSectionSize + this.stringDataSectionDiffAlg.getPatchedSectionSize();
        this.typeListSectionDiffAlg.simulatePatchOperation(this.patchedTypeListsOffset);

        this.patchedProtoIdsOffset = this.patchedTypeIdsOffset + patchedTypeIdsSize;
        this.protoIdSectionDiffAlg.simulatePatchOperation(this.patchedProtoIdsOffset);

        this.patchedFieldIdsOffset = this.patchedProtoIdsOffset + patchedProtoIdsSize;
        this.fieldIdSectionDiffAlg.simulatePatchOperation(this.patchedFieldIdsOffset);

        this.patchedMethodIdsOffset = this.patchedFieldIdsOffset + patchedFieldIdsSize;
        this.methodIdSectionDiffAlg.simulatePatchOperation(this.patchedMethodIdsOffset);

        this.patchedClassDefsOffset = this.patchedMethodIdsOffset + patchedMethodIdsSize;

        // classDef section's simulatePatchOperation method should be called after
        // classData and encodedArray section's simulatePatchOperation method is called.

        this.patchedAnnotationItemsOffset
                = this.patchedTypeListsOffset
                + this.typeListSectionDiffAlg.getPatchedSectionSize();
        this.annotationSectionDiffAlg.simulatePatchOperation(this.patchedAnnotationItemsOffset);

        this.patchedAnnotationSetItemsOffset
                = this.patchedAnnotationItemsOffset
                + this.annotationSectionDiffAlg.getPatchedSectionSize();
        this.annotationSetSectionDiffAlg.simulatePatchOperation(
                this.patchedAnnotationSetItemsOffset
        );

        this.patchedAnnotationSetRefListItemsOffset
                = this.patchedAnnotationSetItemsOffset
                + this.annotationSetSectionDiffAlg.getPatchedSectionSize();
        this.annotationSetRefListSectionDiffAlg.simulatePatchOperation(
                this.patchedAnnotationSetRefListItemsOffset
        );

        this.patchedAnnotationsDirectoryItemsOffset
                = this.patchedAnnotationSetRefListItemsOffset
                + this.annotationSetRefListSectionDiffAlg.getPatchedSectionSize();
        this.annotationsDirectorySectionDiffAlg.simulatePatchOperation(
                this.patchedAnnotationsDirectoryItemsOffset
        );

        this.patchedDebugInfoItemsOffset
                = this.patchedAnnotationsDirectoryItemsOffset
                + this.annotationsDirectorySectionDiffAlg.getPatchedSectionSize();
        this.debugInfoSectionDiffAlg.simulatePatchOperation(this.patchedDebugInfoItemsOffset);

        this.patchedCodeItemsOffset
                = this.patchedDebugInfoItemsOffset
                + this.debugInfoSectionDiffAlg.getPatchedSectionSize();
        this.codeSectionDiffAlg.simulatePatchOperation(this.patchedCodeItemsOffset);

        this.patchedClassDataItemsOffset
                = this.patchedCodeItemsOffset
                + this.codeSectionDiffAlg.getPatchedSectionSize();
        this.classDataSectionDiffAlg.simulatePatchOperation(this.patchedClassDataItemsOffset);

        this.patchedEncodedArrayItemsOffset
                = this.patchedClassDataItemsOffset
                + this.classDataSectionDiffAlg.getPatchedSectionSize();
        this.encodedArraySectionDiffAlg.simulatePatchOperation(this.patchedEncodedArrayItemsOffset);

        this.classDefSectionDiffAlg.simulatePatchOperation(this.patchedClassDefsOffset);

        this.patchedMapListOffset
                = this.patchedEncodedArrayItemsOffset
                + this.encodedArraySectionDiffAlg.getPatchedSectionSize();
        int patchedMapListSize = newDex.getTableOfContents().mapList.byteCount;

        this.patchedDexSize
                = this.patchedMapListOffset
                + patchedMapListSize;

        // Finally, write these result to patch file, then print statistic if we can.
        writeResultToStream(out);
        writeStaticLogIfNeeded();
    }

    private void writeStaticLogIfNeeded() {
        DexPatcherLogManager.writeLog(DexPatchGenerator.class.getName(), "------------------ Statistic Start ------------------");

        writeEachItemStaticLogIfNeeded("Strings", stringDataSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded("TypeIds", typeIdSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded("TypeLists", typeListSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded("ProtoIds", protoIdSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded("FieldIds", fieldIdSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded("MethodIds", methodIdSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded(
                "ClassDefs (Excluded class was also counted)",
                classDefSectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded(
                "Annotations",
                annotationSectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded(
                "AnnotationSets",
                annotationSetSectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded(
                "AnnotationSetRefLists",
                annotationSetRefListSectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded(
                "AnnotationDirectories",
                annotationsDirectorySectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded(
                "StaticValues",
                encodedArraySectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded(
                "DebugInfos",
                debugInfoSectionDiffAlg.getPatchOperationList()
        );
        writeEachItemStaticLogIfNeeded("Codes", codeSectionDiffAlg.getPatchOperationList());
        writeEachItemStaticLogIfNeeded(
                "ClassData",
                classDataSectionDiffAlg.getPatchOperationList()
        );

        DexPatcherLogManager.writeLog(DexPatchGenerator.class.getName(), "------------------  Statistic End  ------------------");
    }

    private <T extends Comparable<T>> void writeEachItemStaticLogIfNeeded(
            String title, List<PatchOperation<T>> patchOperationList
    ) {
        int addedItemCount = 0;
        int deletedItemCount = 0;
        int changedItemCount = 0;

        for (PatchOperation<?> patchOperation : patchOperationList) {
            switch (patchOperation.op) {
                case PatchOperation.OP_ADD: {
                    ++addedItemCount;
                    break;
                }
                case PatchOperation.OP_DEL: {
                    ++deletedItemCount;
                    break;
                }
                case PatchOperation.OP_REPLACE: {
                    ++changedItemCount;
                    break;
                }
                default: {
                    break;
                }
            }
        }

        DexPatcherLogManager.writeLog(DexPatchGenerator.class.getName(), "%s:", title);
        DexPatcherLogManager.writeLog(DexPatchGenerator.class.getName(), "\t%d items added.", addedItemCount);
        DexPatcherLogManager.writeLog(DexPatchGenerator.class.getName(), "\t%d items deleted.", deletedItemCount);
        DexPatcherLogManager.writeLog(DexPatchGenerator.class.getName(), "\t%d items changed.", changedItemCount);
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
        buffer.write(this.oldDex.computeSignature());
        int firstChunkOffset = buffer.position();
        buffer.position(posOfFirstChunkOffsetField);
        buffer.writeInt(firstChunkOffset);
        buffer.position(firstChunkOffset);

        new PatchOperationsWriter<StringData>(this.stringDataSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, StringData item) {
                buffer.writeStringData(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<Integer>(this.typeIdSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, Integer item) {
                buffer.writeInt(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<TypeList>(this.typeListSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, TypeList item) {
                buffer.writeTypeList(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<ProtoId>(this.protoIdSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, ProtoId item) {
                buffer.writeProtoId(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<FieldId>(this.fieldIdSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, FieldId item) {
                buffer.writeFieldId(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<MethodId>(this.methodIdSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, MethodId item) {
                buffer.writeMethodId(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<Annotation>(this.annotationSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, Annotation item) {
                buffer.writeAnnotation(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<AnnotationSet>(this.annotationSetSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, AnnotationSet item) {
                buffer.writeAnnotationSet(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<AnnotationSetRefList>(this.annotationSetRefListSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, AnnotationSetRefList item) {
                buffer.writeAnnotationSetRefList(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<AnnotationsDirectory>(this.annotationsDirectorySectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, AnnotationsDirectory item) {
                buffer.writeAnnotationsDirectory(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<EncodedValue>(this.encodedArraySectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, EncodedValue item) {
                buffer.writeEncodedArray(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<DebugInfoItem>(this.debugInfoSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, DebugInfoItem item) {
                buffer.writeDebugInfoItem(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<Code>(this.codeSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, Code item) {
                buffer.writeCode(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<ClassData>(this.classDataSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, ClassData item) {
                buffer.writeClassData(item);
            }
        }.writeToBuffer(buffer);

        new PatchOperationsWriter<ClassDef>(this.classDefSectionDiffAlg.getPatchOperationList()) {
            @Override
            protected void writeItem(DexDataBuffer buffer, ClassDef item) {
                buffer.writeClassDef(item);
            }
        }.writeToBuffer(buffer);

        byte[] bufferData = buffer.array();
        os.write(bufferData);
        os.flush();
    }

    private abstract class PatchOperationsWriter<T> {
        private final List<PatchOperation<T>> patchOperationList;

        public PatchOperationsWriter(List<PatchOperation<T>> patchOperationList) {
            this.patchOperationList = patchOperationList;
        }

        protected abstract void writeItem(DexDataBuffer buffer, T item);

        public final void writeToBuffer(DexDataBuffer buffer) {
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
                writeItem(buffer, newItem);
            }
        }
    }
}
