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
package com.tencent.tinker.android.dx.instruction;

/**
 * All the Dalvik opcode value constants. See the related spec
 * document for the meaning and instruction format of each opcode.
 */
public final class Opcodes {

    /**
     * pseudo-opcode used for nonstandard format payload "instructions". TODO:
     * Retire this concept, and start treating the payload instructions
     * more like the rest.
     */
    public static final int SPECIAL_FORMAT = -1;

    // BEGIN(opcodes); GENERATED AUTOMATICALLY BY opcode-gen
    public static final int NOP = 0x00;

    public static final int MOVE = 0x01;

    public static final int MOVE_FROM16 = 0x02;

    public static final int MOVE_16 = 0x03;

    public static final int MOVE_WIDE = 0x04;

    public static final int MOVE_WIDE_FROM16 = 0x05;

    public static final int MOVE_WIDE_16 = 0x06;

    public static final int MOVE_OBJECT = 0x07;

    public static final int MOVE_OBJECT_FROM16 = 0x08;

    public static final int MOVE_OBJECT_16 = 0x09;

    public static final int MOVE_RESULT = 0x0a;

    public static final int MOVE_RESULT_WIDE = 0x0b;

    public static final int MOVE_RESULT_OBJECT = 0x0c;

    public static final int MOVE_EXCEPTION = 0x0d;

    public static final int RETURN_VOID = 0x0e;

    public static final int RETURN = 0x0f;

    public static final int RETURN_WIDE = 0x10;

    public static final int RETURN_OBJECT = 0x11;

    public static final int CONST_4 = 0x12;

    public static final int CONST_16 = 0x13;

    public static final int CONST = 0x14;

    public static final int CONST_HIGH16 = 0x15;

    public static final int CONST_WIDE_16 = 0x16;

    public static final int CONST_WIDE_32 = 0x17;

    public static final int CONST_WIDE = 0x18;

    public static final int CONST_WIDE_HIGH16 = 0x19;

    public static final int CONST_STRING = 0x1a;

    public static final int CONST_STRING_JUMBO = 0x1b;

    public static final int CONST_CLASS = 0x1c;

    public static final int MONITOR_ENTER = 0x1d;

    public static final int MONITOR_EXIT = 0x1e;

    public static final int CHECK_CAST = 0x1f;

    public static final int INSTANCE_OF = 0x20;

    public static final int ARRAY_LENGTH = 0x21;

    public static final int NEW_INSTANCE = 0x22;

    public static final int NEW_ARRAY = 0x23;

    public static final int FILLED_NEW_ARRAY = 0x24;

    public static final int FILLED_NEW_ARRAY_RANGE = 0x25;

    public static final int FILL_ARRAY_DATA = 0x26;

    public static final int THROW = 0x27;

    public static final int GOTO = 0x28;

    public static final int GOTO_16 = 0x29;

    public static final int GOTO_32 = 0x2a;

    public static final int PACKED_SWITCH = 0x2b;

    public static final int SPARSE_SWITCH = 0x2c;

    public static final int CMPL_FLOAT = 0x2d;

    public static final int CMPG_FLOAT = 0x2e;

    public static final int CMPL_DOUBLE = 0x2f;

    public static final int CMPG_DOUBLE = 0x30;

    public static final int CMP_LONG = 0x31;

    public static final int IF_EQ = 0x32;

    public static final int IF_NE = 0x33;

    public static final int IF_LT = 0x34;

    public static final int IF_GE = 0x35;

    public static final int IF_GT = 0x36;

    public static final int IF_LE = 0x37;

    public static final int IF_EQZ = 0x38;

    public static final int IF_NEZ = 0x39;

    public static final int IF_LTZ = 0x3a;

    public static final int IF_GEZ = 0x3b;

    public static final int IF_GTZ = 0x3c;

    public static final int IF_LEZ = 0x3d;

    public static final int AGET = 0x44;

    public static final int AGET_WIDE = 0x45;

    public static final int AGET_OBJECT = 0x46;

    public static final int AGET_BOOLEAN = 0x47;

    public static final int AGET_BYTE = 0x48;

    public static final int AGET_CHAR = 0x49;

    public static final int AGET_SHORT = 0x4a;

    public static final int APUT = 0x4b;

    public static final int APUT_WIDE = 0x4c;

    public static final int APUT_OBJECT = 0x4d;

    public static final int APUT_BOOLEAN = 0x4e;

    public static final int APUT_BYTE = 0x4f;

    public static final int APUT_CHAR = 0x50;

    public static final int APUT_SHORT = 0x51;

    public static final int IGET = 0x52;

    public static final int IGET_WIDE = 0x53;

    public static final int IGET_OBJECT = 0x54;

    public static final int IGET_BOOLEAN = 0x55;

    public static final int IGET_BYTE = 0x56;

    public static final int IGET_CHAR = 0x57;

    public static final int IGET_SHORT = 0x58;

    public static final int IPUT = 0x59;

    public static final int IPUT_WIDE = 0x5a;

    public static final int IPUT_OBJECT = 0x5b;

    public static final int IPUT_BOOLEAN = 0x5c;

    public static final int IPUT_BYTE = 0x5d;

    public static final int IPUT_CHAR = 0x5e;

    public static final int IPUT_SHORT = 0x5f;

    public static final int SGET = 0x60;

    public static final int SGET_WIDE = 0x61;

    public static final int SGET_OBJECT = 0x62;

    public static final int SGET_BOOLEAN = 0x63;

    public static final int SGET_BYTE = 0x64;

    public static final int SGET_CHAR = 0x65;

    public static final int SGET_SHORT = 0x66;

    public static final int SPUT = 0x67;

    public static final int SPUT_WIDE = 0x68;

    public static final int SPUT_OBJECT = 0x69;

    public static final int SPUT_BOOLEAN = 0x6a;

    public static final int SPUT_BYTE = 0x6b;

    public static final int SPUT_CHAR = 0x6c;

    public static final int SPUT_SHORT = 0x6d;

    public static final int INVOKE_VIRTUAL = 0x6e;

    public static final int INVOKE_SUPER = 0x6f;

    public static final int INVOKE_DIRECT = 0x70;

    public static final int INVOKE_STATIC = 0x71;

    public static final int INVOKE_INTERFACE = 0x72;

    public static final int INVOKE_VIRTUAL_RANGE = 0x74;

    public static final int INVOKE_SUPER_RANGE = 0x75;

    public static final int INVOKE_DIRECT_RANGE = 0x76;

    public static final int INVOKE_STATIC_RANGE = 0x77;

    public static final int INVOKE_INTERFACE_RANGE = 0x78;

    public static final int NEG_INT = 0x7b;

    public static final int NOT_INT = 0x7c;

    public static final int NEG_LONG = 0x7d;

    public static final int NOT_LONG = 0x7e;

    public static final int NEG_FLOAT = 0x7f;

    public static final int NEG_DOUBLE = 0x80;

    public static final int INT_TO_LONG = 0x81;

    public static final int INT_TO_FLOAT = 0x82;

    public static final int INT_TO_DOUBLE = 0x83;

    public static final int LONG_TO_INT = 0x84;

    public static final int LONG_TO_FLOAT = 0x85;

    public static final int LONG_TO_DOUBLE = 0x86;

    public static final int FLOAT_TO_INT = 0x87;

    public static final int FLOAT_TO_LONG = 0x88;

    public static final int FLOAT_TO_DOUBLE = 0x89;

    public static final int DOUBLE_TO_INT = 0x8a;

    public static final int DOUBLE_TO_LONG = 0x8b;

    public static final int DOUBLE_TO_FLOAT = 0x8c;

    public static final int INT_TO_BYTE = 0x8d;

    public static final int INT_TO_CHAR = 0x8e;

    public static final int INT_TO_SHORT = 0x8f;

    public static final int ADD_INT = 0x90;

    public static final int SUB_INT = 0x91;

    public static final int MUL_INT = 0x92;

    public static final int DIV_INT = 0x93;

    public static final int REM_INT = 0x94;

    public static final int AND_INT = 0x95;

    public static final int OR_INT = 0x96;

    public static final int XOR_INT = 0x97;

    public static final int SHL_INT = 0x98;

    public static final int SHR_INT = 0x99;

    public static final int USHR_INT = 0x9a;

    public static final int ADD_LONG = 0x9b;

    public static final int SUB_LONG = 0x9c;

    public static final int MUL_LONG = 0x9d;

    public static final int DIV_LONG = 0x9e;

    public static final int REM_LONG = 0x9f;

    public static final int AND_LONG = 0xa0;

    public static final int OR_LONG = 0xa1;

    public static final int XOR_LONG = 0xa2;

    public static final int SHL_LONG = 0xa3;

    public static final int SHR_LONG = 0xa4;

    public static final int USHR_LONG = 0xa5;

    public static final int ADD_FLOAT = 0xa6;

    public static final int SUB_FLOAT = 0xa7;

    public static final int MUL_FLOAT = 0xa8;

    public static final int DIV_FLOAT = 0xa9;

    public static final int REM_FLOAT = 0xaa;

    public static final int ADD_DOUBLE = 0xab;

    public static final int SUB_DOUBLE = 0xac;

    public static final int MUL_DOUBLE = 0xad;

    public static final int DIV_DOUBLE = 0xae;

    public static final int REM_DOUBLE = 0xaf;

    public static final int ADD_INT_2ADDR = 0xb0;

    public static final int SUB_INT_2ADDR = 0xb1;

    public static final int MUL_INT_2ADDR = 0xb2;

    public static final int DIV_INT_2ADDR = 0xb3;

    public static final int REM_INT_2ADDR = 0xb4;

    public static final int AND_INT_2ADDR = 0xb5;

    public static final int OR_INT_2ADDR = 0xb6;

    public static final int XOR_INT_2ADDR = 0xb7;

    public static final int SHL_INT_2ADDR = 0xb8;

    public static final int SHR_INT_2ADDR = 0xb9;

    public static final int USHR_INT_2ADDR = 0xba;

    public static final int ADD_LONG_2ADDR = 0xbb;

    public static final int SUB_LONG_2ADDR = 0xbc;

    public static final int MUL_LONG_2ADDR = 0xbd;

    public static final int DIV_LONG_2ADDR = 0xbe;

    public static final int REM_LONG_2ADDR = 0xbf;

    public static final int AND_LONG_2ADDR = 0xc0;

    public static final int OR_LONG_2ADDR = 0xc1;

    public static final int XOR_LONG_2ADDR = 0xc2;

    public static final int SHL_LONG_2ADDR = 0xc3;

    public static final int SHR_LONG_2ADDR = 0xc4;

    public static final int USHR_LONG_2ADDR = 0xc5;

    public static final int ADD_FLOAT_2ADDR = 0xc6;

    public static final int SUB_FLOAT_2ADDR = 0xc7;

    public static final int MUL_FLOAT_2ADDR = 0xc8;

    public static final int DIV_FLOAT_2ADDR = 0xc9;

    public static final int REM_FLOAT_2ADDR = 0xca;

    public static final int ADD_DOUBLE_2ADDR = 0xcb;

    public static final int SUB_DOUBLE_2ADDR = 0xcc;

    public static final int MUL_DOUBLE_2ADDR = 0xcd;

    public static final int DIV_DOUBLE_2ADDR = 0xce;

    public static final int REM_DOUBLE_2ADDR = 0xcf;

    public static final int ADD_INT_LIT16 = 0xd0;

    public static final int RSUB_INT = 0xd1;

    public static final int MUL_INT_LIT16 = 0xd2;

    public static final int DIV_INT_LIT16 = 0xd3;

    public static final int REM_INT_LIT16 = 0xd4;

    public static final int AND_INT_LIT16 = 0xd5;

    public static final int OR_INT_LIT16 = 0xd6;

    public static final int XOR_INT_LIT16 = 0xd7;

    public static final int ADD_INT_LIT8 = 0xd8;

    public static final int RSUB_INT_LIT8 = 0xd9;

    public static final int MUL_INT_LIT8 = 0xda;

    public static final int DIV_INT_LIT8 = 0xdb;

    public static final int REM_INT_LIT8 = 0xdc;

    public static final int AND_INT_LIT8 = 0xdd;

    public static final int OR_INT_LIT8 = 0xde;

    public static final int XOR_INT_LIT8 = 0xdf;

    public static final int SHL_INT_LIT8 = 0xe0;

    public static final int SHR_INT_LIT8 = 0xe1;

    public static final int USHR_INT_LIT8 = 0xe2;

    // END(opcodes)
    // TODO: Generate these payload opcodes with opcode-gen.
    /**
     * special pseudo-opcode value for packed-switch data payload
     * instructions
     */
    public static final int PACKED_SWITCH_PAYLOAD = 0x100;

    /**
     * special pseudo-opcode value for packed-switch data payload
     * instructions
     */
    public static final int SPARSE_SWITCH_PAYLOAD = 0x200;

    /**
     * special pseudo-opcode value for fill-array-data data payload
     * instructions
     */
    public static final int FILL_ARRAY_DATA_PAYLOAD = 0x300;

    /**
     * This class is uninstantiable.
     */
    private Opcodes() {
        // This space intentionally left blank.
    }

    /**
     * Gets the opcode out of an opcode unit, the latter of which may also
     * include one or more argument values.
     *
     * @param opcodeUnit the opcode-containing code unit
     * @return the extracted opcode
     */
    public static int extractOpcodeFromUnit(int opcodeUnit) {
        /*
         * Note: This method bakes in knowledge that all opcodes are
         * either single-byte or of the forms (byteValue << 8) or
         * ((byteValue << 8) | 0xff).
         */
        int lowByte = opcodeUnit & 0xff;
        return ((lowByte == 0) || (lowByte == 0xff)) ? opcodeUnit : lowByte;
    }
}
