/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.tencent.tinker.commons.ziputil;

/**
 * modify by zhangshaowen on 16/6/7.
 */
public abstract class BufferIterator {
    /**
     * Seeks to the absolute position {@code offset}, measured in bytes from the start.
     */
    public abstract void seek(int offset);
    /**
     * Skips forwards or backwards {@code byteCount} bytes from the current position.
     */
    public abstract void skip(int byteCount);

    public abstract int readInt();

    public abstract short readShort();
}
