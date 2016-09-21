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

package com.tencent.tinker.android.dex.io;

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
import com.tencent.tinker.android.dex.EncodedValueReader;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.Leb128;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.Mutf8;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.StringData;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.android.dex.util.ByteInput;
import com.tencent.tinker.android.dex.util.ByteOutput;

import java.io.ByteArrayOutputStream;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * *** This file is NOT a part of AOSP. ***
 * Created by tangyinsheng on 2016/6/30.
 */
public class DexDataBuffer implements ByteInput, ByteOutput {
    public static final int DEFAULT_BUFFER_SIZE = 512;

    private static final short[] EMPTY_SHORT_ARRAY = new short[0];

    private ByteBuffer data;
    private int dataBound;
    private boolean isResizeAllowed;

    public DexDataBuffer() {
        this.data = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.dataBound = this.data.position();
        this.data.limit(this.data.capacity());
        this.isResizeAllowed = true;
    }

    public DexDataBuffer(ByteBuffer data) {
        this.data = data;
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.dataBound = data.limit();
        this.isResizeAllowed = false;
    }

    public DexDataBuffer(ByteBuffer data, boolean isResizeAllowed) {
        this.data = data;
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.dataBound = data.limit();
        this.isResizeAllowed = isResizeAllowed;
    }

    public int position() {
        return data.position();
    }

    public void position(int pos) {
        data.position(pos);
    }

    public int available() {
        return dataBound - data.position();
    }

    private void ensureBufferSize(int bytes) {
        if (this.data.position() + bytes > this.data.limit()) {
            if (this.isResizeAllowed) {
                byte[] array = this.data.array();
                byte[] newArray = new byte[array.length + bytes + (array.length >> 1)];
                System.arraycopy(array, 0, newArray, 0, this.data.position());
                int lastPos = this.data.position();
                this.data = ByteBuffer.wrap(newArray);
                this.data.order(ByteOrder.LITTLE_ENDIAN);
                this.data.position(lastPos);
                this.data.limit(this.data.capacity());
            }
        }
    }

    public byte[] array() {
        byte[] result = new byte[this.dataBound];
        byte[] dataArray = this.data.array();
        System.arraycopy(dataArray, 0, result, 0, this.dataBound);
        return result;
    }

    @Override
    public byte readByte() {
        return data.get();
    }

    public int readUnsignedByte() {
        return readByte() & 0xFF;
    }

    public short readShort() {
        return data.getShort();
    }

    public int readUnsignedShort() {
        return readShort() & 0xffff;
    }

    public int readInt() {
        return data.getInt();
    }

    public byte[] readByteArray(int length) {
        byte[] result = new byte[length];
        data.get(result);
        return result;
    }

    public short[] readShortArray(int length) {
        if (length == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        short[] result = new short[length];
        for (int i = 0; i < length; i++) {
            result[i] = readShort();
        }
        return result;
    }

    public int readUleb128() {
        return Leb128.readUnsignedLeb128(this);
    }

    public int readUleb128p1() {
        return Leb128.readUnsignedLeb128(this) - 1;
    }

    public int readSleb128() {
        return Leb128.readSignedLeb128(this);
    }

    public StringData readStringData() {
        int off = data.position();
        try {
            int expectedLength = readUleb128();
            String result = Mutf8.decode(this, new char[expectedLength]);
            if (result.length() != expectedLength) {
                throw new DexException("Declared length " + expectedLength
                        + " doesn't match decoded length of " + result.length());
            }
            return new StringData(off, result);
        } catch (UTFDataFormatException e) {
            throw new DexException(e);
        }
    }

    public TypeList readTypeList() {
        int off = data.position();
        int size = readInt();
        short[] types = readShortArray(size);
        return new TypeList(off, types);
    }

    public FieldId readFieldId() {
        int off = data.position();
        int declaringClassIndex = readUnsignedShort();
        int typeIndex = readUnsignedShort();
        int nameIndex = readInt();
        return new FieldId(off, declaringClassIndex, typeIndex, nameIndex);
    }

    public MethodId readMethodId() {
        int off = data.position();
        int declaringClassIndex = readUnsignedShort();
        int protoIndex = readUnsignedShort();
        int nameIndex = readInt();
        return new MethodId(off, declaringClassIndex, protoIndex, nameIndex);
    }

    public ProtoId readProtoId() {
        int off = data.position();
        int shortyIndex = readInt();
        int returnTypeIndex = readInt();
        int parametersOffset = readInt();
        return new ProtoId(off, shortyIndex, returnTypeIndex, parametersOffset);
    }

    public ClassDef readClassDef() {
        int off = position();
        int type = readInt();
        int accessFlags = readInt();
        int supertype = readInt();
        int interfacesOffset = readInt();
        int sourceFileIndex = readInt();
        int annotationsOffset = readInt();
        int classDataOffset = readInt();
        int staticValuesOffset = readInt();
        return new ClassDef(off, type, accessFlags, supertype,
                interfacesOffset, sourceFileIndex, annotationsOffset, classDataOffset,
                staticValuesOffset);
    }

    public Code readCode() {
        int off = data.position();
        int registersSize = readUnsignedShort();
        int insSize = readUnsignedShort();
        int outsSize = readUnsignedShort();
        int triesSize = readUnsignedShort();
        int debugInfoOffset = readInt();
        int instructionsSize = readInt();
        short[] instructions = readShortArray(instructionsSize);
        Code.Try[] tries;
        Code.CatchHandler[] catchHandlers;
        if (triesSize > 0) {
            if (instructions.length % 2 == 1) {
                readShort(); // padding
            }

            /*
             * We can't read the tries until we've read the catch handlers.
             * Unfortunately they're in the opposite order in the dex file
             * so we need to read them out-of-order.
             */
            int posBeforeTries = data.position();
            skip(triesSize * SizeOf.TRY_ITEM);
            catchHandlers = readCatchHandlers();
            int posAfterCatchHandlers = data.position();
            data.position(posBeforeTries);
            tries = readTries(triesSize, catchHandlers);
            data.position(posAfterCatchHandlers);
        } else {
            tries = new Code.Try[0];
            catchHandlers = new Code.CatchHandler[0];
        }
        return new Code(off, registersSize, insSize, outsSize, debugInfoOffset, instructions,
                tries, catchHandlers);
    }

    private Code.CatchHandler[] readCatchHandlers() {
        int baseOffset = data.position();
        int catchHandlersSize = readUleb128();
        Code.CatchHandler[] result = new Code.CatchHandler[catchHandlersSize];
        for (int i = 0; i < catchHandlersSize; i++) {
            int offset = data.position() - baseOffset;
            result[i] = readCatchHandler(offset);
        }
        return result;
    }

    private Code.Try[] readTries(int triesSize, Code.CatchHandler[] catchHandlers) {
        Code.Try[] result = new Code.Try[triesSize];
        for (int i = 0; i < triesSize; i++) {
            int startAddress = readInt();
            int instructionCount = readUnsignedShort();
            int handlerOffset = readUnsignedShort();
            int catchHandlerIndex = findCatchHandlerIndex(catchHandlers, handlerOffset);
            result[i] = new Code.Try(startAddress, instructionCount, catchHandlerIndex);
        }
        return result;
    }

    private int findCatchHandlerIndex(Code.CatchHandler[] catchHandlers, int offset) {
        for (int i = 0; i < catchHandlers.length; i++) {
            Code.CatchHandler catchHandler = catchHandlers[i];
            if (catchHandler.offset == offset) {
                return i;
            }
        }
        throw new IllegalArgumentException();
    }

    private Code.CatchHandler readCatchHandler(int offset) {
        int size = readSleb128();
        int handlersCount = Math.abs(size);
        int[] typeIndexes = new int[handlersCount];
        int[] addresses = new int[handlersCount];
        for (int i = 0; i < handlersCount; i++) {
            typeIndexes[i] = readUleb128();
            addresses[i] = readUleb128();
        }
        int catchAllAddress = size <= 0 ? readUleb128() : -1;
        return new Code.CatchHandler(typeIndexes, addresses, catchAllAddress, offset);
    }

    public DebugInfoItem readDebugInfoItem() {
        int off = data.position();

        int lineStart = readUleb128();
        int parametersSize = readUleb128();
        int[] parameterNames = new int[parametersSize];
        for (int i = 0; i < parametersSize; ++i) {
            parameterNames[i] = readUleb128p1();
        }

        ByteArrayOutputStream baos = null;

        try {
            baos = new ByteArrayOutputStream(64);

            final ByteArrayOutputStream baosRef = baos;

            ByteOutput outAdapter = new ByteOutput() {
                @Override
                public void writeByte(int i) {
                    baosRef.write(i);
                }
            };

            outside_whileloop:
                while (true) {
                    int opcode = readByte();
                    baos.write(opcode);
                    switch (opcode) {
                        case DebugInfoItem.DBG_END_SEQUENCE: {
                            break outside_whileloop;
                        }
                        case DebugInfoItem.DBG_ADVANCE_PC: {
                            int addrDiff = readUleb128();
                            Leb128.writeUnsignedLeb128(outAdapter, addrDiff);
                            break;
                        }
                        case DebugInfoItem.DBG_ADVANCE_LINE: {
                            int lineDiff = readSleb128();
                            Leb128.writeSignedLeb128(outAdapter, lineDiff);
                            break;
                        }
                        case DebugInfoItem.DBG_START_LOCAL:
                        case DebugInfoItem.DBG_START_LOCAL_EXTENDED: {
                            int registerNum = readUleb128();
                            Leb128.writeUnsignedLeb128(outAdapter, registerNum);
                            int nameIndex = readUleb128p1();
                            Leb128.writeUnsignedLeb128p1(outAdapter, nameIndex);
                            int typeIndex = readUleb128p1();
                            Leb128.writeUnsignedLeb128p1(outAdapter, typeIndex);
                            if (opcode == DebugInfoItem.DBG_START_LOCAL_EXTENDED) {
                                int sigIndex = readUleb128p1();
                                Leb128.writeUnsignedLeb128p1(outAdapter, sigIndex);
                            }
                            break;
                        }
                        case DebugInfoItem.DBG_END_LOCAL:
                        case DebugInfoItem.DBG_RESTART_LOCAL: {
                            int registerNum = readUleb128();
                            Leb128.writeUnsignedLeb128(outAdapter, registerNum);
                            break;
                        }
                        case DebugInfoItem.DBG_SET_FILE: {
                            int nameIndex = readUleb128p1();
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

            byte[] infoSTM = baos.toByteArray();
            return new DebugInfoItem(off, lineStart, parameterNames, infoSTM);
        } finally {
            if (baos != null) {
                try {
                    baos.close();
                } catch (Exception e) {
                    // Do nothing.
                }
            }
        }
    }

    public ClassData readClassData() {
        int off = data.position();
        int staticFieldsSize = readUleb128();
        int instanceFieldsSize = readUleb128();
        int directMethodsSize = readUleb128();
        int virtualMethodsSize = readUleb128();
        ClassData.Field[] staticFields = readFields(staticFieldsSize);
        ClassData.Field[] instanceFields = readFields(instanceFieldsSize);
        ClassData.Method[] directMethods = readMethods(directMethodsSize);
        ClassData.Method[] virtualMethods = readMethods(virtualMethodsSize);
        return new ClassData(off, staticFields, instanceFields, directMethods, virtualMethods);
    }

    private ClassData.Field[] readFields(int count) {
        ClassData.Field[] result = new ClassData.Field[count];
        int fieldIndex = 0;
        for (int i = 0; i < count; i++) {
            fieldIndex += readUleb128(); // field index diff
            int accessFlags = readUleb128();
            result[i] = new ClassData.Field(fieldIndex, accessFlags);
        }
        return result;
    }

    private ClassData.Method[] readMethods(int count) {
        ClassData.Method[] result = new ClassData.Method[count];
        int methodIndex = 0;
        for (int i = 0; i < count; i++) {
            methodIndex += readUleb128(); // method index diff
            int accessFlags = readUleb128();
            int codeOff = readUleb128();
            result[i] = new ClassData.Method(methodIndex, accessFlags, codeOff);
        }
        return result;
    }

    /**
     * Returns a byte array containing the bytes from {@code start} to this
     * section's current position.
     */
    private byte[] getBytesFrom(int start) {
        int end = data.position();
        byte[] result = new byte[end - start];
        data.position(start);
        data.get(result);
        return result;
    }

    public Annotation readAnnotation() {
        int off = data.position();
        byte visibility = readByte();
        int start = data.position();
        new EncodedValueReader(this, EncodedValueReader.ENCODED_ANNOTATION).skipValue();
        return new Annotation(off, visibility, new EncodedValue(start, getBytesFrom(start)));
    }

    public AnnotationSet readAnnotationSet() {
        int off = data.position();
        int size = readInt();
        int[] annotationOffsets = new int[size];
        for (int i = 0; i < size; ++i) {
            annotationOffsets[i] = readInt();
        }
        return new AnnotationSet(off, annotationOffsets);
    }

    public AnnotationSetRefList readAnnotationSetRefList() {
        int off = data.position();
        int size = readInt();
        int[] annotationSetRefItems = new int[size];
        for (int i = 0; i < size; ++i) {
            annotationSetRefItems[i] = readInt();
        }
        return new AnnotationSetRefList(off, annotationSetRefItems);
    }

    public AnnotationsDirectory readAnnotationsDirectory() {
        int off = data.position();
        int classAnnotationsOffset = readInt();
        int fieldsSize = readInt();
        int methodsSize = readInt();
        int parameterListSize = readInt();

        int[][] fieldAnnotations = new int[fieldsSize][2];
        for (int i = 0; i < fieldsSize; ++i) {
            // field index
            fieldAnnotations[i][0] = readInt();
            // annotations offset
            fieldAnnotations[i][1] = readInt();
        }

        int[][] methodAnnotations = new int[methodsSize][2];
        for (int i = 0; i < methodsSize; ++i) {
            // method index
            methodAnnotations[i][0] = readInt();
            // annotation set offset
            methodAnnotations[i][1] = readInt();
        }

        int[][] parameterAnnotations = new int[parameterListSize][2];
        for (int i = 0; i < parameterListSize; ++i) {
            // method index
            parameterAnnotations[i][0] = readInt();
            // annotations offset
            parameterAnnotations[i][1] = readInt();
        }

        return new AnnotationsDirectory(off, classAnnotationsOffset, fieldAnnotations, methodAnnotations, parameterAnnotations);
    }

    public EncodedValue readEncodedArray() {
        int start = data.position();
        new EncodedValueReader(this, EncodedValueReader.ENCODED_ARRAY).skipValue();
        return new EncodedValue(start, getBytesFrom(start));
    }

    public void skip(int count) {
        if (count < 0) {
            throw new IllegalArgumentException();
        }
        data.position(data.position() + count);
    }

    public void skipWithAutoExpand(int count) {
        ensureBufferSize(SizeOf.UBYTE * count);
        skip(count);
    }

    /**
     * Skips bytes until the position is aligned to a multiple of 4.
     */
    public void alignToFourBytes() {
        data.position((data.position() + 3) & ~3);
    }

    /**
     * Writes 0x00 until the position is aligned to a multiple of 4.
     */
    public void alignToFourBytesWithZeroFill() {
        int alignedPos = SizeOf.roundToTimesOfFour(data.position());
        ensureBufferSize((alignedPos - data.position()) * SizeOf.UBYTE);
        while ((data.position() & 3) != 0) {
            data.put((byte) 0);
        }
        if (this.data.position() > this.dataBound) {
            this.dataBound = this.data.position();
        }
    }

    @Override
    public void writeByte(int b) {
        ensureBufferSize(SizeOf.UBYTE);
        data.put((byte) b);
        if (this.data.position() > this.dataBound) {
            this.dataBound = this.data.position();
        }
    }

    public void writeShort(short i) {
        ensureBufferSize(SizeOf.USHORT);
        data.putShort(i);
        if (this.data.position() > this.dataBound) {
            this.dataBound = this.data.position();
        }
    }

    public void writeUnsignedShort(int i) {
        short s = (short) i;
        if (i != (s & 0xffff)) {
            throw new IllegalArgumentException("Expected an unsigned short: " + i);
        }
        writeShort(s);
    }

    public void writeInt(int i) {
        ensureBufferSize(SizeOf.UINT);
        this.data.putInt(i);
        if (this.data.position() > this.dataBound) {
            this.dataBound = this.data.position();
        }
    }

    public void write(byte[] bytes) {
        ensureBufferSize(bytes.length * SizeOf.UBYTE);
        this.data.put(bytes);
        if (this.data.position() > this.dataBound) {
            this.dataBound = this.data.position();
        }
    }

    public void write(short[] shorts) {
        ensureBufferSize(shorts.length * SizeOf.USHORT);
        for (short s : shorts) {
            writeShort(s);
        }
        if (this.data.position() > this.dataBound) {
            this.dataBound = this.data.position();
        }
    }

    public void writeUleb128(int i) {
        Leb128.writeUnsignedLeb128(this, i);
    }

    public void writeUleb128p1(int i) {
        writeUleb128(i + 1);
    }

    public void writeSleb128(int i) {
        Leb128.writeSignedLeb128(this, i);
    }

    /**
     * Write String data into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeStringData(StringData stringData) {
        int off = data.position();
        try {
            int length = stringData.value.length();
            writeUleb128(length);
            write(Mutf8.encode(stringData.value));
            writeByte(0);
            return off;
        } catch (UTFDataFormatException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Write TypeList item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeTypeList(TypeList typeList) {
        int off = data.position();
        short[] types = typeList.types;
        writeInt(types.length);
        for (short type : types) {
            writeShort(type);
        }
        return off;
    }

    /**
     * Write FieldId item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeFieldId(FieldId fieldId) {
        int off = data.position();
        writeUnsignedShort(fieldId.declaringClassIndex);
        writeUnsignedShort(fieldId.typeIndex);
        writeInt(fieldId.nameIndex);
        return off;
    }

    /**
     * Write MethodId item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeMethodId(MethodId methodId) {
        int off = data.position();
        writeUnsignedShort(methodId.declaringClassIndex);
        writeUnsignedShort(methodId.protoIndex);
        writeInt(methodId.nameIndex);
        return off;
    }

    /**
     * Write ProtoId item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeProtoId(ProtoId protoId) {
        int off = data.position();
        writeInt(protoId.shortyIndex);
        writeInt(protoId.returnTypeIndex);
        writeInt(protoId.parametersOffset);
        return off;
    }

    /**
     * Write ClassDef item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeClassDef(ClassDef classDef) {
        int off = data.position();
        writeInt(classDef.typeIndex);
        writeInt(classDef.accessFlags);
        writeInt(classDef.supertypeIndex);
        writeInt(classDef.interfacesOffset);
        writeInt(classDef.sourceFileIndex);
        writeInt(classDef.annotationsOffset);
        writeInt(classDef.classDataOffset);
        writeInt(classDef.staticValuesOffset);
        return off;
    }

    /**
     * Write Code item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeCode(Code code) {
        int off = data.position();
        writeUnsignedShort(code.registersSize);
        writeUnsignedShort(code.insSize);
        writeUnsignedShort(code.outsSize);
        writeUnsignedShort(code.tries.length);
        writeInt(code.debugInfoOffset);
        writeInt(code.instructions.length);
        write(code.instructions);

        if (code.tries.length > 0) {
            if ((code.instructions.length & 1) == 1) {
                writeShort((short) 0); // padding
            }

            /*
             * We can't write the tries until we've written the catch handlers.
             * Unfortunately they're in the opposite order in the dex file so we
             * need to transform them out-of-order.
             */
            int posBeforeTries = data.position();
            skipWithAutoExpand(code.tries.length * SizeOf.TRY_ITEM);
            int[] offsets = writeCatchHandlers(code.catchHandlers);
            int posAfterCatchHandlers = data.position();
            data.position(posBeforeTries);
            writeTries(code.tries, offsets);
            data.position(posAfterCatchHandlers);
        }
        return off;
    }

    private int[] writeCatchHandlers(Code.CatchHandler[] catchHandlers) {
        int baseOffset = data.position();
        writeUleb128(catchHandlers.length);
        int[] offsets = new int[catchHandlers.length];
        for (int i = 0; i < catchHandlers.length; i++) {
            offsets[i] = data.position() - baseOffset;
            writeCatchHandler(catchHandlers[i]);
        }
        return offsets;
    }

    private void writeCatchHandler(Code.CatchHandler catchHandler) {
        int catchAllAddress = catchHandler.catchAllAddress;
        int[] typeIndexes = catchHandler.typeIndexes;
        int[] addresses = catchHandler.addresses;

        if (catchAllAddress != -1) {
            writeSleb128(-typeIndexes.length);
        } else {
            writeSleb128(typeIndexes.length);
        }

        for (int i = 0; i < typeIndexes.length; i++) {
            writeUleb128(typeIndexes[i]);
            writeUleb128(addresses[i]);
        }

        if (catchAllAddress != -1) {
            writeUleb128(catchAllAddress);
        }
    }

    private void writeTries(Code.Try[] tries, int[] catchHandlerOffsets) {
        for (Code.Try tryItem : tries) {
            writeInt(tryItem.startAddress);
            writeUnsignedShort(tryItem.instructionCount);
            writeUnsignedShort(catchHandlerOffsets[tryItem.catchHandlerIndex]);
        }
    }

    /**
     * Write DebugInfo item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeDebugInfoItem(DebugInfoItem debugInfoItem) {
        int off = data.position();

        writeUleb128(debugInfoItem.lineStart);

        int parametersSize = debugInfoItem.parameterNames.length;
        writeUleb128(parametersSize);

        for (int i = 0; i < parametersSize; ++i) {
            int parameterName = debugInfoItem.parameterNames[i];
            writeUleb128p1(parameterName);
        }

        write(debugInfoItem.infoSTM);

        return off;
    }

    /**
     * Write ClassData item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeClassData(ClassData classData) {
        int off = data.position();
        writeUleb128(classData.staticFields.length);
        writeUleb128(classData.instanceFields.length);
        writeUleb128(classData.directMethods.length);
        writeUleb128(classData.virtualMethods.length);
        writeFields(classData.staticFields);
        writeFields(classData.instanceFields);
        writeMethods(classData.directMethods);
        writeMethods(classData.virtualMethods);
        return off;
    }

    private void writeFields(ClassData.Field[] fields) {
        int lastOutFieldIndex = 0;
        for (ClassData.Field field : fields) {
            writeUleb128(field.fieldIndex - lastOutFieldIndex);
            lastOutFieldIndex = field.fieldIndex;
            writeUleb128(field.accessFlags);
        }
    }

    private void writeMethods(ClassData.Method[] methods) {
        int lastOutMethodIndex = 0;
        for (ClassData.Method method : methods) {
            writeUleb128(method.methodIndex - lastOutMethodIndex);
            lastOutMethodIndex = method.methodIndex;
            writeUleb128(method.accessFlags);
            writeUleb128(method.codeOffset);
        }
    }

    /**
     * Write Annotation item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeAnnotation(Annotation annotation) {
        int off = data.position();
        writeByte(annotation.visibility);
        writeEncodedArray(annotation.encodedAnnotation);
        return off;
    }

    /**
     * Write AnnotationSet item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeAnnotationSet(AnnotationSet annotationSet) {
        int off = data.position();
        writeInt(annotationSet.annotationOffsets.length);
        for (int annotationOffset : annotationSet.annotationOffsets) {
            writeInt(annotationOffset);
        }
        return off;
    }

    /**
     * Write AnnotationSetRefList item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeAnnotationSetRefList(AnnotationSetRefList annotationSetRefList) {
        int off = data.position();
        writeInt(annotationSetRefList.annotationSetRefItems.length);
        for (int annotationSetRefItem : annotationSetRefList.annotationSetRefItems) {
            writeInt(annotationSetRefItem);
        }
        return off;
    }

    /**
     * Write AnnotationDirectory item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeAnnotationsDirectory(AnnotationsDirectory annotationsDirectory) {
        int off = data.position();
        writeInt(annotationsDirectory.classAnnotationsOffset);
        writeInt(annotationsDirectory.fieldAnnotations.length);
        writeInt(annotationsDirectory.methodAnnotations.length);
        writeInt(annotationsDirectory.parameterAnnotations.length);

        for (int[] fieldAnnotation : annotationsDirectory.fieldAnnotations) {
            writeInt(fieldAnnotation[0]);
            writeInt(fieldAnnotation[1]);
        }

        for (int[] methodAnnotation : annotationsDirectory.methodAnnotations) {
            writeInt(methodAnnotation[0]);
            writeInt(methodAnnotation[1]);
        }

        for (int[] parameterAnnotation : annotationsDirectory.parameterAnnotations) {
            writeInt(parameterAnnotation[0]);
            writeInt(parameterAnnotation[1]);
        }
        return off;
    }

    /**
     * Write EncodedValue/EncodedArray item into current section.
     *
     * @return real offset of item we've just written in this section.
     */
    public int writeEncodedArray(EncodedValue encodedValue) {
        int off = data.position();
        write(encodedValue.data);
        return off;
    }
}