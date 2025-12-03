/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader.shareutil;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tangyinsheng on 2017/3/13.
 */

public class ShareElfFile implements Closeable {
    public static final int FILE_TYPE_OTHERS = -1;
    public static final int FILE_TYPE_ODEX = 0;
    public static final int FILE_TYPE_ELF = 1;

    private final FileInputStream fis;
    private final Map<String, SectionHeader> sectionNameToHeaderMap = new HashMap<>();
    public ElfHeader elfHeader = null;
    public ProgramHeader[] programHeaders = null;
    public SectionHeader[] sectionHeaders = null;

    public ShareElfFile(File file) throws IOException {
        fis = new FileInputStream(file);
        final FileChannel channel = fis.getChannel();

        elfHeader = new ElfHeader(channel);

        final ByteBuffer headerBuffer = ByteBuffer.allocate(128);

        headerBuffer.limit(elfHeader.ePhEntSize);
        headerBuffer.order(elfHeader.eIndent[ElfHeader.EI_DATA] == ElfHeader.ELFDATA2LSB ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        channel.position(elfHeader.ePhOff);
        programHeaders = new ProgramHeader[elfHeader.ePhNum];
        for (int i = 0; i < programHeaders.length; ++i) {
            readUntilLimit(channel, headerBuffer, "failed to read phdr.");
            programHeaders[i] = new ProgramHeader(headerBuffer, elfHeader.eIndent[ElfHeader.EI_CLASS]);
        }

        channel.position(elfHeader.eShOff);
        headerBuffer.limit(elfHeader.eShEntSize);
        sectionHeaders = new SectionHeader[elfHeader.eShNum];
        for (int i = 0; i < sectionHeaders.length; ++i) {
            readUntilLimit(channel, headerBuffer, "failed to read shdr.");
            sectionHeaders[i] = new SectionHeader(headerBuffer, elfHeader.eIndent[ElfHeader.EI_CLASS]);
        }

        if (elfHeader.eShStrNdx > 0) {
            final SectionHeader shStrTabSectionHeader = sectionHeaders[elfHeader.eShStrNdx];
            final ByteBuffer shStrTab = getSection(shStrTabSectionHeader);
            for (SectionHeader shdr : sectionHeaders) {
                shStrTab.position(shdr.shName);
                shdr.shNameStr = readCString(shStrTab);
                sectionNameToHeaderMap.put(shdr.shNameStr, shdr);
            }
        }
    }

    private static void assertInRange(int b, int lb, int ub, String errMsg) throws IOException {
        if (b < lb || b > ub) {
            throw new IOException(errMsg);
        }
    }

    public static int getFileTypeByMagic(File file) throws IOException {
        InputStream is = null;
        try {
            final byte[] magicBuf = new byte[4];
            is = new FileInputStream(file);
            is.read(magicBuf);
            if (magicBuf[0] == 'd' && magicBuf[1] == 'e' && magicBuf[2] == 'y' && magicBuf[3] == '\n') {
                return FILE_TYPE_ODEX;
            } else if (magicBuf[0] == 0x7F && magicBuf[1] == 'E' && magicBuf[2] == 'L' && magicBuf[3] == 'F') {
                return FILE_TYPE_ELF;
            } else {
                return FILE_TYPE_OTHERS;
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable thr) {
                    // Ignored.
                }
            }
        }
    }

    public static void readUntilLimit(FileChannel channel, ByteBuffer bufferOut, String errMsg) throws IOException {
        bufferOut.rewind();
        int bytesRead = channel.read(bufferOut);
        if (bytesRead != bufferOut.limit()) {
            throw new IOException(errMsg + " Rest bytes insufficient, expect to read "
                    + bufferOut.limit() + " bytes but only "
                    + bytesRead + " bytes were read.");
        }
        bufferOut.flip();
    }

    public static String readCString(ByteBuffer buffer) {
        final byte[] rawBuffer = buffer.array();
        int begin = buffer.position();
        while (buffer.hasRemaining() && rawBuffer[buffer.position()] != 0) {
            buffer.position(buffer.position() + 1);
        }
        // Move to the start of next cstring.
        buffer.position(buffer.position() + 1);
        return new String(rawBuffer, begin, buffer.position() - begin - 1, Charset.forName("ASCII"));
    }

    public FileChannel getChannel() {
        return fis.getChannel();
    }

    public boolean is32BitElf() {
        return (elfHeader.eIndent[ElfHeader.EI_CLASS] == ElfHeader.ELFCLASS32);
    }

    public ByteOrder getDataOrder() {
        return (elfHeader.eIndent[ElfHeader.EI_DATA] == ElfHeader.ELFDATA2LSB ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }

    public SectionHeader getSectionHeaderByName(String name) {
        return sectionNameToHeaderMap.get(name);
    }

    public ByteBuffer getSection(SectionHeader sectionHeader) throws IOException {
        final ByteBuffer result = ByteBuffer.allocate((int) sectionHeader.shSize);
        fis.getChannel().position(sectionHeader.shOffset);
        readUntilLimit(fis.getChannel(), result, "failed to read section: " + sectionHeader.shNameStr);
        return result;
    }

    public ByteBuffer getSegment(ProgramHeader programHeader) throws IOException {
        final ByteBuffer result = ByteBuffer.allocate((int) programHeader.pFileSize);
        fis.getChannel().position(programHeader.pOffset);
        readUntilLimit(fis.getChannel(), result, "failed to read segment (type: " + programHeader.pType + ").");
        return result;
    }

    @Override
    public void close() throws IOException {
        fis.close();
        sectionNameToHeaderMap.clear();
        programHeaders = null;
        sectionHeaders = null;
    }

    public static class ElfHeader {
        // Elf indent field index.
        public static final int EI_CLASS = 4;
        public static final int EI_DATA = 5;
        public static final int EI_VERSION = 6;
        // Elf classes.
        public static final int ELFCLASS32 = 1;
        public static final int ELFCLASS64 = 2;
        // Elf data encoding.
        public static final int ELFDATA2LSB = 1;
        public static final int ELFDATA2MSB = 2;
        // Elf types.
        public static final int ET_NONE = 0;
        public static final int ET_REL = 1;
        public static final int ET_EXEC = 2;
        public static final int ET_DYN = 3;
        public static final int ET_CORE = 4;
        public static final int ET_LOPROC = 0xff00;
        public static final int ET_HIPROC = 0xffff;
        // Elf indent version.
        public static final int EV_CURRENT = 1;
        private static final int EI_NINDENT = 16;
        public final byte[] eIndent = new byte[EI_NINDENT];
        public final short eType;
        public final short eMachine;
        public final int eVersion;
        public final long eEntry;
        public final long ePhOff;
        public final long eShOff;
        public final int eFlags;
        public final short eEhSize;
        public final short ePhEntSize;
        public final short ePhNum;
        public final short eShEntSize;
        public final short eShNum;
        public final short eShStrNdx;

        private ElfHeader(FileChannel channel) throws IOException {
            channel.position(0);
            channel.read(ByteBuffer.wrap(eIndent));
            if (eIndent[0] != 0x7F || eIndent[1] != 'E' || eIndent[2] != 'L' || eIndent[3] != 'F') {
                throw new IOException(String.format("bad elf magic: %x %x %x %x.", eIndent[0], eIndent[1], eIndent[2], eIndent[3]));
            }

            assertInRange(eIndent[EI_CLASS], ELFCLASS32, ELFCLASS64, "bad elf class: " + eIndent[EI_CLASS]);
            assertInRange(eIndent[EI_DATA], ELFDATA2LSB, ELFDATA2MSB, "bad elf data encoding: " + eIndent[EI_DATA]);

            final ByteBuffer restBuffer = ByteBuffer.allocate(eIndent[EI_CLASS] == ELFCLASS32 ? 36 : 48);
            restBuffer.order(eIndent[EI_DATA] == ELFDATA2LSB ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            readUntilLimit(channel, restBuffer, "failed to read rest part of ehdr.");

            eType = restBuffer.getShort();
            eMachine = restBuffer.getShort();

            eVersion = restBuffer.getInt();
            assertInRange(eVersion, EV_CURRENT, EV_CURRENT, "bad elf version: " + eVersion);

            switch (eIndent[EI_CLASS]) {
                case ELFCLASS32:
                    eEntry = restBuffer.getInt();
                    ePhOff = restBuffer.getInt();
                    eShOff = restBuffer.getInt();
                    break;
                case ELFCLASS64:
                    eEntry = restBuffer.getLong();
                    ePhOff = restBuffer.getLong();
                    eShOff = restBuffer.getLong();
                    break;
                default:
                    throw new IOException("Unexpected elf class: " + eIndent[EI_CLASS]);
            }
            eFlags = restBuffer.getInt();
            eEhSize = restBuffer.getShort();
            ePhEntSize = restBuffer.getShort();
            ePhNum = restBuffer.getShort();
            eShEntSize = restBuffer.getShort();
            eShNum = restBuffer.getShort();
            eShStrNdx = restBuffer.getShort();
        }
    }

    public static class ProgramHeader {
        // Segment types.
        public static final int PT_NULL = 0;
        public static final int PT_LOAD = 1;
        public static final int PT_DYNAMIC = 2;
        public static final int PT_INTERP = 3;
        public static final int PT_NOTE = 4;
        public static final int PT_SHLIB = 5;
        public static final int PT_PHDR = 6;
        public static final int PT_LOPROC = 0x70000000;
        public static final int PT_HIPROC = 0x7fffffff;

        // Segment flags.
        public static final int PF_R = 0x04;
        public static final int PF_W = 0x02;
        public static final int PF_X = 0x01;

        public final int pType;
        public final int pFlags;
        public final long pOffset;
        public final long pVddr;
        public final long pPddr;
        public final long pFileSize;
        public final long pMemSize;
        public final long pAlign;

        private ProgramHeader(ByteBuffer buffer, int elfClass) throws IOException {
            switch (elfClass) {
                case ElfHeader.ELFCLASS32:
                    pType = buffer.getInt();
                    pOffset = buffer.getInt();
                    pVddr = buffer.getInt();
                    pPddr = buffer.getInt();
                    pFileSize = buffer.getInt();
                    pMemSize = buffer.getInt();
                    pFlags = buffer.getInt();
                    pAlign = buffer.getInt();
                    break;
                case ElfHeader.ELFCLASS64:
                    pType = buffer.getInt();
                    pFlags = buffer.getInt();
                    pOffset = buffer.getLong();
                    pVddr = buffer.getLong();
                    pPddr = buffer.getLong();
                    pFileSize = buffer.getLong();
                    pMemSize = buffer.getLong();
                    pAlign = buffer.getLong();
                    break;
                default:
                    throw new IOException("Unexpected elf class: " + elfClass);
            }
        }
    }

    public static class SectionHeader {
        // Special section indexes.
        public static final int SHN_UNDEF = 0;
        public static final int SHN_LORESERVE = 0xff00;
        public static final int SHN_LOPROC = 0xff00;
        public static final int SHN_HIPROC = 0xff1f;
        public static final int SHN_ABS = 0xfff1;
        public static final int SHN_COMMON = 0xfff2;
        public static final int SHN_HIRESERVE = 0xffff;

        // Section types.
        public static final int SHT_NULL = 0;
        public static final int SHT_PROGBITS = 1;
        public static final int SHT_SYMTAB = 2;
        public static final int SHT_STRTAB = 3;
        public static final int SHT_RELA = 4;
        public static final int SHT_HASH = 5;
        public static final int SHT_DYNAMIC = 6;
        public static final int SHT_NOTE = 7;
        public static final int SHT_NOBITS = 8;
        public static final int SHT_REL = 9;
        public static final int SHT_SHLIB = 10;
        public static final int SHT_DYNSYM = 11;
        public static final int SHT_LOPROC = 0x70000000;
        public static final int SHT_HIPROC = 0x7fffffff;
        public static final int SHT_LOUSER = 0x80000000;
        public static final int SHT_HIUSER = 0xffffffff;

        // Section flags.
        public static final int SHF_WRITE = 0x1;
        public static final int SHF_ALLOC = 0x2;
        public static final int SHF_EXECINSTR = 0x4;
        public static final int SHF_MASKPROC = 0xf0000000;

        public final int shName;
        public final int shType;
        public final long shFlags;
        public final long shAddr;
        public final long shOffset;
        public final long shSize;
        public final int shLink;
        public final int shInfo;
        public final long shAddrAlign;
        public final long shEntSize;
        public String shNameStr;

        private SectionHeader(ByteBuffer buffer, int elfClass) throws IOException {
            switch (elfClass) {
                case ElfHeader.ELFCLASS32:
                    shName = buffer.getInt();
                    shType = buffer.getInt();
                    shFlags = buffer.getInt();
                    shAddr = buffer.getInt();
                    shOffset = buffer.getInt();
                    shSize = buffer.getInt();
                    shLink = buffer.getInt();
                    shInfo = buffer.getInt();
                    shAddrAlign = buffer.getInt();
                    shEntSize = buffer.getInt();
                    break;
                case ElfHeader.ELFCLASS64:
                    shName = buffer.getInt();
                    shType = buffer.getInt();
                    shFlags = buffer.getLong();
                    shAddr = buffer.getLong();
                    shOffset = buffer.getLong();
                    shSize = buffer.getLong();
                    shLink = buffer.getInt();
                    shInfo = buffer.getInt();
                    shAddrAlign = buffer.getLong();
                    shEntSize = buffer.getLong();
                    break;
                default:
                    throw new IOException("Unexpected elf class: " + elfClass);
            }
            shNameStr = null;
        }
    }
}
