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

import java.io.EOFException;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Created by tangyinsheng on 2016/5/26.
 */
public final class InstructionReader {
    private final ShortArrayCodeInput codeIn;

    public InstructionReader(ShortArrayCodeInput in) {
        this.codeIn = in;
    }

    public void accept(InstructionVisitor iv) throws EOFException {
        codeIn.reset();
        while (codeIn.hasMore()) {
            int currentAddress = codeIn.cursor();
            int opcodeUnit = codeIn.read();
            int opcodeForSwitch = Opcodes.extractOpcodeFromUnit(opcodeUnit);
            switch (opcodeForSwitch) {
                case Opcodes.SPECIAL_FORMAT: {
                    iv.visitZeroRegisterInsn(currentAddress, opcodeUnit, 0, InstructionCodec.INDEX_TYPE_NONE, 0, 0L);
                    break;
                }
                case Opcodes.GOTO: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int target = (byte) InstructionCodec.byte1(opcodeUnit); // sign-extend
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, currentAddress + target, 0L);
                    break;
                }
                case Opcodes.NOP:
                case Opcodes.RETURN_VOID: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int literal = InstructionCodec.byte1(opcodeUnit); // should be zero
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal);
                    break;
                }
                case Opcodes.CONST_4: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.nibble2(opcodeUnit);
                    int literal = (InstructionCodec.nibble3(opcodeUnit) << 28) >> 28; // sign-extend
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, 0L, a);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.nibble2(opcodeUnit);
                    int b = InstructionCodec.nibble3(opcodeUnit);
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, 0L, a, b);
                    break;
                }
                case Opcodes.GOTO_16: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int literal = InstructionCodec.byte1(opcodeUnit); // should be zero
                    int target = (short) codeIn.read(); // sign-extend
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, currentAddress + target, literal);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int index = codeIn.read();
                    int indexType = InstructionCodec.getInstructionIndexType(opcode);
                    iv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a);
                    break;
                }
                case Opcodes.CONST_HIGH16:
                case Opcodes.CONST_WIDE_HIGH16: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    long literal = (short) codeIn.read(); // sign-extend

                    /*
                     * Format 21h decodes differently depending on the opcode,
                     * because the "signed hat" might represent either a 32-
                     * or 64- bit value.
                     */
                    literal <<= (opcode == Opcodes.CONST_HIGH16) ? 16 : 48;

                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a);

                    break;
                }
                case Opcodes.CONST_16:
                case Opcodes.CONST_WIDE_16: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int literal = (short) codeIn.read(); // sign-extend
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case Opcodes.IF_EQZ:
                case Opcodes.IF_NEZ:
                case Opcodes.IF_LTZ:
                case Opcodes.IF_GEZ:
                case Opcodes.IF_GTZ:
                case Opcodes.IF_LEZ: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int target = (short) codeIn.read(); // sign-extend
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, currentAddress + target, 0L, a);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int bc = codeIn.read();
                    int b = InstructionCodec.byte0(bc);
                    int literal = (byte) InstructionCodec.byte1(bc); // sign-extend
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a, b);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.nibble2(opcodeUnit);
                    int b = InstructionCodec.nibble3(opcodeUnit);
                    int index = codeIn.read();
                    int indexType = InstructionCodec.getInstructionIndexType(opcode);
                    iv.visitTwoRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.nibble2(opcodeUnit);
                    int b = InstructionCodec.nibble3(opcodeUnit);
                    int literal = (short) codeIn.read(); // sign-extend
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a, b);
                    break;
                }
                case Opcodes.IF_EQ:
                case Opcodes.IF_NE:
                case Opcodes.IF_LT:
                case Opcodes.IF_GE:
                case Opcodes.IF_GT:
                case Opcodes.IF_LE: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.nibble2(opcodeUnit);
                    int b = InstructionCodec.nibble3(opcodeUnit);
                    int target = (short) codeIn.read(); // sign-extend
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, currentAddress + target, 0L, a, b);
                    break;
                }
                case Opcodes.MOVE_FROM16:
                case Opcodes.MOVE_WIDE_FROM16:
                case Opcodes.MOVE_OBJECT_FROM16: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int b = codeIn.read();
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, 0L, a, b);
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
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int bc = codeIn.read();
                    int b = InstructionCodec.byte0(bc);
                    int c = InstructionCodec.byte1(bc);
                    iv.visitThreeRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, 0L, a, b, c);
                    break;
                }
                case Opcodes.GOTO_32: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int literal = InstructionCodec.byte1(opcodeUnit); // should be zero
                    int target = codeIn.readInt();
                    iv.visitZeroRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, currentAddress + target, literal);
                    break;
                }
                case Opcodes.CONST_STRING_JUMBO: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int index = codeIn.readInt();
                    int indexType = InstructionCodec.getInstructionIndexType(opcode);
                    iv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a);
                    break;
                }
                case Opcodes.CONST:
                case Opcodes.CONST_WIDE_32: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int literal = codeIn.readInt();
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case Opcodes.FILL_ARRAY_DATA:
                case Opcodes.PACKED_SWITCH:
                case Opcodes.SPARSE_SWITCH: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    int target = currentAddress + codeIn.readInt();

                    /*
                     * Switch instructions need to "forward" their addresses to their
                     * payload target instructions.
                     */
                    switch (opcode) {
                        case Opcodes.PACKED_SWITCH:
                        case Opcodes.SPARSE_SWITCH: {
                            // plus 1 means when we actually lookup the currentAddress
                            // by (payload insn address + 1),
                            codeIn.setBaseAddress(target + 1, currentAddress);
                            break;
                        }
                        default: {
                            break;
                        }
                    }

                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, target, 0L, a);
                    break;
                }
                case Opcodes.MOVE_16:
                case Opcodes.MOVE_WIDE_16:
                case Opcodes.MOVE_OBJECT_16: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int literal = InstructionCodec.byte1(opcodeUnit); // should be zero
                    int a = codeIn.read();
                    int b = codeIn.read();
                    iv.visitTwoRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a, b);
                    break;
                }
                case Opcodes.FILLED_NEW_ARRAY:
                case Opcodes.INVOKE_VIRTUAL:
                case Opcodes.INVOKE_SUPER:
                case Opcodes.INVOKE_DIRECT:
                case Opcodes.INVOKE_STATIC:
                case Opcodes.INVOKE_INTERFACE: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int e = InstructionCodec.nibble2(opcodeUnit);
                    int registerCount = InstructionCodec.nibble3(opcodeUnit);
                    int index = codeIn.read();
                    int abcd = codeIn.read();
                    int a = InstructionCodec.nibble0(abcd);
                    int b = InstructionCodec.nibble1(abcd);
                    int c = InstructionCodec.nibble2(abcd);
                    int d = InstructionCodec.nibble3(abcd);
                    int indexType = InstructionCodec.getInstructionIndexType(opcode);

                    switch (registerCount) {
                        case 0: {
                            iv.visitZeroRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L);
                            break;
                        }
                        case 1: {
                            iv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a);
                            break;
                        }
                        case 2: {
                            iv.visitTwoRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b);
                            break;
                        }
                        case 3: {
                            iv.visitThreeRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b, c);
                            break;
                        }
                        case 4: {
                            iv.visitFourRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b, c, d);
                            break;
                        }
                        case 5: {
                            iv.visitFiveRegisterInsn(currentAddress, opcode, index, indexType, 0, 0L, a, b, c, d, e);
                            break;
                        }
                        default: {
                            throw new DexException("bogus registerCount: " + Hex.uNibble(registerCount));
                        }
                    }
                    break;
                }
                case Opcodes.FILLED_NEW_ARRAY_RANGE:
                case Opcodes.INVOKE_VIRTUAL_RANGE:
                case Opcodes.INVOKE_SUPER_RANGE:
                case Opcodes.INVOKE_DIRECT_RANGE:
                case Opcodes.INVOKE_STATIC_RANGE:
                case Opcodes.INVOKE_INTERFACE_RANGE: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int registerCount = InstructionCodec.byte1(opcodeUnit);
                    int index = codeIn.read();
                    int a = codeIn.read();
                    int indexType = InstructionCodec.getInstructionIndexType(opcode);
                    iv.visitRegisterRangeInsn(currentAddress, opcode, index, indexType, 0, 0L, a, registerCount);
                    break;
                }
                case Opcodes.CONST_WIDE: {
                    int opcode = InstructionCodec.byte0(opcodeUnit);
                    int a = InstructionCodec.byte1(opcodeUnit);
                    long literal = codeIn.readLong();
                    iv.visitOneRegisterInsn(currentAddress, opcode, 0, InstructionCodec.INDEX_TYPE_NONE, 0, literal, a);
                    break;
                }
                case Opcodes.FILL_ARRAY_DATA_PAYLOAD: {
                    int elementWidth = codeIn.read();
                    int size = codeIn.readInt();

                    switch (elementWidth) {
                        case 1: {
                            byte[] array = new byte[size];
                            boolean even = true;
                            for (int i = 0, value = 0; i < size; ++i, even = !even) {
                                if (even) {
                                    value = codeIn.read();
                                }
                                array[i] = (byte) (value & 0xff);
                                value >>= 8;
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 1);
                            break;
                        }
                        case 2: {
                            short[] array = new short[size];
                            for (int i = 0; i < size; i++) {
                                array[i] = (short) codeIn.read();
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 2);
                            break;
                        }
                        case 4: {
                            int[] array = new int[size];
                            for (int i = 0; i < size; i++) {
                                array[i] = codeIn.readInt();
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 4);
                            break;
                        }
                        case 8: {
                            long[] array = new long[size];
                            for (int i = 0; i < size; i++) {
                                array[i] = codeIn.readLong();
                            }
                            iv.visitFillArrayDataPayloadInsn(currentAddress, opcodeUnit, array, array.length, 8);
                            break;
                        }
                        default: {
                            throw new DexException("bogus element_width: " + Hex.u2(elementWidth));
                        }
                    }
                    break;
                }
                case Opcodes.PACKED_SWITCH_PAYLOAD: {
                    int baseAddress = codeIn.baseAddressForCursor();
                    int size = codeIn.read();
                    int firstKey = codeIn.readInt();
                    int[] targets = new int[size];

                    for (int i = 0; i < size; i++) {
                        targets[i] = baseAddress + codeIn.readInt();
                    }
                    iv.visitPackedSwitchPayloadInsn(currentAddress, opcodeUnit, firstKey, targets);
                    break;
                }
                case Opcodes.SPARSE_SWITCH_PAYLOAD: {
                    int baseAddress = codeIn.baseAddressForCursor();
                    int size = codeIn.read();
                    int[] keys = new int[size];
                    int[] targets = new int[size];

                    for (int i = 0; i < size; i++) {
                        keys[i] = codeIn.readInt();
                    }

                    for (int i = 0; i < size; i++) {
                        targets[i] = baseAddress + codeIn.readInt();
                    }

                    iv.visitSparseSwitchPayloadInsn(currentAddress, opcodeUnit, keys, targets);
                    break;
                }
                default: {
                    throw new IllegalStateException("Unknown opcode: " + Hex.u4(opcodeForSwitch));
                }
            }
        }
    }
}
