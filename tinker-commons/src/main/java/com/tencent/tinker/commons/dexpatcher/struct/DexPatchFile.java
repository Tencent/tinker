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

package com.tencent.tinker.commons.dexpatcher.struct;

import com.tencent.tinker.android.dex.SizeOf;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dex.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by tangyinsheng on 2016/7/1.
 */
public final class DexPatchFile {
    public static final byte[] MAGIC = {0x44, 0x58, 0x44, 0x49, 0x46, 0x46}; // DXDIFF
    public static final short CURRENT_VERSION = 0x0002;
    private final DexDataBuffer buffer;
    private short version;
    private int patchedDexSize;
    private int firstChunkOffset;
    private int patchedStringIdSectionOffset;
    private int patchedTypeIdSectionOffset;
    private int patchedProtoIdSectionOffset;
    private int patchedFieldIdSectionOffset;
    private int patchedMethodIdSectionOffset;
    private int patchedClassDefSectionOffset;
    private int patchedMapListSectionOffset;
    private int patchedTypeListSectionOffset;
    private int patchedAnnotationSetRefListSectionOffset;
    private int patchedAnnotationSetSectionOffset;
    private int patchedClassDataSectionOffset;
    private int patchedCodeSectionOffset;
    private int patchedStringDataSectionOffset;
    private int patchedDebugInfoSectionOffset;
    private int patchedAnnotationSectionOffset;
    private int patchedEncodedArraySectionOffset;
    private int patchedAnnotationsDirectorySectionOffset;
    private byte[] oldDexSignature;

    public DexPatchFile(File file) throws IOException {
        this.buffer = new DexDataBuffer(ByteBuffer.wrap(FileUtils.readFile(file)));
        init();
    }

    public DexPatchFile(InputStream is) throws IOException {
        this.buffer = new DexDataBuffer(ByteBuffer.wrap(FileUtils.readStream(is)));
        init();
    }

    private void init() {
        byte[] magic = this.buffer.readByteArray(MAGIC.length);
        if (CompareUtils.uArrCompare(magic, MAGIC) != 0) {
            throw new IllegalStateException("bad dex patch file magic: " + Arrays.toString(magic));
        }

        this.version = this.buffer.readShort();
        if (CompareUtils.uCompare(this.version, CURRENT_VERSION) != 0) {
            throw new IllegalStateException("bad dex patch file version: " + this.version + ", expected: " + CURRENT_VERSION);
        }

        this.patchedDexSize = this.buffer.readInt();
        this.firstChunkOffset = this.buffer.readInt();
        this.patchedStringIdSectionOffset = this.buffer.readInt();
        this.patchedTypeIdSectionOffset = this.buffer.readInt();
        this.patchedProtoIdSectionOffset = this.buffer.readInt();
        this.patchedFieldIdSectionOffset = this.buffer.readInt();
        this.patchedMethodIdSectionOffset = this.buffer.readInt();
        this.patchedClassDefSectionOffset = this.buffer.readInt();
        this.patchedMapListSectionOffset = this.buffer.readInt();
        this.patchedTypeListSectionOffset = this.buffer.readInt();
        this.patchedAnnotationSetRefListSectionOffset = this.buffer.readInt();
        this.patchedAnnotationSetSectionOffset = this.buffer.readInt();
        this.patchedClassDataSectionOffset = this.buffer.readInt();
        this.patchedCodeSectionOffset = this.buffer.readInt();
        this.patchedStringDataSectionOffset = this.buffer.readInt();
        this.patchedDebugInfoSectionOffset = this.buffer.readInt();
        this.patchedAnnotationSectionOffset = this.buffer.readInt();
        this.patchedEncodedArraySectionOffset = this.buffer.readInt();
        this.patchedAnnotationsDirectorySectionOffset = this.buffer.readInt();
        this.oldDexSignature = this.buffer.readByteArray(SizeOf.SIGNATURE);

        this.buffer.position(firstChunkOffset);
    }

    public short getVersion() {
        return version;
    }

    public byte[] getOldDexSignature() {
        return this.oldDexSignature;
    }

    public int getPatchedDexSize() {
        return patchedDexSize;
    }

    public int getPatchedStringIdSectionOffset() {
        return patchedStringIdSectionOffset;
    }

    public int getPatchedTypeIdSectionOffset() {
        return patchedTypeIdSectionOffset;
    }

    public int getPatchedProtoIdSectionOffset() {
        return patchedProtoIdSectionOffset;
    }

    public int getPatchedFieldIdSectionOffset() {
        return patchedFieldIdSectionOffset;
    }

    public int getPatchedMethodIdSectionOffset() {
        return patchedMethodIdSectionOffset;
    }

    public int getPatchedClassDefSectionOffset() {
        return patchedClassDefSectionOffset;
    }

    public int getPatchedMapListSectionOffset() {
        return patchedMapListSectionOffset;
    }

    public int getPatchedTypeListSectionOffset() {
        return patchedTypeListSectionOffset;
    }

    public int getPatchedAnnotationSetRefListSectionOffset() {
        return patchedAnnotationSetRefListSectionOffset;
    }

    public int getPatchedAnnotationSetSectionOffset() {
        return patchedAnnotationSetSectionOffset;
    }

    public int getPatchedClassDataSectionOffset() {
        return patchedClassDataSectionOffset;
    }

    public int getPatchedCodeSectionOffset() {
        return patchedCodeSectionOffset;
    }

    public int getPatchedStringDataSectionOffset() {
        return patchedStringDataSectionOffset;
    }

    public int getPatchedDebugInfoSectionOffset() {
        return patchedDebugInfoSectionOffset;
    }

    public int getPatchedAnnotationSectionOffset() {
        return patchedAnnotationSectionOffset;
    }

    public int getPatchedEncodedArraySectionOffset() {
        return patchedEncodedArraySectionOffset;
    }

    public int getPatchedAnnotationsDirectorySectionOffset() {
        return patchedAnnotationsDirectorySectionOffset;
    }

    public DexDataBuffer getBuffer() {
        return buffer;
    }
}
