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
 * Created by tomystang on 2016/5/27.
 */
public final class InstructionWriter extends InstructionVisitor {
    private final ShortArrayCodeOutput codeOut;

    public InstructionWriter(ShortArrayCodeOutput codeOut) {
        super(null);
        this.codeOut = codeOut;
    }

    private int getTargetByte(int target, int baseAddress) {
        int relativeTarget = getTarget(target, baseAddress);

        if (relativeTarget != (byte) relativeTarget) {
            throw new DexException("Target out of range: " + Hex.s4(relativeTarget));
        }

        return relativeTarget & 0xff;
    }

    private short getTargetUnit(int target, int baseAddress) {
        int relativeTarget = getTarget(target, baseAddress);

        if (relativeTarget != (short) relativeTarget) {
            throw new DexException("Target out of range: " + Hex.s4(relativeTarget));
        }

        return (short) relativeTarget;
    }

    private int getTarget(int target, int baseAddress) {
        return target - baseAddress;
    }

    private int getLiteralByte(long literal) {
        if (literal != (byte) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal & 0xff;
    }

    private short getLiteralUnit(long literal) {
        if (literal != (short) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (short) literal;
    }

    private int getLiteralInt(long literal) {
        if (literal != (int) literal) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal;
    }

    private int getLiteralNibble(long literal) {
        if ((literal < -8) || (literal > 7)) {
            throw new DexException("Literal out of range: " + Hex.u8(literal));
        }

        return (int) literal & 0xf;
    }

    /**
     * Gets the A register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    private short getAUnit(int a) {
        if ((a & ~0xffff) != 0) {
            throw new DexException("Register A out of range: " + Hex.u8(a));
        }

        return (short) a;
    }

    /**
     * Gets the B register number, as a code unit. This will throw if the
     * value is out of the range of an unsigned code unit.
     */
    private short getBUnit(int b) {
        if ((b & ~0xffff) != 0) {
            throw new DexException("Register B out of range: " + Hex.u8(b));
        }

        return (short) b;
    }

    public void visitZeroRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal) {
        switch (opcode) {
            case Opcodes.SPECIAL_FORMAT:
            case Opcodes.NOP:
            case Opcodes.RETURN_VOID: {
                short opcodeUnit = (short) opcode;
                codeOut.write(opcodeUnit);
                break;
            }
            case Opcodes.GOTO: {
                int relativeTarget = getTargetByte(target, codeOut.cursor());
                codeOut.write(InstructionCodec.codeUnit(opcode, relativeTarget));
                break;
            }
            case Opcodes.GOTO_16: {
                short relativeTarget = getTargetUnit(target, codeOut.cursor());
                short opcodeUnit = (short) opcode;
                codeOut.write(opcodeUnit, relativeTarget);
                break;
            }
            case Opcodes.GOTO_32: {
                int relativeTarget = getTarget(target, codeOut.cursor());
                short opcodeUnit = (short) opcode;
                codeOut.write(opcodeUnit, InstructionCodec.unit0(relativeTarget), InstructionCodec.unit1(relativeTarget));
                break;
            }
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(0, 0)
                        ),
                        indexUnit,
                        InstructionCodec.codeUnit(0, 0, 0, 0)
                );
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitOneRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a) {
        switch (opcode) {
            case Opcodes.CONST_4: {
                short opcodeUnit = (short) opcode;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcodeUnit,
                                InstructionCodec.makeByte(a, getLiteralNibble(literal))
                        )
                );
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
                codeOut.write(InstructionCodec.codeUnit(opcode, a));
                break;
            }
            case Opcodes.IF_EQZ:
            case Opcodes.IF_NEZ:
            case Opcodes.IF_LTZ:
            case Opcodes.IF_GEZ:
            case Opcodes.IF_GTZ:
            case Opcodes.IF_LEZ: {
                short relativeTarget = getTargetUnit(target, codeOut.cursor());
                codeOut.write(InstructionCodec.codeUnit(opcode, a), relativeTarget);
                break;
            }
            case Opcodes.CONST_16:
            case Opcodes.CONST_WIDE_16: {
                codeOut.write(InstructionCodec.codeUnit(opcode, a), getLiteralUnit(literal));
                break;
            }
            case Opcodes.CONST_HIGH16:
            case Opcodes.CONST_WIDE_HIGH16: {
                int shift = (opcode == Opcodes.CONST_HIGH16) ? 16 : 48;
                short literalShifted = (short) (literal >> shift);
                codeOut.write(InstructionCodec.codeUnit(opcode, a), literalShifted);
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
                short indexUnit = (short) index;
                codeOut.write(InstructionCodec.codeUnit(opcode, a), indexUnit);
                break;
            }
            case Opcodes.CONST:
            case Opcodes.CONST_WIDE_32: {
                int literalInt = getLiteralInt(literal);
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        InstructionCodec.unit0(literalInt),
                        InstructionCodec.unit1(literalInt)
                );
                break;
            }
            case Opcodes.FILL_ARRAY_DATA:
            case Opcodes.PACKED_SWITCH:
            case Opcodes.SPARSE_SWITCH: {
                int relativeTarget = getTarget(target, codeOut.cursor());
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        InstructionCodec.unit0(relativeTarget),
                        InstructionCodec.unit1(relativeTarget)
                );
                break;
            }
            case Opcodes.CONST_STRING_JUMBO: {
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        InstructionCodec.unit0(index),
                        InstructionCodec.unit1(index)
                );
                break;
            }
            case Opcodes.CONST_WIDE: {
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        InstructionCodec.unit0(literal),
                        InstructionCodec.unit1(literal),
                        InstructionCodec.unit2(literal),
                        InstructionCodec.unit3(literal)
                );
                break;
            }
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(0, 1)
                        ),
                        indexUnit,
                        InstructionCodec.codeUnit(a, 0, 0, 0)
                );
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitTwoRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b) {
        switch (opcode) {
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
                short opcodeUnit = (short) opcode;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcodeUnit,
                                InstructionCodec.makeByte(a, b)
                        )
                );
                break;
            }
            case Opcodes.MOVE_FROM16:
            case Opcodes.MOVE_WIDE_FROM16:
            case Opcodes.MOVE_OBJECT_FROM16: {
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        getBUnit(b)
                );
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
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        InstructionCodec.codeUnit(b, getLiteralByte(literal))
                );
                break;
            }
            case Opcodes.IF_EQ:
            case Opcodes.IF_NE:
            case Opcodes.IF_LT:
            case Opcodes.IF_GE:
            case Opcodes.IF_GT:
            case Opcodes.IF_LE: {
                short relativeTarget = getTargetUnit(target, codeOut.cursor());
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(a, b)
                        ),
                        relativeTarget
                );
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
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(a, b)
                        ),
                        getLiteralUnit(literal)
                );
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
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(a, b)
                        ),
                        indexUnit
                );
                break;
            }
            case Opcodes.MOVE_16:
            case Opcodes.MOVE_WIDE_16:
            case Opcodes.MOVE_OBJECT_16: {
                short opcodeUnit = (short) opcode;
                codeOut.write(opcodeUnit, getAUnit(a), getBUnit(b));
                break;
            }
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(0, 2)
                        ),
                        indexUnit,
                        InstructionCodec.codeUnit(a, b, 0, 0)
                );
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitThreeRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b, int c) {
        switch (opcode) {
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
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, a),
                        InstructionCodec.codeUnit(b, c)
                );
                break;
            }
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(0, 3)
                        ),
                        indexUnit,
                        InstructionCodec.codeUnit(a, b, c, 0)
                );
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitFourRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b, int c, int d) {
        switch (opcode) {
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(0, 4)
                        ),
                        indexUnit,
                        InstructionCodec.codeUnit(a, b, c, d)
                );
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitFiveRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b, int c, int d, int e) {
        switch (opcode) {
            case Opcodes.FILLED_NEW_ARRAY:
            case Opcodes.INVOKE_VIRTUAL:
            case Opcodes.INVOKE_SUPER:
            case Opcodes.INVOKE_DIRECT:
            case Opcodes.INVOKE_STATIC:
            case Opcodes.INVOKE_INTERFACE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(
                                opcode,
                                InstructionCodec.makeByte(e, 5)
                        ),
                        indexUnit,
                        InstructionCodec.codeUnit(a, b, c, d)
                );
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitRegisterRangeInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int registerCount) {
        switch (opcode) {
            case Opcodes.FILLED_NEW_ARRAY_RANGE:
            case Opcodes.INVOKE_VIRTUAL_RANGE:
            case Opcodes.INVOKE_SUPER_RANGE:
            case Opcodes.INVOKE_DIRECT_RANGE:
            case Opcodes.INVOKE_STATIC_RANGE:
            case Opcodes.INVOKE_INTERFACE_RANGE: {
                short indexUnit = (short) index;
                codeOut.write(
                        InstructionCodec.codeUnit(opcode, registerCount),
                        indexUnit,
                        getAUnit(a));
                break;
            }
            default: {
                throw new IllegalStateException("unexpected opcode: " + Hex.u2or4(opcode));
            }
        }
    }

    public void visitSparseSwitchPayloadInsn(int opcode, int[] keys, int[] targets) {
        int baseAddress = codeOut.baseAddressForCursor();

        short opcodeUnit = (short) opcode;
        codeOut.write(opcodeUnit);
        codeOut.write(InstructionCodec.asUnsignedUnit(targets.length));

        for (int key : keys) {
            codeOut.writeInt(key);
        }

        for (int target : targets) {
            codeOut.writeInt(target - baseAddress);
        }
    }

    public void visitPackedSwitchPayloadInsn(int opcode, int firstKey, int[] targets) {
        int baseAddress = codeOut.baseAddressForCursor();

        short opcodeUnit = (short) opcode;
        codeOut.write(opcodeUnit);
        codeOut.write(InstructionCodec.asUnsignedUnit(targets.length));
        codeOut.writeInt(firstKey);

        for (int target : targets) {
            codeOut.writeInt(target - baseAddress);
        }
    }

    public void visitFillArrayDataPayloadInsn(int opcode, Object data, int size, int elementWidth) {
        short opcodeUnit = (short) opcode;
        codeOut.write(opcodeUnit);

        short elementWidthUnit = (short) elementWidth;
        codeOut.write(elementWidthUnit);

        codeOut.writeInt(size);

        switch (elementWidth) {
            case 1: {
                codeOut.write((byte[]) data);
                break;
            }
            case 2: {
                codeOut.write((short[]) data);
                break;
            }
            case 4: {
                codeOut.write((int[]) data);
                break;
            }
            case 8: {
                codeOut.write((long[]) data);
                break;
            }
            default: {
                throw new DexException("bogus element_width: " + Hex.u2(elementWidth));
            }
        }
    }
}
