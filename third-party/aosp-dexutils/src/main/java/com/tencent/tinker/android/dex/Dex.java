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

package com.tencent.tinker.android.dex;

import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dex.util.FileUtils;
import com.tencent.tinker.android.dx.util.Hex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The bytes of a dex file in memory for reading and writing. All int offsets
 * are unsigned.
 */
public final class Dex {
    // Provided as a convenience to avoid a memory allocation to benefit Dalvik.
    // Note: libcore.util.EmptyArray cannot be accessed when this code isn't run on Dalvik.
    static final short[] EMPTY_SHORT_ARRAY = new short[0];
    private static final int CHECKSUM_OFFSET = 8;
    private static final int SIGNATURE_OFFSET = CHECKSUM_OFFSET + SizeOf.CHECKSUM;
    private final TableOfContents tableOfContents = new TableOfContents();
    private final StringTable strings = new StringTable();
    private final TypeIndexToDescriptorIndexTable typeIds = new TypeIndexToDescriptorIndexTable();
    private final TypeIndexToDescriptorTable typeNames = new TypeIndexToDescriptorTable();
    private final ProtoIdTable protoIds = new ProtoIdTable();
    private final FieldIdTable fieldIds = new FieldIdTable();
    private final MethodIdTable methodIds = new MethodIdTable();
    private final ClassDefTable classDefs = new ClassDefTable();
    private ByteBuffer data;
    private int nextSectionStart = 0;
    private byte[] signature = null;

    /**
     * Creates a new dex that reads from {@code data}. It is an error to modify
     * {@code data} after using it to create a dex buffer.
     */
    public Dex(byte[] data) throws IOException {
        this(ByteBuffer.wrap(data));
    }

    private Dex(ByteBuffer data) throws IOException {
        this.data = data;
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.tableOfContents.readFrom(this);
    }

    /**
     * Creates a new empty dex of the specified size.
     */
    public Dex(int byteCount) {
        this.data = ByteBuffer.wrap(new byte[byteCount]);
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.tableOfContents.fileSize = byteCount;
    }

    /**
     * Creates a new dex buffer of the dex in {@code in}, and closes {@code in}.
     */
    public Dex(InputStream in) throws IOException {
        loadFrom(in);
    }

    public Dex(InputStream in, int initSize) throws IOException {
        loadFrom(in, initSize);
    }

    /**
     * Creates a new dex buffer from the dex file {@code file}.
     */
    public Dex(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is null.");
        }

        if (FileUtils.hasArchiveSuffix(file.getName())) {
            ZipFile zipFile = null;
            try {
                zipFile = new ZipFile(file);
                ZipEntry entry = zipFile.getEntry(DexFormat.DEX_IN_JAR_NAME);
                if (entry != null) {
                    InputStream inputStream = null;
                    try {
                        inputStream = zipFile.getInputStream(entry);
                        loadFrom(inputStream, (int) entry.getSize());
                    } finally {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    }
                } else {
                    throw new DexException("Expected " + DexFormat.DEX_IN_JAR_NAME + " in " + file);
                }
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (Exception e) {
                        // ignored.
                    }
                }
            }
        } else if (file.getName().endsWith(".dex")) {
            InputStream in = null;
            try {
                in = new BufferedInputStream(new FileInputStream(file));
                loadFrom(in, (int) file.length());
            } catch (Exception e) {
                throw new DexException(e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception e) {
                        // ignored.
                    }
                }
            }
        } else {
            throw new DexException("unknown output extension: " + file);
        }
    }

    private static void checkBounds(int index, int length) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index:" + index + ", length=" + length);
        }
    }

    private void loadFrom(InputStream in) throws IOException {
        loadFrom(in, 0);
    }

    private void loadFrom(InputStream in, int initSize) throws IOException {
        byte[] rawData = FileUtils.readStream(in, initSize);
        this.data = ByteBuffer.wrap(rawData);
        this.data.order(ByteOrder.LITTLE_ENDIAN);
        this.tableOfContents.readFrom(this);
    }

    public void writeTo(OutputStream out) throws IOException {
        byte[] rawData = data.array();
        out.write(rawData);
        out.flush();
    }

    public void writeTo(File dexOut) throws IOException {
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(dexOut));
            writeTo(out);
        } catch (Exception e) {
            throw new DexException(e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                    // ignored.
                }
            }
        }
    }

    public TableOfContents getTableOfContents() {
        return tableOfContents;
    }

    /**
     *  <b>IMPORTANT</b> To open a dex section by {@code TableOfContents.Section},
     *  please use {@code openSection(TableOfContents.Section tocSec)} instead of
     *  passing tocSec.off to this method.
     *
     *  <b>Because dex section returned by this method never checks
     *  tocSec's bound when reading or writing data.</b>
     */
    public Section openSection(int position) {
        if (position < 0 || position >= data.capacity()) {
            throw new IllegalArgumentException(
                    "position=" + position + " length=" + data.capacity()
            );
        }
        ByteBuffer sectionData = data.duplicate();
        sectionData.order(ByteOrder.LITTLE_ENDIAN); // necessary?
        sectionData.position(position);
        sectionData.limit(data.capacity());
        return new Section("temp-section", sectionData);
    }

    public Section openSection(TableOfContents.Section tocSec) {
        int position = tocSec.off;
        if (position < 0 || position >= data.capacity()) {
            throw new IllegalArgumentException(
                    "position=" + position + " length=" + data.capacity()
            );
        }
        ByteBuffer sectionData = data.duplicate();
        sectionData.order(ByteOrder.LITTLE_ENDIAN); // necessary?
        sectionData.position(position);
        sectionData.limit(position + tocSec.byteCount);
        return new Section("section", sectionData);
    }

    public Section appendSection(int maxByteCount, String name) {
        int limit = nextSectionStart + maxByteCount;
        ByteBuffer sectionData = data.duplicate();
        sectionData.order(ByteOrder.LITTLE_ENDIAN); // necessary?
        sectionData.position(nextSectionStart);
        sectionData.limit(limit);
        Section result = new Section(name, sectionData);
        nextSectionStart = limit;
        return result;
    }

    public int getLength() {
        return data.capacity();
    }

    public int getNextSectionStart() {
        return nextSectionStart;
    }

    /**
     * Returns a copy of the the bytes of this dex.
     */
    public byte[] getBytes() {
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        byte[] result = new byte[data.capacity()];
        data.position(0);
        data.get(result);
        return result;
    }

    public List<String> strings() {
        return strings;
    }

    public List<Integer> typeIds() {
        return typeIds;
    }

    public List<String> typeNames() {
        return typeNames;
    }

    public List<ProtoId> protoIds() {
        return protoIds;
    }

    public List<FieldId> fieldIds() {
        return fieldIds;
    }

    public List<MethodId> methodIds() {
        return methodIds;
    }

    public List<ClassDef> classDefs() {
        return classDefs;
    }

    public Iterable<ClassDef> classDefIterable() {
        return new ClassDefIterable();
    }

    public ClassData readClassData(ClassDef classDef) {
        int offset = classDef.classDataOffset;
        if (offset == 0) {
            throw new IllegalArgumentException("offset == 0");
        }
        return openSection(offset).readClassData();
    }

    public Code readCode(ClassData.Method method) {
        int offset = method.codeOffset;
        if (offset == 0) {
            throw new IllegalArgumentException("offset == 0");
        }
        return openSection(offset).readCode();
    }

    /**
     * Returns the signature of all but the first 32 bytes of this dex. The
     * first 32 bytes of dex files are not specified to be included in the
     * signature.
     */
    public byte[] computeSignature(boolean forceRecompute) {
        if (this.signature != null) {
            if (!forceRecompute) {
                return this.signature;
            }
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError();
        }
        byte[] buffer = new byte[8192];
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        data.limit(data.capacity());
        data.position(SIGNATURE_OFFSET + SizeOf.SIGNATURE);
        while (data.hasRemaining()) {
            int count = Math.min(buffer.length, data.remaining());
            data.get(buffer, 0, count);
            digest.update(buffer, 0, count);
        }
        return (this.signature = digest.digest());
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder strBuilder = new StringBuilder(bytes.length << 1);
        for (byte b : bytes) {
            strBuilder.append(Hex.u1(b));
        }
        return strBuilder.toString();
    }

    /**
     * Returns the checksum of all but the first 12 bytes of {@code dex}.
     */
    public int computeChecksum() throws IOException {
        Adler32 adler32 = new Adler32();
        byte[] buffer = new byte[8192];
        ByteBuffer data = this.data.duplicate(); // positioned ByteBuffers aren't thread safe
        data.limit(data.capacity());
        data.position(CHECKSUM_OFFSET + SizeOf.CHECKSUM);
        while (data.hasRemaining()) {
            int count = Math.min(buffer.length, data.remaining());
            data.get(buffer, 0, count);
            adler32.update(buffer, 0, count);
        }
        return (int) adler32.getValue();
    }

    /**
     * Generates the signature and checksum of the dex file {@code out} and
     * writes them to the file.
     */
    public void writeHashes() throws IOException {
        openSection(SIGNATURE_OFFSET).write(computeSignature(true));
        openSection(CHECKSUM_OFFSET).writeInt(computeChecksum());
    }

    /**
     * Look up a field id name index from a field index. Cheaper than:
     * {@code fieldIds().get(fieldDexIndex).getNameIndex();}
     */
    public int nameIndexFromFieldIndex(int fieldIndex) {
        checkBounds(fieldIndex, tableOfContents.fieldIds.size);
        int position = tableOfContents.fieldIds.off + (SizeOf.MEMBER_ID_ITEM * fieldIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        position += SizeOf.USHORT;  // typeIndex
        return data.getInt(position);  // nameIndex
    }

    public int findStringIndex(String s) {
        return Collections.binarySearch(strings, s);
    }

    public int findTypeIndex(String descriptor) {
        return Collections.binarySearch(typeNames, descriptor);
    }

    public int findFieldIndex(FieldId fieldId) {
        return Collections.binarySearch(fieldIds, fieldId);
    }

    public int findMethodIndex(MethodId methodId) {
        return Collections.binarySearch(methodIds, methodId);
    }

    public int findClassDefIndexFromTypeIndex(int typeIndex) {
        checkBounds(typeIndex, tableOfContents.typeIds.size);
        if (!tableOfContents.classDefs.exists()) {
            return -1;
        }
        for (int i = 0; i < tableOfContents.classDefs.size; i++) {
            if (typeIndexFromClassDefIndex(i) == typeIndex) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Look up a field id type index from a field index. Cheaper than:
     * {@code fieldIds().get(fieldDexIndex).getTypeIndex();}
     */
    public int typeIndexFromFieldIndex(int fieldIndex) {
        checkBounds(fieldIndex, tableOfContents.fieldIds.size);
        int position = tableOfContents.fieldIds.off + (SizeOf.MEMBER_ID_ITEM * fieldIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        return data.getShort(position) & 0xFFFF;  // typeIndex
    }

    /**
     * Look up a method id declaring class index from a method index. Cheaper than:
     * {@code methodIds().get(methodIndex).getDeclaringClassIndex();}
     */
    public int declaringClassIndexFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        return data.getShort(position) & 0xFFFF;  // declaringClassIndex
    }

    /**
     * Look up a method id name index from a method index. Cheaper than:
     * {@code methodIds().get(methodIndex).getNameIndex();}
     */
    public int nameIndexFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        position += SizeOf.USHORT;  // protoIndex
        return data.getInt(position);  // nameIndex
    }

    /**
     * Look up a parameter type ids from a method index. Cheaper than:
     * {@code readTypeList(protoIds.get(methodIds().get(methodDexIndex).getProtoIndex()).getParametersOffset()).getTypes();}
     */
    public short[] parameterTypeIndicesFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        int protoIndex = data.getShort(position) & 0xFFFF;
        checkBounds(protoIndex, tableOfContents.protoIds.size);
        position = tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * protoIndex);
        position += SizeOf.UINT;  // shortyIndex
        position += SizeOf.UINT;  // returnTypeIndex
        int parametersOffset = data.getInt(position);
        if (parametersOffset == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        position = parametersOffset;
        int size = data.getInt(position);
        if (size <= 0) {
            throw new AssertionError("Unexpected parameter type list size: " + size);
        }
        position += SizeOf.UINT;
        short[] types = new short[size];
        for (int i = 0; i < size; i++) {
            types[i] = data.getShort(position);
            position += SizeOf.USHORT;
        }
        return types;
    }

    /**
     * Look up a parameter type ids from a methodId. Cheaper than:
     * {@code readTypeList(protoIds.get(methodIds().get(methodDexIndex).getProtoIndex()).getParametersOffset()).getTypes();}
     */
    public short[] parameterTypeIndicesFromMethodId(MethodId methodId) {
        int protoIndex = methodId.protoIndex & 0xFFFF;
        checkBounds(protoIndex, tableOfContents.protoIds.size);
        int position = tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * protoIndex);
        position += SizeOf.UINT;  // shortyIndex
        position += SizeOf.UINT;  // returnTypeIndex
        int parametersOffset = data.getInt(position);
        if (parametersOffset == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        position = parametersOffset;
        int size = data.getInt(position);
        if (size <= 0) {
            throw new AssertionError("Unexpected parameter type list size: " + size);
        }
        position += SizeOf.UINT;
        short[] types = new short[size];
        for (int i = 0; i < size; i++) {
            types[i] = data.getShort(position);
            position += SizeOf.USHORT;
        }
        return types;
    }

    /**
     * Look up a method id return type index from a method index. Cheaper than:
     * {@code protoIds().get(methodIds().get(methodDexIndex).getProtoIndex()).getReturnTypeIndex();}
     */
    public int returnTypeIndexFromMethodIndex(int methodIndex) {
        checkBounds(methodIndex, tableOfContents.methodIds.size);
        int position = tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * methodIndex);
        position += SizeOf.USHORT;  // declaringClassIndex
        int protoIndex = data.getShort(position) & 0xFFFF;
        checkBounds(protoIndex, tableOfContents.protoIds.size);
        position = tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * protoIndex);
        position += SizeOf.UINT;  // shortyIndex
        return data.getInt(position);  // returnTypeIndex
    }

    /**
     * Look up a descriptor index from a type index. Cheaper than:
     * {@code openSection(tableOfContents.typeIds.off + (index * SizeOf.TYPE_ID_ITEM)).readInt();}
     */
    public int descriptorIndexFromTypeIndex(int typeIndex) {
       checkBounds(typeIndex, tableOfContents.typeIds.size);
       int position = tableOfContents.typeIds.off + (SizeOf.TYPE_ID_ITEM * typeIndex);
       return data.getInt(position);
    }

    /**
     * Look up a type index index from a class def index.
     */
    public int typeIndexFromClassDefIndex(int classDefIndex) {
        checkBounds(classDefIndex, tableOfContents.classDefs.size);
        int position = tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * classDefIndex);
        return data.getInt(position);
    }

    /**
     * Look up an annotation directory offset from a class def index.
     */
    public int annotationDirectoryOffsetFromClassDefIndex(int classDefIndex) {
        checkBounds(classDefIndex, tableOfContents.classDefs.size);
        int position = tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * classDefIndex);
        position += SizeOf.UINT;  // type
        position += SizeOf.UINT;  // accessFlags
        position += SizeOf.UINT;  // superType
        position += SizeOf.UINT;  // interfacesOffset
        position += SizeOf.UINT;  // sourceFileIndex
        return data.getInt(position);
    }

    /**
     * Look up interface types indices from a  return type index from a method index. Cheaper than:
     * {@code ...getClassDef(classDefIndex).getInterfaces();}
     */
    public short[] interfaceTypeIndicesFromClassDefIndex(int classDefIndex) {
        checkBounds(classDefIndex, tableOfContents.classDefs.size);
        int position = tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * classDefIndex);
        position += SizeOf.UINT;  // type
        position += SizeOf.UINT;  // accessFlags
        position += SizeOf.UINT;  // superType
        int interfacesOffset = data.getInt(position);
        if (interfacesOffset == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        position = interfacesOffset;
        int size = data.getInt(position);
        if (size <= 0) {
            throw new AssertionError("Unexpected interfaces list size: " + size);
        }
        position += SizeOf.UINT;
        short[] types = new short[size];
        for (int i = 0; i < size; i++) {
            types[i] = data.getShort(position);
            position += SizeOf.USHORT;
        }
        return types;
    }

    public short[] interfaceTypeIndicesFromClassDef(ClassDef classDef) {
        int position = classDef.off;
        position += SizeOf.UINT;  // type
        position += SizeOf.UINT;  // accessFlags
        position += SizeOf.UINT;  // superType
        int interfacesOffset = data.getInt(position);
        if (interfacesOffset == 0) {
            return EMPTY_SHORT_ARRAY;
        }
        position = interfacesOffset;
        int size = data.getInt(position);
        if (size <= 0) {
            throw new AssertionError("Unexpected interfaces list size: " + size);
        }
        position += SizeOf.UINT;
        short[] types = new short[size];
        for (int i = 0; i < size; i++) {
            types[i] = data.getShort(position);
            position += SizeOf.USHORT;
        }
        return types;
    }

    public final class Section extends DexDataBuffer {
        private final String name;

        private Section(String name, ByteBuffer data) {
            super(data);
            this.name = name;
        }

        /**
         * @inheritDoc
         */
        @Override
        public StringData readStringData() {
            ensureFourBytesAligned(tableOfContents.stringDatas, false);
            return super.readStringData();
        }

        /**
         * @inheritDoc
         */
        @Override
        public TypeList readTypeList() {
            ensureFourBytesAligned(tableOfContents.typeLists, false);
            return super.readTypeList();
        }

        /**
         * @inheritDoc
         */
        @Override
        public FieldId readFieldId() {
            ensureFourBytesAligned(tableOfContents.fieldIds, false);
            return super.readFieldId();
        }

        /**
         * @inheritDoc
         */
        @Override
        public MethodId readMethodId() {
            ensureFourBytesAligned(tableOfContents.methodIds, false);
            return super.readMethodId();
        }

        /**
         * @inheritDoc
         */
        @Override
        public ProtoId readProtoId() {
            ensureFourBytesAligned(tableOfContents.protoIds, false);
            return super.readProtoId();
        }

        /**
         * @inheritDoc
         */
        @Override
        public ClassDef readClassDef() {
            ensureFourBytesAligned(tableOfContents.classDefs, false);
            return super.readClassDef();
        }

        /**
         * @inheritDoc
         */
        @Override
        public Code readCode() {
            ensureFourBytesAligned(tableOfContents.codes, false);
            return super.readCode();
        }

        /**
         * @inheritDoc
         */
        @Override
        public DebugInfoItem readDebugInfoItem() {
            ensureFourBytesAligned(tableOfContents.debugInfos, false);
            return super.readDebugInfoItem();
        }

        /**
         * @inheritDoc
         */
        @Override
        public ClassData readClassData() {
            ensureFourBytesAligned(tableOfContents.classDatas, false);
            return super.readClassData();
        }

        /**
         * @inheritDoc
         */
        @Override
        public Annotation readAnnotation() {
            ensureFourBytesAligned(tableOfContents.annotations, false);
            return super.readAnnotation();
        }

        /**
         * @inheritDoc
         */
        @Override
        public AnnotationSet readAnnotationSet() {
            ensureFourBytesAligned(tableOfContents.annotationSets, false);
            return super.readAnnotationSet();
        }

        /**
         * @inheritDoc
         */
        @Override
        public AnnotationSetRefList readAnnotationSetRefList() {
            ensureFourBytesAligned(tableOfContents.annotationSetRefLists, false);
            return super.readAnnotationSetRefList();
        }

        /**
         * @inheritDoc
         */
        @Override
        public AnnotationsDirectory readAnnotationsDirectory() {
            ensureFourBytesAligned(tableOfContents.annotationsDirectories, false);
            return super.readAnnotationsDirectory();
        }

        /**
         * @inheritDoc
         */
        @Override
        public EncodedValue readEncodedArray() {
            ensureFourBytesAligned(tableOfContents.encodedArrays, false);
            return super.readEncodedArray();
        }

        private void ensureFourBytesAligned(TableOfContents.Section tocSec, boolean isFillWithZero) {
            if (tocSec.isElementFourByteAligned) {
                if (isFillWithZero) {
                    alignToFourBytesWithZeroFill();
                } else {
                    alignToFourBytes();
                }
            }
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeStringData(StringData stringData) {
            ensureFourBytesAligned(tableOfContents.stringDatas, true);
            return super.writeStringData(stringData);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeTypeList(TypeList typeList) {
            ensureFourBytesAligned(tableOfContents.typeLists, true);
            return super.writeTypeList(typeList);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeFieldId(FieldId fieldId) {
            ensureFourBytesAligned(tableOfContents.fieldIds, true);
            return super.writeFieldId(fieldId);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeMethodId(MethodId methodId) {
            ensureFourBytesAligned(tableOfContents.methodIds, true);
            return super.writeMethodId(methodId);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeProtoId(ProtoId protoId) {
            ensureFourBytesAligned(tableOfContents.protoIds, true);
            return super.writeProtoId(protoId);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeClassDef(ClassDef classDef) {
            ensureFourBytesAligned(tableOfContents.classDefs, true);
            return super.writeClassDef(classDef);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeCode(Code code) {
            ensureFourBytesAligned(tableOfContents.codes, true);
            return super.writeCode(code);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeDebugInfoItem(DebugInfoItem debugInfoItem) {
            ensureFourBytesAligned(tableOfContents.debugInfos, true);
            return super.writeDebugInfoItem(debugInfoItem);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeClassData(ClassData classData) {
            ensureFourBytesAligned(tableOfContents.classDatas, true);
            return super.writeClassData(classData);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeAnnotation(Annotation annotation) {
            ensureFourBytesAligned(tableOfContents.annotations, true);
            return super.writeAnnotation(annotation);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeAnnotationSet(AnnotationSet annotationSet) {
            ensureFourBytesAligned(tableOfContents.annotationSets, true);
            return super.writeAnnotationSet(annotationSet);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeAnnotationSetRefList(AnnotationSetRefList annotationSetRefList) {
            ensureFourBytesAligned(tableOfContents.annotationSetRefLists, true);
            return super.writeAnnotationSetRefList(annotationSetRefList);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeAnnotationsDirectory(AnnotationsDirectory annotationsDirectory) {
            ensureFourBytesAligned(tableOfContents.annotationsDirectories, true);
            return super.writeAnnotationsDirectory(annotationsDirectory);
        }

        /**
         * @inheritDoc
         */
        @Override
        public int writeEncodedArray(EncodedValue encodedValue) {
            ensureFourBytesAligned(tableOfContents.encodedArrays, true);
            return super.writeEncodedArray(encodedValue);
        }
    }

    private final class StringTable extends AbstractList<String> implements RandomAccess {
        @Override public String get(int index) {
            checkBounds(index, tableOfContents.stringIds.size);
            int stringOff = openSection(tableOfContents.stringIds.off + (index * SizeOf.STRING_ID_ITEM)).readInt();
            return openSection(stringOff).readStringData().value;
        }
        @Override public int size() {
            return tableOfContents.stringIds.size;
        }
    }

    private final class TypeIndexToDescriptorIndexTable extends AbstractList<Integer>
            implements RandomAccess {
        @Override public Integer get(int index) {
            return descriptorIndexFromTypeIndex(index);
        }
        @Override public int size() {
            return tableOfContents.typeIds.size;
        }
    }

    private final class TypeIndexToDescriptorTable extends AbstractList<String>
            implements RandomAccess {
        @Override public String get(int index) {
            return strings.get(descriptorIndexFromTypeIndex(index));
        }
        @Override public int size() {
            return tableOfContents.typeIds.size;
        }
    }

    private final class ProtoIdTable extends AbstractList<ProtoId> implements RandomAccess {
        @Override public ProtoId get(int index) {
            checkBounds(index, tableOfContents.protoIds.size);
            return openSection(tableOfContents.protoIds.off + (SizeOf.PROTO_ID_ITEM * index))
                    .readProtoId();
        }
        @Override public int size() {
            return tableOfContents.protoIds.size;
        }
    }

    private final class FieldIdTable extends AbstractList<FieldId> implements RandomAccess {
        @Override public FieldId get(int index) {
            checkBounds(index, tableOfContents.fieldIds.size);
            return openSection(tableOfContents.fieldIds.off + (SizeOf.MEMBER_ID_ITEM * index))
                    .readFieldId();
        }
        @Override public int size() {
            return tableOfContents.fieldIds.size;
        }
    }

    private final class MethodIdTable extends AbstractList<MethodId> implements RandomAccess {
        @Override public MethodId get(int index) {
            checkBounds(index, tableOfContents.methodIds.size);
            return openSection(tableOfContents.methodIds.off + (SizeOf.MEMBER_ID_ITEM * index))
                    .readMethodId();
        }
        @Override public int size() {
            return tableOfContents.methodIds.size;
        }
    }

    private final class ClassDefTable extends AbstractList<ClassDef> implements RandomAccess {
        @Override
        public ClassDef get(int index) {
            checkBounds(index, tableOfContents.classDefs.size);
            return openSection(tableOfContents.classDefs.off + (SizeOf.CLASS_DEF_ITEM * index))
                    .readClassDef();
        }

        @Override
        public int size() {
            return tableOfContents.classDefs.size;
        }
    }

    private final class ClassDefIterator implements Iterator<ClassDef> {
        private final Section in = openSection(tableOfContents.classDefs);
        private int count = 0;

        @Override
        public boolean hasNext() {
            return count < tableOfContents.classDefs.size;
        }
        @Override
        public ClassDef next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            count++;
            return in.readClassDef();
        }
        @Override
            public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class ClassDefIterable implements Iterable<ClassDef> {
        public Iterator<ClassDef> iterator() {
            return !tableOfContents.classDefs.exists()
               ? Collections.<ClassDef>emptySet().iterator()
               : new ClassDefIterator();
        }
    }
}
