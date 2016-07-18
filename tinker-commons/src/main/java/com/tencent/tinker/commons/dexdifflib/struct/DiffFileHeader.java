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

import com.tencent.tinker.commons.dexdifflib.io.DiffFileInputStream;

import java.io.IOException;
import java.util.Arrays;

/**
 * Created by tomystang on 2016/4/14.
 */
public class DiffFileHeader {
    public static final int    SIZE            = 6 + 2 + 2 + 20 * 4;
    public static final byte[] MAGIC           = {0x44, 0x58, 0x44, 0x49, 0x46, 0x46}; // DXDIFF
    public static final short  CURRENT_VERSION = 0x0001;
    public final short version;
    public final int   newDexSize;
    public final int   firstChunkOffset;
    public final int   newStringIdSectionOffset;
    public final int   newTypeIdSectionOffset;
    public final int   newProtoIdSectionOffset;
    public final int   newFieldIdSectionOffset;
    public final int   newMethodIdSectionOffset;
    public final int   newClassDefSectionOffset;
    public final int   newMapListSectionOffset;
    public final int   newTypeListSectionOffset;
    public final int   newAnnotationSetRefListSectionOffset;
    public final int   newAnnotationSetSectionOffset;
    public final int   newClassDataSectionOffset;
    public final int   newCodeSectionOffset;
    public final int   newStringDataSectionOffset;
    public final int   newDebugInfoSectionOffset;
    public final int   newAnnotationSectionOffset;
    public final int   newEncodedArraySectionOffset;
    public final int   newAnnotationsDirectorySectionOffset;

    public DiffFileHeader(
        short version, int newDexSize, int firstChunkOffset,
        int newStringIdSectionOffset, int newTypeIdSectionOffset, int newProtoIdSectionOffset,
        int newFieldIdSectionOffset, int newMethodIdSectionOffset, int newClassDefSectionOffset,
        int newMapListSectionOffset, int newTypeListSectionOffset, int newAnnotationSetRefListSectionOffset,
        int newAnnotationSetSectionOffset, int newClassDataSectionOffset, int newCodeSectionOffset,
        int newStringDataSectionOffset, int newDebugInfoSectionOffset, int newAnnotationSectionOffset,
        int newEncodedArraySectionOffset, int newAnnotationsDirectorySectionOffset
    ) {
        this.version = version;
        this.newDexSize = newDexSize;
        this.firstChunkOffset = firstChunkOffset;

        this.newStringIdSectionOffset = newStringIdSectionOffset;
        this.newTypeIdSectionOffset = newTypeIdSectionOffset;
        this.newProtoIdSectionOffset = newProtoIdSectionOffset;
        this.newFieldIdSectionOffset = newFieldIdSectionOffset;
        this.newMethodIdSectionOffset = newMethodIdSectionOffset;
        this.newClassDefSectionOffset = newClassDefSectionOffset;
        this.newMapListSectionOffset = newMapListSectionOffset;
        this.newTypeListSectionOffset = newTypeListSectionOffset;
        this.newAnnotationSetRefListSectionOffset = newAnnotationSetRefListSectionOffset;
        this.newAnnotationSetSectionOffset = newAnnotationSetSectionOffset;
        this.newClassDataSectionOffset = newClassDataSectionOffset;
        this.newCodeSectionOffset = newCodeSectionOffset;
        this.newStringDataSectionOffset = newStringDataSectionOffset;
        this.newDebugInfoSectionOffset = newDebugInfoSectionOffset;
        this.newAnnotationSectionOffset = newAnnotationSectionOffset;
        this.newEncodedArraySectionOffset = newEncodedArraySectionOffset;
        this.newAnnotationsDirectorySectionOffset = newAnnotationsDirectorySectionOffset;
    }

    public DiffFileHeader(DiffFileInputStream in) throws IOException {
        byte[] magic = new byte[MAGIC.length];
        in.read(magic);

        if (!Arrays.equals(magic, MAGIC)) {
            throw new IOException("Illegal magic: " + Arrays.toString(magic));
        }

        this.version = in.readShort();
        this.newDexSize = in.readInt();
        this.firstChunkOffset = in.readInt();

        this.newStringIdSectionOffset = in.readInt();
        this.newTypeIdSectionOffset = in.readInt();
        this.newProtoIdSectionOffset = in.readInt();
        this.newFieldIdSectionOffset = in.readInt();
        this.newMethodIdSectionOffset = in.readInt();
        this.newClassDefSectionOffset = in.readInt();
        this.newMapListSectionOffset = in.readInt();
        this.newTypeListSectionOffset = in.readInt();
        this.newAnnotationSetRefListSectionOffset = in.readInt();
        this.newAnnotationSetSectionOffset = in.readInt();
        this.newClassDataSectionOffset = in.readInt();
        this.newCodeSectionOffset = in.readInt();
        this.newStringDataSectionOffset = in.readInt();
        this.newDebugInfoSectionOffset = in.readInt();
        this.newAnnotationSectionOffset = in.readInt();
        this.newEncodedArraySectionOffset = in.readInt();
        this.newAnnotationsDirectorySectionOffset = in.readInt();
    }
}
