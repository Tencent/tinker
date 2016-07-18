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
import com.tencent.tinker.android.dex.util.ByteOutput;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DexDataOutputStream extends DataOutputStream {
    private DexByteOutputAdapter dexByteOutputAdapter = new DexByteOutputAdapter();

    public DexDataOutputStream(OutputStream out) {
        super(out);
    }

    public void writeUleb128(int value) {
        Leb128.writeUnsignedLeb128(dexByteOutputAdapter, value);
    }

    public void writeUleb128p1(int value) {
        Leb128.writeUnsignedLeb128p1(dexByteOutputAdapter, value);
    }

    public void writeSleb128(int value) {
        Leb128.writeSignedLeb128(dexByteOutputAdapter, value);
    }

    private class DexByteOutputAdapter implements ByteOutput {
        @Override
        public void writeByte(int i) {
            try {
                DexDataOutputStream.this.writeByte(i);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
