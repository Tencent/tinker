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

package com.tencent.tinker.android.dx.instruction;

import com.tencent.tinker.android.utils.SparseIntArray;

/**
 * Cursor over code units, for reading or writing out Dalvik bytecode.
 */
public abstract class CodeCursor {
    /** base address map */
    private final SparseIntArray baseAddressMap;

    /** next index within {@link #array} to read from or write to */
    private int cursor;

    /**
     * Constructs an instance.
     */
    public CodeCursor() {
        this.baseAddressMap = new SparseIntArray();
        this.cursor = 0;
    }

    /**
     * Gets the cursor. The cursor is the offset in code units from
     * the start of the input of the next code unit to be read or
     * written, where the input generally consists of the code for a
     * single method.
     */
    public final int cursor() {
        return cursor;
    }

    /**
     * Gets the base address associated with the current cursor. This
     * differs from the cursor value when explicitly set (by {@link
     * #setBaseAddress}). This is used, in particular, to convey base
     * addresses to switch data payload instructions, whose relative
     * addresses are relative to the address of a dependant switch
     * instruction.
     */
    public final int baseAddressForCursor() {
        int index = baseAddressMap.indexOfKey(cursor);
        if (index < 0) {
            return cursor;
        } else {
            return baseAddressMap.valueAt(index);
        }
    }

    /**
     * Sets the base address for the given target address to be as indicated.
     *
     * @see #baseAddressForCursor
     */
    public final void setBaseAddress(int targetAddress, int baseAddress) {
        baseAddressMap.put(targetAddress, baseAddress);
    }

    /**
     * Reset this cursor's status.
     */
    public void reset() {
        this.baseAddressMap.clear();
        this.cursor = 0;
    }

    /**
     * Advance the cursor by the indicated amount.
     */
    protected final void advance(int amount) {
        cursor += amount;
    }
}
