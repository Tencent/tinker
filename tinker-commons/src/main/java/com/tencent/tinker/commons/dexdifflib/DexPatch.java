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

package com.tencent.tinker.commons.dexdifflib;

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
import com.tencent.tinker.commons.dexdifflib.algorithm.SectionPatchAlgorithm;
import com.tencent.tinker.commons.dexdifflib.io.DiffFileInputStream;
import com.tencent.tinker.commons.dexdifflib.struct.ChunkValueType;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileChunk;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileHeader;
import com.tencent.tinker.commons.dexdifflib.struct.IndexMap;
import com.tencent.tinker.commons.dexdifflib.util.IOUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by tomystang on 2016/4/14.
 */
public class DexPatch {
    private Dex oldDex = null;
    private Dex newDex = null;

    private DiffFileInputStream dfis = null;

    @SuppressWarnings("unused")
    private DiffFileHeader diffFileHeader = null;

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

    @SuppressWarnings("unchecked")
    public DexPatch(File dexDiffFile) throws IOException {
        this(new FileInputStream(dexDiffFile));
    }

    @SuppressWarnings("unchecked")
    public DexPatch(InputStream in) throws IOException {
        dfis = new DiffFileInputStream(new BufferedInputStream(in));
        this.diffFileHeader = dfis.readDiffFileHeader();

        IOUtils.skipIndeed(dfis, this.diffFileHeader.firstChunkOffset - DiffFileHeader.SIZE);
    }

    public void applyPatchAndSave(File oldDexFile, File newDexFile) throws Exception {
        this.oldDex = new Dex(oldDexFile);
        new PatchHelper().applyPatch();
        this.newDex.writeTo(newDexFile);
    }

    public void applyPatchAndSave(InputStream oldDexStream, File newDexFile) throws Exception {
        this.oldDex = new Dex(oldDexStream);
        new PatchHelper().applyPatch();
        this.newDex.writeTo(newDexFile);
    }

    public void applyPatchAndSave(File oldDexFile, OutputStream newDexStream) throws Exception {
        this.oldDex = new Dex(oldDexFile);
        new PatchHelper().applyPatch();
        this.newDex.writeTo(newDexStream);
    }

    public void applyPatchAndSave(InputStream oldDexStream, OutputStream newDexStream) throws Exception {
        this.oldDex = new Dex(oldDexStream);
        new PatchHelper().applyPatch();
        this.newDex.writeTo(newDexStream);
    }

    private class PatchHelper {
        private TableOfContents contentsOut             = null;
        private Dex.Section     headerOut               = null;
        private Dex.Section     stringIdsOut            = null;
        private Dex.Section     typeIdsOut              = null;
        private Dex.Section     protoIdsOut             = null;
        private Dex.Section     fieldIdsOut             = null;
        private Dex.Section     methodIdsOut            = null;
        private Dex.Section     classDefsOut            = null;
        private Dex.Section     mapListOut              = null;
        private Dex.Section     typeListOut             = null;
        private Dex.Section     classDataOut            = null;
        private Dex.Section     codeOut                 = null;
        private Dex.Section     stringDataOut           = null;
        private Dex.Section     debugInfoOut            = null;
        private Dex.Section     encodedArrayOut         = null;
        private Dex.Section     annotationsDirectoryOut = null;
        private Dex.Section     annotationSetOut        = null;
        private Dex.Section     annotationSetRefListOut = null;
        private Dex.Section     annotationOut           = null;

        private IndexMap indexOrOffsetMap = null;

        PatchHelper() {
            this.indexOrOffsetMap = new IndexMap(oldDex);
        }

        private SectionPatchAlgorithm<StringData> getStringDataPatchAlgorithm() {
            return new SectionPatchAlgorithm<StringData>(oldDex, stringDataPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.stringDatas;
                }

                @Override
                protected StringData readItemFromDexSection(Dex.Section section, int index) {
                    return section.readStringData();
                }

                @Override
                protected void constructPatchedItem(int newIndex, StringData newItem) {
                    stringIdsOut.writeInt(stringDataOut.getPosition());
                    stringDataOut.writeStringData(newItem);
                    ++contentsOut.stringDatas.size;
                    ++contentsOut.stringIds.size;
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.stringIds.isFourByteAlign) {
                        stringIdsOut.alignToFourBytesWithZeroFill();
                    }

                    if (contentsOut.stringDatas.isFourByteAlign) {
                        stringDataOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldIndex != newIndex) {
                        indexOrOffsetMap.mapStringIds(oldIndex, newIndex);
                    }
                }
            };
        }

        private SectionPatchAlgorithm<TypeId> getTypeIdPatchAlgorithm() {
            return new SectionPatchAlgorithm<TypeId>(oldDex, typeIdPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.typeIds;
                }

                @Override
                protected TypeId readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readTypeId());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.typeIds.isFourByteAlign) {
                        typeIdsOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, TypeId newItem) {
                    typeIdsOut.writeTypeId(newItem);
                    ++contentsOut.typeIds.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldIndex != newIndex) {
                        indexOrOffsetMap.mapTypeIds(oldIndex, newIndex);
                    }
                }
            };
        }

        private SectionPatchAlgorithm<TypeList> getTypeListPatchAlgorithm() {
            return new SectionPatchAlgorithm<TypeList>(oldDex, typeListPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.typeLists;
                }

                @Override
                protected TypeList readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readTypeList());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.typeLists.isFourByteAlign) {
                        typeListOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, TypeList newItem) {
                    typeListOut.writeTypeList(newItem);
                    ++contentsOut.typeLists.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != typeListOut.getPosition()) {
                        indexOrOffsetMap.mapTypeListOffset(oldOffset, typeListOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<ProtoId> getProtoIdPatchAlgorithm() {
            return new SectionPatchAlgorithm<ProtoId>(oldDex, protoIdPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.protoIds;
                }

                @Override
                protected ProtoId readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readProtoId());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.protoIds.isFourByteAlign) {
                        protoIdsOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, ProtoId newItem) {
                    protoIdsOut.writeProtoId(newItem);
                    ++contentsOut.protoIds.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldIndex != newIndex) {
                        indexOrOffsetMap.mapProtoIds(oldIndex, newIndex);
                    }
                }
            };
        }

        private SectionPatchAlgorithm<FieldId> getFieldIdPatchAlgorithm() {
            return new SectionPatchAlgorithm<FieldId>(oldDex, fieldIdPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.fieldIds;
                }

                @Override
                protected FieldId readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readFieldId());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.fieldIds.isFourByteAlign) {
                        fieldIdsOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, FieldId newItem) {
                    fieldIdsOut.writeFieldId(newItem);
                    ++contentsOut.fieldIds.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldIndex != newIndex) {
                        indexOrOffsetMap.mapFieldIds(oldIndex, newIndex);
                    }
                }
            };
        }

        private SectionPatchAlgorithm<MethodId> getMethodIdPatchAlgorithm() {
            return new SectionPatchAlgorithm<MethodId>(oldDex, methodIdPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.methodIds;
                }

                @Override
                protected MethodId readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readMethodId());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.methodIds.isFourByteAlign) {
                        methodIdsOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, MethodId newItem) {
                    methodIdsOut.writeMethodId(newItem);
                    ++contentsOut.methodIds.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldIndex != newIndex) {
                        indexOrOffsetMap.mapMethodIds(oldIndex, newIndex);
                    }
                }
            };
        }

        private SectionPatchAlgorithm<Annotation> getAnnotationPatchAlgorithm() {
            return new SectionPatchAlgorithm<Annotation>(oldDex, annotationPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotations;
                }

                @Override
                protected Annotation readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readAnnotation());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.annotations.isFourByteAlign) {
                        annotationOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, Annotation newItem) {
                    annotationOut.writeAnnotation(newItem);
                    ++contentsOut.annotations.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != annotationOut.getPosition()) {
                        indexOrOffsetMap.mapAnnotationOffset(oldOffset, annotationOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<AnnotationSet> getAnnotationSetPatchAlgorithm() {
            return new SectionPatchAlgorithm<AnnotationSet>(oldDex, annotationSetPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotationSets;
                }

                @Override
                protected AnnotationSet readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readAnnotationSet());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.annotationSets.isFourByteAlign) {
                        annotationSetOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, AnnotationSet newItem) {
                    annotationSetOut.writeAnnotationSet(newItem);
                    ++contentsOut.annotationSets.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != annotationSetOut.getPosition()) {
                        indexOrOffsetMap.mapAnnotationSetOffset(oldOffset, annotationSetOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<AnnotationSetRefList> getAnnotationSetRefListPatchAlgorithm() {
            return new SectionPatchAlgorithm<AnnotationSetRefList>(oldDex, annotationSetRefListPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotationSetRefLists;
                }

                @Override
                protected AnnotationSetRefList readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readAnnotationSetRefList());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.annotationSetRefLists.isFourByteAlign) {
                        annotationSetRefListOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, AnnotationSetRefList newItem) {
                    annotationSetRefListOut.writeAnnotationSetRefList(newItem);
                    ++contentsOut.annotationSetRefLists.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != annotationSetRefListOut.getPosition()) {
                        indexOrOffsetMap.mapAnnotationSetRefListOffset(oldOffset, annotationSetRefListOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<AnnotationDirectory> getAnnotationDirectoryPatchAlgorithm() {
            return new SectionPatchAlgorithm<AnnotationDirectory>(oldDex, annotationDirectoryPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.annotationsDirectories;
                }

                @Override
                protected AnnotationDirectory readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readAnnotationDirectory());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.annotationsDirectories.isFourByteAlign) {
                        annotationsDirectoryOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, AnnotationDirectory newItem) {
                    annotationsDirectoryOut.writeAnnotationDirectory(newItem);
                    ++contentsOut.annotationsDirectories.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != annotationsDirectoryOut.getPosition()) {
                        indexOrOffsetMap.mapAnnotationDirectoryOffset(oldOffset, annotationsDirectoryOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<EncodedValue> getStaticValuePatchAlgorithm() {
            return new SectionPatchAlgorithm<EncodedValue>(oldDex, staticValuePatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.encodedArrays;
                }

                @Override
                protected EncodedValue readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjustEncodedArray(section.readEncodedArray());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.encodedArrays.isFourByteAlign) {
                        encodedArrayOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, EncodedValue newItem) {
                    encodedArrayOut.writeEncodedArray(newItem);
                    ++contentsOut.encodedArrays.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != encodedArrayOut.getPosition()) {
                        indexOrOffsetMap.mapStaticValuesOffset(oldOffset, encodedArrayOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<DebugInfoItem> getDebugInfoItemPatchAlgorithm() throws FileNotFoundException {
            return new SectionPatchAlgorithm<DebugInfoItem>(oldDex, debugInfoItemPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.debugInfos;
                }

                @Override
                protected DebugInfoItem readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readDebugInfoItem());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.debugInfos.isFourByteAlign) {
                        debugInfoOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, DebugInfoItem newItem) {
                    debugInfoOut.writeDebugInfoItem(newItem);
                    ++contentsOut.debugInfos.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != debugInfoOut.getPosition()) {
                        indexOrOffsetMap.mapDebugInfoItemOffset(oldOffset, debugInfoOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<Code> getCodePatchAlgorithm() {
            return new SectionPatchAlgorithm<Code>(oldDex, codePatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.codes;
                }

                @Override
                protected Code readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readCode());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.codes.isFourByteAlign) {
                        codeOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, Code newItem) {
                    codeOut.alignToFourBytesWithZeroFill();
                    codeOut.writeCode(newItem);
                    ++contentsOut.codes.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != codeOut.getPosition()) {
                        indexOrOffsetMap.mapCodeOffset(oldOffset, codeOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<ClassData> getClassDataPatchAlgorithm() {
            return new SectionPatchAlgorithm<ClassData>(oldDex, classDataPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.classDatas;
                }

                @Override
                protected ClassData readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readClassData());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.classDatas.isFourByteAlign) {
                        classDataOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, ClassData newItem) {
                    classDataOut.writeClassData(newItem);
                    ++contentsOut.classDatas.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    if (oldOffset != classDataOut.getPosition()) {
                        indexOrOffsetMap.mapClassDataOffset(oldOffset, classDataOut.getPosition());
                    }
                }
            };
        }

        private SectionPatchAlgorithm<ClassDef> getClassDefPatchAlgorithm() {
            return new SectionPatchAlgorithm<ClassDef>(oldDex, classDefPatchOpChunk.patchOpList) {
                @Override
                protected TableOfContents.Section getToCSection(TableOfContents toc) {
                    return toc.classDefs;
                }

                @Override
                protected ClassDef readItemFromDexSection(Dex.Section section, int index) {
                    return indexOrOffsetMap.adjust(section.readClassDef());
                }

                @Override
                protected void beforePatchItemConstruct(int newIndex) {
                    if (contentsOut.classDefs.isFourByteAlign) {
                        classDefsOut.alignToFourBytesWithZeroFill();
                    }
                }

                @Override
                protected void constructPatchedItem(int newIndex, ClassDef newItem) {
                    classDefsOut.writeClassDef(newItem);
                    ++contentsOut.classDefs.size;
                }

                @Override
                protected void updateIndex(int oldOffset, int oldIndex, int newIndex) {
                    // Do nothing.
                }
            };
        }

        public Dex applyPatch() throws Exception {
            TableOfContents oldToC = oldDex.getTableOfContents();

            if (!oldToC.mapList.exists()) {
                throw new IllegalStateException("Tinker Exception:DexPatch tool cannot handle a dex without map section.");
            }

            newDex = new Dex(diffFileHeader.newDexSize);
            contentsOut = newDex.getTableOfContents();

            contentsOut.header.off = 0;
            contentsOut.header.size = 1;
            contentsOut.stringIds.off = diffFileHeader.newStringIdSectionOffset;
            contentsOut.typeIds.off = diffFileHeader.newTypeIdSectionOffset;
            contentsOut.protoIds.off = diffFileHeader.newProtoIdSectionOffset;
            contentsOut.fieldIds.off = diffFileHeader.newFieldIdSectionOffset;
            contentsOut.methodIds.off = diffFileHeader.newMethodIdSectionOffset;
            contentsOut.classDefs.off = diffFileHeader.newClassDefSectionOffset;
            contentsOut.mapList.off = diffFileHeader.newMapListSectionOffset;
            contentsOut.mapList.size = 1;
            contentsOut.typeLists.off = diffFileHeader.newTypeListSectionOffset;
            contentsOut.annotationSetRefLists.off = diffFileHeader.newAnnotationSetRefListSectionOffset;
            contentsOut.annotationSets.off = diffFileHeader.newAnnotationSetSectionOffset;
            contentsOut.classDatas.off = diffFileHeader.newClassDataSectionOffset;
            contentsOut.codes.off = diffFileHeader.newCodeSectionOffset;
            contentsOut.stringDatas.off = diffFileHeader.newStringDataSectionOffset;
            contentsOut.debugInfos.off = diffFileHeader.newDebugInfoSectionOffset;
            contentsOut.annotations.off = diffFileHeader.newAnnotationSectionOffset;
            contentsOut.encodedArrays.off = diffFileHeader.newEncodedArraySectionOffset;
            contentsOut.annotationsDirectories.off = diffFileHeader.newAnnotationsDirectorySectionOffset;

            contentsOut.fileSize = newDex.getLength();

            Arrays.sort(contentsOut.sections);

            contentsOut.computeSizesFromOffsets();

            contentsOut.dataOff = contentsOut.header.byteCount
                + contentsOut.stringIds.byteCount
                + contentsOut.typeIds.byteCount
                + contentsOut.protoIds.byteCount
                + contentsOut.fieldIds.byteCount
                + contentsOut.methodIds.byteCount
                + contentsOut.classDefs.byteCount;

            for (TableOfContents.Section section : contentsOut.sections) {
                if (!section.exists()) continue;

                switch (section.type) {
                    case TableOfContents.SECTION_TYPE_HEADER: {
                        headerOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_STRINGIDS: {
                        stringIdsOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_TYPEIDS: {
                        typeIdsOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_PROTOIDS: {
                        protoIdsOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_FIELDIDS: {
                        fieldIdsOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_METHODIDS: {
                        methodIdsOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_CLASSDEFS: {
                        classDefsOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_MAPLIST: {
                        mapListOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_TYPELISTS: {
                        typeListOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_ANNOTATIONSETREFLISTS: {
                        annotationSetRefListOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_ANNOTATIONSETS: {
                        annotationSetOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_CLASSDATA: {
                        classDataOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_CODES: {
                        codeOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_STRINGDATAS: {
                        stringDataOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_DEBUGINFOS: {
                        debugInfoOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_ANNOTATIONS: {
                        annotationOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_ENCODEDARRAYS: {
                        encodedArrayOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    case TableOfContents.SECTION_TYPE_ANNOTATIONSDIRECTORIES: {
                        annotationsDirectoryOut = newDex.appendSection(section.byteCount, section);
                        break;
                    }
                    default: {
                        throw new RuntimeException("Tinker Exception:Should not be here.");
                    }
                }
            }

            contentsOut.dataSize = newDex.getNextSectionStart() - contentsOut.dataOff;

            stringDataPatchOpChunk = (DiffFileChunk<StringData>) dfis.readChunk(ChunkValueType.TYPE_STRINGDATA);
            if (stringIdsOut != null) {
                SectionPatchAlgorithm<StringData> stringDataPatchAlg = getStringDataPatchAlgorithm();
                stringDataPatchAlg.prepare().process();
            }
            stringDataPatchOpChunk.patchOpList.clear();
            stringDataPatchOpChunk = null;

            typeIdPatchOpChunk = (DiffFileChunk<TypeId>) dfis.readChunk(ChunkValueType.TYPE_TYPEID);
            if (typeIdsOut != null) {
                SectionPatchAlgorithm<TypeId> typeIdPatchAlg = getTypeIdPatchAlgorithm();
                typeIdPatchAlg.prepare().process();
            }
            typeIdPatchOpChunk.patchOpList.clear();
            typeIdPatchOpChunk = null;

            typeListPatchOpChunk = (DiffFileChunk<TypeList>) dfis.readChunk(ChunkValueType.TYPE_TYPELIST);
            if (typeListOut != null) {
                SectionPatchAlgorithm<TypeList> typeListPatchAlg = getTypeListPatchAlgorithm();
                typeListPatchAlg.prepare().process();
            }
            typeListPatchOpChunk.patchOpList.clear();
            typeListPatchOpChunk = null;

            protoIdPatchOpChunk = (DiffFileChunk<ProtoId>) dfis.readChunk(ChunkValueType.TYPE_PROTOID);
            if (protoIdsOut != null) {
                SectionPatchAlgorithm<ProtoId> protoIdPatchAlg = getProtoIdPatchAlgorithm();
                protoIdPatchAlg.prepare().process();
            }
            protoIdPatchOpChunk.patchOpList.clear();
            protoIdPatchOpChunk = null;

            fieldIdPatchOpChunk = (DiffFileChunk<FieldId>) dfis.readChunk(ChunkValueType.TYPE_FIELDID);
            if (fieldIdsOut != null) {
                SectionPatchAlgorithm<FieldId> fieldIdPatchAlg = getFieldIdPatchAlgorithm();
                fieldIdPatchAlg.prepare().process();
            }
            fieldIdPatchOpChunk.patchOpList.clear();
            fieldIdPatchOpChunk = null;

            methodIdPatchOpChunk = (DiffFileChunk<MethodId>) dfis.readChunk(ChunkValueType.TYPE_METHODID);
            if (methodIdsOut != null) {
                SectionPatchAlgorithm<MethodId> methodIdPatchAlg = getMethodIdPatchAlgorithm();
                methodIdPatchAlg.prepare().process();
            }
            methodIdPatchOpChunk.patchOpList.clear();
            methodIdPatchOpChunk = null;

            annotationPatchOpChunk = (DiffFileChunk<Annotation>) dfis.readChunk(ChunkValueType.TYPE_ANNOTATION);
            if (annotationOut != null) {
                SectionPatchAlgorithm<Annotation> annotationPatchAlg = getAnnotationPatchAlgorithm();
                annotationPatchAlg.prepare().process();
            }
            annotationPatchOpChunk.patchOpList.clear();
            annotationPatchOpChunk = null;

            annotationSetPatchOpChunk = (DiffFileChunk<AnnotationSet>) dfis.readChunk(ChunkValueType.TYPE_ANNOTATION_SET);
            if (annotationSetOut != null) {
                SectionPatchAlgorithm<AnnotationSet> annotationSetPatchAlg = getAnnotationSetPatchAlgorithm();
                annotationSetPatchAlg.prepare().process();
            }
            annotationSetPatchOpChunk.patchOpList.clear();
            annotationSetPatchOpChunk = null;

            annotationSetRefListPatchOpChunk = (DiffFileChunk<AnnotationSetRefList>) dfis.readChunk(ChunkValueType.TYPE_ANNOTATION_SET_REFLIST);
            if (annotationSetRefListOut != null) {
                SectionPatchAlgorithm<AnnotationSetRefList> annotationSetRefListPatchAlg = getAnnotationSetRefListPatchAlgorithm();
                annotationSetRefListPatchAlg.prepare().process();
            }
            annotationSetRefListPatchOpChunk.patchOpList.clear();
            annotationSetRefListPatchOpChunk = null;

            annotationDirectoryPatchOpChunk = (DiffFileChunk<AnnotationDirectory>) dfis.readChunk(ChunkValueType.TYPE_ANNOTATION_DIRECTORY);
            if (annotationsDirectoryOut != null) {
                SectionPatchAlgorithm<AnnotationDirectory> annotationDirectoryPatchAlg = getAnnotationDirectoryPatchAlgorithm();
                annotationDirectoryPatchAlg.prepare().process();
            }
            annotationDirectoryPatchOpChunk.patchOpList.clear();
            annotationDirectoryPatchOpChunk = null;

            staticValuePatchOpChunk = (DiffFileChunk<EncodedValue>) dfis.readChunk(ChunkValueType.TYPE_ENCODEDVALUE);
            if (encodedArrayOut != null) {
                SectionPatchAlgorithm<EncodedValue> staticValuePatchAlg = getStaticValuePatchAlgorithm();
                staticValuePatchAlg.prepare().process();
            }
            staticValuePatchOpChunk.patchOpList.clear();
            staticValuePatchOpChunk = null;

            debugInfoItemPatchOpChunk = (DiffFileChunk<DebugInfoItem>) dfis.readChunk(ChunkValueType.TYPE_DEBUGINFO_ITEM);
            if (debugInfoOut != null) {
                SectionPatchAlgorithm<DebugInfoItem> debugInfoItemPatchAlg = getDebugInfoItemPatchAlgorithm();
                debugInfoItemPatchAlg.prepare().process();
            }
            debugInfoItemPatchOpChunk.patchOpList.clear();
            debugInfoItemPatchOpChunk = null;

            codePatchOpChunk = (DiffFileChunk<Code>) dfis.readChunk(ChunkValueType.TYPE_CODE);
            if (codeOut != null) {
                SectionPatchAlgorithm<Code> codePatchAlg = getCodePatchAlgorithm();
                codePatchAlg.prepare().process();
            }
            codePatchOpChunk.patchOpList.clear();
            codePatchOpChunk = null;

            classDataPatchOpChunk = (DiffFileChunk<ClassData>) dfis.readChunk(ChunkValueType.TYPE_CLASSDATA);
            if (classDataOut != null) {
                SectionPatchAlgorithm<ClassData> classDataPatchAlg = getClassDataPatchAlgorithm();
                classDataPatchAlg.prepare().process();
            }
            classDataPatchOpChunk.patchOpList.clear();
            classDataPatchOpChunk = null;

            classDefPatchOpChunk = (DiffFileChunk<ClassDef>) dfis.readChunk(ChunkValueType.TYPE_CLASSDEF);
            if (classDefsOut != null) {
                SectionPatchAlgorithm<ClassDef> classDefPatchAlg = getClassDefPatchAlgorithm();
                classDefPatchAlg.prepare().process();
            }
            classDefPatchOpChunk.patchOpList.clear();
            classDefPatchOpChunk = null;

            contentsOut.writeHeader(headerOut);
            contentsOut.writeMap(mapListOut);

            // generate and write the hashes
            newDex.writeHashes();

            try {
                dfis.close();
            } catch (Exception e) {
                // ignored.
            }

            return newDex;
        }
    }
}
