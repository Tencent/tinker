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

import com.tencent.tinker.android.dex.TableOfContents.Section;
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.android.dex.util.ArrayUtils;

/**
 * Modifications by tomystang:
 * Make this class derived from {@code SectionItem} so that
 * we can trace dex section this element belongs to easily.
 */
public final class Code extends SectionItem<Code> {
    public int            registersSize;
    public int            insSize;
    public int            outsSize;
    public int            debugInfoOffset;
    public short[]        instructions;
    public Try[]          tries;
    public CatchHandler[] catchHandlers;

    public Code(Section owner, int offset, int registersSize, int insSize, int outsSize, int debugInfoOffset,
                short[] instructions, Try[] tries, CatchHandler[] catchHandlers) {
        super(owner, offset);
        this.registersSize = registersSize;
        this.insSize = insSize;
        this.outsSize = outsSize;
        this.debugInfoOffset = debugInfoOffset;
        this.instructions = instructions;
        this.tries = tries;
        this.catchHandlers = catchHandlers;
    }

    @Override
    public Code clone(Section newOwner, int newOffset) {
        return new Code(newOwner, newOffset, registersSize, insSize, outsSize, debugInfoOffset, instructions, tries,
            catchHandlers);
    }

    @Override
    public int compareTo(Code o) {
        if (registersSize != o.registersSize) {
            return registersSize - o.registersSize;
        }
        if (insSize != o.insSize) {
            return insSize - o.insSize;
        }
        if (outsSize != o.outsSize) {
            return outsSize - o.outsSize;
        }
        if (debugInfoOffset != o.debugInfoOffset) {
            return debugInfoOffset - o.debugInfoOffset;
        }
        int cmpRes = ArrayUtils.compareArray(instructions, o.instructions);
        if (cmpRes != 0) {
            return cmpRes;
        }
        cmpRes = ArrayUtils.compareArray(tries, o.tries);
        if (cmpRes != 0) {
            return cmpRes;
        }
        cmpRes = ArrayUtils.compareArray(catchHandlers, o.catchHandlers);
        if (cmpRes != 0) {
            return cmpRes;
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((Code) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        int byteCount = 4 * SizeOf.USHORT + 2 * SizeOf.UINT + SizeOf.USHORT * instructions.length;
        if (tries.length > 0) {
            byteCount += SizeOf.TRY_ITEM * tries.length;
            if ((instructions.length & 1) == 1) {
                byteCount += SizeOf.USHORT; /* padding */
            }
        }
        for (CatchHandler catchHandler : catchHandlers) {
            byteCount += catchHandler.getByteCountInDex();
        }
        return byteCount;
    }

    public static class Try extends Item<Try> {
        public int startAddress;
        public int instructionCount;

        /**
         * Note that this is distinct from the its catch handler <strong>offset</strong>.
         */
        public int catchHandlerIndex;

        public Try(int startAddress, int instructionCount, int catchHandlerIndex) {
            this.startAddress = startAddress;
            this.instructionCount = instructionCount;
            this.catchHandlerIndex = catchHandlerIndex;
        }

        @Override
        public int compareTo(Try o) {
            if (startAddress != o.startAddress) {
                return startAddress - o.startAddress;
            }
            if (instructionCount != o.instructionCount) {
                return instructionCount - o.instructionCount;
            }
            if (catchHandlerIndex != o.catchHandlerIndex) {
                return catchHandlerIndex - o.catchHandlerIndex;
            }
            return 0;
        }

        @Override
        public int getByteCountInDex() {
            return SizeOf.TRY_ITEM;
        }
    }

    public static class CatchHandler extends Item<CatchHandler> {
        public int[] typeIndexes;
        public int[] addresses;
        public int   catchAllAddress;
        public int   offset;

        public CatchHandler(int[] typeIndexes, int[] addresses, int catchAllAddress, int offset) {
            int typeCount = typeIndexes.length;
            int addrCount = addresses.length;
            if (typeCount != addrCount) {
                throw new IllegalArgumentException("Length of typeIndexes and addresses are not match.");
            }
            this.typeIndexes = typeIndexes;
            this.addresses = addresses;
            this.catchAllAddress = catchAllAddress;
            this.offset = offset;
        }

        @Override
        public int compareTo(CatchHandler o) {
            int cmpRes = ArrayUtils.compareArray(typeIndexes, o.typeIndexes);
            if (cmpRes != 0) {
                return cmpRes;
            }
            cmpRes = ArrayUtils.compareArray(addresses, o.addresses);
            if (cmpRes != 0) {
                return cmpRes;
            }
            if (catchAllAddress != o.catchAllAddress) {
                return catchAllAddress - o.catchAllAddress;
            }
            if (offset != o.offset) {
                return offset - o.offset;
            }
            return 0;
        }

        @Override
        public int getByteCountInDex() {
            int byteCount = 0;
            if (catchAllAddress != -1) {
                byteCount += Leb128.signedLeb128Size(-typeIndexes.length);
            } else {
                byteCount += Leb128.signedLeb128Size(typeIndexes.length);
            }

            int typeIndexesLen = typeIndexes.length;
            for (int i = 0; i < typeIndexesLen; i++) {
                byteCount += Leb128.unsignedLeb128Size(typeIndexes[i])
                    + Leb128.unsignedLeb128Size(addresses[i]);
            }

            if (catchAllAddress != -1) {
                byteCount += Leb128.unsignedLeb128Size(catchAllAddress);
            }

            return byteCount;
        }
    }
}
