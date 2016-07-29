/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.tencent.tinker.commons.dexdifflib.io;

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
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TypeId;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.commons.dexdifflib.struct.ChunkValueType;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileChunk;
import com.tencent.tinker.commons.dexdifflib.struct.DiffFileHeader;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecord;
import com.tencent.tinker.commons.dexdifflib.struct.PatchOpRecordList;

import java.io.IOException;
import java.io.InputStream;

public class DiffFileInputStream extends DexDataInputStream {
    private static final byte[]  EMPTY_BYTEARRAY  = new byte[0];
    private static final short[] EMPTY_SHORTARRAY = new short[0];
    private static final int[]   EMPTY_INTARRAY   = new int[0];

    public DiffFileInputStream(InputStream in) {
        super(in);
    }

    public byte[] readByteArray() throws IOException {
        int length = readUleb128p1();

        if (length < 0) {
            throw new IllegalArgumentException("Length is less than zero.");
        }
        if (length == 0) {
            return EMPTY_BYTEARRAY;
        }

        byte[] result = new byte[length];
        read(result);
        return result;
    }

    public short[] readShortArray() throws IOException {
        int length = readUleb128p1();

        if (length < 0) {
            throw new IllegalArgumentException("Length is less than zero.");
        }
        if (length == 0) {
            return EMPTY_SHORTARRAY;
        }
        short[] result = new short[length];
        for (int i = 0; i < length; i++) {
            result[i] = readShort();
        }
        return result;
    }

    public int[] readIntArray() throws IOException {
        int length = readUleb128p1();

        if (length < 0) {
            throw new IllegalArgumentException("Length is less than zero.");
        }
        if (length == 0) {
            return EMPTY_INTARRAY;
        }
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = readUleb128();
        }
        return result;
    }

    public DiffFileHeader readDiffFileHeader() throws IOException {
        return new DiffFileHeader(this);
    }

    @SuppressWarnings("unchecked")
    public DiffFileChunk<?> readChunk(byte type) throws IOException {
        PatchOpRecordList<?> patchOpList = (PatchOpRecordList<?>) readValue(ChunkValueType.TYPE_PATCHOP_LIST);
        return new DiffFileChunk(type, patchOpList);
    }

    private Object readValue(int valType) throws IOException {
        Object newValue = null;
        switch (valType) {
            case ChunkValueType.TYPE_BYTE: {
                newValue = readByte();
                break;
            }
            case ChunkValueType.TYPE_SHORT: {
                newValue = readShort();
                break;
            }
            case ChunkValueType.TYPE_INT: {
                newValue = readUleb128();
                break;
            }
            case ChunkValueType.TYPE_STRINGDATA: {
                newValue = readStringData();
                break;
            }
            case ChunkValueType.TYPE_TYPEID: {
                newValue = readTypeId();
                break;
            }
            case ChunkValueType.TYPE_TYPELIST: {
                newValue = readTypeList();
                break;
            }
            case ChunkValueType.TYPE_PROTOID: {
                newValue = readProtoId();
                break;
            }
            case ChunkValueType.TYPE_FIELDID: {
                newValue = readFieldId();
                break;
            }
            case ChunkValueType.TYPE_METHODID: {
                newValue = readMethodId();
                break;
            }
            case ChunkValueType.TYPE_ANNOTATION: {
                newValue = readAnnotation();
                break;
            }
            case ChunkValueType.TYPE_ANNOTATION_SET: {
                newValue = readAnnotationSet();
                break;
            }
            case ChunkValueType.TYPE_ANNOTATION_SET_REFLIST: {
                newValue = readAnnotationSetRefList();
                break;
            }
            case ChunkValueType.TYPE_ANNOTATION_DIRECTORY: {
                newValue = readAnnotationDirectory();
                break;
            }
            case ChunkValueType.TYPE_ENCODEDVALUE: {
                newValue = readEncodedArray();
                break;
            }
            case ChunkValueType.TYPE_CLASSDEF: {
                newValue = readClassDef();
                break;
            }
            case ChunkValueType.TYPE_CLASSDATA: {
                newValue = readClassData();
                break;
            }
            case ChunkValueType.TYPE_CODE: {
                newValue = readCode();
                break;
            }
            case ChunkValueType.TYPE_TRY: {
                newValue = readTry();
                break;
            }
            case ChunkValueType.TYPE_CATCH_HANDLER: {
                newValue = readCatchHandler();
                break;
            }
            case ChunkValueType.TYPE_DEBUGINFO_ITEM: {
                newValue = readDebugInfoItem();
                break;
            }
            case ChunkValueType.TYPE_PATCHOP_LIST: {
                PatchOpRecordList<Object> patchOpList = new PatchOpRecordList<>();
                int lastOldIndex = 0;
                int delOpCount = readUleb128p1();
                for (int i = 0; i < delOpCount; ++i) {
                    PatchOpRecord<Object> patchOp = readPatchOpRecord(lastOldIndex, PatchOpRecord.OP_DEL);
                    lastOldIndex = patchOp.oldIndex;
                    patchOpList.add(patchOp);
                }
                lastOldIndex = 0;
                int replaceOpCount = readUleb128p1();
                for (int i = 0; i < replaceOpCount; ++i) {
                    PatchOpRecord<Object> patchOp = readPatchOpRecord(lastOldIndex, PatchOpRecord.OP_REPLACE);
                    lastOldIndex = patchOp.oldIndex;
                    patchOpList.add(patchOp);
                }
                lastOldIndex = 0;
                int moveOpCount = readUleb128p1();
                for (int i = 0; i < moveOpCount; ++i) {
                    PatchOpRecord<Object> patchOp = readPatchOpRecord(lastOldIndex, PatchOpRecord.OP_MOVE);
                    lastOldIndex = patchOp.oldIndex;
                    patchOpList.add(patchOp);
                }
                lastOldIndex = 0;
                int addOpCount = readUleb128p1();
                for (int i = 0; i < addOpCount; ++i) {
                    PatchOpRecord<Object> patchOp = readPatchOpRecord(lastOldIndex, PatchOpRecord.OP_ADD);
                    lastOldIndex = patchOp.oldIndex;
                    patchOpList.add(patchOp);
                }
                newValue = patchOpList;
                break;
            }
            default: {
                throw new IOException("Unknown value type: " + valType);
            }
        }
        return newValue;
    }

    private PatchOpRecord<Object> readPatchOpRecord(int lastOldIndex, byte op) throws IOException {
        int oldIndex = lastOldIndex + readSleb128();
        int newIndex = oldIndex;
        Comparable<?> newValue = null;
        if (op != PatchOpRecord.OP_DEL) {
            newIndex = oldIndex + readSleb128();
            if (newIndex == -1) {
                throw new IOException("Illegal new Index (" + newIndex + ").");
            } else {
                if (op != PatchOpRecord.OP_MOVE) {
                    byte valType = readByte();
                    newValue = (Comparable<?>) readValue(valType);
                }
            }
        }
        switch (op) {
            case PatchOpRecord.OP_ADD: {
                return PatchOpRecord.<Object>createAddOpRecord(newIndex, newValue);
            }
            case PatchOpRecord.OP_DEL: {
                return PatchOpRecord.createDelOpRecord(oldIndex);
            }
            case PatchOpRecord.OP_REPLACE: {
                return PatchOpRecord.<Object>createReplaceOpRecord(oldIndex, newValue);
            }
            case PatchOpRecord.OP_MOVE: {
                return PatchOpRecord.createMoveOpRecord(oldIndex, newIndex);
            }
            default: {
                throw new IllegalStateException();
            }
        }
    }

    public TypeId readTypeId() throws IOException {
        int descriptorIndex = readInt();
        return new TypeId(null, TableOfContents.Section.INT_VALUE_UNSET, descriptorIndex);
    }

    public TypeList readTypeList() throws IOException {
        short[] types = readShortArray();
        return new TypeList(null, TableOfContents.Section.INT_VALUE_UNSET, types);
    }

    public StringData readStringData() throws IOException {
        String value = readUTF();
        return new StringData(null, TableOfContents.Section.INT_VALUE_UNSET, value);
    }

    public FieldId readFieldId() throws IOException {
        int declaringClassIndex = readUnsignedShort();
        int typeIndex = readUnsignedShort();
        int nameIndex = readInt();
        return new FieldId(null, TableOfContents.Section.INT_VALUE_UNSET, declaringClassIndex, typeIndex, nameIndex);
    }

    public MethodId readMethodId() throws IOException {
        int declaringClassIndex = readUnsignedShort();
        int protoIndex = readUnsignedShort();
        int nameIndex = readInt();
        return new MethodId(null, TableOfContents.Section.INT_VALUE_UNSET, declaringClassIndex, protoIndex, nameIndex);
    }

    public ProtoId readProtoId() throws IOException {
        int shortyIndex = readInt();
        int returnTypeIndex = readInt();
        int parametersOffset = readInt();
        return new ProtoId(null, TableOfContents.Section.INT_VALUE_UNSET, shortyIndex, returnTypeIndex, parametersOffset);
    }

    public ClassDef readClassDef() throws IOException {
        int typeIndex = readInt();
        int accessFlags = readInt();
        int supertypeIndex = readInt();
        int interfacesOffset = readInt();
        int sourceFileIndex = readInt();
        int annotationsOffset = readInt();
        int classDataOffset = readInt();
        int staticValuesOffset = readInt();
        return new ClassDef(null, TableOfContents.Section.INT_VALUE_UNSET, typeIndex, accessFlags, supertypeIndex, interfacesOffset, sourceFileIndex, annotationsOffset, classDataOffset, staticValuesOffset);
    }

    public Code readCode() throws IOException {
        int registersSize = readUnsignedShort();
        int insSize = readUnsignedShort();
        int outsSize = readUnsignedShort();
        int triesSize = readUnsignedShort();
        int debugInfoOffset = readInt();
        short[] instructions = readShortArray();
        Try[] tries = readTries(triesSize);
        CatchHandler[] catchHandlers = readCatchHandlers();
        return new Code(null, TableOfContents.Section.INT_VALUE_UNSET, registersSize, insSize, outsSize, debugInfoOffset, instructions, tries, catchHandlers);
    }

    private Try[] readTries(int triesSize) throws IOException {
        Try[] result = new Try[triesSize];
        for (int i = 0; i < triesSize; ++i) {
            result[i] = readTry();
        }
        return result;
    }

    private Try readTry() throws IOException {
        int startAddress = readInt();
        int instructionCount = readUleb128();
        int catchHandlerIdx = readUleb128();
        return new Try(startAddress, instructionCount, catchHandlerIdx);
    }

    private CatchHandler[] readCatchHandlers() throws IOException {
        int count = readUleb128();
        CatchHandler[] result = new CatchHandler[count];

        for (int i = 0; i < count; ++i) {
            result[i] = readCatchHandler();
        }
        return result;
    }

    private CatchHandler readCatchHandler() throws IOException {
        int typeAddrPairCount = readSleb128();
        int realTypeAddrPairCount = (typeAddrPairCount < 0 ? -typeAddrPairCount : typeAddrPairCount);

        int[] typeIndexes = new int[realTypeAddrPairCount];
        int[] addresses = new int[realTypeAddrPairCount];
        for (int i = 0; i < realTypeAddrPairCount; ++i) {
            typeIndexes[i] = readUleb128();
            addresses[i] = readUleb128();
        }

        int catchAllAddress = (typeAddrPairCount <= 0 ? readUleb128() : -1);
        int offset = readUleb128();

        return new CatchHandler(typeIndexes, addresses, catchAllAddress, offset);
    }

    public DebugInfoItem readDebugInfoItem() throws IOException {
        int lineStart = readUleb128();
        int[] parameterNames = readIntArray();
        byte[] infoSTM = readByteArray();
        return new DebugInfoItem(null, TableOfContents.Section.INT_VALUE_UNSET, lineStart, parameterNames, infoSTM);
    }

    public ClassData readClassData() throws IOException {
        Field[] staticFields = readFields();
        Field[] instanceFields = readFields();
        Method[] directMethods = readMethods();
        Method[] virtualMethods = readMethods();
        return new ClassData(null, TableOfContents.Section.INT_VALUE_UNSET, staticFields, instanceFields, directMethods, virtualMethods);
    }

    private Field[] readFields() throws IOException {
        int count = readUleb128();
        Field[] result = new Field[count];
        for (int i = 0; i < count; i++) {
            int fieldIndex = readUleb128();
            int accessFlags = readUleb128();
            result[i] = new Field(fieldIndex, accessFlags);
        }
        return result;
    }

    private Method[] readMethods() throws IOException {
        int count = readUleb128();
        Method[] result = new Method[count];
        for (int i = 0; i < count; i++) {
            int methodIndex = readUleb128();
            int accessFlags = readUleb128();
            int codeOffset = readUleb128();
            result[i] = new Method(methodIndex, accessFlags, codeOffset);
        }
        return result;
    }

    public Annotation readAnnotation() throws IOException {
        byte visibility = readByte();
        EncodedValue encodedAnnotation = readEncodedArray();
        return new Annotation(null, TableOfContents.Section.INT_VALUE_UNSET, visibility, encodedAnnotation);
    }

    public AnnotationSet readAnnotationSet() throws IOException {
        int[] annotationOffsets = readIntArray();
        return new AnnotationSet(null, TableOfContents.Section.INT_VALUE_UNSET, annotationOffsets);
    }

    public AnnotationSetRefList readAnnotationSetRefList() throws IOException {
        int[] annotationSetRefItems = readIntArray();
        return new AnnotationSetRefList(null, TableOfContents.Section.INT_VALUE_UNSET, annotationSetRefItems);
    }

    public AnnotationDirectory readAnnotationDirectory() throws IOException {
        int classAnnotationsOffset = readUleb128();

        int fieldAnnotationCount = readUleb128();
        int[][] fieldAnnotations = new int[fieldAnnotationCount][2];
        for (int i = 0; i < fieldAnnotationCount; ++i) {
            fieldAnnotations[i][0] = readUleb128();
            fieldAnnotations[i][1] = readUleb128();
        }

        int methodAnnotationCount = readUleb128();
        int[][] methodAnnotations = new int[methodAnnotationCount][2];
        for (int i = 0; i < methodAnnotationCount; ++i) {
            methodAnnotations[i][0] = readUleb128();
            methodAnnotations[i][1] = readUleb128();
        }

        int parameterAnnotationCount = readUleb128();
        int[][] parameterAnnotations = new int[parameterAnnotationCount][2];
        for (int i = 0; i < parameterAnnotationCount; ++i) {
            parameterAnnotations[i][0] = readUleb128();
            parameterAnnotations[i][1] = readUleb128();
        }

        return new AnnotationDirectory(null, TableOfContents.Section.INT_VALUE_UNSET, classAnnotationsOffset, fieldAnnotations, methodAnnotations, parameterAnnotations);
    }

    public EncodedValue readEncodedArray() throws IOException {
        byte[] data = readByteArray();
        return new EncodedValue(null, TableOfContents.Section.INT_VALUE_UNSET, data);
    }
}
