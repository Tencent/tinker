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

import com.tencent.tinker.android.dex.TableOfContents.Section.Item;
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dex.util.HashCodeHelper;

public final class Code extends Item<Code> {
    public int registersSize;
    public int insSize;
    public int outsSize;
    public int debugInfoOffset;
    public short[] instructions;
    public Try[] tries;
    public CatchHandler[] catchHandlers;

    public Code(int off, int registersSize, int insSize, int outsSize, int debugInfoOffset,
            short[] instructions, Try[] tries, CatchHandler[] catchHandlers) {
        super(off);
        this.registersSize = registersSize;
        this.insSize = insSize;
        this.outsSize = outsSize;
        this.debugInfoOffset = debugInfoOffset;
        this.instructions = instructions;
        this.tries = tries;
        this.catchHandlers = catchHandlers;
    }

    @Override
    public int compareTo(Code other) {
        int res = CompareUtils.sCompare(registersSize, other.registersSize);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(insSize, other.insSize);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(outsSize, other.outsSize);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.sCompare(debugInfoOffset, other.debugInfoOffset);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.uArrCompare(instructions, other.instructions);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.aArrCompare(tries, other.tries);
        if (res != 0) {
            return res;
        }
        return CompareUtils.aArrCompare(catchHandlers, other.catchHandlers);
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(registersSize,
                insSize, outsSize, debugInfoOffset, instructions, tries, catchHandlers);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Code)) {
            return false;
        }
        return this.compareTo((Code) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        int insnsSize = instructions.length;
        int res = 4 * SizeOf.USHORT + 2 * SizeOf.UINT + insnsSize * SizeOf.USHORT;
        if (tries.length > 0) {
            if ((insnsSize & 1) == 1) {
                res += SizeOf.USHORT;
            }
            res += tries.length * SizeOf.TRY_ITEM;

            int catchHandlerSize = catchHandlers.length;
            res += Leb128.unsignedLeb128Size(catchHandlerSize);

            for (CatchHandler catchHandler : catchHandlers) {
                int typeIdxAddrPairCount = catchHandler.typeIndexes.length;
                if (catchHandler.catchAllAddress != -1) {
                    res += Leb128.signedLeb128Size(-typeIdxAddrPairCount)
                         + Leb128.unsignedLeb128Size(catchHandler.catchAllAddress);
                } else {
                    res += Leb128.signedLeb128Size(typeIdxAddrPairCount);
                }
                for (int i = 0; i < typeIdxAddrPairCount; ++i) {
                    res += Leb128.unsignedLeb128Size(catchHandler.typeIndexes[i])
                         + Leb128.unsignedLeb128Size(catchHandler.addresses[i]);
                }
            }
        }

        return res;
    }

    public static class Try implements Comparable<Try> {
        public int startAddress;
        public int instructionCount;
        public int catchHandlerIndex;

        public Try(int startAddress, int instructionCount, int catchHandlerIndex) {
            this.startAddress = startAddress;
            this.instructionCount = instructionCount;
            this.catchHandlerIndex = catchHandlerIndex;
        }

        @Override
        public int compareTo(Try other) {
            int res = CompareUtils.sCompare(startAddress, other.startAddress);
            if (res != 0) {
                return res;
            }
            res = CompareUtils.sCompare(instructionCount, other.instructionCount);
            if (res != 0) {
                return res;
            }
            return CompareUtils.sCompare(catchHandlerIndex, other.catchHandlerIndex);
        }
    }

    public static class CatchHandler implements Comparable<CatchHandler> {
        public int[] typeIndexes;
        public int[] addresses;
        public int catchAllAddress;
        public int offset;

        public CatchHandler(int[] typeIndexes, int[] addresses, int catchAllAddress, int offset) {
            this.typeIndexes = typeIndexes;
            this.addresses = addresses;
            this.catchAllAddress = catchAllAddress;
            this.offset = offset;
        }

        @Override
        public int compareTo(CatchHandler other) {
            int res = CompareUtils.sArrCompare(typeIndexes, other.typeIndexes);
            if (res != 0) {
                return res;
            }
            res = CompareUtils.sArrCompare(addresses, other.addresses);
            if (res != 0) {
                return res;
            }
            return CompareUtils.sCompare(catchAllAddress, other.catchAllAddress);
        }
    }
}
