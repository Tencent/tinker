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

import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dx.util.Hex;

/**
 * Encode/Decode instruction opcode.
 */
public final class InstructionCodec {
    /** "Unknown." Used for undefined opcodes. */
    public static final int INDEX_TYPE_UNKNOWN = 0;
    /** no index used */
    public static final int INDEX_TYPE_NONE = 1;
    /** type reference index */
    public static final int INDEX_TYPE_TYPE_REF = 2;
    /** string reference index */
    public static final int INDEX_TYPE_STRING_REF = 3;
    /** method reference index */
    public static final int INDEX_TYPE_METHOD_REF = 4;
    /** field reference index */
    public static final int INDEX_TYPE_FIELD_REF = 5;
    /** "Unknown." Used for undefined opcodes. */
    public static final int INSN_FORMAT_UNKNOWN = 0;
    public static final int INSN_FORMAT_00X = 1;
    public static final int INSN_FORMAT_10T = 2;
    public static final int INSN_FORMAT_10X = 3;
    public static final int INSN_FORMAT_11N = 4;
    public static final int INSN_FORMAT_11X = 5;
    public static final int INSN_FORMAT_12X = 6;
    public static final int INSN_FORMAT_20T = 7;
    public static final int INSN_FORMAT_21C = 8;
    public static final int INSN_FORMAT_21H = 9;
    public static final int INSN_FORMAT_21S = 10;
    public static final int INSN_FORMAT_21T = 11;
    public static final int INSN_FORMAT_22B = 12;
    public static final int INSN_FORMAT_22C = 13;
    public static final int INSN_FORMAT_22S = 14;
    public static final int INSN_FORMAT_22T = 15;
    public static final int INSN_FORMAT_22X = 16;
    public static final int INSN_FORMAT_23X = 17;
    public static final int INSN_FORMAT_30T = 18;
    public static final int INSN_FORMAT_31C = 19;
    public static final int INSN_FORMAT_31I = 20;
    public static final int INSN_FORMAT_31T = 21;
    public static final int INSN_FORMAT_32X = 22;
    public static final int INSN_FORMAT_35C = 23;
    public static final int INSN_FORMAT_3RC = 24;
    public static final int INSN_FORMAT_51L = 25;
    public static final int INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD = 26;
    public static final int INSN_FORMAT_PACKED_SWITCH_PAYLOAD = 27;
    public static final int INSN_FORMAT_SPARSE_SWITCH_PAYLOAD = 28;
    private InstructionCodec() {
        throw new UnsupportedOperationException();
    }

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

    public static int getTargetByte(int target, int baseAddress) {
        int relativeTarget = getTarget(target, baseAddress);

        if (relativeTarget != (byte) relativeTarget) {
            throw new DexException(
                    "Target out of range: "
                            + Hex.s4(relativeTarget)
                            + ", perhaps you need to enable force jumbo mode."
            );
        }

        return relativeTarget & 0xff;
    }

    public static short getTargetUnit(int target, int baseAddress) {
        int relativeTarget = getTarget(target, baseAddress);

        if (relativeTarget != (short) relativeTarget) {
            throw new DexException(
                    "Target out of range: "
                            + Hex.s4(relativeTarget)
                            + ", perhaps you need to enable force jumbo mode."
            );
        }

        return (short) relativeTarget;
    }

    public static int getTarget(int target, int baseAddress) {
        return target - baseAddress;
    }

    public static int getLiteralByte(long literal) {
        if (literal != (byte) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal & 0xff;
    }

    public static short getLiteralUnit(long literal) {
        if (literal != (short) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (short) literal;
    }

    public static int getLiteralInt(long literal) {
        if (literal != (int) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal;
    }

    public static int getLiteralNibble(long literal) {
        if ((literal < -8) || (literal > 7)) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal & 0xf;
    }

    /**
     * Gets the A register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    public static short getAUnit(int a) {
        if ((a & ~0xffff) != 0) {
            throw new DexException("Register A out of range: " + Hex.u8(a));
        }

        return (short) a;
    }

    /**
     * Gets the B register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    public static short getBUnit(int b) {
        if ((b & ~0xffff) != 0) {
            throw new DexException("Register B out of range: " + Hex.u8(b));
        }

        return (short) b;
    }

    public static int getInstructionIndexType(int opcode) {
        switch (opcode) {
            case Opcodes.CONST_STRING:
            case Opcodes.CONST_STRING_JUMBO: {
                return INDEX_TYPE_STRING_REF;
            }
            case Opcodes.CONST_CLASS:
            case Opcodes.CHECK_CAST:
            case Opcodes.INSTANCE_OF:
            case Opcodes.NEW_INSTANCE:
            case Opcodes.NEW_ARRAY:
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.FILLED_NEW_ARRAY_RANGE: {
                return INDEX_TYPE_TYPE_REF;
            }
            case Opcodes.IGET:
            case Opcodes.IGET_WIDE:
            case Opcodes.IGET_OBJECT:
            case Opcodes.IGET_BOOLEAN:
            case Opcodes.IGET_BYTE:
            case Opcodes.IGET_CHAR:
            case Opcodes.IGET_SHORT:
            case Opcodes.IPUT:
            case Opcodes.IPUT_WIDE:
            case Opcodes.IPUT_OBJECT:
            case Opcodes.IPUT_BOOLEAN:
            case Opcodes.IPUT_BYTE:
            case Opcodes.IPUT_CHAR:
            case Opcodes.IPUT_SHORT:
            case Opcodes.SGET:
            case Opcodes.SGET_WIDE:
            case Opcodes.SGET_OBJECT:
            case Opcodes.SGET_BOOLEAN:
            case Opcodes.SGET_BYTE:
            case Opcodes.SGET_CHAR:
            case Opcodes.SGET_SHORT:
            case Opcodes.SPUT:
            case Opcodes.SPUT_WIDE:
            case Opcodes.SPUT_OBJECT:
            case Opcodes.SPUT_BOOLEAN:
            case Opcodes.SPUT_BYTE:
            case Opcodes.SPUT_CHAR:
            case Opcodes.SPUT_SHORT: {
                return INDEX_TYPE_FIELD_REF;
            }
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE:
            case Opcodes.INVOKE_VIRTUAL_RANGE:
            case Opcodes.INVOKE_SUPER_RANGE:
            case Opcodes.INVOKE_DIRECT_RANGE:
            case Opcodes.INVOKE_STATIC_RANGE:
            case Opcodes.INVOKE_INTERFACE_RANGE: {
                return INDEX_TYPE_METHOD_REF;
            }
            case Opcodes.SPECIAL_FORMAT:
            case Opcodes.PACKED_SWITCH_PAYLOAD:
            case Opcodes.SPARSE_SWITCH_PAYLOAD:
            case Opcodes.FILL_ARRAY_DATA_PAYLOAD:
            case Opcodes.NOP:
            case Opcodes.MOVE:
            case Opcodes.MOVE_FROM16:
            case Opcodes.MOVE_16:
            case Opcodes.MOVE_WIDE:
            case Opcodes.MOVE_WIDE_FROM16:
            case Opcodes.MOVE_WIDE_16:
            case Opcodes.MOVE_OBJECT:
            case Opcodes.MOVE_OBJECT_FROM16:
            case Opcodes.MOVE_OBJECT_16:
            case Opcodes.MOVE_RESULT:
            case Opcodes.MOVE_RESULT_WIDE:
            case Opcodes.MOVE_RESULT_OBJECT:
            case Opcodes.MOVE_EXCEPTION:
            case Opcodes.RETURN_VOID:
            case Opcodes.RETURN:
            case Opcodes.RETURN_WIDE:
            case Opcodes.RETURN_OBJECT:
            case Opcodes.CONST_4:
            case Opcodes.CONST_16:
            case Opcodes.CONST:
            case Opcodes.CONST_HIGH16:
            case Opcodes.CONST_WIDE_16:
            case Opcodes.CONST_WIDE_32:
            case Opcodes.CONST_WIDE:
            case Opcodes.CONST_WIDE_HIGH16:
            case Opcodes.MONITOR_ENTER:
            case Opcodes.MONITOR_EXIT:
            case Opcodes.ARRAY_LENGTH:
            case Opcodes.FILL_ARRAY_DATA:
            case Opcodes.THROW:
            case Opcodes.GOTO:
            case Opcodes.GOTO_16:
            case Opcodes.GOTO_32:
            case Opcodes.PACKED_SWITCH:
            case Opcodes.SPARSE_SWITCH:
            case Opcodes.CMPL_FLOAT:
            case Opcodes.CMPG_FLOAT:
            case Opcodes.CMPL_DOUBLE:
            case Opcodes.CMPG_DOUBLE:
            case Opcodes.CMP_LONG:
            case Opcodes.IF_EQ:
            case Opcodes.IF_NE:
            case Opcodes.IF_LT:
            case Opcodes.IF_GE:
            case Opcodes.IF_GT:
            case Opcodes.IF_LE:
            case Opcodes.IF_EQZ:
            case Opcodes.IF_NEZ:
            case Opcodes.IF_LTZ:
            case Opcodes.IF_GEZ:
            case Opcodes.IF_GTZ:
            case Opcodes.IF_LEZ:
            case Opcodes.AGET:
            case Opcodes.AGET_WIDE:
            case Opcodes.AGET_OBJECT:
            case Opcodes.AGET_BOOLEAN:
            case Opcodes.AGET_BYTE:
            case Opcodes.AGET_CHAR:
            case Opcodes.AGET_SHORT:
            case Opcodes.APUT:
            case Opcodes.APUT_WIDE:
            case Opcodes.APUT_OBJECT:
            case Opcodes.APUT_BOOLEAN:
            case Opcodes.APUT_BYTE:
            case Opcodes.APUT_CHAR:
            case Opcodes.APUT_SHORT:
            case Opcodes.NEG_INT:
            case Opcodes.NOT_INT:
            case Opcodes.NEG_LONG:
            case Opcodes.NOT_LONG:
            case Opcodes.NEG_FLOAT:
            case Opcodes.NEG_DOUBLE:
            case Opcodes.INT_TO_LONG:
            case Opcodes.INT_TO_FLOAT:
            case Opcodes.INT_TO_DOUBLE:
            case Opcodes.LONG_TO_INT:
            case Opcodes.LONG_TO_FLOAT:
            case Opcodes.LONG_TO_DOUBLE:
            case Opcodes.FLOAT_TO_INT:
            case Opcodes.FLOAT_TO_LONG:
            case Opcodes.FLOAT_TO_DOUBLE:
            case Opcodes.DOUBLE_TO_INT:
            case Opcodes.DOUBLE_TO_LONG:
            case Opcodes.DOUBLE_TO_FLOAT:
            case Opcodes.INT_TO_BYTE:
            case Opcodes.INT_TO_CHAR:
            case Opcodes.INT_TO_SHORT:
            case Opcodes.ADD_INT:
            case Opcodes.SUB_INT:
            case Opcodes.MUL_INT:
            case Opcodes.DIV_INT:
            case Opcodes.REM_INT:
            case Opcodes.AND_INT:
            case Opcodes.OR_INT:
            case Opcodes.XOR_INT:
            case Opcodes.SHL_INT:
            case Opcodes.SHR_INT:
            case Opcodes.USHR_INT:
            case Opcodes.ADD_LONG:
            case Opcodes.SUB_LONG:
            case Opcodes.MUL_LONG:
            case Opcodes.DIV_LONG:
            case Opcodes.REM_LONG:
            case Opcodes.AND_LONG:
            case Opcodes.OR_LONG:
            case Opcodes.XOR_LONG:
            case Opcodes.SHL_LONG:
            case Opcodes.SHR_LONG:
            case Opcodes.USHR_LONG:
            case Opcodes.ADD_FLOAT:
            case Opcodes.SUB_FLOAT:
            case Opcodes.MUL_FLOAT:
            case Opcodes.DIV_FLOAT:
            case Opcodes.REM_FLOAT:
            case Opcodes.ADD_DOUBLE:
            case Opcodes.SUB_DOUBLE:
            case Opcodes.MUL_DOUBLE:
            case Opcodes.DIV_DOUBLE:
            case Opcodes.REM_DOUBLE:
            case Opcodes.ADD_INT_2ADDR:
            case Opcodes.SUB_INT_2ADDR:
            case Opcodes.MUL_INT_2ADDR:
            case Opcodes.DIV_INT_2ADDR:
            case Opcodes.REM_INT_2ADDR:
            case Opcodes.AND_INT_2ADDR:
            case Opcodes.OR_INT_2ADDR:
            case Opcodes.XOR_INT_2ADDR:
            case Opcodes.SHL_INT_2ADDR:
            case Opcodes.SHR_INT_2ADDR:
            case Opcodes.USHR_INT_2ADDR:
            case Opcodes.ADD_LONG_2ADDR:
            case Opcodes.SUB_LONG_2ADDR:
            case Opcodes.MUL_LONG_2ADDR:
            case Opcodes.DIV_LONG_2ADDR:
            case Opcodes.REM_LONG_2ADDR:
            case Opcodes.AND_LONG_2ADDR:
            case Opcodes.OR_LONG_2ADDR:
            case Opcodes.XOR_LONG_2ADDR:
            case Opcodes.SHL_LONG_2ADDR:
            case Opcodes.SHR_LONG_2ADDR:
            case Opcodes.USHR_LONG_2ADDR:
            case Opcodes.ADD_FLOAT_2ADDR:
            case Opcodes.SUB_FLOAT_2ADDR:
            case Opcodes.MUL_FLOAT_2ADDR:
            case Opcodes.DIV_FLOAT_2ADDR:
            case Opcodes.REM_FLOAT_2ADDR:
            case Opcodes.ADD_DOUBLE_2ADDR:
            case Opcodes.SUB_DOUBLE_2ADDR:
            case Opcodes.MUL_DOUBLE_2ADDR:
            case Opcodes.DIV_DOUBLE_2ADDR:
            case Opcodes.REM_DOUBLE_2ADDR:
            case Opcodes.ADD_INT_LIT16:
            case Opcodes.RSUB_INT:
            case Opcodes.MUL_INT_LIT16:
            case Opcodes.DIV_INT_LIT16:
            case Opcodes.REM_INT_LIT16:
            case Opcodes.AND_INT_LIT16:
            case Opcodes.OR_INT_LIT16:
            case Opcodes.XOR_INT_LIT16:
            case Opcodes.ADD_INT_LIT8:
            case Opcodes.RSUB_INT_LIT8:
            case Opcodes.MUL_INT_LIT8:
            case Opcodes.DIV_INT_LIT8:
            case Opcodes.REM_INT_LIT8:
            case Opcodes.AND_INT_LIT8:
            case Opcodes.OR_INT_LIT8:
            case Opcodes.XOR_INT_LIT8:
            case Opcodes.SHL_INT_LIT8:
            case Opcodes.SHR_INT_LIT8:
            case Opcodes.USHR_INT_LIT8: {
                return INDEX_TYPE_NONE;
            }
            default: {
                return INDEX_TYPE_UNKNOWN;
            }
        }
    }

    public static int getInstructionFormat(int opcode) {
        switch (opcode) {
            case Opcodes.SPECIAL_FORMAT: {
                return INSN_FORMAT_00X;
            }
            case Opcodes.GOTO: {
                return INSN_FORMAT_10T;
            }
            case Opcodes.NOP:
            case Opcodes.RETURN_VOID: {
                return INSN_FORMAT_10X;
            }
            case Opcodes.CONST_4: {
                return INSN_FORMAT_11N;
            }
            case Opcodes.MOVE_RESULT:
            case Opcodes.MOVE_RESULT_WIDE:
            case Opcodes.MOVE_RESULT_OBJECT:
            case Opcodes.MOVE_EXCEPTION:
            case Opcodes.RETURN:
            case Opcodes.RETURN_WIDE:
            case Opcodes.RETURN_OBJECT:
            case Opcodes.MONITOR_ENTER:
            case Opcodes.MONITOR_EXIT:
            case Opcodes.THROW: {
                return INSN_FORMAT_11X;
            }
            case Opcodes.MOVE:
            case Opcodes.MOVE_WIDE:
            case Opcodes.MOVE_OBJECT:
            case Opcodes.ARRAY_LENGTH:
            case Opcodes.NEG_INT:
            case Opcodes.NOT_INT:
            case Opcodes.NEG_LONG:
            case Opcodes.NOT_LONG:
            case Opcodes.NEG_FLOAT:
            case Opcodes.NEG_DOUBLE:
            case Opcodes.INT_TO_LONG:
            case Opcodes.INT_TO_FLOAT:
            case Opcodes.INT_TO_DOUBLE:
            case Opcodes.LONG_TO_INT:
            case Opcodes.LONG_TO_FLOAT:
            case Opcodes.LONG_TO_DOUBLE:
            case Opcodes.FLOAT_TO_INT:
            case Opcodes.FLOAT_TO_LONG:
            case Opcodes.FLOAT_TO_DOUBLE:
            case Opcodes.DOUBLE_TO_INT:
            case Opcodes.DOUBLE_TO_LONG:
            case Opcodes.DOUBLE_TO_FLOAT:
            case Opcodes.INT_TO_BYTE:
            case Opcodes.INT_TO_CHAR:
            case Opcodes.INT_TO_SHORT:
            case Opcodes.ADD_INT_2ADDR:
            case Opcodes.SUB_INT_2ADDR:
            case Opcodes.MUL_INT_2ADDR:
            case Opcodes.DIV_INT_2ADDR:
            case Opcodes.REM_INT_2ADDR:
            case Opcodes.AND_INT_2ADDR:
            case Opcodes.OR_INT_2ADDR:
            case Opcodes.XOR_INT_2ADDR:
            case Opcodes.SHL_INT_2ADDR:
            case Opcodes.SHR_INT_2ADDR:
            case Opcodes.USHR_INT_2ADDR:
            case Opcodes.ADD_LONG_2ADDR:
            case Opcodes.SUB_LONG_2ADDR:
            case Opcodes.MUL_LONG_2ADDR:
            case Opcodes.DIV_LONG_2ADDR:
            case Opcodes.REM_LONG_2ADDR:
            case Opcodes.AND_LONG_2ADDR:
            case Opcodes.OR_LONG_2ADDR:
            case Opcodes.XOR_LONG_2ADDR:
            case Opcodes.SHL_LONG_2ADDR:
            case Opcodes.SHR_LONG_2ADDR:
            case Opcodes.USHR_LONG_2ADDR:
            case Opcodes.ADD_FLOAT_2ADDR:
            case Opcodes.SUB_FLOAT_2ADDR:
            case Opcodes.MUL_FLOAT_2ADDR:
            case Opcodes.DIV_FLOAT_2ADDR:
            case Opcodes.REM_FLOAT_2ADDR:
            case Opcodes.ADD_DOUBLE_2ADDR:
            case Opcodes.SUB_DOUBLE_2ADDR:
            case Opcodes.MUL_DOUBLE_2ADDR:
            case Opcodes.DIV_DOUBLE_2ADDR:
            case Opcodes.REM_DOUBLE_2ADDR: {
                return INSN_FORMAT_12X;
            }
            case Opcodes.GOTO_16: {
                return INSN_FORMAT_20T;
            }
            case Opcodes.CONST_STRING:
            case Opcodes.CONST_CLASS:
            case Opcodes.CHECK_CAST:
            case Opcodes.NEW_INSTANCE:
            case Opcodes.SGET:
            case Opcodes.SGET_WIDE:
            case Opcodes.SGET_OBJECT:
            case Opcodes.SGET_BOOLEAN:
            case Opcodes.SGET_BYTE:
            case Opcodes.SGET_CHAR:
            case Opcodes.SGET_SHORT:
            case Opcodes.SPUT:
            case Opcodes.SPUT_WIDE:
            case Opcodes.SPUT_OBJECT:
            case Opcodes.SPUT_BOOLEAN:
            case Opcodes.SPUT_BYTE:
            case Opcodes.SPUT_CHAR:
            case Opcodes.SPUT_SHORT: {
                return INSN_FORMAT_21C;
            }
            case Opcodes.CONST_HIGH16:
            case Opcodes.CONST_WIDE_HIGH16: {
                return INSN_FORMAT_21H;
            }
            case Opcodes.CONST_16:
            case Opcodes.CONST_WIDE_16: {
                return INSN_FORMAT_21S;
            }
            case Opcodes.IF_EQZ:
            case Opcodes.IF_NEZ:
            case Opcodes.IF_LTZ:
            case Opcodes.IF_GEZ:
            case Opcodes.IF_GTZ:
            case Opcodes.IF_LEZ: {
                return INSN_FORMAT_21T;
            }
            case Opcodes.ADD_INT_LIT8:
            case Opcodes.RSUB_INT_LIT8:
            case Opcodes.MUL_INT_LIT8:
            case Opcodes.DIV_INT_LIT8:
            case Opcodes.REM_INT_LIT8:
            case Opcodes.AND_INT_LIT8:
            case Opcodes.OR_INT_LIT8:
            case Opcodes.XOR_INT_LIT8:
            case Opcodes.SHL_INT_LIT8:
            case Opcodes.SHR_INT_LIT8:
            case Opcodes.USHR_INT_LIT8: {
                return INSN_FORMAT_22B;
            }
            case Opcodes.INSTANCE_OF:
            case Opcodes.NEW_ARRAY:
            case Opcodes.IGET:
            case Opcodes.IGET_WIDE:
            case Opcodes.IGET_OBJECT:
            case Opcodes.IGET_BOOLEAN:
            case Opcodes.IGET_BYTE:
            case Opcodes.IGET_CHAR:
            case Opcodes.IGET_SHORT:
            case Opcodes.IPUT:
            case Opcodes.IPUT_WIDE:
            case Opcodes.IPUT_OBJECT:
            case Opcodes.IPUT_BOOLEAN:
            case Opcodes.IPUT_BYTE:
            case Opcodes.IPUT_CHAR:
            case Opcodes.IPUT_SHORT: {
                return INSN_FORMAT_22C;
            }
            case Opcodes.ADD_INT_LIT16:
            case Opcodes.RSUB_INT:
            case Opcodes.MUL_INT_LIT16:
            case Opcodes.DIV_INT_LIT16:
            case Opcodes.REM_INT_LIT16:
            case Opcodes.AND_INT_LIT16:
            case Opcodes.OR_INT_LIT16:
            case Opcodes.XOR_INT_LIT16: {
                return INSN_FORMAT_22S;
            }
            case Opcodes.IF_EQ:
            case Opcodes.IF_NE:
            case Opcodes.IF_LT:
            case Opcodes.IF_GE:
            case Opcodes.IF_GT:
            case Opcodes.IF_LE: {
                return INSN_FORMAT_22T;
            }
            case Opcodes.MOVE_FROM16:
            case Opcodes.MOVE_WIDE_FROM16:
            case Opcodes.MOVE_OBJECT_FROM16: {
                return INSN_FORMAT_22X;
            }
            case Opcodes.CMPL_FLOAT:
            case Opcodes.CMPG_FLOAT:
            case Opcodes.CMPL_DOUBLE:
            case Opcodes.CMPG_DOUBLE:
            case Opcodes.CMP_LONG:
            case Opcodes.AGET:
            case Opcodes.AGET_WIDE:
            case Opcodes.AGET_OBJECT:
            case Opcodes.AGET_BOOLEAN:
            case Opcodes.AGET_BYTE:
            case Opcodes.AGET_CHAR:
            case Opcodes.AGET_SHORT:
            case Opcodes.APUT:
            case Opcodes.APUT_WIDE:
            case Opcodes.APUT_OBJECT:
            case Opcodes.APUT_BOOLEAN:
            case Opcodes.APUT_BYTE:
            case Opcodes.APUT_CHAR:
            case Opcodes.APUT_SHORT:
            case Opcodes.ADD_INT:
            case Opcodes.SUB_INT:
            case Opcodes.MUL_INT:
            case Opcodes.DIV_INT:
            case Opcodes.REM_INT:
            case Opcodes.AND_INT:
            case Opcodes.OR_INT:
            case Opcodes.XOR_INT:
            case Opcodes.SHL_INT:
            case Opcodes.SHR_INT:
            case Opcodes.USHR_INT:
            case Opcodes.ADD_LONG:
            case Opcodes.SUB_LONG:
            case Opcodes.MUL_LONG:
            case Opcodes.DIV_LONG:
            case Opcodes.REM_LONG:
            case Opcodes.AND_LONG:
            case Opcodes.OR_LONG:
            case Opcodes.XOR_LONG:
            case Opcodes.SHL_LONG:
            case Opcodes.SHR_LONG:
            case Opcodes.USHR_LONG:
            case Opcodes.ADD_FLOAT:
            case Opcodes.SUB_FLOAT:
            case Opcodes.MUL_FLOAT:
            case Opcodes.DIV_FLOAT:
            case Opcodes.REM_FLOAT:
            case Opcodes.ADD_DOUBLE:
            case Opcodes.SUB_DOUBLE:
            case Opcodes.MUL_DOUBLE:
            case Opcodes.DIV_DOUBLE:
            case Opcodes.REM_DOUBLE: {
                return INSN_FORMAT_23X;
            }
            case Opcodes.GOTO_32: {
                return INSN_FORMAT_30T;
            }
            case Opcodes.CONST_STRING_JUMBO: {
                return INSN_FORMAT_31C;
            }
            case Opcodes.CONST:
            case Opcodes.CONST_WIDE_32: {
                return INSN_FORMAT_31I;
            }
            case Opcodes.FILL_ARRAY_DATA:
            case Opcodes.PACKED_SWITCH:
            case Opcodes.SPARSE_SWITCH: {
                return INSN_FORMAT_31T;
            }
            case Opcodes.MOVE_16:
            case Opcodes.MOVE_WIDE_16:
            case Opcodes.MOVE_OBJECT_16: {
                return INSN_FORMAT_32X;
            }
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                return INSN_FORMAT_35C;
            }
            case Opcodes.FILLED_NEW_ARRAY_RANGE:
            case Opcodes.INVOKE_VIRTUAL_RANGE:
            case Opcodes.INVOKE_SUPER_RANGE:
            case Opcodes.INVOKE_DIRECT_RANGE:
            case Opcodes.INVOKE_STATIC_RANGE:
            case Opcodes.INVOKE_INTERFACE_RANGE: {
                return INSN_FORMAT_3RC;
            }
            case Opcodes.CONST_WIDE: {
                return INSN_FORMAT_51L;
            }
            case Opcodes.PACKED_SWITCH_PAYLOAD: {
                return INSN_FORMAT_PACKED_SWITCH_PAYLOAD;
            }
            case Opcodes.SPARSE_SWITCH_PAYLOAD: {
                return INSN_FORMAT_SPARSE_SWITCH_PAYLOAD;
            }
            case Opcodes.FILL_ARRAY_DATA_PAYLOAD: {
                return INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD;
            }
            default: {
                return INSN_FORMAT_UNKNOWN;
            }
        }
    }
}
