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
import com.tencent.tinker.android.dx.util.Hex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by tangyinsheng on 2016/8/10.
 */
public final class SmallPatchedDexItemFile {
    public static final byte[] MAGIC = {0x44, 0x44, 0x45, 0x58, 0x54, 0x52, 0x41}; // DDEXTRA
    public static final short CURRENT_VERSION = 0x0001;
    private final List<String> oldDexSigns = new ArrayList<>();
    private final Map<String, Integer>
            oldDexSignToPatchedStringIdOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedTypeIdOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedProtoIdOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedFieldIdOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedMethodIdOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedClassDefOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedMapListOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedTypeListOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedAnnotationSetRefListOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedAnnotationSetOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedClassDataOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedCodeOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedStringDataOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedDebugInfoOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedAnnotationOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedEncodedArrayOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedAnnotationsDirectoryOffsetMap = new HashMap<>();
    private final Map<String, Integer>
            oldDexSignToPatchedDexSizeMap = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToStringIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToTypeIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToTypeListIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToProtoIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToFieldIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToMethodIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToAnnotationIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToAnnotationSetIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToAnnotationSetRefListIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToAnnotationsDirectoryIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToEncodedArrayIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToDebugInfoIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToCodeIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToClassDataIndicesInSmallPatch = new HashMap<>();
    private final Map<String, Set<Integer>>
            oldDexSignToClassDefIndicesInSmallPatch = new HashMap<>();
    private int version;
    private int firstChunkOffset;

    public SmallPatchedDexItemFile(File input) throws IOException {
        DexDataBuffer buffer = new DexDataBuffer(ByteBuffer.wrap(FileUtils.readFile(input)));
        init(buffer);
    }

    public SmallPatchedDexItemFile(InputStream is) throws IOException {
        DexDataBuffer buffer = new DexDataBuffer(ByteBuffer.wrap(FileUtils.readStream(is)));
        init(buffer);
    }

    private void init(DexDataBuffer buffer) throws IOException {
        byte[] magic = buffer.readByteArray(MAGIC.length);
        if (CompareUtils.uArrCompare(magic, MAGIC) != 0) {
            throw new IllegalStateException(
                    "bad dexdiff extra file magic: " + Arrays.toString(magic)
            );
        }
        this.version = buffer.readShort();
        if (this.version != CURRENT_VERSION) {
            throw new IllegalStateException(
                    "bad dexdiff extra file version: " + this.version + ", expected: " + CURRENT_VERSION
            );
        }

        this.firstChunkOffset = buffer.readInt();
        buffer.position(this.firstChunkOffset);

        int oldDexSignCount = buffer.readUleb128();
        for (int i = 0; i < oldDexSignCount; ++i) {
            byte[] oldDexSign = buffer.readByteArray(SizeOf.SIGNATURE);
            oldDexSigns.add(Hex.toHexString(oldDexSign));
        }

        for (int i = 0; i < oldDexSignCount; ++i) {
            final String oldDexSign = oldDexSigns.get(i);
            oldDexSignToPatchedStringIdOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedTypeIdOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedProtoIdOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedFieldIdOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedMethodIdOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedClassDefOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedStringDataOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedTypeListOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedAnnotationOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedAnnotationSetOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedAnnotationSetRefListOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedAnnotationsDirectoryOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedDebugInfoOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedCodeOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedClassDataOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedEncodedArrayOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedMapListOffsetMap.put(oldDexSign, buffer.readInt());
            oldDexSignToPatchedDexSizeMap.put(oldDexSign, buffer.readInt());
        }

        readDataChunk(buffer, oldDexSignToStringIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToTypeIdIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToTypeListIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToProtoIdIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToFieldIdIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToMethodIdIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToAnnotationIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToAnnotationSetIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToAnnotationSetRefListIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToAnnotationsDirectoryIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToEncodedArrayIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToDebugInfoIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToCodeIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToClassDataIndicesInSmallPatch);
        readDataChunk(buffer, oldDexSignToClassDefIndicesInSmallPatch);
    }

    private void readDataChunk(
            DexDataBuffer buffer, Map<String, Set<Integer>> oldDexSignToIndicesInSmallPatchMap
    ) {
        int oldDexSignCount = oldDexSigns.size();
        for (int i = 0; i < oldDexSignCount; ++i) {
            int itemCount = buffer.readUleb128();
            int prevIndex = 0;
            for (int j = 0; j < itemCount; ++j) {
                int indexDelta = buffer.readSleb128();
                prevIndex += indexDelta;

                final String oldDexSign = oldDexSigns.get(i);
                Set<Integer> indices = oldDexSignToIndicesInSmallPatchMap.get(oldDexSign);
                if (indices == null) {
                    indices = new HashSet<>();
                    oldDexSignToIndicesInSmallPatchMap.put(oldDexSign, indices);
                }

                indices.add(prevIndex);
            }
        }
    }

    public boolean isAffectedOldDex(String oldDexSign) {
        return this.oldDexSigns.contains(oldDexSign);
    }

    public boolean isSmallPatchedDexEmpty(String oldDexSign) {
        Set<Integer> indices = this.oldDexSignToClassDefIndicesInSmallPatch.get(oldDexSign);
        return (indices == null || indices.isEmpty());
    }

    public int getPatchedStringIdOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedStringIdOffsetMap.get(oldDexSign);
    }

    public int getPatchedTypeIdOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedTypeIdOffsetMap.get(oldDexSign);
    }

    public int getPatchedProtoIdOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedProtoIdOffsetMap.get(oldDexSign);
    }

    public int getPatchedFieldIdOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedFieldIdOffsetMap.get(oldDexSign);
    }

    public int getPatchedMethodIdOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedMethodIdOffsetMap.get(oldDexSign);
    }

    public int getPatchedClassDefOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedClassDefOffsetMap.get(oldDexSign);
    }

    public int getPatchedMapListOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedMapListOffsetMap.get(oldDexSign);
    }

    public int getPatchedTypeListOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedTypeListOffsetMap.get(oldDexSign);
    }

    public int getPatchedAnnotationSetRefListOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedAnnotationSetRefListOffsetMap.get(oldDexSign);
    }

    public int getPatchedAnnotationSetOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedAnnotationSetOffsetMap.get(oldDexSign);
    }

    public int getPatchedClassDataOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedClassDataOffsetMap.get(oldDexSign);
    }

    public int getPatchedCodeOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedCodeOffsetMap.get(oldDexSign);
    }

    public int getPatchedStringDataOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedStringDataOffsetMap.get(oldDexSign);
    }

    public int getPatchedDebugInfoOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedDebugInfoOffsetMap.get(oldDexSign);
    }

    public int getPatchedAnnotationOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedAnnotationOffsetMap.get(oldDexSign);
    }

    public int getPatchedEncodedArrayOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedEncodedArrayOffsetMap.get(oldDexSign);
    }

    public int getPatchedAnnotationsDirectoryOffsetByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedAnnotationsDirectoryOffsetMap.get(oldDexSign);
    }

    public int getPatchedDexSizeByOldDexSign(String oldDexSign) {
        return this.oldDexSignToPatchedDexSizeMap.get(oldDexSign);
    }

    public boolean isStringInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToStringIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isTypeIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToTypeIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isTypeListInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToTypeListIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isProtoIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToProtoIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isFieldIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToFieldIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isMethodIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToMethodIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isAnnotationInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToAnnotationIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isAnnotationSetInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToAnnotationSetIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isAnnotationSetRefListInSmallPatchedDex(
            String oldDexSign, int indexInPatchedDex
    ) {
        Set<Integer> indices = oldDexSignToAnnotationSetRefListIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isAnnotationsDirectoryInSmallPatchedDex(
            String oldDexSign, int indexInPatchedDex
    ) {
        Set<Integer> indices = oldDexSignToAnnotationsDirectoryIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isEncodedArrayInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToEncodedArrayIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isDebugInfoInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToDebugInfoIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isCodeInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToCodeIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isClassDataInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToClassDataIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }

    public boolean isClassDefInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        Set<Integer> indices = oldDexSignToClassDefIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.contains(indexInPatchedDex);
    }
}
