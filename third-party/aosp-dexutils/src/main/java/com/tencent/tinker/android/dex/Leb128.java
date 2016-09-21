/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.tencent.tinker.android.dex.util.ByteInput;
import com.tencent.tinker.android.dex.util.ByteOutput;

/**
 * Reads and writes DWARFv3 LEB 128 signed and unsigned integers. See DWARF v3
 * section 7.6.
 */
public final class Leb128 {
    private Leb128() {
    }

    /**
     * Gets the number of bytes in the unsigned LEB128 encoding of the
     * given value.
     *
     * @param value the value in question
     * @return its write size, in bytes
     */
    public static int unsignedLeb128Size(int value) {
        // TODO: This could be much cleverer.

        int remaining = value >>> 7;
        int count = 0;

        while (remaining != 0) {
            remaining >>>= 7;
            count++;
        }

        return count + 1;
    }

    public static int unsignedLeb128p1Size(int value) {
        return unsignedLeb128Size(value + 1);
    }

    /**
     * Gets the number of bytes in the signed LEB128 encoding of the
     * given value.
     *
     * @param value the value in question
     * @return its write size, in bytes
     */
    public static int signedLeb128Size(int value) {
        // TODO: This could be much cleverer.

        int remaining = value >> 7;
        int count = 0;
        boolean hasMore = true;
        int end = ((value & Integer.MIN_VALUE) == 0) ? 0 : -1;

        while (hasMore) {
            hasMore = (remaining != end)
                || ((remaining & 1) != ((value >> 6) & 1));

            value = remaining;
            remaining >>= 7;
            count++;
        }

        return count;
    }

    /**
     * Reads an signed integer from {@code in}.
     */
    public static int readSignedLeb128(ByteInput in) {
        int result = 0;
        int cur;
        int count = 0;
        int signBits = -1;

        do {
            cur = in.readByte() & 0xff;
            result |= (cur & 0x7f) << (count * 7);
            signBits <<= 7;
            count++;
        } while (((cur & 0x80) == 0x80) && count < 5);

        if ((cur & 0x80) == 0x80) {
            throw new DexException("invalid LEB128 sequence");
        }

        // Sign extend if appropriate
        if (((signBits >> 1) & result) != 0) {
            result |= signBits;
        }

        return result;
    }

    /**
     * Reads an unsigned leb128 integer from {@code in}.
     */
    public static int readUnsignedLeb128(ByteInput in) {
        int result = 0;
        int cur;
        int count = 0;

        do {
            cur = in.readByte() & 0xff;
            result |= (cur & 0x7f) << (count * 7);
            count++;
        } while (((cur & 0x80) == 0x80) && count < 5);

        if ((cur & 0x80) == 0x80) {
            throw new DexException("invalid LEB128 sequence");
        }

        return result;
    }

    /**
     * Reads an unsigned leb128p1 integer from {@code in}.
     */
    public static int readUnsignedLeb128p1(ByteInput in) {
        return readUnsignedLeb128(in) - 1;
    }

    /**
     * Writes {@code value} as an unsigned leb128 integer to {@code out}, starting at
     * {@code offset}. Returns the number of bytes written.
     */
    public static int writeUnsignedLeb128(ByteOutput out, int value) {
        int remaining = value >>> 7;
        int bytesWritten = 0;
        while (remaining != 0) {
            out.writeByte((byte) ((value & 0x7f) | 0x80));
            ++bytesWritten;
            value = remaining;
            remaining >>>= 7;
        }

        out.writeByte((byte) (value & 0x7f));
        ++bytesWritten;

        return bytesWritten;
    }

    /**
     * Writes {@code value} as an unsigned integer to {@code out}, starting at
     * {@code offset}. Returns the number of bytes written.
     */
    public static int writeUnsignedLeb128p1(ByteOutput out, int value) {
        return writeUnsignedLeb128(out, value + 1);
    }

    /**
     * Writes {@code value} as a signed integer to {@code out}, starting at
     * {@code offset}. Returns the number of bytes written.
     */
    public static int writeSignedLeb128(ByteOutput out, int value) {
        int remaining = value >> 7;
        boolean hasMore = true;
        int end = ((value & Integer.MIN_VALUE) == 0) ? 0 : -1;
        int bytesWritten = 0;
        while (hasMore) {
            hasMore = (remaining != end)
                    || ((remaining & 1) != ((value >> 6) & 1));

            out.writeByte((byte) ((value & 0x7f) | (hasMore ? 0x80 : 0)));
            ++bytesWritten;
            value = remaining;
            remaining >>= 7;
        }

        return bytesWritten;
    }
}
