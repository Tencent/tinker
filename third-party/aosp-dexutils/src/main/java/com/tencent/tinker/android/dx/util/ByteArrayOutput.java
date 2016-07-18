/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.tencent.tinker.android.dx.util;

import com.tencent.tinker.android.dex.Leb128;
import com.tencent.tinker.android.dex.util.ExceptionWithContext;

/**
 * Stores the written data
 * into a {@code byte[]}.
 *
 * <p><b>Note:</b> As per the {@link Output} interface, multi-byte
 * writes all use little-endian order.</p>
 *
 * Modifications by tomystang:
 * Method writeAnnotationsTo was removed since we needn't use it in
 * DexDiff library.
 */
public final class ByteArrayOutput implements Output {
    /**
     * default size for stretchy instances
     */
    private static final int DEFAULT_SIZE = 1000;

    /**
     * whether the instance is stretchy, that is, whether its array
     * may be resized to increase capacity
     */
    private final boolean stretchy;

    /**
     * {@code non-null;} the data itself
     */
    private byte[] data;

    /**
     * {@code >= 0;} current output cursor
     */
    private int cursor;

    /**
     * Constructs an instance with a fixed maximum size. Note that the
     * given array is the only one that will be used to store data. In
     * particular, no reallocation will occur in order to expand the
     * capacity of the resulting instance. Also, the constructed
     * instance does not keep annotations by default.
     *
     * @param data {@code non-null;} data array to use for output
     */
    public ByteArrayOutput(byte[] data) {
        this(data, false);
    }

    /**
     * Constructs a "stretchy" instance. The underlying array may be
     * reallocated. The constructed instance does not keep annotations
     * by default.
     */
    public ByteArrayOutput() {
        this(DEFAULT_SIZE);
    }

    /**
     * Constructs a "stretchy" instance with initial size {@code size}. The
     * underlying array may be reallocated. The constructed instance does not
     * keep annotations by default.
     */
    public ByteArrayOutput(int size) {
        this(new byte[size], true);
    }

    /**
     * Internal constructor.
     *
     * @param data     {@code non-null;} data array to use for output
     * @param stretchy whether the instance is to be stretchy
     */
    private ByteArrayOutput(byte[] data, boolean stretchy) {
        if (data == null) {
            throw new NullPointerException("data == null");
        }

        this.stretchy = stretchy;
        this.data = data;
        this.cursor = 0;
    }

    /**
     * Throws the excpetion for when an attempt is made to write past the
     * end of the instance.
     */
    private static void throwBounds() {
        throw new IndexOutOfBoundsException("attempt to write past the end");
    }

    /**
     * Gets the underlying {@code byte[]} of this instance, which
     * may be larger than the number of bytes written
     *
     * @return {@code non-null;} the {@code byte[]}
     * @see #toByteArray
     */
    public byte[] getArray() {
        return data;
    }

    /**
     * Constructs and returns a new {@code byte[]} that contains
     * the written contents exactly (that is, with no extra unwritten
     * bytes at the end).
     *
     * @return {@code non-null;} an appropriately-constructed array
     * @see #getArray
     */
    public byte[] toByteArray() {
        byte[] result = new byte[cursor];
        System.arraycopy(data, 0, result, 0, cursor);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCursor() {
        return cursor;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void assertCursor(int expectedCursor) {
        if (cursor != expectedCursor) {
            throw new ExceptionWithContext("expected cursor "
                + expectedCursor
                + "; actual value: "
                + cursor);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeByte(int value) {
        int writeAt = cursor;
        int end = writeAt + 1;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        data[writeAt] = (byte) value;
        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeShort(int value) {
        int writeAt = cursor;
        int end = writeAt + 2;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        data[writeAt] = (byte) value;
        data[writeAt + 1] = (byte) (value >> 8);
        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInt(int value) {
        int writeAt = cursor;
        int end = writeAt + 4;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        data[writeAt] = (byte) value;
        data[writeAt + 1] = (byte) (value >> 8);
        data[writeAt + 2] = (byte) (value >> 16);
        data[writeAt + 3] = (byte) (value >> 24);
        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeLong(long value) {
        int writeAt = cursor;
        int end = writeAt + 8;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        int half = (int) value;
        data[writeAt] = (byte) half;
        data[writeAt + 1] = (byte) (half >> 8);
        data[writeAt + 2] = (byte) (half >> 16);
        data[writeAt + 3] = (byte) (half >> 24);

        half = (int) (value >> 32);
        data[writeAt + 4] = (byte) half;
        data[writeAt + 5] = (byte) (half >> 8);
        data[writeAt + 6] = (byte) (half >> 16);
        data[writeAt + 7] = (byte) (half >> 24);

        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int writeUleb128(int value) {
        if (stretchy) {
            ensureCapacity(cursor + 5); // pessimistic
        }
        int cursorBefore = cursor;
        Leb128.writeUnsignedLeb128(this, value);
        return (cursor - cursorBefore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int writeSleb128(int value) {
        if (stretchy) {
            ensureCapacity(cursor + 5); // pessimistic
        }
        int cursorBefore = cursor;
        Leb128.writeSignedLeb128(this, value);
        return (cursor - cursorBefore);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(ByteArrayInput bytes) {
        int blen = bytes.size();
        int writeAt = cursor;
        int end = writeAt + blen;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        bytes.getBytes(data, writeAt);
        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] bytes, int offset, int length) {
        int writeAt = cursor;
        int end = writeAt + length;
        int bytesEnd = offset + length;

        // twos-complement math trick: ((x < 0) || (y < 0)) <=> ((x|y) < 0)
        if (((offset | length | end) < 0) || (bytesEnd > bytes.length)) {
            throw new IndexOutOfBoundsException("bytes.length "
                + bytes.length
                + "; "
                + offset
                + "..!" + end);
        }

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        System.arraycopy(bytes, offset, data, writeAt, length);
        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeZeroes(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }

        int end = cursor + count;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        /*
         * There is no need to actually write zeroes, since the array is
         * already preinitialized with zeroes.
         */

        cursor = end;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void alignTo(int alignment) {
        int mask = alignment - 1;

        if ((alignment < 0) || ((mask & alignment) != 0)) {
            throw new IllegalArgumentException("bogus alignment");
        }

        int end = (cursor + mask) & ~mask;

        if (stretchy) {
            ensureCapacity(end);
        } else if (end > data.length) {
            throwBounds();
            return;
        }

        /*
         * There is no need to actually write zeroes, since the array is
         * already preinitialized with zeroes.
         */

        cursor = end;
    }

    /**
     * Reallocates the underlying array if necessary. Calls to this method
     * should be guarded by a test of {@link #stretchy}.
     *
     * @param desiredSize {@code >= 0;} the desired minimum total size of the array
     */
    private void ensureCapacity(int desiredSize) {
        if (data.length < desiredSize) {
            byte[] newData = new byte[desiredSize * 2 + 1000];
            System.arraycopy(data, 0, newData, 0, cursor);
            data = newData;
        }
    }
}
