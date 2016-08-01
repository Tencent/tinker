/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.tencent.tinker.android.dx.util;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.AnnotationsDirectory;
import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.EncodedValueCodec;
import com.tencent.tinker.android.dex.EncodedValueReader;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.Leb128;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.android.dex.util.ByteInput;
import com.tencent.tinker.android.dex.util.ByteOutput;
import com.tencent.tinker.android.utils.SparseIntArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Created by tomystang on 2016/6/29.
 */
public class IndexMap {
    private final SparseIntArray stringIdsMap = new SparseIntArray();
    private final SparseIntArray typeIdsMap = new SparseIntArray();
    private final SparseIntArray protoIdsMap = new SparseIntArray();
    private final SparseIntArray fieldIdsMap = new SparseIntArray();
    private final SparseIntArray methodIdsMap = new SparseIntArray();
    private final SparseIntArray typeListOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationSetOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationSetRefListOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationDirectoryOffsetsMap = new SparseIntArray();
    private final SparseIntArray staticValuesOffsetsMap = new SparseIntArray();
    private final SparseIntArray classDataOffsetsMap = new SparseIntArray();
    private final SparseIntArray debugInfoItemOffsetsMap = new SparseIntArray();
    private final SparseIntArray codeOffsetsMap = new SparseIntArray();

    public void mapStringIds(int oldIndex, int newIndex) {
        stringIdsMap.put(oldIndex, newIndex);
    }

    public void mapTypeIds(int oldIndex, int newIndex) {
        typeIdsMap.put(oldIndex, newIndex);
    }

    public void mapProtoIds(int oldIndex, int newIndex) {
        protoIdsMap.put(oldIndex, newIndex);
    }

    public void mapFieldIds(int oldIndex, int newIndex) {
        fieldIdsMap.put(oldIndex, newIndex);
    }

    public void mapMethodIds(int oldIndex, int newIndex) {
        methodIdsMap.put(oldIndex, newIndex);
    }

    public void mapTypeListOffset(int oldOffset, int newOffset) {
        typeListOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapAnnotationOffset(int oldOffset, int newOffset) {
        annotationOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapAnnotationSetOffset(int oldOffset, int newOffset) {
        annotationSetOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapAnnotationSetRefListOffset(int oldOffset, int newOffset) {
        annotationSetRefListOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapAnnotationsDirectoryOffset(int oldOffset, int newOffset) {
        annotationDirectoryOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapStaticValuesOffset(int oldOffset, int newOffset) {
        staticValuesOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapClassDataOffset(int oldOffset, int newOffset) {
        classDataOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapDebugInfoItemOffset(int oldOffset, int newOffset) {
        debugInfoItemOffsetsMap.put(oldOffset, newOffset);
    }

    public void mapCodeOffset(int oldOffset, int newOffset) {
        codeOffsetsMap.put(oldOffset, newOffset);
    }

    public int adjustStringIndex(int stringIndex) {
        int index = stringIdsMap.indexOfKey(stringIndex);
        if (index < 0) {
            return stringIndex;
        } else {
            return stringIdsMap.valueAt(index);
        }
    }

    public int adjustTypeIdIndex(int typeIdIndex) {
        int index = typeIdsMap.indexOfKey(typeIdIndex);
        if (index < 0) {
            return typeIdIndex;
        } else {
            return typeIdsMap.valueAt(index);
        }
    }

    public int adjustProtoIdIndex(int protoIndex) {
        int index = protoIdsMap.indexOfKey(protoIndex);
        if (index < 0) {
            return protoIndex;
        } else {
            return protoIdsMap.valueAt(index);
        }
    }

    public int adjustFieldIdIndex(int fieldIndex) {
        int index = fieldIdsMap.indexOfKey(fieldIndex);
        if (index < 0) {
            return fieldIndex;
        } else {
            return fieldIdsMap.valueAt(index);
        }
    }

    public int adjustMethodIdIndex(int methodIndex) {
        int index = methodIdsMap.indexOfKey(methodIndex);
        if (index < 0) {
            return methodIndex;
        } else {
            return methodIdsMap.valueAt(index);
        }
    }

    public int adjustTypeListOffset(int typeListOffset) {
        int index = typeListOffsetsMap.indexOfKey(typeListOffset);
        if (index < 0) {
            return typeListOffset;
        } else {
            return typeListOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationOffset(int annotationOffset) {
        int index = annotationOffsetsMap.indexOfKey(annotationOffset);
        if (index < 0) {
            return annotationOffset;
        } else {
            return annotationOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationSetOffset(int annotationSetOffset) {
        int index = annotationSetOffsetsMap.indexOfKey(annotationSetOffset);
        if (index < 0) {
            return annotationSetOffset;
        } else {
            return annotationSetOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationSetRefListOffset(int annotationSetRefListOffset) {
        int index = annotationSetRefListOffsetsMap.indexOfKey(annotationSetRefListOffset);
        if (index < 0) {
            return annotationSetRefListOffset;
        } else {
            return annotationSetRefListOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationDirectoryOffset(int annotationDirectoryOffset) {
        int index = annotationDirectoryOffsetsMap.indexOfKey(annotationDirectoryOffset);
        if (index < 0) {
            return annotationDirectoryOffset;
        } else {
            return annotationDirectoryOffsetsMap.valueAt(index);
        }
    }

    public int adjustStaticValuesOffset(int staticValuesOffset) {
        int index = staticValuesOffsetsMap.indexOfKey(staticValuesOffset);
        if (index < 0) {
            return staticValuesOffset;
        } else {
            return staticValuesOffsetsMap.valueAt(index);
        }
    }

    public int adjustClassDataOffset(int classDataOffset) {
        int index = classDataOffsetsMap.indexOfKey(classDataOffset);
        if (index < 0) {
            return classDataOffset;
        } else {
            return classDataOffsetsMap.valueAt(index);
        }
    }

    public int adjustDebugInfoItemOffset(int debugInfoItemOffset) {
        int index = debugInfoItemOffsetsMap.indexOfKey(debugInfoItemOffset);
        if (index < 0) {
            return debugInfoItemOffset;
        } else {
            return debugInfoItemOffsetsMap.valueAt(index);
        }
    }

    public int adjustCodeOffset(int codeOffset) {
        int index = codeOffsetsMap.indexOfKey(codeOffset);
        if (index < 0) {
            return codeOffset;
        } else {
            return codeOffsetsMap.valueAt(index);
        }
    }

    public TypeList adjust(TypeList typeList) {
        if (typeList == TypeList.EMPTY) {
            return typeList;
        }
        short[] types = typeList.types;
        for (int i = 0; i < types.length; ++i) {
            types[i] = (short) adjustTypeIdIndex(types[i]);
        }
        return typeList;
    }

    public MethodId adjust(MethodId methodId) {
        methodId.declaringClassIndex = adjustTypeIdIndex(methodId.declaringClassIndex);
        methodId.protoIndex = adjustProtoIdIndex(methodId.protoIndex);
        methodId.nameIndex = adjustStringIndex(methodId.nameIndex);
        return methodId;
    }

    public FieldId adjust(FieldId fieldId) {
        fieldId.declaringClassIndex = adjustTypeIdIndex(fieldId.declaringClassIndex);
        fieldId.typeIndex = adjustTypeIdIndex(fieldId.typeIndex);
        fieldId.nameIndex = adjustStringIndex(fieldId.nameIndex);
        return fieldId;
    }

    public ProtoId adjust(ProtoId protoId) {
        protoId.shortyIndex = adjustStringIndex(protoId.shortyIndex);
        protoId.returnTypeIndex = adjustTypeIdIndex(protoId.returnTypeIndex);
        protoId.parametersOffset = adjustTypeListOffset(protoId.parametersOffset);
        return protoId;
    }

    public ClassDef adjust(ClassDef classDef) {
        classDef.typeIndex = adjustTypeIdIndex(classDef.typeIndex);
        classDef.supertypeIndex = adjustTypeIdIndex(classDef.supertypeIndex);
        classDef.interfacesOffset = adjustTypeListOffset(classDef.interfacesOffset);
        classDef.sourceFileIndex = adjustStringIndex(classDef.sourceFileIndex);
        classDef.annotationsOffset = adjustAnnotationDirectoryOffset(classDef.annotationsOffset);
        classDef.classDataOffset = adjustClassDataOffset(classDef.classDataOffset);
        classDef.staticValuesOffset = adjustStaticValuesOffset(classDef.staticValuesOffset);
        return classDef;
    }

    public ClassData adjust(ClassData classData) {
        classData.staticFields = adjustFields(classData.staticFields);
        classData.instanceFields = adjustFields(classData.instanceFields);
        classData.directMethods = adjustMethods(classData.directMethods);
        classData.virtualMethods = adjustMethods(classData.virtualMethods);
        return classData;
    }

    public Code adjust(Code code) {
        code.debugInfoOffset = adjustDebugInfoItemOffset(code.debugInfoOffset);
        code.instructions = adjustInstructions(code.instructions);
        code.catchHandlers = adjustCatchHandlers(code.catchHandlers);
        return code;
    }

    private short[] adjustInstructions(short[] instructions) {
        if (instructions == null || instructions.length == 0) {
            return instructions;
        }
        InstructionTransformer insTrans = new InstructionTransformer(this);
        return insTrans.transform(instructions);
    }

    private Code.CatchHandler[] adjustCatchHandlers(Code.CatchHandler[] catchHandlers) {
        if (catchHandlers == null || catchHandlers.length == 0) {
            return catchHandlers;
        }
        for (Code.CatchHandler catchHandler : catchHandlers) {
            int typeIndexesCount = catchHandler.typeIndexes.length;
            for (int i = 0; i < typeIndexesCount; ++i) {
                catchHandler.typeIndexes[i] = adjustTypeIdIndex(catchHandler.typeIndexes[i]);
            }
        }
        return catchHandlers;
    }

    private ClassData.Field[] adjustFields(ClassData.Field[] fields) {
        for (ClassData.Field field : fields) {
            field.fieldIndex = adjustFieldIdIndex(field.fieldIndex);
        }
        return fields;
    }

    private ClassData.Method[] adjustMethods(ClassData.Method[] methods) {
        for (ClassData.Method method : methods) {
            method.methodIndex = adjustMethodIdIndex(method.methodIndex);
            method.codeOffset = adjustCodeOffset(method.codeOffset);
        }
        return methods;
    }

    public DebugInfoItem adjust(DebugInfoItem debugInfoItem) {
        debugInfoItem.parameterNames = adjustParameterNames(debugInfoItem.parameterNames);
        debugInfoItem.infoSTM = adjustDebugInfoItemSTM(debugInfoItem.infoSTM);
        return debugInfoItem;
    }

    private int[] adjustParameterNames(int[] parameterNames) {
        int size = parameterNames.length;
        for (int i = 0; i < size; ++i) {
            parameterNames[i] = adjustStringIndex(parameterNames[i]);
        }
        return parameterNames;
    }

    private byte[] adjustDebugInfoItemSTM(byte[] infoSTM) {
        ByteArrayInputStream bais = new ByteArrayInputStream(infoSTM);
        final ByteArrayInputStream baisRef = bais;
        ByteInput inAdapter = new ByteInput() {
            @Override
            public byte readByte() {
                return (byte) (baisRef.read() & 0xFF);
            }
        };

        ByteArrayOutputStream baos = new ByteArrayOutputStream(infoSTM.length + 512);
        final ByteArrayOutputStream baosRef = baos;
        ByteOutput outAdapter = new ByteOutput() {
            @Override
            public void writeByte(int i) {
                baosRef.write(i);
            }
        };

        outside_whileloop:
        while (true) {
            int opcode = bais.read() & 0xFF;
            baos.write(opcode);
            switch (opcode) {
                case DebugInfoItem.DBG_END_SEQUENCE: {
                    break outside_whileloop;
                }
                case DebugInfoItem.DBG_ADVANCE_PC: {
                    int addrDiff = Leb128.readUnsignedLeb128(inAdapter);
                    Leb128.writeUnsignedLeb128(outAdapter, addrDiff);
                    break;
                }
                case DebugInfoItem.DBG_ADVANCE_LINE: {
                    int lineDiff = Leb128.readSignedLeb128(inAdapter);
                    Leb128.writeSignedLeb128(outAdapter, lineDiff);
                    break;
                }
                case DebugInfoItem.DBG_START_LOCAL:
                case DebugInfoItem.DBG_START_LOCAL_EXTENDED: {
                    int registerNum = Leb128.readUnsignedLeb128(inAdapter);
                    Leb128.writeUnsignedLeb128(outAdapter, registerNum);

                    int nameIndex = adjustStringIndex(Leb128.readUnsignedLeb128p1(inAdapter));
                    Leb128.writeUnsignedLeb128p1(outAdapter, nameIndex);

                    int typeIndex = adjustTypeIdIndex(Leb128.readUnsignedLeb128p1(inAdapter));
                    Leb128.writeUnsignedLeb128p1(outAdapter, typeIndex);

                    if (opcode == DebugInfoItem.DBG_START_LOCAL_EXTENDED) {
                        int sigIndex = adjustStringIndex(Leb128.readUnsignedLeb128p1(inAdapter));
                        Leb128.writeUnsignedLeb128p1(outAdapter, sigIndex);
                    }
                    break;
                }
                case DebugInfoItem.DBG_END_LOCAL:
                case DebugInfoItem.DBG_RESTART_LOCAL: {
                    int registerNum = Leb128.readUnsignedLeb128(inAdapter);
                    Leb128.writeUnsignedLeb128(outAdapter, registerNum);
                    break;
                }
                case DebugInfoItem.DBG_SET_FILE: {
                    int nameIndex = adjustStringIndex(Leb128.readUnsignedLeb128p1(inAdapter));
                    Leb128.writeUnsignedLeb128p1(outAdapter, nameIndex);
                    break;
                }
                case DebugInfoItem.DBG_SET_PROLOGUE_END:
                case DebugInfoItem.DBG_SET_EPILOGUE_BEGIN:
                default: {
                    break;
                }
            }
        }

        return baos.toByteArray();
    }

    public EncodedValue adjust(EncodedValue encodedArray) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(encodedArray.data.length);
        new EncodedValueTransformer(
                new ByteOutput() {
                    @Override
                    public void writeByte(int i) {
                        baos.write(i);
                    }
                }
        ).transformArray(
                new EncodedValueReader(encodedArray, EncodedValueReader.ENCODED_ARRAY)
        );
        encodedArray.data = baos.toByteArray();
        return encodedArray;
    }

    public Annotation adjust(Annotation annotation) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(annotation.encodedAnnotation.data.length);
        new EncodedValueTransformer(
                new ByteOutput() {
                    @Override
                    public void writeByte(int i) {
                        baos.write(i);
                    }
                }
        ).transformAnnotation(annotation.getReader());
        annotation.encodedAnnotation.data = baos.toByteArray();
        return annotation;
    }

    public AnnotationSet adjust(AnnotationSet annotationSet) {
        int[] annotationOffsets = annotationSet.annotationOffsets;
        int size = annotationOffsets.length;
        for (int i = 0; i < size; ++i) {
            annotationOffsets[i] = adjustAnnotationOffset(annotationOffsets[i]);
        }
        return annotationSet;
    }

    public AnnotationSetRefList adjust(AnnotationSetRefList annotationSetRefList) {
        int[] annotationSetRefItems = annotationSetRefList.annotationSetRefItems;
        int size = annotationSetRefItems.length;
        for (int i = 0; i < size; ++i) {
            annotationSetRefItems[i] = adjustAnnotationSetOffset(annotationSetRefItems[i]);
        }
        return annotationSetRefList;
    }

    public AnnotationsDirectory adjust(AnnotationsDirectory annotationsDirectory) {
        annotationsDirectory.classAnnotationsOffset = adjustAnnotationSetOffset(annotationsDirectory.classAnnotationsOffset);

        int[][] fieldAnnotations = annotationsDirectory.fieldAnnotations;
        for (int[] fieldAnnotation : fieldAnnotations) {
            fieldAnnotation[0] = adjustFieldIdIndex(fieldAnnotation[0]);
            fieldAnnotation[1] = adjustAnnotationSetOffset(fieldAnnotation[1]);
        }

        int[][] methodAnnotations = annotationsDirectory.methodAnnotations;
        for (int[] methodAnnotation : methodAnnotations) {
            methodAnnotation[0] = adjustMethodIdIndex(methodAnnotation[0]);
            methodAnnotation[1] = adjustAnnotationSetOffset(methodAnnotation[1]);
        }

        int[][] parameterAnnotations = annotationsDirectory.parameterAnnotations;
        for (int[] parameterAnnotation : parameterAnnotations) {
            parameterAnnotation[0] = adjustMethodIdIndex(parameterAnnotation[0]);
            parameterAnnotation[1] = adjustAnnotationSetRefListOffset(parameterAnnotation[1]);
        }

        return annotationsDirectory;
    }

    /**
     * Adjust an encoded value or array.
     */
    private final class EncodedValueTransformer {
        private final ByteOutput out;

        EncodedValueTransformer(ByteOutput out) {
            this.out = out;
        }

        public void transform(EncodedValueReader reader) {
            switch (reader.peek()) {
                case EncodedValueReader.ENCODED_BYTE:
                    EncodedValueCodec.writeSignedIntegralValue(out, EncodedValueReader.ENCODED_BYTE, reader.readByte());
                    break;
                case EncodedValueReader.ENCODED_SHORT:
                    EncodedValueCodec.writeSignedIntegralValue(out, EncodedValueReader.ENCODED_SHORT, reader.readShort());
                    break;
                case EncodedValueReader.ENCODED_INT:
                    EncodedValueCodec.writeSignedIntegralValue(out, EncodedValueReader.ENCODED_INT, reader.readInt());
                    break;
                case EncodedValueReader.ENCODED_LONG:
                    EncodedValueCodec.writeSignedIntegralValue(out, EncodedValueReader.ENCODED_LONG, reader.readLong());
                    break;
                case EncodedValueReader.ENCODED_CHAR:
                    EncodedValueCodec.writeUnsignedIntegralValue(out, EncodedValueReader.ENCODED_CHAR, reader.readChar());
                    break;
                case EncodedValueReader.ENCODED_FLOAT:
                    // Shift value left 32 so that right-zero-extension works.
                    long longBits = ((long) Float.floatToIntBits(reader.readFloat())) << 32;
                    EncodedValueCodec.writeRightZeroExtendedValue(out, EncodedValueReader.ENCODED_FLOAT, longBits);
                    break;
                case EncodedValueReader.ENCODED_DOUBLE:
                    EncodedValueCodec.writeRightZeroExtendedValue(
                            out, EncodedValueReader.ENCODED_DOUBLE, Double.doubleToLongBits(reader.readDouble()));
                    break;
                case EncodedValueReader.ENCODED_STRING:
                    EncodedValueCodec.writeUnsignedIntegralValue(
                            out, EncodedValueReader.ENCODED_STRING, adjustStringIndex(reader.readString()));
                    break;
                case EncodedValueReader.ENCODED_TYPE:
                    EncodedValueCodec.writeUnsignedIntegralValue(
                            out, EncodedValueReader.ENCODED_TYPE, adjustTypeIdIndex(reader.readType()));
                    break;
                case EncodedValueReader.ENCODED_FIELD:
                    EncodedValueCodec.writeUnsignedIntegralValue(
                            out, EncodedValueReader.ENCODED_FIELD, adjustFieldIdIndex(reader.readField()));
                    break;
                case EncodedValueReader.ENCODED_ENUM:
                    EncodedValueCodec.writeUnsignedIntegralValue(
                            out, EncodedValueReader.ENCODED_ENUM, adjustFieldIdIndex(reader.readEnum()));
                    break;
                case EncodedValueReader.ENCODED_METHOD:
                    EncodedValueCodec.writeUnsignedIntegralValue(
                            out, EncodedValueReader.ENCODED_METHOD, adjustMethodIdIndex(reader.readMethod()));
                    break;
                case EncodedValueReader.ENCODED_ARRAY:
                    writeTypeAndArg(EncodedValueReader.ENCODED_ARRAY, 0);
                    transformArray(reader);
                    break;
                case EncodedValueReader.ENCODED_ANNOTATION:
                    writeTypeAndArg(EncodedValueReader.ENCODED_ANNOTATION, 0);
                    transformAnnotation(reader);
                    break;
                case EncodedValueReader.ENCODED_NULL:
                    reader.readNull();
                    writeTypeAndArg(EncodedValueReader.ENCODED_NULL, 0);
                    break;
                case EncodedValueReader.ENCODED_BOOLEAN:
                    boolean value = reader.readBoolean();
                    writeTypeAndArg(EncodedValueReader.ENCODED_BOOLEAN, value ? 1 : 0);
                    break;
                default:
                    throw new DexException("Unexpected type: " + Integer.toHexString(reader.peek()));
            }
        }

        private void transformAnnotation(EncodedValueReader reader) {
            int fieldCount = reader.readAnnotation();
            Leb128.writeUnsignedLeb128(out, adjustTypeIdIndex(reader.getAnnotationType()));
            Leb128.writeUnsignedLeb128(out, fieldCount);
            for (int i = 0; i < fieldCount; i++) {
                Leb128.writeUnsignedLeb128(out, adjustStringIndex(reader.readAnnotationName()));
                transform(reader);
            }
        }

        private void transformArray(EncodedValueReader reader) {
            int size = reader.readArray();
            Leb128.writeUnsignedLeb128(out, size);
            for (int i = 0; i < size; i++) {
                transform(reader);
            }
        }

        private void writeTypeAndArg(int type, int arg) {
            out.writeByte((arg << 5) | type);
        }
    }
}
