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

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Structure of DebugInfoItem element in Dex file.
 */
public class DebugInfoItem extends Item<DebugInfoItem> {
    public static final byte DBG_END_SEQUENCE = 0x00;
    public static final byte DBG_ADVANCE_PC = 0x01;
    public static final byte DBG_ADVANCE_LINE = 0x02;
    public static final byte DBG_START_LOCAL = 0x03;
    public static final byte DBG_START_LOCAL_EXTENDED = 0x04;
    public static final byte DBG_END_LOCAL = 0x05;
    public static final byte DBG_RESTART_LOCAL = 0x06;
    public static final byte DBG_SET_PROLOGUE_END = 0x07;
    public static final byte DBG_SET_EPILOGUE_BEGIN = 0x08;
    public static final byte DBG_SET_FILE = 0x09;

    public int lineStart;
    public int[] parameterNames;

    public byte[] infoSTM;

    public DebugInfoItem(int off, int lineStart, int[] parameterNames, byte[] infoSTM) {
        super(off);
        this.lineStart = lineStart;
        this.parameterNames = parameterNames;
        this.infoSTM = infoSTM;
    }

    @Override
    public int compareTo(DebugInfoItem o) {
        int origLineStart = lineStart;
        int destLineStart = o.lineStart;
        if (origLineStart != destLineStart) {
            return origLineStart - destLineStart;
        }

        int cmpRes = CompareUtils.uArrCompare(parameterNames, o.parameterNames);
        if (cmpRes != 0) return cmpRes;

        cmpRes = CompareUtils.uArrCompare(infoSTM, o.infoSTM);
        return cmpRes;
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(lineStart, parameterNames, infoSTM);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DebugInfoItem)) {
            return false;
        }
        return this.compareTo((DebugInfoItem) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        int byteCount = Leb128.unsignedLeb128Size(lineStart) + Leb128.unsignedLeb128Size(parameterNames.length);
        for (int pn : parameterNames) {
            byteCount += Leb128.unsignedLeb128p1Size(pn);
        }
        byteCount += infoSTM.length * SizeOf.UBYTE;
        return byteCount;
    }
}
