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

/**
 * Writes code to a {@code short[]}.
 */
public final class ShortArrayCodeOutput extends CodeCursor {
    /** array to write to */
    private short[] array;

    /**
     * Constructs an instance.
     *
     * @param initSize the maximum number of code units that will be written
     */
    public ShortArrayCodeOutput(int initSize) {
        if (initSize < 0) {
            throw new IllegalArgumentException("initSize < 0");
        }

        this.array = new short[initSize];
    }

    /**
     * Constructs an instance by wrapping an exist array.
     * @param array the array to write.
     */
    public ShortArrayCodeOutput(short[] array) {
        if (array == null) {
            throw new IllegalArgumentException("array is null.");
        }
        this.array = array;
    }

    /**
     * Gets the array. The returned array contains exactly the data
     * written (e.g. no leftover space at the end).
     */
    public short[] getArray() {
        int cursor = cursor();

        if (cursor == array.length) {
            return array;
        }

        short[] result = new short[cursor];
        System.arraycopy(array, 0, result, 0, cursor);
        return result;
    }

    /**
     * Writes a code unit.
     */
    public void write(short codeUnit) {
        ensureArrayLength(1);
        array[cursor()] = codeUnit;
        advance(1);
    }

    /**
     * Writes two code units.
     */
    public void write(short u0, short u1) {
        write(u0);
        write(u1);
    }

    /**
     * Writes three code units.
     */
    public void write(short u0, short u1, short u2) {
        write(u0);
        write(u1);
        write(u2);
    }

    /**
     * Writes four code units.
     */
    public void write(short u0, short u1, short u2, short u3) {
        write(u0);
        write(u1);
        write(u2);
        write(u3);
    }

    /**
     * Writes five code units.
     */
    public void write(short u0, short u1, short u2, short u3, short u4) {
        write(u0);
        write(u1);
        write(u2);
        write(u3);
        write(u4);
    }

    /**
     * Writes an {@code int}, little-endian.
     */
    public void writeInt(int value) {
        write((short) value);
        write((short) (value >> 16));
    }

    /**
     * Writes a {@code long}, little-endian.
     */
    public void writeLong(long value) {
        write((short) value);
        write((short) (value >> 16));
        write((short) (value >> 32));
        write((short) (value >> 48));
    }

    /**
     * Writes the contents of the given array.
     */
    public void write(byte[] data) {
        int value = 0;
        boolean even = true;
        for (byte b : data) {
            if (even) {
                value = b & 0xff;
                even = false;
            } else {
                value |= b << 8;
                write((short) value);
                even = true;
            }
        }

        if (!even) {
            write((short) value);
        }
    }

    /**
     * Writes the contents of the given array.
     */
    public void write(short[] data) {
        for (short unit : data) {
            write(unit);
        }
    }

    /**
     * Writes the contents of the given array.
     */
    public void write(int[] data) {
        for (int i : data) {
            writeInt(i);
        }
    }

    /**
     * Writes the contents of the given array.
     */
    public void write(long[] data) {
        for (long l : data) {
            writeLong(l);
        }
    }

    private void ensureArrayLength(int shortCountToWrite) {
        int currPos = cursor();
        if (array.length - currPos < shortCountToWrite) {
            short[] newArray = new short[array.length + (array.length >> 1)];
            System.arraycopy(array, 0, newArray, 0, currPos);
            array = newArray;
        }
    }
}
