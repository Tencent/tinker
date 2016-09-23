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
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tangyinsheng on 2016/8/10.
 */
public final class SmallPatchedDexItemFile {
    public static final byte[] MAGIC = {0x44, 0x44, 0x45, 0x58, 0x54, 0x52, 0x41}; // DDEXTRA
    public static final short CURRENT_VERSION = 0x0001;
    private final List<String> oldDexSigns = new ArrayList<>();

    private final Map<String, DexOffsets> oldDexSignToOffsetInfoMap = new HashMap<>();

    private final Map<String, BitSet>
            oldDexSignToStringIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToTypeIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToTypeListIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToProtoIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToFieldIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToMethodIdIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToAnnotationIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToAnnotationSetIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToAnnotationSetRefListIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToAnnotationsDirectoryIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToEncodedArrayIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToDebugInfoIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToCodeIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToClassDataIndicesInSmallPatch = new HashMap<>();
    private final Map<String, BitSet>
            oldDexSignToClassDefIndicesInSmallPatch = new HashMap<>();
    private int version;
    private int firstChunkOffset;

    private static final class DexOffsets {
        int stringIdsOffset = -1;
        int typeIdsOffset = -1;
        int protoIdsOffset = -1;
        int fieldIdsOffset = -1;
        int methodIdsOffset = -1;
        int classDefsOffset = -1;
        int mapListOffset = -1;
        int typeListsOffset = -1;
        int annotationsOffset = -1;
        int annotationSetsOffset = -1;
        int annotationSetRefListsOffset = -1;
        int annotationsDirectoriesOffset = -1;
        int classDataItemsOffset = -1;
        int codeItemsOffset = -1;
        int stringDataItemsOffset = -1;
        int debugInfoItemsOffset = -1;
        int encodedArraysOffset = -1;
        int dexSize = -1;
    }

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
            final DexOffsets dexOffsets = new DexOffsets();
            dexOffsets.stringIdsOffset = buffer.readInt();
            dexOffsets.typeIdsOffset = buffer.readInt();
            dexOffsets.protoIdsOffset = buffer.readInt();
            dexOffsets.fieldIdsOffset = buffer.readInt();
            dexOffsets.methodIdsOffset = buffer.readInt();
            dexOffsets.classDefsOffset = buffer.readInt();
            dexOffsets.stringDataItemsOffset = buffer.readInt();
            dexOffsets.typeListsOffset = buffer.readInt();
            dexOffsets.annotationsOffset = buffer.readInt();
            dexOffsets.annotationSetsOffset = buffer.readInt();
            dexOffsets.annotationSetRefListsOffset = buffer.readInt();
            dexOffsets.annotationsDirectoriesOffset = buffer.readInt();
            dexOffsets.debugInfoItemsOffset = buffer.readInt();
            dexOffsets.codeItemsOffset = buffer.readInt();
            dexOffsets.classDataItemsOffset = buffer.readInt();
            dexOffsets.encodedArraysOffset = buffer.readInt();
            dexOffsets.mapListOffset = buffer.readInt();
            dexOffsets.dexSize = buffer.readInt();
            oldDexSignToOffsetInfoMap.put(oldDexSign, dexOffsets);
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
            DexDataBuffer buffer, Map<String, BitSet> oldDexSignToIndicesInSmallPatchMap
    ) {
        int oldDexSignCount = oldDexSigns.size();
        for (int i = 0; i < oldDexSignCount; ++i) {
            int itemCount = buffer.readUleb128();
            int prevIndex = 0;
            for (int j = 0; j < itemCount; ++j) {
                int indexDelta = buffer.readSleb128();
                prevIndex += indexDelta;

                final String oldDexSign = oldDexSigns.get(i);
                BitSet indices = oldDexSignToIndicesInSmallPatchMap.get(oldDexSign);
                if (indices == null) {
                    indices = new BitSet();
                    oldDexSignToIndicesInSmallPatchMap.put(oldDexSign, indices);
                }

                indices.set(prevIndex);
            }
        }
    }

    public boolean isAffectedOldDex(String oldDexSign) {
        return this.oldDexSigns.contains(oldDexSign);
    }

    public boolean isSmallPatchedDexEmpty(String oldDexSign) {
        BitSet indices = this.oldDexSignToClassDefIndicesInSmallPatch.get(oldDexSign);
        return (indices == null || indices.isEmpty());
    }

    public int getPatchedStringIdOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.stringIdsOffset : -1;
    }

    public int getPatchedTypeIdOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.typeIdsOffset : -1;
    }

    public int getPatchedProtoIdOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.protoIdsOffset : -1;
    }

    public int getPatchedFieldIdOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.fieldIdsOffset : -1;
    }

    public int getPatchedMethodIdOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.methodIdsOffset : -1;
    }

    public int getPatchedClassDefOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.classDefsOffset : -1;
    }

    public int getPatchedMapListOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.mapListOffset : -1;
    }

    public int getPatchedTypeListOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.typeListsOffset : -1;
    }

    public int getPatchedAnnotationSetRefListOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.annotationSetRefListsOffset : -1;
    }

    public int getPatchedAnnotationSetOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.annotationSetsOffset : -1;
    }

    public int getPatchedClassDataOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.classDataItemsOffset : -1;
    }

    public int getPatchedCodeOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.codeItemsOffset : -1;
    }

    public int getPatchedStringDataOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.stringDataItemsOffset : -1;
    }

    public int getPatchedDebugInfoOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.debugInfoItemsOffset : -1;
    }

    public int getPatchedAnnotationOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.annotationsOffset : -1;
    }

    public int getPatchedEncodedArrayOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.encodedArraysOffset : -1;
    }

    public int getPatchedAnnotationsDirectoryOffsetByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.annotationsDirectoriesOffset : -1;
    }

    public int getPatchedDexSizeByOldDexSign(String oldDexSign) {
        DexOffsets dexOffsets = this.oldDexSignToOffsetInfoMap.get(oldDexSign);
        return dexOffsets != null ? dexOffsets.dexSize : -1;
    }

    public boolean isStringInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToStringIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isTypeIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToTypeIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isTypeListInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToTypeListIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isProtoIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToProtoIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isFieldIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToFieldIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isMethodIdInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToMethodIdIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isAnnotationInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToAnnotationIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isAnnotationSetInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToAnnotationSetIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isAnnotationSetRefListInSmallPatchedDex(
            String oldDexSign, int indexInPatchedDex
    ) {
        BitSet indices = oldDexSignToAnnotationSetRefListIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isAnnotationsDirectoryInSmallPatchedDex(
            String oldDexSign, int indexInPatchedDex
    ) {
        BitSet indices = oldDexSignToAnnotationsDirectoryIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isEncodedArrayInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToEncodedArrayIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isDebugInfoInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToDebugInfoIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isCodeInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToCodeIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isClassDataInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToClassDataIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }

    public boolean isClassDefInSmallPatchedDex(String oldDexSign, int indexInPatchedDex) {
        BitSet indices = oldDexSignToClassDefIndicesInSmallPatch.get(oldDexSign);
        if (indices == null) {
            return false;
        }
        return indices.get(indexInPatchedDex);
    }
}
