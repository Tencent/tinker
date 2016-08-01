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
 * Encode/Decode instruction opcode.
 */
public class InstructionCodec {
    public static short codeUnit(int lowByte, int highByte) {
        if ((lowByte & ~0xff) != 0) {
            throw new IllegalArgumentException("bogus lowByte");
        }

        if ((highByte & ~0xff) != 0) {
            throw new IllegalArgumentException("bogus highByte");
        }

        return (short) (lowByte | (highByte << 8));
    }

    public static short codeUnit(int nibble0, int nibble1, int nibble2,
                                 int nibble3) {
        if ((nibble0 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble0");
        }

        if ((nibble1 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble1");
        }

        if ((nibble2 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble2");
        }

        if ((nibble3 & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus nibble3");
        }

        return (short) (nibble0 | (nibble1 << 4)
                | (nibble2 << 8) | (nibble3 << 12));
    }

    public static int makeByte(int lowNibble, int highNibble) {
        if ((lowNibble & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus lowNibble");
        }

        if ((highNibble & ~0xf) != 0) {
            throw new IllegalArgumentException("bogus highNibble");
        }

        return lowNibble | (highNibble << 4);
    }

    public static short asUnsignedUnit(int value) {
        if ((value & ~0xffff) != 0) {
            throw new IllegalArgumentException("bogus unsigned code unit");
        }

        return (short) value;
    }

    public static short unit0(int value) {
        return (short) value;
    }

    public static short unit1(int value) {
        return (short) (value >> 16);
    }

    public static short unit0(long value) {
        return (short) value;
    }

    public static short unit1(long value) {
        return (short) (value >> 16);
    }

    public static short unit2(long value) {
        return (short) (value >> 32);
    }

    public static short unit3(long value) {
        return (short) (value >> 48);
    }

    public static int byte0(int value) {
        return value & 0xff;
    }

    public static int byte1(int value) {
        return (value >> 8) & 0xff;
    }

    public static int nibble0(int value) {
        return value & 0xf;
    }

    public static int nibble1(int value) {
        return (value >> 4) & 0xf;
    }

    public static int nibble2(int value) {
        return (value >> 8) & 0xf;
    }

    public static int nibble3(int value) {
        return (value >> 12) & 0xf;
    }
}
