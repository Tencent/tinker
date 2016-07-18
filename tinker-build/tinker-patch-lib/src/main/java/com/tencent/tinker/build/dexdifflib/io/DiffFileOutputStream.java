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

package com.tencent.tinker.build.dexdifflib.io;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationDirectory;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.ClassData.Field;
import com.tencent.tinker.android.dex.ClassData.Method;
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.Code.CatchHandler;
import com.tencent.tinker.android.dex.Code.Try;
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.android.dex.TypeId;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.commons.dexdifflib.io.DexDataOutputStream;
import com.tencent.tinker.commons.dexdifflib.struct.ChunkValueType;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileChunk;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileHeader;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecord;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class DiffFileOutputStream extends DexDataOutputStream {

    public DiffFileOutputStream(OutputStream out) {
        super(out);
    }

    public void writeByteArray(byte[] value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Value is null.");
        }

        int length = value.length;

        writeUleb128p1(length);

        if (length != 0) {
            write(value);
        }
    }

    public void writeShortArray(short[] value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Value is null.");
        }

        int length = value.length;

        writeUleb128p1(length);

        for (int i = 0; i < length; i++) {
            writeShort(value[i]);
        }
    }

    public void writeIntArray(int[] value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Value is null.");
        }

        int length = value.length;

        writeUleb128p1(length);

        for (int i = 0; i < length; i++) {
            writeUleb128(value[i]);
        }
    }

    public void writeDiffFileHeader(DiffFileHeader value) throws IOException {
        write(DiffFileHeader.MAGIC);
        writeShort(value.version);
        writeInt(value.newDexSize);
        writeInt(value.firstChunkOffset);
        writeInt(value.newStringIdSectionOffset);
        writeInt(value.newTypeIdSectionOffset);
        writeInt(value.newProtoIdSectionOffset);
        writeInt(value.newFieldIdSectionOffset);
        writeInt(value.newMethodIdSectionOffset);
        writeInt(value.newClassDefSectionOffset);
        writeInt(value.newMapListSectionOffset);
        writeInt(value.newTypeListSectionOffset);
        writeInt(value.newAnnotationSetRefListSectionOffset);
        writeInt(value.newAnnotationSetSectionOffset);
        writeInt(value.newClassDataSectionOffset);
        writeInt(value.newCodeSectionOffset);
        writeInt(value.newStringDataSectionOffset);
        writeInt(value.newDebugInfoSectionOffset);
        writeInt(value.newAnnotationSectionOffset);
        writeInt(value.newEncodedArraySectionOffset);
        writeInt(value.newAnnotationsDirectorySectionOffset);
    }

    public void writeChunk(DiffFileChunk<? extends SectionItem<?>> value) throws IOException {
        writeValue(value.patchOpList, false);
    }

    private void writeValue(Object value, boolean isWriteType) throws IOException {
        Class<?> valClazz = value.getClass();
        if (Byte.class.equals(valClazz) || byte.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_BYTE);
            }
            writeByte((Byte) value);
        } else if (Short.class.equals(valClazz) || short.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_SHORT);
            }
            writeShort((Short) value);
        } else if (Integer.class.equals(valClazz) || int.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_INT);
            }
            writeUleb128((Integer) value);
        } else if (StringData.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_STRINGDATA);
            }
            writeStringData((StringData) value);
        } else if (TypeId.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_TYPEID);
            }
            writeTypeId((TypeId) value);
        } else if (TypeList.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_TYPELIST);
            }
            writeTypeList((TypeList) value);
        } else if (ProtoId.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_PROTOID);
            }
            writeProtoId((ProtoId) value);
        } else if (FieldId.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_FIELDID);
            }
            writeFieldId((FieldId) value);
        } else if (MethodId.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_METHODID);
            }
            writeMethodId((MethodId) value);
        } else if (Annotation.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_ANNOTATION);
            }
            writeAnnotation((Annotation) value);
        } else if (AnnotationSet.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_ANNOTATION_SET);
            }
            writeAnnotationSet((AnnotationSet) value);
        } else if (AnnotationSetRefList.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_ANNOTATION_SET_REFLIST);
            }
            writeAnnotationSetRefList((AnnotationSetRefList) value);
        } else if (AnnotationDirectory.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_ANNOTATION_DIRECTORY);
            }
            writeAnnotationDirectory((AnnotationDirectory) value);
        } else if (EncodedValue.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_ENCODEDVALUE);
            }
            writeEncodedArray((EncodedValue) value);
        } else if (ClassDef.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_CLASSDEF);
            }
            writeClassDef((ClassDef) value);
        } else if (ClassData.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_CLASSDATA);
            }
            writeClassData((ClassData) value);
        } else if (Code.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_CODE);
            }
            writeCode((Code) value);
        } else if (Try.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_TRY);
            }
            writeTry((Try) value);
        } else if (CatchHandler.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_CATCH_HANDLER);
            }
            writeCatchHandler((CatchHandler) value);
        } else if (DebugInfoItem.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_DEBUGINFO_ITEM);
            }
            writeDebugInfoItem((DebugInfoItem) value);
        } else if (PatchOpRecordList.class.equals(valClazz)) {
            if (isWriteType) {
                writeByte(ChunkValueType.TYPE_PATCHOP_LIST);
            }
            PatchOpRecordList<?> patchOpList = (PatchOpRecordList<?>) value;
            int patchOpCount = patchOpList.size();
            List<PatchOpRecord<?>> delOpList = new ArrayList<>();
            List<PatchOpRecord<?>> replaceOpList = new ArrayList<>();
            List<PatchOpRecord<?>> moveOpList = new ArrayList<>();
            List<PatchOpRecord<?>> addOpList = new ArrayList<>();
            for (int i = 0; i < patchOpCount; ++i) {
                PatchOpRecord<?> patchOp = patchOpList.get(i);
                switch (patchOp.op) {
                    case PatchOpRecord.OP_DEL: {
                        delOpList.add(patchOp);
                        break;
                    }
                    case PatchOpRecord.OP_REPLACE: {
                        replaceOpList.add(patchOp);
                        break;
                    }
                    case PatchOpRecord.OP_MOVE: {
                        moveOpList.add(patchOp);
                        break;
                    }
                    case PatchOpRecord.OP_ADD: {
                        addOpList.add(patchOp);
                        break;
                    }
                    default: {
                        break;
                    }
                }
            }
            int lastOldIndex = 0;
            writeUleb128p1(delOpList.size());
            for (PatchOpRecord<?> delOp : delOpList) {
                writePatchOpRecord(lastOldIndex, delOp);
                lastOldIndex = delOp.oldIndex;
            }
            lastOldIndex = 0;
            writeUleb128p1(replaceOpList.size());
            for (PatchOpRecord<?> replaceOp : replaceOpList) {
                writePatchOpRecord(lastOldIndex, replaceOp);
                lastOldIndex = replaceOp.oldIndex;
            }
            lastOldIndex = 0;
            writeUleb128p1(moveOpList.size());
            for (PatchOpRecord<?> moveOp : moveOpList) {
                writePatchOpRecord(lastOldIndex, moveOp);
                lastOldIndex = moveOp.oldIndex;
            }
            lastOldIndex = 0;
            writeUleb128p1(addOpList.size());
            for (PatchOpRecord<?> addOp : addOpList) {
                writePatchOpRecord(lastOldIndex, addOp);
                lastOldIndex = addOp.oldIndex;
            }
        } else {
            throw new IOException("Unknown value type: " + valClazz);
        }
    }

    private void writePatchOpRecord(int lastOldIndex, PatchOpRecord<?> patchOp) throws IOException {
        writeSleb128(patchOp.oldIndex - lastOldIndex);
        if (patchOp.op != PatchOpRecord.OP_DEL) {
            writeSleb128(patchOp.newIndex - patchOp.oldIndex);
            if (patchOp.op != PatchOpRecord.OP_MOVE) {
                writeValue(patchOp.newItem, true);
            }
        }
    }

    public void writeTypeId(TypeId value) throws IOException {
        writeInt(value.descriptorIndex);
    }

    public void writeTypeList(TypeList value) throws IOException {
        writeShortArray(value.types);
    }

    public void writeStringData(StringData value) throws IOException {
        writeUTF(value.value);
    }

    public void writeFieldId(FieldId value) throws IOException {
        writeShort(value.declaringClassIndex);
        writeShort(value.typeIndex);
        writeInt(value.nameIndex);
    }

    public void writeMethodId(MethodId value) throws IOException {
        writeShort(value.declaringClassIndex);
        writeShort(value.protoIndex);
        writeInt(value.nameIndex);
    }

    public void writeProtoId(ProtoId value) throws IOException {
        writeInt(value.shortyIndex);
        writeInt(value.returnTypeIndex);
        writeInt(value.parametersOffset);
    }

    public void writeClassDef(ClassDef value) throws IOException {
        writeInt(value.typeIndex);
        writeInt(value.accessFlags);
        writeInt(value.supertypeIndex);
        writeInt(value.interfacesOffset);
        writeInt(value.sourceFileIndex);
        writeInt(value.annotationsOffset);
        writeInt(value.classDataOffset);
        writeInt(value.staticValuesOffset);
    }

    private void writeCode(Code value) throws IOException {
        writeShort(value.registersSize);
        writeShort(value.insSize);
        writeShort(value.outsSize);
        writeShort(value.tries.length);
        writeInt(value.debugInfoOffset);
        writeShortArray(value.instructions);
        writeTries(value.tries);
        writeCatchHandlers(value.catchHandlers);
    }

    private void writeTries(Try[] value) throws IOException {
        int count = value.length;
        for (int i = 0; i < count; ++i) {
            writeTry(value[i]);
        }
    }

    private void writeTry(Try value) throws IOException {
        writeInt(value.startAddress);
        writeUleb128(value.instructionCount);
        writeUleb128(value.catchHandlerIndex);
    }

    private void writeCatchHandlers(CatchHandler[] value) throws IOException {
        int count = value.length;
        writeUleb128(count);

        for (int i = 0; i < count; ++i) {
            writeCatchHandler(value[i]);
        }
    }

    private void writeCatchHandler(CatchHandler value) {
        int typeAddrPairCount = value.typeIndexes.length;

        if (value.catchAllAddress != -1) {
            writeSleb128(-typeAddrPairCount);
        } else {
            writeSleb128(typeAddrPairCount);
        }

        for (int i = 0; i < typeAddrPairCount; ++i) {
            writeUleb128(value.typeIndexes[i]);
            writeUleb128(value.addresses[i]);
        }

        if (value.catchAllAddress != -1) {
            writeUleb128(value.catchAllAddress);
        }

        writeUleb128(value.offset);
    }

    public void writeDebugInfoItem(DebugInfoItem value) throws IOException {
        writeUleb128(value.lineStart);
        writeIntArray(value.parameterNames);
        writeByteArray(value.infoSTM);
    }

    public void writeClassData(ClassData value) throws IOException {
        writeFields(value.staticFields);
        writeFields(value.instanceFields);
        writeMethods(value.directMethods);
        writeMethods(value.virtualMethods);
    }

    private void writeFields(Field[] value) throws IOException {
        int count = value.length;
        writeUleb128(count);

        for (int i = 0; i < count; i++) {
            writeUleb128(value[i].fieldIndex);
            writeUleb128(value[i].accessFlags);
        }
    }

    private void writeMethods(Method[] value) throws IOException {
        int count = value.length;
        writeUleb128(count);

        for (int i = 0; i < count; i++) {
            writeUleb128(value[i].methodIndex);
            writeUleb128(value[i].accessFlags);
            writeUleb128(value[i].codeOffset);
        }
    }

    public void writeAnnotation(Annotation value) throws IOException {
        writeByte(value.visibility);
        writeEncodedArray(value.encodedAnnotation);
    }

    public void writeAnnotationSet(AnnotationSet value) throws IOException {
        writeIntArray(value.annotationOffsets);
    }

    public void writeAnnotationSetRefList(AnnotationSetRefList value) throws IOException {
        writeIntArray(value.annotationSetRefItems);
    }

    public void writeAnnotationDirectory(AnnotationDirectory value) throws IOException {
        writeUleb128(value.classAnnotationsOffset);

        int fieldAnnotationCount = value.fieldAnnotations.length;
        writeUleb128(fieldAnnotationCount);

        for (int i = 0; i < fieldAnnotationCount; ++i) {
            writeUleb128(value.fieldAnnotations[i][0]);
            writeUleb128(value.fieldAnnotations[i][1]);
        }

        int methodAnnotationCount = value.methodAnnotations.length;
        writeUleb128(methodAnnotationCount);

        for (int i = 0; i < methodAnnotationCount; ++i) {
            writeUleb128(value.methodAnnotations[i][0]);
            writeUleb128(value.methodAnnotations[i][1]);
        }

        int parameterAnnotationCount = value.parameterAnnotations.length;
        writeUleb128(parameterAnnotationCount);

        for (int i = 0; i < parameterAnnotationCount; ++i) {
            writeUleb128(value.parameterAnnotations[i][0]);
            writeUleb128(value.parameterAnnotations[i][1]);
        }
    }

    public void writeEncodedArray(EncodedValue value) throws IOException {
        writeByteArray(value.data);
    }
}
