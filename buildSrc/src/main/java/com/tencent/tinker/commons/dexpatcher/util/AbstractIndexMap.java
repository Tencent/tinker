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

package com.tencent.tinker.commons.dexpatcher.util;

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Created by tangyinsheng on 2016/6/29.
 *
 * *** This file is renamed from IndexMap in dx project. ***
 */

public abstract class AbstractIndexMap {

    public abstract int adjustStringIndex(int stringIndex);

    public abstract int adjustTypeIdIndex(int typeIdIndex);

    public abstract int adjustProtoIdIndex(int protoIndex);

    public abstract int adjustFieldIdIndex(int fieldIndex);

    public abstract int adjustMethodIdIndex(int methodIndex);

    public abstract int adjustTypeListOffset(int typeListOffset);

    public abstract int adjustAnnotationOffset(int annotationOffset);

    public abstract int adjustAnnotationSetOffset(int annotationSetOffset);

    public abstract int adjustAnnotationSetRefListOffset(int annotationSetRefListOffset);

    public abstract int adjustAnnotationsDirectoryOffset(int annotationsDirectoryOffset);

    public abstract int adjustStaticValuesOffset(int staticValuesOffset);

    public abstract int adjustClassDataOffset(int classDataOffset);

    public abstract int adjustDebugInfoItemOffset(int debugInfoItemOffset);

    public abstract int adjustCodeOffset(int codeOffset);

    public TypeList adjust(TypeList typeList) {
        if (typeList == TypeList.EMPTY) {
            return typeList;
        }
        short[] types = new short[typeList.types.length];
        for (int i = 0; i < types.length; ++i) {
            types[i] = (short) adjustTypeIdIndex(typeList.types[i]);
        }
        return new TypeList(typeList.off, types);
    }

    public MethodId adjust(MethodId methodId) {
        int adjustedDeclaringClassIndex = adjustTypeIdIndex(methodId.declaringClassIndex);
        int adjustedProtoIndex = adjustProtoIdIndex(methodId.protoIndex);
        int adjustedNameIndex = adjustStringIndex(methodId.nameIndex);
        return new MethodId(
                methodId.off, adjustedDeclaringClassIndex, adjustedProtoIndex, adjustedNameIndex
        );
    }

    public FieldId adjust(FieldId fieldId) {
        int adjustedDeclaringClassIndex = adjustTypeIdIndex(fieldId.declaringClassIndex);
        int adjustedTypeIndex = adjustTypeIdIndex(fieldId.typeIndex);
        int adjustedNameIndex = adjustStringIndex(fieldId.nameIndex);
        return new FieldId(
                fieldId.off, adjustedDeclaringClassIndex, adjustedTypeIndex, adjustedNameIndex
        );
    }

    public ProtoId adjust(ProtoId protoId) {
        int adjustedShortyIndex = adjustStringIndex(protoId.shortyIndex);
        int adjustedReturnTypeIndex = adjustTypeIdIndex(protoId.returnTypeIndex);
        int adjustedParametersOffset = adjustTypeListOffset(protoId.parametersOffset);
        return new ProtoId(
                protoId.off, adjustedShortyIndex, adjustedReturnTypeIndex, adjustedParametersOffset
        );
    }

    public ClassDef adjust(ClassDef classDef) {
        int adjustedTypeIndex = adjustTypeIdIndex(classDef.typeIndex);
        int adjustedSupertypeIndex = adjustTypeIdIndex(classDef.supertypeIndex);
        int adjustedInterfacesOffset = adjustTypeListOffset(classDef.interfacesOffset);
        int adjustedSourceFileIndex = adjustStringIndex(classDef.sourceFileIndex);
        int adjustedAnnotationsOffset = adjustAnnotationsDirectoryOffset(classDef.annotationsOffset);
        int adjustedClassDataOffset = adjustClassDataOffset(classDef.classDataOffset);
        int adjustedStaticValuesOffset = adjustStaticValuesOffset(classDef.staticValuesOffset);
        return new ClassDef(
                classDef.off, adjustedTypeIndex, classDef.accessFlags, adjustedSupertypeIndex,
                adjustedInterfacesOffset, adjustedSourceFileIndex, adjustedAnnotationsOffset,
                adjustedClassDataOffset, adjustedStaticValuesOffset
        );
    }

    public ClassData adjust(ClassData classData) {
        ClassData.Field[] adjustedStaticFields = adjustFields(classData.staticFields);
        ClassData.Field[] adjustedInstanceFields = adjustFields(classData.instanceFields);
        ClassData.Method[] adjustedDirectMethods = adjustMethods(classData.directMethods);
        ClassData.Method[] adjustedVirtualMethods = adjustMethods(classData.virtualMethods);
        return new ClassData(
                classData.off, adjustedStaticFields, adjustedInstanceFields,
                adjustedDirectMethods, adjustedVirtualMethods
        );
    }

    public Code adjust(Code code) {
        int adjustedDebugInfoOffset = adjustDebugInfoItemOffset(code.debugInfoOffset);
        short[] adjustedInstructions = adjustInstructions(code.instructions);
        Code.CatchHandler[] adjustedCatchHandlers = adjustCatchHandlers(code.catchHandlers);
        return new Code(
                code.off, code.registersSize, code.insSize, code.outsSize,
                adjustedDebugInfoOffset, adjustedInstructions, code.tries, adjustedCatchHandlers
        );
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
        Code.CatchHandler[] adjustedCatchHandlers = new Code.CatchHandler[catchHandlers.length];
        for (int i = 0; i < catchHandlers.length; ++i) {
            Code.CatchHandler catchHandler = catchHandlers[i];
            int typeIndexesCount = catchHandler.typeIndexes.length;
            int[] adjustedTypeIndexes = new int[typeIndexesCount];
            for (int j = 0; j < typeIndexesCount; ++j) {
                adjustedTypeIndexes[j] = adjustTypeIdIndex(catchHandler.typeIndexes[j]);
            }
            adjustedCatchHandlers[i] = new Code.CatchHandler(
                    adjustedTypeIndexes, catchHandler.addresses,
                    catchHandler.catchAllAddress, catchHandler.offset
            );
        }
        return adjustedCatchHandlers;
    }

    private ClassData.Field[] adjustFields(ClassData.Field[] fields) {
        ClassData.Field[] adjustedFields = new ClassData.Field[fields.length];
        for (int i = 0; i < fields.length; ++i) {
            ClassData.Field field = fields[i];
            int adjustedFieldIndex = adjustFieldIdIndex(field.fieldIndex);
            adjustedFields[i] = new ClassData.Field(adjustedFieldIndex, field.accessFlags);
        }
        return adjustedFields;
    }

    private ClassData.Method[] adjustMethods(ClassData.Method[] methods) {
        ClassData.Method[] adjustedMethods = new ClassData.Method[methods.length];
        for (int i = 0; i < methods.length; ++i) {
            ClassData.Method method = methods[i];
            int adjustedMethodIndex = adjustMethodIdIndex(method.methodIndex);
            int adjustedCodeOffset = adjustCodeOffset(method.codeOffset);
            adjustedMethods[i] = new ClassData.Method(
                    adjustedMethodIndex, method.accessFlags, adjustedCodeOffset
            );
        }
        return adjustedMethods;
    }

    public DebugInfoItem adjust(DebugInfoItem debugInfoItem) {
        int[] parameterNames = adjustParameterNames(debugInfoItem.parameterNames);
        byte[] infoSTM = adjustDebugInfoItemSTM(debugInfoItem.infoSTM);
        return new DebugInfoItem(
                debugInfoItem.off, debugInfoItem.lineStart, parameterNames, infoSTM
        );
    }

    private int[] adjustParameterNames(int[] parameterNames) {
        int size = parameterNames.length;
        int[] adjustedParameterNames = new int[size];
        for (int i = 0; i < size; ++i) {
            adjustedParameterNames[i] = adjustStringIndex(parameterNames[i]);
        }
        return adjustedParameterNames;
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
        return new EncodedValue(encodedArray.off, baos.toByteArray());
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
        return new Annotation(
                annotation.off,
                annotation.visibility,
                new EncodedValue(annotation.encodedAnnotation.off, baos.toByteArray())
        );
    }

    public AnnotationSet adjust(AnnotationSet annotationSet) {
        int size = annotationSet.annotationOffsets.length;
        int[] adjustedAnnotationOffsets = new int[size];
        for (int i = 0; i < size; ++i) {
            adjustedAnnotationOffsets[i]
                    = adjustAnnotationOffset(annotationSet.annotationOffsets[i]);
        }
        return new AnnotationSet(annotationSet.off, adjustedAnnotationOffsets);
    }

    public AnnotationSetRefList adjust(AnnotationSetRefList annotationSetRefList) {
        int size = annotationSetRefList.annotationSetRefItems.length;
        int[] adjustedAnnotationSetRefItems = new int[size];
        for (int i = 0; i < size; ++i) {
            adjustedAnnotationSetRefItems[i]
                    = adjustAnnotationSetOffset(annotationSetRefList.annotationSetRefItems[i]);
        }
        return new AnnotationSetRefList(annotationSetRefList.off, adjustedAnnotationSetRefItems);
    }

    public AnnotationsDirectory adjust(AnnotationsDirectory annotationsDirectory) {
        int adjustedClassAnnotationsOffset
                = adjustAnnotationSetOffset(annotationsDirectory.classAnnotationsOffset);

        int[][] adjustedFieldAnnotations
                = new int[annotationsDirectory.fieldAnnotations.length][2];
        for (int i = 0; i < adjustedFieldAnnotations.length; ++i) {
            adjustedFieldAnnotations[i][0]
                    = adjustFieldIdIndex(annotationsDirectory.fieldAnnotations[i][0]);
            adjustedFieldAnnotations[i][1]
                    = adjustAnnotationSetOffset(annotationsDirectory.fieldAnnotations[i][1]);
        }

        int[][] adjustedMethodAnnotations
                = new int[annotationsDirectory.methodAnnotations.length][2];
        for (int i = 0; i < adjustedMethodAnnotations.length; ++i) {
            adjustedMethodAnnotations[i][0]
                    = adjustMethodIdIndex(annotationsDirectory.methodAnnotations[i][0]);
            adjustedMethodAnnotations[i][1]
                    = adjustAnnotationSetOffset(annotationsDirectory.methodAnnotations[i][1]);
        }

        int[][] adjustedParameterAnnotations
                = new int[annotationsDirectory.parameterAnnotations.length][2];
        for (int i = 0; i < adjustedParameterAnnotations.length; ++i) {
            adjustedParameterAnnotations[i][0]
                    = adjustMethodIdIndex(annotationsDirectory.parameterAnnotations[i][0]);
            adjustedParameterAnnotations[i][1]
                    = adjustAnnotationSetRefListOffset(
                    annotationsDirectory.parameterAnnotations[i][1]
            );
        }

        return new AnnotationsDirectory(
                annotationsDirectory.off, adjustedClassAnnotationsOffset,
                adjustedFieldAnnotations, adjustedMethodAnnotations, adjustedParameterAnnotations
        );
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
