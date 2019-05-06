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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * The file header and map.
 */
public final class TableOfContents {
    public static final short SECTION_TYPE_HEADER = 0x0000;
    public static final short SECTION_TYPE_STRINGIDS = 0x0001;
    public static final short SECTION_TYPE_TYPEIDS = 0x0002;
    public static final short SECTION_TYPE_PROTOIDS = 0x0003;
    public static final short SECTION_TYPE_FIELDIDS = 0x0004;
    public static final short SECTION_TYPE_METHODIDS = 0x0005;
    public static final short SECTION_TYPE_CLASSDEFS = 0x0006;
    public static final short SECTION_TYPE_MAPLIST = 0x1000;
    public static final short SECTION_TYPE_TYPELISTS = 0x1001;
    public static final short SECTION_TYPE_ANNOTATIONSETREFLISTS = 0x1002;
    public static final short SECTION_TYPE_ANNOTATIONSETS = 0x1003;
    public static final short SECTION_TYPE_CLASSDATA = 0x2000;
    public static final short SECTION_TYPE_CODES = 0x2001;
    public static final short SECTION_TYPE_STRINGDATAS = 0x2002;
    public static final short SECTION_TYPE_DEBUGINFOS = 0x2003;
    public static final short SECTION_TYPE_ANNOTATIONS = 0x2004;
    public static final short SECTION_TYPE_ENCODEDARRAYS = 0x2005;
    public static final short SECTION_TYPE_ANNOTATIONSDIRECTORIES = 0x2006;

    public final Section header = new Section(SECTION_TYPE_HEADER, true);
    public final Section stringIds = new Section(SECTION_TYPE_STRINGIDS, true);
    public final Section typeIds = new Section(SECTION_TYPE_TYPEIDS, true);
    public final Section protoIds = new Section(SECTION_TYPE_PROTOIDS, true);
    public final Section fieldIds = new Section(SECTION_TYPE_FIELDIDS, true);
    public final Section methodIds = new Section(SECTION_TYPE_METHODIDS, true);
    public final Section classDefs = new Section(SECTION_TYPE_CLASSDEFS, true);
    public final Section mapList = new Section(SECTION_TYPE_MAPLIST, true);
    public final Section typeLists = new Section(SECTION_TYPE_TYPELISTS, true);
    public final Section annotationSetRefLists = new Section(SECTION_TYPE_ANNOTATIONSETREFLISTS, true);
    public final Section annotationSets = new Section(SECTION_TYPE_ANNOTATIONSETS, true);
    public final Section classDatas = new Section(SECTION_TYPE_CLASSDATA, false);
    public final Section codes = new Section(SECTION_TYPE_CODES, true);
    public final Section stringDatas = new Section(SECTION_TYPE_STRINGDATAS, false);
    public final Section debugInfos = new Section(SECTION_TYPE_DEBUGINFOS, false);
    public final Section annotations = new Section(SECTION_TYPE_ANNOTATIONS, false);
    public final Section encodedArrays = new Section(SECTION_TYPE_ENCODEDARRAYS, false);
    public final Section annotationsDirectories = new Section(SECTION_TYPE_ANNOTATIONSDIRECTORIES, true);
    public final Section[] sections = {
            header, stringIds, typeIds, protoIds, fieldIds, methodIds, classDefs, mapList,
            typeLists, annotationSetRefLists, annotationSets, classDatas, codes, stringDatas,
            debugInfos, annotations, encodedArrays, annotationsDirectories
    };

    public int checksum;
    public byte[] signature;
    public int fileSize;
    public int linkSize;
    public int linkOff;
    public int dataSize;
    public int dataOff;

    public TableOfContents() {
        signature = new byte[20];
    }

    public Section getSectionByType(int type) {
        switch (type) {
            case SECTION_TYPE_HEADER: {
                return header;
            }
            case SECTION_TYPE_STRINGIDS: {
                return stringIds;
            }
            case SECTION_TYPE_TYPEIDS: {
                return typeIds;
            }
            case SECTION_TYPE_PROTOIDS: {
                return protoIds;
            }
            case SECTION_TYPE_FIELDIDS: {
                return fieldIds;
            }
            case SECTION_TYPE_METHODIDS: {
                return methodIds;
            }
            case SECTION_TYPE_CLASSDEFS: {
                return classDefs;
            }
            case SECTION_TYPE_MAPLIST: {
                return mapList;
            }
            case SECTION_TYPE_TYPELISTS: {
                return typeLists;
            }
            case SECTION_TYPE_ANNOTATIONSETREFLISTS: {
                return annotationSetRefLists;
            }
            case SECTION_TYPE_ANNOTATIONSETS: {
                return annotationSets;
            }
            case SECTION_TYPE_CLASSDATA: {
                return classDatas;
            }
            case SECTION_TYPE_CODES: {
                return codes;
            }
            case SECTION_TYPE_STRINGDATAS: {
                return stringDatas;
            }
            case SECTION_TYPE_DEBUGINFOS: {
                return debugInfos;
            }
            case SECTION_TYPE_ANNOTATIONS: {
                return annotations;
            }
            case SECTION_TYPE_ENCODEDARRAYS: {
                return encodedArrays;
            }
            case SECTION_TYPE_ANNOTATIONSDIRECTORIES: {
                return annotationsDirectories;
            }
            default: {
                throw new IllegalArgumentException("unknown section type: " + type);
            }
        }
    }

    public void readFrom(Dex dex) throws IOException {
        readHeader(dex.openSection(header));
        // special case, since mapList.byteCount is available only after
        // computeSizesFromOffsets() was invoked, so here we can't use
        // dex.openSection(mapList) to get dex section. Or
        // an {@code java.nio.BufferUnderflowException} will be thrown.
        readMap(dex.openSection(mapList.off));
        computeSizesFromOffsets();
    }

    private void readHeader(Dex.Section headerIn) throws UnsupportedEncodingException {
        byte[] magic = headerIn.readByteArray(8);
        int apiTarget = DexFormat.magicToApi(magic);

        if (apiTarget != DexFormat.API_NO_EXTENDED_OPCODES) {
            throw new DexException("Unexpected magic: " + Arrays.toString(magic));
        }

        checksum = headerIn.readInt();
        signature = headerIn.readByteArray(20);
        fileSize = headerIn.readInt();
        int headerSize = headerIn.readInt();
        if (headerSize != SizeOf.HEADER_ITEM) {
            throw new DexException("Unexpected header: 0x" + Integer.toHexString(headerSize));
        }
        int endianTag = headerIn.readInt();
        if (endianTag != DexFormat.ENDIAN_TAG) {
            throw new DexException("Unexpected endian tag: 0x" + Integer.toHexString(endianTag));
        }
        linkSize = headerIn.readInt();
        linkOff = headerIn.readInt();
        mapList.off = headerIn.readInt();
        if (mapList.off == 0) {
            throw new DexException("Cannot merge dex files that do not contain a map");
        }
        stringIds.size = headerIn.readInt();
        stringIds.off = headerIn.readInt();
        typeIds.size = headerIn.readInt();
        typeIds.off = headerIn.readInt();
        protoIds.size = headerIn.readInt();
        protoIds.off = headerIn.readInt();
        fieldIds.size = headerIn.readInt();
        fieldIds.off = headerIn.readInt();
        methodIds.size = headerIn.readInt();
        methodIds.off = headerIn.readInt();
        classDefs.size = headerIn.readInt();
        classDefs.off = headerIn.readInt();
        dataSize = headerIn.readInt();
        dataOff = headerIn.readInt();
    }

    private void readMap(Dex.Section in) throws IOException {
        int mapSize = in.readInt();
        Section previous = null;
        for (int i = 0; i < mapSize; i++) {
            short type = in.readShort();
            in.readShort(); // unused
            Section section = getSection(type);
            int size = in.readInt();
            int offset = in.readInt();

            if ((section.size != 0 && section.size != size)
                    || (section.off != Section.UNDEF_OFFSET && section.off != offset)) {
                throw new DexException("Unexpected map value for 0x" + Integer.toHexString(type));
            }

            section.size = size;
            section.off = offset;

            if (previous != null && previous.off > section.off) {
                throw new DexException("Map is unsorted at " + previous + ", " + section);
            }

            previous = section;
        }

        header.off = 0;

        Arrays.sort(sections);

        // Skip header section, since its offset must be zero.
        for (int i = 1; i < sections.length; ++i) {
            if (sections[i].off == Section.UNDEF_OFFSET) {
                sections[i].off = sections[i - 1].off;
            }
        }
    }

    public void computeSizesFromOffsets() {
        int end = fileSize;
        for (int i = sections.length - 1; i >= 0; i--) {
            Section section = sections[i];
            if (section.off == Section.UNDEF_OFFSET) {
                continue;
            }
            if (section.off > end) {
                throw new DexException("Map is unsorted at " + section);
            }
            section.byteCount = end - section.off;
            end = section.off;
        }

        dataOff = header.byteCount
                + stringIds.byteCount
                + typeIds.byteCount
                + protoIds.byteCount
                + fieldIds.byteCount
                + methodIds.byteCount
                + classDefs.byteCount;

        dataSize = fileSize - dataOff;
    }

    private Section getSection(short type) {
        for (Section section : sections) {
            if (section.type == type) {
                return section;
            }
        }
        throw new IllegalArgumentException("No such map item: " + type);
    }

    public void writeHeader(Dex.Section out) throws IOException {
        out.write(DexFormat.apiToMagic(DexFormat.API_NO_EXTENDED_OPCODES).getBytes("UTF-8"));
        out.writeInt(checksum);
        out.write(signature);
        out.writeInt(fileSize);
        out.writeInt(SizeOf.HEADER_ITEM);
        out.writeInt(DexFormat.ENDIAN_TAG);
        out.writeInt(linkSize);
        out.writeInt(linkOff);
        out.writeInt(mapList.off);
        out.writeInt(stringIds.size);
        out.writeInt((stringIds.exists() ? stringIds.off : 0));
        out.writeInt(typeIds.size);
        out.writeInt((typeIds.exists() ? typeIds.off : 0));
        out.writeInt(protoIds.size);
        out.writeInt((protoIds.exists() ? protoIds.off : 0));
        out.writeInt(fieldIds.size);
        out.writeInt((fieldIds.exists() ? fieldIds.off : 0));
        out.writeInt(methodIds.size);
        out.writeInt((methodIds.exists() ? methodIds.off : 0));
        out.writeInt(classDefs.size);
        out.writeInt((classDefs.exists() ? classDefs.off : 0));
        out.writeInt(dataSize);
        out.writeInt(dataOff);
    }

    public void writeMap(Dex.Section out) throws IOException {
        int count = 0;
        for (Section section : sections) {
            if (section.exists()) {
                count++;
            }
        }

        out.writeInt(count);
        for (Section section : sections) {
            if (section.exists()) {
                out.writeShort(section.type);
                out.writeShort((short) 0);
                out.writeInt(section.size);
                out.writeInt(section.off);
            }
        }
    }

    public static class Section implements Comparable<Section> {
        public static final int UNDEF_INDEX = -1;
        public static final int UNDEF_OFFSET = -1;
        public final short type;
        public boolean isElementFourByteAligned;
        public int size = 0;
        public int off = UNDEF_OFFSET;
        public int byteCount = 0;

        public Section(int type, boolean isElementFourByteAligned) {
            this.type = (short) type;
            this.isElementFourByteAligned = isElementFourByteAligned;
            if (type == SECTION_TYPE_HEADER) {
                off = 0;
                size = 1;
                byteCount = SizeOf.HEADER_ITEM;
            } else
            if (type == SECTION_TYPE_MAPLIST) {
                size = 1;
            }
        }

        public boolean exists() {
            return size > 0;
        }

        private int remapTypeOrderId(int type) {
            switch (type) {
                case SECTION_TYPE_HEADER: {
                    return 0;
                }
                case SECTION_TYPE_STRINGIDS: {
                    return 1;
                }
                case SECTION_TYPE_TYPEIDS: {
                    return 2;
                }
                case SECTION_TYPE_PROTOIDS: {
                    return 3;
                }
                case SECTION_TYPE_FIELDIDS: {
                    return 4;
                }
                case SECTION_TYPE_METHODIDS: {
                    return 5;
                }
                case SECTION_TYPE_CLASSDEFS: {
                    return 6;
                }
                case SECTION_TYPE_STRINGDATAS: {
                    return 7;
                }
                case SECTION_TYPE_TYPELISTS: {
                    return 8;
                }
                case SECTION_TYPE_ANNOTATIONS: {
                    return 9;
                }
                case SECTION_TYPE_ANNOTATIONSETS: {
                    return 10;
                }
                case SECTION_TYPE_ANNOTATIONSETREFLISTS: {
                    return 11;
                }
                case SECTION_TYPE_ANNOTATIONSDIRECTORIES: {
                    return 12;
                }
                case SECTION_TYPE_DEBUGINFOS: {
                    return 13;
                }
                case SECTION_TYPE_CODES: {
                    return 14;
                }
                case SECTION_TYPE_CLASSDATA: {
                    return 15;
                }
                case SECTION_TYPE_ENCODEDARRAYS: {
                    return 16;
                }
                case SECTION_TYPE_MAPLIST: {
                    return 17;
                }
                default: {
                    throw new IllegalArgumentException("unknown section type: " + type);
                }
            }
        }

        public int compareTo(Section section) {
            if (off != section.off) {
                return off < section.off ? -1 : 1;
            }

            int remappedType = remapTypeOrderId(type);
            int otherRemappedType = remapTypeOrderId(section.type);

            if (remappedType != otherRemappedType) {
                return (remappedType < otherRemappedType ? -1 : 1);
            }

            return 0;
        }

        @Override
        public String toString() {
            return String.format("Section[type=%#x,off=%#x,size=%#x]", type, off, size);
        }

        public static abstract class Item<T> implements Comparable<T> {
            public int off;

            public Item(int off) {
                this.off = off;
            }

            @Override
            public int hashCode() {
                return super.hashCode();
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean equals(Object obj) {
                return compareTo((T) obj) == 0;
            }

            public abstract int byteCountInDex();
        }
    }
}
