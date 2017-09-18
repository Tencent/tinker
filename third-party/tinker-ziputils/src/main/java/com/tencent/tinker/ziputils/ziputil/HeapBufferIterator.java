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

package com.tencent.tinker.ziputils.ziputil;

import java.nio.ByteOrder;

/**
 * Iterates over big- or little-endian bytes in a Java byte[].
 *
 * @hide don't make this public without adding bounds checking.
 */
public final class HeapBufferIterator extends BufferIterator {
    private final byte[] buffer;
    private final int offset;
    private final int byteCount;
    private final ByteOrder order;
    private int position;
    HeapBufferIterator(byte[] buffer, int offset, int byteCount, ByteOrder order) {
        this.buffer = buffer;
        this.offset = offset;
        this.byteCount = byteCount;
        this.order = order;
    }

    /**
     * Returns a new iterator over {@code buffer}, starting at {@code offset} and continuing for
     * {@code byteCount} bytes. Items larger than a byte are interpreted using the given byte order.
     */
    public static BufferIterator iterator(byte[] buffer, int offset, int byteCount, ByteOrder order) {
        return new HeapBufferIterator(buffer, offset, byteCount, order);
    }

    public void seek(int offset) {
        position = offset;
    }

    public void skip(int byteCount) {
        position += byteCount;
    }

    public void readByteArray(byte[] dst, int dstOffset, int byteCount) {
        System.arraycopy(buffer, offset + position, dst, dstOffset, byteCount);
        position += byteCount;
    }

    public byte readByte() {
        byte result = buffer[offset + position];
        ++position;
        return result;
    }

    public int readInt() {
        int result = Memory.peekInt(buffer, offset + position, order);
        position += SizeOf.INT;
        return result;
    }

    public short readShort() {
        short result = Memory.peekShort(buffer, offset + position, order);
        position += SizeOf.SHORT;
        return result;
    }
}
