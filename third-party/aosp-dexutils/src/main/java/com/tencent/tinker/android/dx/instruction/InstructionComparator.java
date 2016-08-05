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

import com.tencent.tinker.android.dx.util.Hex;

import java.io.EOFException;

/**
 * Created by tangyinsheng on 2016/7/12.
 */
public abstract class InstructionComparator {
    private final ShortArrayCodeInput codeIn1;
    private final ShortArrayCodeInput codeIn2;

    public InstructionComparator(ShortArrayCodeInput in1, ShortArrayCodeInput in2) {
        this.codeIn1 = in1;
        this.codeIn2 = in2;
    }

    public final boolean compare() {
        try {
            while (this.codeIn1.hasMore() && this.codeIn2.hasMore()) {
                int opcodeUnit1 = codeIn1.read();
                int opcodeForSwitch1 = Opcodes.extractOpcodeFromUnit(opcodeUnit1);
                int opcodeUnit2 = codeIn2.read();
                int opcodeForSwitch2 = Opcodes.extractOpcodeFromUnit(opcodeUnit2);
                if (opcodeForSwitch1 != opcodeForSwitch2) {
                    return false;
                }
                switch (opcodeForSwitch1) {
                    case Opcodes.SPECIAL_FORMAT: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.GOTO: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int baseAddress1 = codeIn1.cursor() - 1;
                        int baseAddress2 = codeIn2.cursor() - 1;
                        int target1 = (byte) InstructionCodec.byte1(opcodeUnit1); // sign-extend
                        int target2 = (byte) InstructionCodec.byte1(opcodeUnit2); // sign-extend
                        if (baseAddress1 + target1 != baseAddress2 + target2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.NOP:
                    case Opcodes.RETURN_VOID: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.CONST_4: {
                        int opcode1 = InstructionCodec.byte0(opcodeUnit1);
                        int opcode2 = InstructionCodec.byte0(opcodeUnit2);
                        if (opcode1 != opcode2) {
                            return false;
                        }
                        int a1 = InstructionCodec.nibble2(opcodeUnit1);
                        int a2 = InstructionCodec.nibble2(opcodeUnit2);
                        if (a1 != a2) {
                            return false;
                        }
                        int literal1 = (InstructionCodec.nibble3(opcodeUnit1) << 28) >> 28; // sign-extend
                        int literal2 = (InstructionCodec.nibble3(opcodeUnit2) << 28) >> 28; // sign-extend
                        if (literal1 != literal2) {
                            return false;
                        }
                        break;
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
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        break;
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
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.GOTO_16: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int baseAddress1 = codeIn1.cursor() - 1;
                        int baseAddress2 = codeIn2.cursor() - 1;
                        int target1 = (short) codeIn1.read(); // sign-extend
                        int target2 = (short) codeIn2.read(); // sign-extend
                        if (baseAddress1 + target1 != baseAddress2 + target2) {
                            return false;
                        }
                        break;
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
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int index1 = codeIn1.read();
                        int index2 = codeIn2.read();
                        if (!compareIndex(opcodeForSwitch1, index1, index2)) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.CONST_HIGH16:
                    case Opcodes.CONST_WIDE_HIGH16: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }

                        int opcode1 = InstructionCodec.byte0(opcodeUnit1);
                        int opcode2 = InstructionCodec.byte0(opcodeUnit2);

                        long literal1 = (short) codeIn1.read(); // sign-extend
                        long literal2 = (short) codeIn2.read(); // sign-extend

                        /*
                         * Format 21h decodes differently depending on the opcode,
                         * because the "signed hat" might represent either a 32-
                         * or 64- bit value.
                         */
                        literal1 <<= (opcode1 == Opcodes.CONST_HIGH16) ? 16 : 48;
                        literal2 <<= (opcode2 == Opcodes.CONST_HIGH16) ? 16 : 48;

                        if (literal1 != literal2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.CONST_16:
                    case Opcodes.CONST_WIDE_16: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int literal1 = (short) codeIn1.read(); // sign-extend
                        int literal2 = (short) codeIn2.read(); // sign-extend
                        if (literal1 != literal2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.IF_EQZ:
                    case Opcodes.IF_NEZ:
                    case Opcodes.IF_LTZ:
                    case Opcodes.IF_GEZ:
                    case Opcodes.IF_GTZ:
                    case Opcodes.IF_LEZ: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int baseAddress1 = codeIn1.cursor() - 1;
                        int baseAddress2 = codeIn2.cursor() - 1;
                        int target1 = (short) codeIn1.read(); // sign-extend
                        int target2 = (short) codeIn2.read(); // sign-extend
                        if (baseAddress1 + target1 != baseAddress2 + target2) {
                            return false;
                        }
                        break;
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
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int bc1 = codeIn1.read();
                        int bc2 = codeIn2.read();
                        if (bc1 != bc2) {
                            return false;
                        }
                        break;
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
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int index1 = codeIn1.read();
                        int index2 = codeIn2.read();
                        if (!compareIndex(opcodeForSwitch1, index1, index2)) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.ADD_INT_LIT16:
                    case Opcodes.RSUB_INT:
                    case Opcodes.MUL_INT_LIT16:
                    case Opcodes.DIV_INT_LIT16:
                    case Opcodes.REM_INT_LIT16:
                    case Opcodes.AND_INT_LIT16:
                    case Opcodes.OR_INT_LIT16:
                    case Opcodes.XOR_INT_LIT16: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int literal1 = (short) codeIn1.read(); // sign-extend
                        int literal2 = (short) codeIn2.read(); // sign-extend
                        if (literal1 != literal2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.IF_EQ:
                    case Opcodes.IF_NE:
                    case Opcodes.IF_LT:
                    case Opcodes.IF_GE:
                    case Opcodes.IF_GT:
                    case Opcodes.IF_LE: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int baseAddress1 = codeIn1.cursor() - 1;
                        int baseAddress2 = codeIn2.cursor() - 1;
                        int target1 = (short) codeIn1.read(); // sign-extend
                        int target2 = (short) codeIn2.read(); // sign-extend
                        if (baseAddress1 + target1 != baseAddress2 + target2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.MOVE_FROM16:
                    case Opcodes.MOVE_WIDE_FROM16:
                    case Opcodes.MOVE_OBJECT_FROM16: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int b1 = codeIn1.read();
                        int b2 = codeIn2.read();
                        if (b1 != b2) {
                            return false;
                        }
                        break;
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
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int bc1 = codeIn1.read();
                        int bc2 = codeIn2.read();
                        if (bc1 != bc2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.GOTO_32: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int baseAddress1 = codeIn1.cursor() - 1;
                        int baseAddress2 = codeIn2.cursor() - 1;
                        int target1 = codeIn1.readInt();
                        int target2 = codeIn2.readInt();
                        if (baseAddress1 + target1 != baseAddress2 + target2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.CONST_STRING_JUMBO: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int index1 = codeIn1.readInt();
                        int index2 = codeIn2.readInt();
                        if (!compareIndex(opcodeForSwitch1, index1, index2)) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.CONST:
                    case Opcodes.CONST_WIDE_32: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int literal1 = codeIn1.readInt();
                        int literal2 = codeIn2.readInt();
                        if (literal1 != literal2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.FILL_ARRAY_DATA:
                    case Opcodes.PACKED_SWITCH:
                    case Opcodes.SPARSE_SWITCH: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }

                        int baseAddress1 = codeIn1.cursor() - 1;
                        int baseAddress2 = codeIn2.cursor() - 1;

                        int target1 = baseAddress1 + codeIn1.readInt();
                        int target2 = baseAddress2 + codeIn2.readInt();

                        int opcode1Or2 = InstructionCodec.byte0(opcodeUnit1);

                        /*
                         * Switch instructions need to "forward" their addresses to their
                         * payload target instructions.
                         */
                        switch (opcode1Or2) {
                            case Opcodes.PACKED_SWITCH:
                            case Opcodes.SPARSE_SWITCH: {
                                codeIn1.setBaseAddress(target1, baseAddress1);
                                codeIn2.setBaseAddress(target2, baseAddress2);
                                break;
                            }
                        }
                        break;
                    }
                    case Opcodes.MOVE_16:
                    case Opcodes.MOVE_WIDE_16:
                    case Opcodes.MOVE_OBJECT_16: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int a1 = codeIn1.read();
                        int a2 = codeIn2.read();
                        if (a1 != a2) {
                            return false;
                        }
                        int b1 = codeIn1.read();
                        int b2 = codeIn2.read();
                        if (b1 != b2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.FILLED_NEW_ARRAY:
                    case Opcodes.INVOKE_VIRTUAL:
                    case Opcodes.INVOKE_SUPER:
                    case Opcodes.INVOKE_DIRECT:
                    case Opcodes.INVOKE_STATIC:
                    case Opcodes.INVOKE_INTERFACE: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int index1 = codeIn1.read();
                        int index2 = codeIn2.read();
                        if (!compareIndex(opcodeForSwitch1, index1, index2)) {
                            return false;
                        }
                        int abcd1 = codeIn1.read();
                        int abcd2 = codeIn2.read();
                        if (abcd1 != abcd2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.FILLED_NEW_ARRAY_RANGE:
                    case Opcodes.INVOKE_VIRTUAL_RANGE:
                    case Opcodes.INVOKE_SUPER_RANGE:
                    case Opcodes.INVOKE_DIRECT_RANGE:
                    case Opcodes.INVOKE_STATIC_RANGE:
                    case Opcodes.INVOKE_INTERFACE_RANGE: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        int index1 = codeIn1.read();
                        int index2 = codeIn2.read();
                        if (!compareIndex(opcodeForSwitch1, index1, index2)) {
                            return false;
                        }
                        int a1 = codeIn1.read();
                        int a2 = codeIn2.read();
                        if (a1 != a2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.CONST_WIDE: {
                        if (opcodeUnit1 != opcodeUnit2) {
                            return false;
                        }
                        long literal1 = codeIn1.readLong();
                        long literal2 = codeIn2.readLong();
                        if (literal1 != literal2) {
                            return false;
                        }
                        break;
                    }
                    case Opcodes.FILL_ARRAY_DATA_PAYLOAD: {
                        int elementWidth1 = codeIn1.read();
                        int elementWidth2 = codeIn2.read();
                        if (elementWidth1 != elementWidth2) {
                            return false;
                        }
                        int size1 = codeIn1.readInt();
                        int size2 = codeIn2.readInt();
                        if (size1 != size2) {
                            return false;
                        }
                        switch (elementWidth1) {
                            case 1: {
                                int value1;
                                int value2;
                                for (int i = 0; i < size1; i += 2) {
                                    value1 = codeIn1.read();
                                    value2 = codeIn2.read();
                                    if (value1 != value2) {
                                        return false;
                                    }
                                }
                                break;
                            }
                            case 2: {
                                short value1;
                                short value2;
                                for (int i = 0; i < size1; ++i) {
                                    value1 = (short) codeIn1.read();
                                    value2 = (short) codeIn2.read();
                                    if (value1 != value2) {
                                        return false;
                                    }
                                }
                                break;
                            }
                            case 4: {
                                int value1;
                                int value2;
                                for (int i = 0; i < size1; ++i) {
                                    value1 = codeIn1.readInt();
                                    value2 = codeIn2.readInt();
                                    if (value1 != value2) {
                                        return false;
                                    }
                                }
                                break;
                            }
                            case 8: {
                                long value1;
                                long value2;
                                for (int i = 0; i < size1; ++i) {
                                    value1 = codeIn1.readLong();
                                    value2 = codeIn2.readLong();
                                    if (value1 != value2) {
                                        return false;
                                    }
                                }
                                break;
                            }
                            default: {
                                throw new IllegalStateException("bogus element_width: " + Hex.u2(elementWidth1));
                            }
                        }
                        break;
                    }
                    case Opcodes.PACKED_SWITCH_PAYLOAD: {
                        int baseAddress1 = codeIn1.baseAddressForCursor() - 1; // already read opcode
                        int baseAddress2 = codeIn2.baseAddressForCursor() - 1; // already read opcode
                        if (baseAddress1 != baseAddress2) {
                            return false;
                        }
                        int size1 = codeIn1.read();
                        int size2 = codeIn2.read();
                        if (size1 != size2) {
                            return false;
                        }
                        int firstKey1 = codeIn1.readInt();
                        int firstKey2 = codeIn2.readInt();
                        if (firstKey1 != firstKey2) {
                            return false;
                        }
                        for (int i = 0; i < size1; ++i) {
                            int target1 = baseAddress1 + codeIn1.readInt();
                            int target2 = baseAddress2 + codeIn2.readInt();
                            if (target1 != target2) {
                                return false;
                            }
                        }
                        break;
                    }
                    case Opcodes.SPARSE_SWITCH_PAYLOAD: {
                        int baseAddress1 = codeIn1.baseAddressForCursor() - 1; // already read opcode
                        int baseAddress2 = codeIn2.baseAddressForCursor() - 1; // already read opcode
                        if (baseAddress1 != baseAddress2) {
                            return false;
                        }
                        int size1 = codeIn1.read();
                        int size2 = codeIn2.read();
                        if (size1 != size2) {
                            return false;
                        }

                        for (int i = 0; i < size1; i++) {
                            int key1 = codeIn1.readInt();
                            int key2 = codeIn2.readInt();
                            if (key1 != key2) {
                                return false;
                            }
                        }

                        for (int i = 0; i < size1; i++) {
                            int target1 = baseAddress1 + codeIn1.readInt();
                            int target2 = baseAddress2 + codeIn2.readInt();
                            if (target1 != target2) {
                                return false;
                            }
                        }
                        break;
                    }
                    default: {
                        throw new IllegalStateException("Unknown opcode: " + Hex.u4(opcodeForSwitch1));
                    }
                }
            }
        } catch (EOFException e) {
            throw new RuntimeException(e);
        }
        return (!this.codeIn1.hasMore() && !this.codeIn2.hasMore());
    }

    private boolean compareIndex(int opcode, int index1, int index2) {
        switch (IndexType.getIndexType(opcode)) {
            case STRING_REF: {
                return compareString(index1, index2);
            }
            case TYPE_REF: {
                return compareType(index1, index2);
            }
            case FIELD_REF: {
                return compareField(index1, index2);
            }
            case METHOD_REF: {
                return compareMethod(index1, index2);
            }
            default: {
                return index1 == index2;
            }
        }
    }

    protected abstract boolean compareString(int stringIndex1, int stringIndex2);
    protected abstract boolean compareType(int typeIndex1, int typeIndex2);
    protected abstract boolean compareField(int fieldIndex1, int fieldIndex2);
    protected abstract boolean compareMethod(int methodIndex1, int methodIndex2);
}
