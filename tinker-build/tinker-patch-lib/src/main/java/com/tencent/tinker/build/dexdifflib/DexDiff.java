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

package com.tencent.tinker.build.dexdifflib;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationDirectory;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TypeId;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.build.dexdifflib.algorithms.ClassExcludingAlgorithm;
import com.tencent.tinker.build.dexdifflib.algorithms.SectionDiffAlgorithm;
import com.tencent.tinker.build.dexdifflib.io.DiffFileOutputStream;
import com.tencent.tinker.commons.dexdifflib.struct.ChunkValueType;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileChunk;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileHeader;
import com.tencent.tinker.commons.dexdifflib.struct.IndexMap;
import com.tencent.tinker.commons.dexdifflib.struct.IndexedItem;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecord;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;
import com.tencent.tinker.commons.dexdifflib.util.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DexDiff {
    private Dex oldDex = null;
    private Dex newDex = null;

    @SuppressWarnings("unused")
    private DiffFileHeader header = null;

    private DiffFileChunk<StringData>           stringDataPatchOpChunk           = null;
    private DiffFileChunk<TypeId>               typeIdPatchOpChunk               = null;
    private DiffFileChunk<TypeList>             typeListPatchOpChunk             = null;
    private DiffFileChunk<ProtoId>              protoIdPatchOpChunk              = null;
    private DiffFileChunk<FieldId>              fieldIdPatchOpChunk              = null;
    private DiffFileChunk<MethodId>             methodIdPatchOpChunk             = null;
    private DiffFileChunk<Annotation>           annotationPatchOpChunk           = null;
    private DiffFileChunk<AnnotationSet>        annotationSetPatchOpChunk        = null;
    private DiffFileChunk<AnnotationSetRefList> annotationSetRefListPatchOpChunk = null;
    private DiffFileChunk<AnnotationDirectory>  annotationDirectoryPatchOpChunk  = null;
    private DiffFileChunk<EncodedValue>         staticValuePatchOpChunk          = null;
    private DiffFileChunk<Code>                 codePatchOpChunk                 = null;
    private DiffFileChunk<ClassData>            classDataPatchOpChunk            = null;
    private DiffFileChunk<ClassDef>             classDefPatchOpChunk             = null;
    private DiffFileChunk<DebugInfoItem>        debugInfoItemPatchOpChunk        = null;

    private ClassExcludingAlgorithm classExcludingAlgorithm = null;
    private LogWriter logWriter = null;

    public DexDiff(File oldDexFile, File newDexFile) throws Exception {
        this(new Dex(oldDexFile), new Dex(newDexFile));
    }

    public DexDiff(InputStream oldDexStream, InputStream newDexStream) throws Exception {
        this(new Dex(oldDexStream), new Dex(newDexStream));
    }

    private DexDiff(Dex oldDex, Dex newDex) throws Exception {
        this.oldDex = oldDex;
        this.newDex = newDex;
    }

    public void setLogWriter(LogWriter logWriter) {
        this.logWriter = logWriter;
    }

    public void doDiffAndSaveTo(File out) throws Exception {
        doDiffAndSaveTo(out, null);
    }

    public void doDiffAndSaveTo(File out, Collection<String> namePaternOfExcludedClass) throws Exception {
        OutputStream os = null;
        try {
            new DiffHelper().diff(namePaternOfExcludedClass);
            os = new BufferedOutputStream(new FileOutputStream(out));
            saveDiffResultTo(os);
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    public void doDiffAndSaveTo(OutputStream out) throws Exception {
        doDiffAndSaveTo(null, out);
    }

    public void doDiffAndSaveTo(Collection<String> namePaternOfExcludedClass, OutputStream out) throws Exception {
        new DiffHelper().diff(namePaternOfExcludedClass);
        saveDiffResultTo(out);
    }

    private void saveDiffResultTo(File out) throws IOException {
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(out));
            saveDiffResultTo(os);
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private void saveDiffResultTo(OutputStream out) throws IOException {
        DiffFileOutputStream allDfos = null;
        try {
            DiffFileHeader header = new DiffFileHeader(
                DiffFileHeader.CURRENT_VERSION,
                newDex.getLength(),
                DiffFileHeader.SIZE,
                newDex.getTableOfContents().stringIds.off,
                newDex.getTableOfContents().typeIds.off,
                newDex.getTableOfContents().protoIds.off,
                newDex.getTableOfContents().fieldIds.off,
                newDex.getTableOfContents().methodIds.off,
                newDex.getTableOfContents().classDefs.off,
                newDex.getTableOfContents().mapList.off,
                newDex.getTableOfContents().typeLists.off,
                newDex.getTableOfContents().annotationSetRefLists.off,
                newDex.getTableOfContents().annotationSets.off,
                newDex.getTableOfContents().classDatas.off,
                newDex.getTableOfContents().codes.off,
                newDex.getTableOfContents().stringDatas.off,
                newDex.getTableOfContents().debugInfos.off,
                newDex.getTableOfContents().annotations.off,
                newDex.getTableOfContents().encodedArrays.off,
                newDex.getTableOfContents().annotationsDirectories.off
            );

            allDfos = new DiffFileOutputStream(out);
            allDfos.writeDiffFileHeader(header);
            allDfos.writeChunk(stringDataPatchOpChunk);
            allDfos.writeChunk(typeIdPatchOpChunk);
            allDfos.writeChunk(typeListPatchOpChunk);
            allDfos.writeChunk(protoIdPatchOpChunk);
            allDfos.writeChunk(fieldIdPatchOpChunk);
            allDfos.writeChunk(methodIdPatchOpChunk);
            allDfos.writeChunk(annotationPatchOpChunk);
            allDfos.writeChunk(annotationSetPatchOpChunk);
            allDfos.writeChunk(annotationSetRefListPatchOpChunk);
            allDfos.writeChunk(annotationDirectoryPatchOpChunk);
            allDfos.writeChunk(staticValuePatchOpChunk);
            allDfos.writeChunk(debugInfoItemPatchOpChunk);
            allDfos.writeChunk(codePatchOpChunk);
            allDfos.writeChunk(classDataPatchOpChunk);
            allDfos.writeChunk(classDefPatchOpChunk);
        } finally {
            IOUtils.closeQuietly(allDfos);
        }
    }

    public interface LogWriter {
        void write(String message);
    }

    private class DiffHelper {
        private final Map<Dex, IndexMap> dexToIndexOrOffsetMemoMap;

        DiffHelper() {
            dexToIndexOrOffsetMemoMap = new HashMap<>(4);
            dexToIndexOrOffsetMemoMap.put(oldDex, new IndexMap(oldDex));
            dexToIndexOrOffsetMemoMap.put(newDex, new IndexMap(newDex));
        }

        private void diffStrings() throws Exception {
            PatchOpRecordList<StringData> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<StringData> diffAlg = new SectionDiffAlgorithm<StringData>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.stringDatas;
                }

                @Override
                protected StringData readItemFromDexSection(Dex.Section section, int index) {
                    return section.readStringData();
                }

                @Override
                protected int compareItem(StringData oldItem, StringData newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<StringData> oldIndexedItem, IndexedItem<StringData> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapStringIds(oldIndexedItem.index, newIndexedItem.index);
                }
            };
            diffAlg.prepare();
            diffAlg.process();
            stringDataPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_STRINGDATA, patchOpList);
        }

        private void diffTypeIds() throws Exception {
            PatchOpRecordList<TypeId> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<TypeId> diffAlg = new SectionDiffAlgorithm<TypeId>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.typeIds;
                }

                @Override
                protected TypeId readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readTypeId());
                }

                @Override
                protected int compareItem(TypeId oldItem, TypeId newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<TypeId> oldIndexedItem, IndexedItem<TypeId> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapTypeIds(oldIndexedItem.index, newIndexedItem.index);
                }
            };
            diffAlg.prepare().process();
            typeIdPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_TYPEID, patchOpList);
        }

        private void diffTypeLists() throws Exception {
            PatchOpRecordList<TypeList> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<TypeList> diffAlg = new SectionDiffAlgorithm<TypeList>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.typeLists;
                }

                @Override
                protected TypeList readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readTypeList());
                }

                @Override
                protected int compareItem(TypeList oldItem, TypeList newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<TypeList> oldIndexedItem, IndexedItem<TypeList> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapTypeListOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            typeListPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_TYPELIST, patchOpList);
        }

        private void diffProtoIds() throws Exception {
            PatchOpRecordList<ProtoId> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<ProtoId> diffAlg = new SectionDiffAlgorithm<ProtoId>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.protoIds;
                }

                @Override
                protected ProtoId readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readProtoId());
                }

                @Override
                protected int compareItem(ProtoId oldItem, ProtoId newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<ProtoId> oldIndexedItem, IndexedItem<ProtoId> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapProtoIds(oldIndexedItem.index, newIndexedItem.index);
                }
            };
            diffAlg.prepare().process();
            protoIdPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_PROTOID, patchOpList);
        }

        private void diffFieldIds() throws Exception {
            PatchOpRecordList<FieldId> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<FieldId> diffAlg = new SectionDiffAlgorithm<FieldId>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.fieldIds;
                }

                @Override
                protected int getNewItemCount() {
                    return super.getNewItemCount();
                }

                @Override
                protected FieldId readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readFieldId());
                }

                @Override
                protected int compareItem(FieldId oldItem, FieldId newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<FieldId> oldIndexedItem, IndexedItem<FieldId> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapFieldIds(oldIndexedItem.index, newIndexedItem.index);
                }
            };
            diffAlg.prepare().process();
            fieldIdPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_FIELDID, patchOpList);
        }

        private void diffMethodIds() throws Exception {
            PatchOpRecordList<MethodId> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<MethodId> diffAlg = new SectionDiffAlgorithm<MethodId>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.methodIds;
                }

                @Override
                protected int getNewItemCount() {
                    return super.getNewItemCount();
                }

                @Override
                protected MethodId readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readMethodId());
                }

                @Override
                protected int compareItem(MethodId oldItem, MethodId newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<MethodId> oldIndexedItem, IndexedItem<MethodId> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapMethodIds(oldIndexedItem.index, newIndexedItem.index);
                }
            };
            diffAlg.prepare().process();
            methodIdPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_METHODID, patchOpList);
        }

        private void diffAnnotations() throws Exception {
            PatchOpRecordList<Annotation> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<Annotation> diffAlg = new SectionDiffAlgorithm<Annotation>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotations;
                }

                @Override
                protected Annotation readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readAnnotation());
                }

                @Override
                protected int compareItem(Annotation oldItem, Annotation newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<Annotation> oldIndexedItem, IndexedItem<Annotation> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapAnnotationOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            annotationPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_ANNOTATION, patchOpList);
        }

        private void diffAnnotationSets() throws Exception {
            PatchOpRecordList<AnnotationSet> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<AnnotationSet> diffAlg = new SectionDiffAlgorithm<AnnotationSet>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotationSets;
                }

                @Override
                protected AnnotationSet readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readAnnotationSet());
                }

                @Override
                protected int compareItem(AnnotationSet oldItem, AnnotationSet newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<AnnotationSet> oldIndexedItem, IndexedItem<AnnotationSet> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapAnnotationSetOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            annotationSetPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_ANNOTATION_SET, patchOpList);
        }

        private void diffAnnotationSetRefLists() throws Exception {
            PatchOpRecordList<AnnotationSetRefList> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<AnnotationSetRefList> diffAlg = new SectionDiffAlgorithm<AnnotationSetRefList>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotationSetRefLists;
                }

                @Override
                protected AnnotationSetRefList readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readAnnotationSetRefList());
                }

                @Override
                protected int compareItem(AnnotationSetRefList oldItem, AnnotationSetRefList newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<AnnotationSetRefList> oldIndexedItem, IndexedItem<AnnotationSetRefList> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapAnnotationSetRefListOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            annotationSetRefListPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_ANNOTATION_SET_REFLIST, patchOpList);
        }

        private void diffAnnotationDirectories() throws Exception {
            PatchOpRecordList<AnnotationDirectory> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<AnnotationDirectory> diffAlg = new SectionDiffAlgorithm<AnnotationDirectory>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotationsDirectories;
                }

                @Override
                protected AnnotationDirectory readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readAnnotationDirectory());
                }

                @Override
                protected int compareItem(AnnotationDirectory oldItem, AnnotationDirectory newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<AnnotationDirectory> oldIndexedItem, IndexedItem<AnnotationDirectory> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapAnnotationDirectoryOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            annotationDirectoryPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_ANNOTATION_DIRECTORY, patchOpList);
        }

        private void diffStaticValues() throws Exception {
            PatchOpRecordList<EncodedValue> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<EncodedValue> diffAlg = new SectionDiffAlgorithm<EncodedValue>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.encodedArrays;
                }

                @Override
                protected EncodedValue readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjustEncodedArray(section.readEncodedArray());
                }

                @Override
                protected int compareItem(EncodedValue oldItem, EncodedValue newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<EncodedValue> oldIndexedItem, IndexedItem<EncodedValue> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapStaticValuesOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            staticValuePatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_ENCODEDVALUE, patchOpList);
        }

        private void diffDebugInfoItem() throws Exception {
            PatchOpRecordList<DebugInfoItem> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<DebugInfoItem> diffAlg = new SectionDiffAlgorithm<DebugInfoItem>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.debugInfos;
                }

                @Override
                protected int getNewItemCount() {
                    return super.getNewItemCount();
                }

                @Override
                protected DebugInfoItem readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readDebugInfoItem());
                }

                @Override
                protected int compareItem(DebugInfoItem oldItem, DebugInfoItem newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<DebugInfoItem> oldIndexedItem, IndexedItem<DebugInfoItem> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapDebugInfoItemOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            debugInfoItemPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_DEBUGINFO_ITEM, patchOpList);
        }

        private void diffCode() throws Exception {
            PatchOpRecordList<Code> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<Code> diffAlg = new SectionDiffAlgorithm<Code>(oldDex, newDex, patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.codes;
                }

                @Override
                protected int getNewItemCount() {
                    return super.getNewItemCount();
                }

                @Override
                protected Code readItemFromDexSection(Dex.Section section, int index) {
                    return dexToIndexOrOffsetMemoMap.get(section.getOwnerDex()).adjust(section.readCode());
                }

                @Override
                protected int compareItem(Code oldItem, Code newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<Code> oldIndexedItem, IndexedItem<Code> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapCodeOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            codePatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_CODE, patchOpList);
        }

        private void diffClassData() throws Exception {
            PatchOpRecordList<ClassData> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<ClassData> diffAlg = new SectionDiffAlgorithm<ClassData>(oldDex, newDex, patchOpList) {
                private int currClassDataOffset = newDex.getTableOfContents().classDatas.off;

                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.classDatas;
                }

                @Override
                protected int getNewItemCount() {
                    return super.getNewItemCount() - classExcludingAlgorithm.getExcludedClassDataCount();
                }

                @Override
                protected ClassData readItemFromDexSection(Dex.Section section, int index) {
                    if (section.getOwnerDex().hashCode() == oldDex.hashCode()) {
                        return dexToIndexOrOffsetMemoMap.get(oldDex).adjust(section.readClassData());
                    } else {
                        ClassData classData = section.readClassData();
                        while (classExcludingAlgorithm.isClassDataOffsetExcluded(classData.off)) {
                            classData = section.readClassData();
                        }
                        int afterSkipClassDataOffset = classData.off;
                        if (afterSkipClassDataOffset != currClassDataOffset) {
                            classData = classData.clone(classData.owner, currClassDataOffset);
                        }
                        dexToIndexOrOffsetMemoMap.get(newDex).mapClassDataOffset(afterSkipClassDataOffset, currClassDataOffset);
                        currClassDataOffset += classData.getByteCountInDex();
                        return dexToIndexOrOffsetMemoMap.get(newDex).adjust(classData);
                    }
                }

                @Override
                protected int compareItem(ClassData oldItem, ClassData newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<ClassData> oldIndexedItem, IndexedItem<ClassData> newIndexedItem) {
                    dexToIndexOrOffsetMemoMap.get(oldDex).mapClassDataOffset(oldIndexedItem.item.off, newIndexedItem.item.off);
                }
            };
            diffAlg.prepare().process();
            classDataPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_CLASSDATA, patchOpList);
        }

        private void diffClassDefs() throws Exception {
            PatchOpRecordList<ClassDef> patchOpList = new PatchOpRecordList<>();
            SectionDiffAlgorithm<ClassDef> diffAlg = new SectionDiffAlgorithm<ClassDef>(oldDex, newDex, patchOpList) {
                private int actualNewItemIndex = 0;

                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.classDefs;
                }

                @Override
                protected int getNewItemCount() {
                    return super.getNewItemCount() - classExcludingAlgorithm.getExcludedClassDefCount();
                }

                @Override
                protected ClassDef readItemFromDexSection(Dex.Section section, int index) {
                    if (section.getOwnerDex().hashCode() == oldDex.hashCode()) {
                        return dexToIndexOrOffsetMemoMap.get(oldDex).adjust(section.readClassDef());
                    } else {
                        ClassDef classDef = section.readClassDef();
                        while (classExcludingAlgorithm.isClassDefIndexExcluded(actualNewItemIndex)) {
                            writeLogIfNeeded("[Exclude Class] %s was removed.", newDex.typeNames().get(classDef.typeIndex));
                            classDef = section.readClassDef();
                            ++actualNewItemIndex;
                        }
                        ++actualNewItemIndex;
                        return dexToIndexOrOffsetMemoMap.get(newDex).adjust(classDef);
                    }
                }

                @Override
                protected int compareItem(ClassDef oldItem, ClassDef newItem) {
                    return oldItem.compareTo(newItem);
                }

                @Override
                protected void updateIndexedItem(IndexedItem<ClassDef> oldIndexedItem, IndexedItem<ClassDef> newIndexedItem) {
                    // Do nothing.
                }
            };
            diffAlg.prepare().process();
            classDefPatchOpChunk = new DiffFileChunk<>(ChunkValueType.TYPE_CLASSDEF, patchOpList);
        }

        public void diff(Collection<String> namePatternOfExcludedClass) throws Exception {
            if (!oldDex.getTableOfContents().mapList.exists()) {
                throw new IllegalStateException("Old dex has no map section, which cannot be handled by DexDiff tool.");
            }

            if (!newDex.getTableOfContents().mapList.exists()) {
                throw new IllegalStateException("New dex has no map section, which cannot be handled by DexDiff tool.");
            }

            classExcludingAlgorithm = new ClassExcludingAlgorithm(newDex, namePatternOfExcludedClass).prepare().process();

            diffStrings();
            diffTypeIds();
            diffTypeLists();
            diffProtoIds();
            diffFieldIds();
            diffMethodIds();
            diffAnnotations();
            diffAnnotationSets();
            diffAnnotationSetRefLists();
            diffAnnotationDirectories();
            diffStaticValues();
            diffDebugInfoItem();
            diffCode();
            diffClassData();
            diffClassDefs();

            writeStaticLogIfNeeded();
        }

        private void writeStaticLogIfNeeded() {
            if (logWriter == null) {
                return;
            }

            writeLogIfNeeded("------------------ Statistic Start ------------------");

            writeEachItemStaticLogIfNeeded("Strings", stringDataPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("TypeIds", typeIdPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("TypeLists", typeListPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("ProtoIds", protoIdPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("FieldIds", fieldIdPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("MethodIds", methodIdPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded(
                "ClassDefs (Excluded class was also counted)",
                classDefPatchOpChunk.patchOpList
            );
            writeEachItemStaticLogIfNeeded("Annotations", annotationPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("AnnotationSets", annotationSetPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("AnnotationSetRefLists", annotationSetRefListPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("AnnotationDirectories", annotationDirectoryPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("StaticValues", staticValuePatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("DebugInfos", debugInfoItemPatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("Codes", codePatchOpChunk.patchOpList);
            writeEachItemStaticLogIfNeeded("ClassData", classDataPatchOpChunk.patchOpList);

            writeLogIfNeeded("------------------  Statistic End  ------------------");
        }

        private void writeEachItemStaticLogIfNeeded(String title, PatchOpRecordList<?> patchOpList) {
            if (logWriter == null) {
                return;
            }

            int addedItemCount = 0;
            int deletedItemCount = 0;
            int changedItemCount = 0;
            int movedItemCount = 0;

            for (PatchOpRecord<?> opRecord : patchOpList) {
                switch (opRecord.op) {
                    case PatchOpRecord.OP_ADD: {
                        ++addedItemCount;
                        break;
                    }
                    case PatchOpRecord.OP_DEL: {
                        ++deletedItemCount;
                        break;
                    }
                    case PatchOpRecord.OP_REPLACE: {
                        ++changedItemCount;
                        break;
                    }
                    case PatchOpRecord.OP_MOVE: {
                        ++movedItemCount;
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }

            writeLogIfNeeded("%s:", title);
            writeLogIfNeeded("\t%d items added.", addedItemCount);
            writeLogIfNeeded("\t%d items deleted.", deletedItemCount);
            writeLogIfNeeded("\t%d items changed.", changedItemCount);
            writeLogIfNeeded("\t%d items moved.\n", movedItemCount);
        }

        private void writeLogIfNeeded(String fmt, Object... values) {
            if (logWriter != null) {
                logWriter.write(String.format("[DexDiff] " + fmt, values));
            }
        }
    }
}
