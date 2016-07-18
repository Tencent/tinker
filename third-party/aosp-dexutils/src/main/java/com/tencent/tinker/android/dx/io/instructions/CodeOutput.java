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

package com.tencent.tinker.android.dx.io.instructions;

/**
 * Output stream of code units, for writing out Dalvik bytecode.
 */
public interface CodeOutput extends CodeCursor {
    /**
     * Writes a code unit.
     */
    void write(short codeUnit);

    /**
     * Writes two code units.
     */
    void write(short u0, short u1);

    /**
     * Writes three code units.
     */
    void write(short u0, short u1, short u2);

    /**
     * Writes four code units.
     */
    void write(short u0, short u1, short u2, short u3);

    /**
     * Writes five code units.
     */
    void write(short u0, short u1, short u2, short u3, short u4);

    /**
     * Writes an {@code int}, little-endian.
     */
    void writeInt(int value);

    /**
     * Writes a {@code long}, little-endian.
     */
    void writeLong(long value);

    /**
     * Writes the contents of the given array.
     */
    void write(byte[] data);

    /**
     * Writes the contents of the given array.
     */
    void write(short[] data);

    /**
     * Writes the contents of the given array.
     */
    void write(int[] data);

    /**
     * Writes the contents of the given array.
     */
    void write(long[] data);
}
