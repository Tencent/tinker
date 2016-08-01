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

import java.io.EOFException;

/**
 * Reads code from a {@code short[]}.
 */
public final class ShortArrayCodeInput extends CodeCursor {
    /** source array to read from */
    private final short[] array;

    /**
     * Constructs an instance.
     */
    public ShortArrayCodeInput(short[] array) {
        if (array == null) {
            throw new NullPointerException("array == null");
        }

        this.array = array;
    }

    /**
     * Returns whether there are any more code units to read. This
     * is analogous to {@code hasNext()} on an interator.
     */
    public boolean hasMore() {
        return cursor() < array.length;
    }

    /**
     * Reads a code unit.
     */
    public int read() throws EOFException {
        try {
            int value = array[cursor()];
            advance(1);
            return value & 0xffff;
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new EOFException();
        }
    }

    /**
     * Reads two code units, treating them as a little-endian {@code int}.
     */
    public int readInt() throws EOFException {
        int short0 = read();
        int short1 = read();

        return short0 | (short1 << 16);
    }

    /**
     * Reads four code units, treating them as a little-endian {@code long}.
     */
    public long readLong() throws EOFException {
        long short0 = read();
        long short1 = read();
        long short2 = read();
        long short3 = read();

        return short0 | (short1 << 16) | (short2 << 32) | (short3 << 48);
    }
}
