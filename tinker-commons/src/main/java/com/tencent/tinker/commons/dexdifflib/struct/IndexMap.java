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

package com.tencent.tinker.commons.dexdifflib.struct;

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
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.EncodedValueCodec;
import com.tencent.tinker.android.dex.EncodedValueReader;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.Leb128;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TypeId;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.android.dex.util.ByteInput;
import com.tencent.tinker.android.dex.util.ByteOutput;
import com.tencent.tinker.android.dx.util.ByteArrayOutput;
import com.tencent.tinker.commons.dexdifflib.util.IOUtils;
import com.tencent.tinker.commons.dexdifflib.util.InstructionTransformer;
import com.tencent.tinker.commons.dexdifflib.util.SparseIntArray;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;


public class IndexMap {
    private static final short INDEX_UNMAPPED = 0;
    private final int[] stringIds;
    private final int[] typeIds;
    private final int[] protoIds;
    private final int[] fieldIds;
    private final int[] methodIds;
    private final SparseIntArray typeListOffsetsMap             = new SparseIntArray(512);
    private final SparseIntArray annotationOffsetsMap           = new SparseIntArray(512);
    private final SparseIntArray annotationSetOffsetsMap        = new SparseIntArray(512);
    private final SparseIntArray annotationSetRefListOffsetsMap = new SparseIntArray(512);
    private final SparseIntArray annotationDirectoryOffsetsMap  = new SparseIntArray(512);
    private final SparseIntArray staticValuesOffsetsMap         = new SparseIntArray(512);
    private final SparseIntArray classDataOffsetsMap            = new SparseIntArray(512);
    private final SparseIntArray debugInfoItemOffsetsMap        = new SparseIntArray(512);
    private final SparseIntArray codeOffsetsMap                 = new SparseIntArray(512);

    public IndexMap(Dex oldDex) {
        TableOfContents oldToC = oldDex.getTableOfContents();
        stringIds = new int[oldToC.stringIds.size];
        typeIds = new int[oldToC.typeIds.size];
        protoIds = new int[oldToC.protoIds.size];
        fieldIds = new int[oldToC.fieldIds.size];
        methodIds = new int[oldToC.methodIds.size];

        /*
         * A type list, annotation set, annotation directory, or static value at
         * offset 0 is always empty. Always map offset 0 to 0.
         */
        typeListOffsetsMap.put(0, 0);
        annotationSetOffsetsMap.put(0, 0);
        annotationDirectoryOffsetsMap.put(0, 0);
        staticValuesOffsetsMap.put(0, 0);
    }

    public void mapStringIds(int oldIndex, int newIndex) {
        stringIds[oldIndex] = newIndex + 1;
    }

    public void mapTypeIds(int oldIndex, int newIndex) {
        typeIds[oldIndex] = newIndex + 1;
    }

    public void mapProtoIds(int oldIndex, int newIndex) {
        protoIds[oldIndex] = newIndex + 1;
    }

    public void mapFieldIds(int oldIndex, int newIndex) {
        fieldIds[oldIndex] = newIndex + 1;
    }

    public void mapMethodIds(int oldIndex, int newIndex) {
        methodIds[oldIndex] = newIndex + 1;
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

    public void mapAnnotationDirectoryOffset(int oldOffset, int newOffset) {
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
        if (stringIndex == ClassDef.NO_INDEX) return stringIndex;
        int newIdx = stringIds[stringIndex];
        return (newIdx == INDEX_UNMAPPED ? stringIndex : newIdx - 1);
    }

    public int adjustTypeIdIndex(int typeIdIndex) {
        if (typeIdIndex == ClassDef.NO_INDEX) return typeIdIndex;
        int newIdx = typeIds[typeIdIndex];
        return (newIdx == INDEX_UNMAPPED ? typeIdIndex : (newIdx - 1) & 0xFFFF);
    }

    public int adjustProtoIdIndex(int protoIndex) {
        if (protoIndex == ClassDef.NO_INDEX) return protoIndex;
        int newIdx = protoIds[protoIndex];
        return (newIdx == INDEX_UNMAPPED ? protoIndex : (newIdx - 1) & 0xFFFF);
    }

    public int adjustFieldIdIndex(int fieldIndex) {
        if (fieldIndex == ClassDef.NO_INDEX) return fieldIndex;
        int newIdx = fieldIds[fieldIndex];
        return (newIdx == INDEX_UNMAPPED ? fieldIndex : (newIdx - 1) & 0xFFFF);
    }

    public int adjustMethodIdIndex(int methodIndex) {
        if (methodIndex == ClassDef.NO_INDEX) return methodIndex;
        int newIdx = methodIds[methodIndex];
        return (newIdx == INDEX_UNMAPPED ? methodIndex : (newIdx - 1) & 0xFFFF);
    }

    public int adjustTypeListOffset(int typeListOffset) {
        int pos = typeListOffsetsMap.indexOfKey(typeListOffset);
        return (pos < 0 ? typeListOffset : typeListOffsetsMap.valueAt(pos));
    }

    public int adjustAnnotationOffset(int annotationOffset) {
        int pos = annotationOffsetsMap.indexOfKey(annotationOffset);
        return (pos < 0 ? annotationOffset : annotationOffsetsMap.valueAt(pos));
    }

    public int adjustAnnotationSetOffset(int annotationSetOffset) {
        int pos = annotationSetOffsetsMap.indexOfKey(annotationSetOffset);
        return (pos < 0 ? annotationSetOffset : annotationSetOffsetsMap.valueAt(pos));
    }

    public int adjustAnnotationSetRefListOffset(int annotationSetRefListOffset) {
        int pos = annotationSetRefListOffsetsMap.indexOfKey(annotationSetRefListOffset);
        return (pos < 0 ? annotationSetRefListOffset : annotationSetRefListOffsetsMap.valueAt(pos));
    }

    public int adjustAnnotationDirectoryOffset(int annotationDirectoryOffset) {
        int pos = annotationDirectoryOffsetsMap.indexOfKey(annotationDirectoryOffset);
        return (pos < 0 ? annotationDirectoryOffset : annotationDirectoryOffsetsMap.valueAt(pos));
    }

    public int adjustStaticValuesOffset(int staticValuesOffset) {
        int pos = staticValuesOffsetsMap.indexOfKey(staticValuesOffset);
        return (pos < 0 ? staticValuesOffset : staticValuesOffsetsMap.valueAt(pos));
    }

    public int adjustClassDataOffset(int classDataOffset) {
        int pos = classDataOffsetsMap.indexOfKey(classDataOffset);
        return (pos < 0 ? classDataOffset : classDataOffsetsMap.valueAt(pos));
    }

    public int adjustDebugInfoItemOffset(int debugInfoItemOffset) {
        int pos = debugInfoItemOffsetsMap.indexOfKey(debugInfoItemOffset);
        return (pos < 0 ? debugInfoItemOffset : debugInfoItemOffsetsMap.valueAt(pos));
    }

    public int adjustCodeOffset(int codeOffset) {
        int pos = codeOffsetsMap.indexOfKey(codeOffset);
        return (pos < 0 ? codeOffset : codeOffsetsMap.valueAt(pos));
    }

    public TypeId adjust(TypeId typeId) {
        typeId.descriptorIndex = adjustStringIndex(typeId.descriptorIndex);
        return typeId;
    }

    public TypeList adjust(TypeList typeList) {
        if (typeList == TypeList.EMPTY) {
            return typeList;
        }
        short[] types = typeList.types;
        for (int i = 0; i < types.length; i++) {
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
        return new Code(
            code.owner,
            code.off,
            code.registersSize,
            code.insSize,
            code.outsSize,
            adjustDebugInfoItemOffset(code.debugInfoOffset),
            adjustInstructions(code.instructions),
            code.tries,
            adjustCatchHandlers(code.catchHandlers)
        );
    }

    private short[] adjustInstructions(short[] instructions) {
        if (instructions == null || instructions.length == 0) {
            return instructions;
        }
        InstructionTransformer insTrans = new InstructionTransformer(this);
        return insTrans.transform(instructions);
    }

    private CatchHandler[] adjustCatchHandlers(CatchHandler[] catchHandlers) {
        if (catchHandlers == null || catchHandlers.length == 0) {
            return catchHandlers;
        }
        int count = catchHandlers.length;
        for (int i = 0; i < count; ++i) {
            catchHandlers[i] = adjustCatchHandler(catchHandlers[i]);
        }
        return catchHandlers;
    }

    private CatchHandler adjustCatchHandler(CatchHandler catchHandler) {
        int typeIndexesCount = catchHandler.typeIndexes.length;
        for (int i = 0; i < typeIndexesCount; ++i) {
            catchHandler.typeIndexes[i] = adjustTypeIdIndex(catchHandler.typeIndexes[i]);
        }
        return catchHandler;
    }

    private Field[] adjustFields(Field[] fields) {
        int size = fields.length;
        for (int i = 0; i < size; ++i) {
            fields[i] = adjustField(fields[i]);
        }
        return fields;
    }

    private Field adjustField(Field field) {
        field.fieldIndex = adjustFieldIdIndex(field.fieldIndex);
        return field;
    }

    private Method[] adjustMethods(Method[] methods) {
        int size = methods.length;
        for (int i = 0; i < size; ++i) {
            methods[i] = adjustMethod(methods[i]);
        }
        return methods;
    }

    private Method adjustMethod(Method method) {
        method.methodIndex = adjustMethodIdIndex(method.methodIndex);
        method.codeOffset = adjustCodeOffset(method.codeOffset);
        return method;
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
        ByteArrayInputStream bais = null;
        ByteArrayOutputStream baos = null;

        try {
            bais = new ByteArrayInputStream(infoSTM);
            final ByteArrayInputStream baisRef = bais;
            ByteInput inAdapter = new ByteInput() {
                @Override
                public byte readByte() {
                    return (byte) (baisRef.read() & 0xFF);
                }
            };

            baos = new ByteArrayOutputStream(infoSTM.length + 512);
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
        } finally {
            IOUtils.closeQuietly(bais);
            IOUtils.closeQuietly(baos);
        }
    }

    public EncodedValue adjustEncodedArray(EncodedValue encodedArray) {
        ByteArrayOutput out = new ByteArrayOutput();
        new EncodedValueTransformer(out).transformArray(
            new EncodedValueReader(encodedArray, EncodedValueReader.ENCODED_ARRAY)
        );
        encodedArray.data = out.toByteArray();
        return encodedArray;
    }

    public Annotation adjust(Annotation annotation) {
        ByteArrayOutput out = new ByteArrayOutput();
        new EncodedValueTransformer(out).transformAnnotation(annotation.getReader());
        annotation.encodedAnnotation.data = out.toByteArray();
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

    public AnnotationDirectory adjust(AnnotationDirectory annotationDirectory) {
        annotationDirectory.classAnnotationsOffset = adjustAnnotationSetOffset(annotationDirectory.classAnnotationsOffset);

        int[][] fieldAnnotations = annotationDirectory.fieldAnnotations;
        for (int[] fieldAnnotation : fieldAnnotations) {
            fieldAnnotation[0] = adjustFieldIdIndex(fieldAnnotation[0]);
            fieldAnnotation[1] = adjustAnnotationSetOffset(fieldAnnotation[1]);
        }

        int[][] methodAnnotations = annotationDirectory.methodAnnotations;
        for (int[] methodAnnotation : methodAnnotations) {
            methodAnnotation[0] = adjustMethodIdIndex(methodAnnotation[0]);
            methodAnnotation[1] = adjustAnnotationSetOffset(methodAnnotation[1]);
        }

        int[][] parameterAnnotations = annotationDirectory.parameterAnnotations;
        for (int[] parameterAnnotation : parameterAnnotations) {
            parameterAnnotation[0] = adjustMethodIdIndex(parameterAnnotation[0]);
            parameterAnnotation[1] = adjustAnnotationSetRefListOffset(parameterAnnotation[1]);
        }

        return annotationDirectory;
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
