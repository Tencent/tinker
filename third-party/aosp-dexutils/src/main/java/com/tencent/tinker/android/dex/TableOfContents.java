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
 *
 * Modifications by tomystang:
 * 1. Define section type ID as constants.
 * 2. Add owner field so that we can trace the dex file that holds it easily.
 * 3. If a section is not exists, we write 0 as its offset into map section instead of -1.
 * 4. Add defination of {@code SectionItem}
 * 5. Fix {@method Section.exists} so that it wouldn't return false when pass header section in.
 */
public final class TableOfContents {
    public static final short SECTION_TYPE_HEADER                 = 0x0000;
    public static final short SECTION_TYPE_STRINGIDS              = 0x0001;
    public static final short SECTION_TYPE_TYPEIDS                = 0x0002;
    public static final short SECTION_TYPE_PROTOIDS               = 0x0003;
    public static final short SECTION_TYPE_FIELDIDS               = 0x0004;
    public static final short SECTION_TYPE_METHODIDS              = 0x0005;
    public static final short SECTION_TYPE_CLASSDEFS              = 0x0006;
    public static final short SECTION_TYPE_MAPLIST                = 0x1000;
    public static final short SECTION_TYPE_TYPELISTS              = 0x1001;
    public static final short SECTION_TYPE_ANNOTATIONSETREFLISTS  = 0x1002;
    public static final short SECTION_TYPE_ANNOTATIONSETS         = 0x1003;
    public static final short SECTION_TYPE_CLASSDATA              = 0x2000;
    public static final short SECTION_TYPE_CODES                  = 0x2001;
    public static final short SECTION_TYPE_STRINGDATAS            = 0x2002;
    public static final short SECTION_TYPE_DEBUGINFOS             = 0x2003;
    public static final short SECTION_TYPE_ANNOTATIONS            = 0x2004;
    public static final short SECTION_TYPE_ENCODEDARRAYS          = 0x2005;
    public static final short SECTION_TYPE_ANNOTATIONSDIRECTORIES = 0x2006;

    public final Dex       owner;
    public final Section   header;
    public final Section   stringIds;
    public final Section   typeIds;
    public final Section   protoIds;
    public final Section   fieldIds;
    public final Section   methodIds;
    public final Section   classDefs;
    public final Section   mapList;
    public final Section   typeLists;
    public final Section   annotationSetRefLists;
    public final Section   annotationSets;
    public final Section   classDatas;
    public final Section   codes;
    public final Section   stringDatas;
    public final Section   debugInfos;
    public final Section   annotations;
    public final Section   encodedArrays;
    public final Section   annotationsDirectories;
    public final Section[] sections;

    public int    checksum;
    public byte[] signature;
    public int fileSize = 0;
    public int linkSize = 0;
    public int linkOff  = Section.INT_VALUE_UNSET;
    public int dataSize = 0;
    public int dataOff  = Section.INT_VALUE_UNSET;

    public TableOfContents(Dex owner) {
        this.owner = owner;
        this.signature = new byte[20];

        this.header = new Section(this.owner, SECTION_TYPE_HEADER, true);
        this.stringIds = new Section(this.owner, SECTION_TYPE_STRINGIDS, true);
        this.typeIds = new Section(this.owner, SECTION_TYPE_TYPEIDS, true);
        this.protoIds = new Section(this.owner, SECTION_TYPE_PROTOIDS, true);
        this.fieldIds = new Section(this.owner, SECTION_TYPE_FIELDIDS, true);
        this.methodIds = new Section(this.owner, SECTION_TYPE_METHODIDS, true);
        this.classDefs = new Section(this.owner, SECTION_TYPE_CLASSDEFS, true);
        this.mapList = new Section(this.owner, SECTION_TYPE_MAPLIST, true);
        this.typeLists = new Section(this.owner, SECTION_TYPE_TYPELISTS, true);
        this.annotationSetRefLists = new Section(this.owner, SECTION_TYPE_ANNOTATIONSETREFLISTS, true);
        this.annotationSets = new Section(this.owner, SECTION_TYPE_ANNOTATIONSETS, true);
        this.classDatas = new Section(this.owner, SECTION_TYPE_CLASSDATA, false);
        this.codes = new Section(this.owner, SECTION_TYPE_CODES, true);
        this.stringDatas = new Section(this.owner, SECTION_TYPE_STRINGDATAS, false);
        this.debugInfos = new Section(this.owner, SECTION_TYPE_DEBUGINFOS, false);
        this.annotations = new Section(this.owner, SECTION_TYPE_ANNOTATIONS, false);
        this.encodedArrays = new Section(this.owner, SECTION_TYPE_ENCODEDARRAYS, false);
        this.annotationsDirectories = new Section(this.owner, SECTION_TYPE_ANNOTATIONSDIRECTORIES, true);
        this.sections = new Section[]{
            header, stringIds, typeIds, protoIds, fieldIds, methodIds, classDefs, mapList,
            typeLists, annotationSetRefLists, annotationSets, classDatas, codes, stringDatas,
            debugInfos, annotations, encodedArrays, annotationsDirectories
        };
    }

    public void readFrom(Dex dex) throws IOException {
        readHeader(dex.open(null));
        // Just for preventing exception when invoking dex.open below.
        // And mapList.size here will be updated after readMap was called.
        mapList.size = 1;
        readMap(dex.open(mapList));
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
                || (section.off != Section.INT_VALUE_UNSET && section.off != offset)) {
                throw new DexException("Unexpected map value for 0x" + Integer.toHexString(type));
            }

            section.size = size;
            section.off = offset;

            if (previous != null && previous.off > section.off) {
                throw new DexException("Map is unsorted at " + previous + ", " + section);
            }

            previous = section;
        }
        Arrays.sort(sections);
    }

    public void computeSizesFromOffsets() {
        int end = fileSize;
        for (int i = sections.length - 1; i >= 0; i--) {
            Section section = sections[i];
            if (!section.exists()) {
                continue;
            }
            if (section.off > end) {
                throw new DexException("Map is unsorted at " + section);
            }
            section.byteCount = end - section.off;
            end = section.off;
        }
    }

    public Section getSection(short type) {
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
        out.writeInt((linkOff == Section.INT_VALUE_UNSET ? 0 : linkOff));
        out.writeInt((mapList.off == Section.INT_VALUE_UNSET ? 0 : mapList.off));
        out.writeInt(stringIds.size);
        out.writeInt((stringIds.off == Section.INT_VALUE_UNSET ? 0 : stringIds.off));
        out.writeInt(typeIds.size);
        out.writeInt((typeIds.off == Section.INT_VALUE_UNSET ? 0 : typeIds.off));
        out.writeInt(protoIds.size);
        out.writeInt((protoIds.off == Section.INT_VALUE_UNSET ? 0 : protoIds.off));
        out.writeInt(fieldIds.size);
        out.writeInt((fieldIds.off == Section.INT_VALUE_UNSET ? 0 : fieldIds.off));
        out.writeInt(methodIds.size);
        out.writeInt((methodIds.off == Section.INT_VALUE_UNSET ? 0 : methodIds.off));
        out.writeInt(classDefs.size);
        out.writeInt((classDefs.off == Section.INT_VALUE_UNSET ? 0 : classDefs.off));
        out.writeInt(dataSize);
        out.writeInt((dataOff == Section.INT_VALUE_UNSET ? 0 : dataOff));
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
        public static final int INT_VALUE_UNSET = 0;

        public final Dex     owner;
        public final String  name;
        public final short   type;
        public final boolean isFourByteAlign;
        public int size      = 0;
        public int off       = INT_VALUE_UNSET;
        public int byteCount = 0;

        public Section(Dex owner, int type, boolean isFourByteAlign) {
            this.owner = owner;
            this.isFourByteAlign = isFourByteAlign;
            this.name = "";
            this.type = (short) type;
        }

        public Section(Dex owner, String name, int type, boolean isFourByteAlign) {
            this.owner = owner;
            this.isFourByteAlign = isFourByteAlign;
            this.name = name;
            this.type = (short) type;
        }

        public boolean exists() {
            return off > 0 || type == SECTION_TYPE_HEADER;
        }

        public int compareTo(Section section) {
            if (off != section.off) {
                return Integer.compare(off, section.off);
            }
            if (type != section.type) {
                return Integer.compare(type, section.type);
            }
            return 0;
        }

        @Override
        public String toString() {
            return String.format("Section[type=%#x,off=%#x,size=%#x]", type, off, size);
        }

        public abstract static class SectionItem<T> extends Item<T> {
            public final int off;
            public Section owner = null;

            public SectionItem(Section owner, int offset) {
                this.owner = owner;
                this.off = offset;
            }

            public abstract T clone(Section newOwner, int newOffset);

            public int getRelativeOffset() {
                if (owner == null || owner.off == TableOfContents.Section.INT_VALUE_UNSET) {
                    throw new DexException("Try to get relative offset before setting owner section.");
                }
                return this.off - owner.off;
            }
        }
    }
}
