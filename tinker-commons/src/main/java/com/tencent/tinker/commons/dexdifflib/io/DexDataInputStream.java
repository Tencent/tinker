/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
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

package com.tencent.tinker.commons.dexdifflib.io;

import com.tencent.tinker.android.dex.Leb128;
import com.tencent.tinker.android.dex.util.ByteInput;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class DexDataInputStream extends DataInputStream {
    private DexByteInputAdapter dexByteInputAdapter = new DexByteInputAdapter();

    public DexDataInputStream(InputStream in) {
        super(in);
    }

    public int readUleb128() {
        return Leb128.readUnsignedLeb128(dexByteInputAdapter);
    }

    public int readUleb128p1() {
        return Leb128.readUnsignedLeb128p1(dexByteInputAdapter);
    }

    public int readSleb128() {
        return Leb128.readSignedLeb128(dexByteInputAdapter);
    }

    private class DexByteInputAdapter implements ByteInput {
        @Override
        public byte readByte() {
            try {
                return DexDataInputStream.this.readByte();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
