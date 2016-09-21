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
import java.util.BitSet;

/**
 * Created by tangyinsheng on 2016/6/29.
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
    private final SparseIntArray annotationsDirectoryOffsetsMap = new SparseIntArray();
    private final SparseIntArray staticValuesOffsetsMap = new SparseIntArray();
    private final SparseIntArray classDataOffsetsMap = new SparseIntArray();
    private final SparseIntArray debugInfoItemOffsetsMap = new SparseIntArray();
    private final SparseIntArray codeOffsetsMap = new SparseIntArray();

    private final BitSet deletedStringIds = new BitSet();
    private final BitSet deletedTypeIds = new BitSet();
    private final BitSet deletedProtoIds = new BitSet();
    private final BitSet deletedFieldIds = new BitSet();
    private final BitSet deletedMethodIds = new BitSet();
    private final BitSet deletedTypeListOffsets = new BitSet();
    private final BitSet deletedAnnotationOffsets = new BitSet();
    private final BitSet deletedAnnotationSetOffsets = new BitSet();
    private final BitSet deletedAnnotationSetRefListOffsets = new BitSet();
    private final BitSet deletedAnnotationsDirectoryOffsets = new BitSet();
    private final BitSet deletedStaticValuesOffsets = new BitSet();
    private final BitSet deletedClassDataOffsets = new BitSet();
    private final BitSet deletedDebugInfoItemOffsets = new BitSet();
    private final BitSet deletedCodeOffsets = new BitSet();

    public void mapStringIds(int oldIndex, int newIndex) {
        stringIdsMap.put(oldIndex, newIndex);
    }

    public void markStringIdDeleted(int index) {
        if (index < 0) return;
        deletedStringIds.set(index);
    }

    public void mapTypeIds(int oldIndex, int newIndex) {
        typeIdsMap.put(oldIndex, newIndex);
    }

    public void markTypeIdDeleted(int index) {
        if (index < 0) return;
        deletedTypeIds.set(index);
    }

    public void mapProtoIds(int oldIndex, int newIndex) {
        protoIdsMap.put(oldIndex, newIndex);
    }

    public void markProtoIdDeleted(int index) {
        if (index < 0) return;
        deletedProtoIds.set(index);
    }

    public void mapFieldIds(int oldIndex, int newIndex) {
        fieldIdsMap.put(oldIndex, newIndex);
    }

    public void markFieldIdDeleted(int index) {
        if (index < 0) return;
        deletedFieldIds.set(index);
    }

    public void mapMethodIds(int oldIndex, int newIndex) {
        methodIdsMap.put(oldIndex, newIndex);
    }

    public void markMethodIdDeleted(int index) {
        if (index < 0) return;
        deletedMethodIds.set(index);
    }

    public void mapTypeListOffset(int oldOffset, int newOffset) {
        typeListOffsetsMap.put(oldOffset, newOffset);
    }

    public void markTypeListDeleted(int offset) {
        if (offset < 0) return;
        deletedTypeListOffsets.set(offset);
    }

    public void mapAnnotationOffset(int oldOffset, int newOffset) {
        annotationOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationOffsets.set(offset);
    }

    public void mapAnnotationSetOffset(int oldOffset, int newOffset) {
        annotationSetOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationSetDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationSetOffsets.set(offset);
    }

    public void mapAnnotationSetRefListOffset(int oldOffset, int newOffset) {
        annotationSetRefListOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationSetRefListDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationSetRefListOffsets.set(offset);
    }

    public void mapAnnotationsDirectoryOffset(int oldOffset, int newOffset) {
        annotationsDirectoryOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationsDirectoryDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationsDirectoryOffsets.set(offset);
    }

    public void mapStaticValuesOffset(int oldOffset, int newOffset) {
        staticValuesOffsetsMap.put(oldOffset, newOffset);
    }

    public void markStaticValuesDeleted(int offset) {
        if (offset < 0) return;
        deletedStaticValuesOffsets.set(offset);
    }

    public void mapClassDataOffset(int oldOffset, int newOffset) {
        classDataOffsetsMap.put(oldOffset, newOffset);
    }

    public void markClassDataDeleted(int offset) {
        if (offset < 0) return;
        deletedClassDataOffsets.set(offset);
    }

    public void mapDebugInfoItemOffset(int oldOffset, int newOffset) {
        debugInfoItemOffsetsMap.put(oldOffset, newOffset);
    }

    public void markDebugInfoItemDeleted(int offset) {
        if (offset < 0) return;
        deletedDebugInfoItemOffsets.set(offset);
    }

    public void mapCodeOffset(int oldOffset, int newOffset) {
        codeOffsetsMap.put(oldOffset, newOffset);
    }

    public void markCodeDeleted(int offset) {
        if (offset < 0) return;
        deletedCodeOffsets.set(offset);
    }

    public int adjustStringIndex(int stringIndex) {
        int index = stringIdsMap.indexOfKey(stringIndex);
        if (index < 0) {
            return (stringIndex >= 0 && deletedStringIds.get(stringIndex) ? -1 : stringIndex);
        } else {
            return stringIdsMap.valueAt(index);
        }
    }

    public int adjustTypeIdIndex(int typeIdIndex) {
        int index = typeIdsMap.indexOfKey(typeIdIndex);
        if (index < 0) {
            return (typeIdIndex >= 0 && deletedTypeIds.get(typeIdIndex) ? -1 : typeIdIndex);
        } else {
            return typeIdsMap.valueAt(index);
        }
    }

    public int adjustProtoIdIndex(int protoIndex) {
        int index = protoIdsMap.indexOfKey(protoIndex);
        if (index < 0) {
            return (protoIndex >= 0 && deletedProtoIds.get(protoIndex) ? -1 : protoIndex);
        } else {
            return protoIdsMap.valueAt(index);
        }
    }

    public int adjustFieldIdIndex(int fieldIndex) {
        int index = fieldIdsMap.indexOfKey(fieldIndex);
        if (index < 0) {
            return (fieldIndex >= 0 && deletedFieldIds.get(fieldIndex) ? -1 : fieldIndex);
        } else {
            return fieldIdsMap.valueAt(index);
        }
    }

    public int adjustMethodIdIndex(int methodIndex) {
        int index = methodIdsMap.indexOfKey(methodIndex);
        if (index < 0) {
            return (methodIndex >= 0 && deletedMethodIds.get(methodIndex) ? -1 : methodIndex);
        } else {
            return methodIdsMap.valueAt(index);
        }
    }

    public int adjustTypeListOffset(int typeListOffset) {
        int index = typeListOffsetsMap.indexOfKey(typeListOffset);
        if (index < 0) {
            return (typeListOffset >= 0 && deletedTypeListOffsets.get(typeListOffset) ? -1 : typeListOffset);
        } else {
            return typeListOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationOffset(int annotationOffset) {
        int index = annotationOffsetsMap.indexOfKey(annotationOffset);
        if (index < 0) {
            return (annotationOffset >= 0 && deletedAnnotationOffsets.get(annotationOffset) ? -1 : annotationOffset);
        } else {
            return annotationOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationSetOffset(int annotationSetOffset) {
        int index = annotationSetOffsetsMap.indexOfKey(annotationSetOffset);
        if (index < 0) {
            return (annotationSetOffset >= 0 && deletedAnnotationSetOffsets.get(annotationSetOffset) ? -1 : annotationSetOffset);
        } else {
            return annotationSetOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationSetRefListOffset(int annotationSetRefListOffset) {
        int index = annotationSetRefListOffsetsMap.indexOfKey(annotationSetRefListOffset);
        if (index < 0) {
            return (annotationSetRefListOffset >= 0 && deletedAnnotationSetRefListOffsets.get(annotationSetRefListOffset) ? -1 : annotationSetRefListOffset);
        } else {
            return annotationSetRefListOffsetsMap.valueAt(index);
        }
    }

    public int adjustAnnotationsDirectoryOffset(int annotationsDirectoryOffset) {
        int index = annotationsDirectoryOffsetsMap.indexOfKey(annotationsDirectoryOffset);
        if (index < 0) {
            return (annotationsDirectoryOffset >= 0 && deletedAnnotationsDirectoryOffsets.get(annotationsDirectoryOffset) ? -1 : annotationsDirectoryOffset);
        } else {
            return annotationsDirectoryOffsetsMap.valueAt(index);
        }
    }

    public int adjustStaticValuesOffset(int staticValuesOffset) {
        int index = staticValuesOffsetsMap.indexOfKey(staticValuesOffset);
        if (index < 0) {
            return (staticValuesOffset >= 0 && deletedStaticValuesOffsets.get(staticValuesOffset) ? -1 : staticValuesOffset);
        } else {
            return staticValuesOffsetsMap.valueAt(index);
        }
    }

    public int adjustClassDataOffset(int classDataOffset) {
        int index = classDataOffsetsMap.indexOfKey(classDataOffset);
        if (index < 0) {
            return (classDataOffset >= 0 && deletedClassDataOffsets.get(classDataOffset) ? -1 : classDataOffset);
        } else {
            return classDataOffsetsMap.valueAt(index);
        }
    }

    public int adjustDebugInfoItemOffset(int debugInfoItemOffset) {
        int index = debugInfoItemOffsetsMap.indexOfKey(debugInfoItemOffset);
        if (index < 0) {
            return (debugInfoItemOffset >= 0 && deletedDebugInfoItemOffsets.get(debugInfoItemOffset) ? -1 : debugInfoItemOffset);
        } else {
            return debugInfoItemOffsetsMap.valueAt(index);
        }
    }

    public int adjustCodeOffset(int codeOffset) {
        int index = codeOffsetsMap.indexOfKey(codeOffset);
        if (index < 0) {
            return (codeOffset >= 0 && deletedCodeOffsets.get(codeOffset) ? -1 : codeOffset);
        } else {
            return codeOffsetsMap.valueAt(index);
        }
    }

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
