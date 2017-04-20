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

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

/**
 * Created by tangyinsheng on 2017/3/14.
 */

public final class ShareOatUtil {
    private static final String TAG = "Tinker.OatUtil";

    private ShareOatUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Get instruction set used to generate {@code oatFile}.
     *
     * @param oatFile
     *  the oat file.
     * @return
     *  the instruction used to generate this oat file, if the oat file does not
     *  contain this value, an empty string will be returned.
     *
     * @throws IOException
     *  If anything wrong when parsing the elf format or locating target field in oat header.
     */
    public static String getOatFileInstructionSet(File oatFile) throws Throwable {
        ShareElfFile elfFile = null;
        String result = "";
        try {
            elfFile = new ShareElfFile(oatFile);
            final ShareElfFile.SectionHeader roDataHdr = elfFile.getSectionHeaderByName(".rodata");
            if (roDataHdr == null) {
                throw new IOException("Unable to find .rodata section.");
            }

            final FileChannel channel = elfFile.getChannel();
            channel.position(roDataHdr.shOffset);

            final byte[] oatMagicAndVersion = new byte[8];
            ShareElfFile.readUntilLimit(channel, ByteBuffer.wrap(oatMagicAndVersion), "Failed to read oat magic and version.");

            if (oatMagicAndVersion[0] != 'o'
                    || oatMagicAndVersion[1] != 'a'
                    || oatMagicAndVersion[2] != 't'
                    || oatMagicAndVersion[3] != '\n') {
                throw new IOException(
                        String.format("Bad oat magic: %x %x %x %x",
                                oatMagicAndVersion[0],
                                oatMagicAndVersion[1],
                                oatMagicAndVersion[2],
                                oatMagicAndVersion[3])
                );
            }

            final int versionOffsetFromOatBegin = 4;
            final int versionBytes = 3;

            final String oatVersion = new String(oatMagicAndVersion,
                    versionOffsetFromOatBegin, versionBytes, Charset.forName("ASCII"));
            try {
                Integer.parseInt(oatVersion);
            } catch (NumberFormatException e) {
                throw new IOException("Bad oat version: " + oatVersion);
            }

            ByteBuffer buffer = ByteBuffer.allocate(128);
            buffer.order(elfFile.getDataOrder());
            // TODO This is a risk point, since each oat version may use a different offset.
            // So far it's ok. Perhaps we should use oatVersionNum to judge the right offset in
            // the future.
            final int isaNumOffsetFromOatBegin = 12;
            channel.position(roDataHdr.shOffset + isaNumOffsetFromOatBegin);
            buffer.limit(4);
            ShareElfFile.readUntilLimit(channel, buffer, "Failed to read isa num.");

            int isaNum = buffer.getInt();
            if (isaNum < 0 || isaNum >= InstructionSet.values().length) {
                throw new IOException("Bad isa num: " + isaNum);
            }

            switch (InstructionSet.values()[isaNum]) {
                case kArm:
                case kThumb2:
                    result = "arm";
                    break;
                case kArm64:
                    result = "arm64";
                    break;
                case kX86:
                    result = "x86";
                    break;
                case kX86_64:
                    result = "x86_64";
                    break;
                case kMips:
                    result = "mips";
                    break;
                case kMips64:
                    result = "mips64";
                    break;
                case kNone:
                    result = "none";
                    break;
                default:
                    throw new IOException("Should not reach here.");
            }
        } finally {
            if (elfFile != null) {
                try {
                    elfFile.close();
                } catch (Exception ignored) {
                    // Ignored.
                }
            }
        }
        return result;
    }

    private enum InstructionSet {
        kNone,
        kArm,
        kArm64,
        kThumb2,
        kX86,
        kX86_64,
        kMips,
        kMips64
    }
}
