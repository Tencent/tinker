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
import com.tencent.tinker.android.dex.util.ByteInput;

/**
 * An encoded value or array.
 */
public final class EncodedValue extends SectionItem<EncodedValue> {
    public byte[] data;

    public EncodedValue(Section owner, int offset, byte[] data) {
        super(owner, offset);
        this.data = data;
    }

    public ByteInput asByteInput() {
        return new ByteInput() {
            private int pos = 0;

            @Override
            public byte readByte() {
                return data[pos++];
            }
        };
    }

    @Override
    public EncodedValue clone(Section newOwner, int newOffset) {
        return new EncodedValue(newOwner, newOffset, data);
    }

    @Override
    public int compareTo(EncodedValue other) {
        int size = Math.min(data.length, other.data.length);
        for (int i = 0; i < size; i++) {
            if (data[i] != other.data[i]) {
                return (data[i] & 0xff) - (other.data[i] & 0xff);
            }
        }
        return data.length - other.data.length;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((EncodedValue) obj) == 0;
    }

    @Override
    public String toString() {
        return Integer.toHexString(data[0] & 0xff) + "...(" + data.length + ")";
    }

    @Override
    public int getByteCountInDex() {
        return data.length;
    }
}
