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

import static com.tencent.tinker.android.dx.instruction.InstructionCodec.getTarget;

import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dx.util.Hex;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Created by tangyinsheng on 2016/5/27.
 */
public final class InstructionWriter extends InstructionVisitor {
    private final ShortArrayCodeOutput codeOut;
    private final InstructionPromoter insnPromoter;
    private final boolean hasPromoter;

    /* ***  Used by InstructionCodec *** */
    int currOpcode = 0;
    int currIndex = 0;
    int currTarget = 0;
    long currLiteral = 0L;
    int currRegisterCount = 0;
    int currRegA = 0;
    int currRegB = 0;
    int currRegC = 0;
    int currRegD = 0;
    int currRegE = 0;
    int currRegF = 0;
    int currRegG = 0;
    int currProtoIndex = 0;    /* for INSN_FORMAT_45CC & INSN_FORMAT_4RCC encoding */
    int[] currKeys = null;     /* for INSN_FORMAT_SPARSE_SWITCH_PAYLOAD encoding */
    int[] currTargets = null;  /* for INSN_FORMAT_SPARSE_SWITCH_PAYLOAD encoding */
    int currFirstKey = 0;      /* for INSN_FORMAT_PACKED_SWITCH_PAYLOAD encoding */
    int currElementWidth = 0;  /* for INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD encoding */
    Object currData = null;    /* for INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD encoding */
    int currSize = 0;          /* for INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD encoding */
    /* ********************************* */

    public InstructionWriter(ShortArrayCodeOutput codeOut, InstructionPromoter ipmo) {
        super(null);
        this.codeOut = codeOut;
        this.insnPromoter = ipmo;
        this.hasPromoter = (ipmo != null);
    }

    public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
        if (this.hasPromoter) {
            target = this.insnPromoter.getPromotedAddress(target);
            switch (opcode) {
                case Opcodes.GOTO: {
                    int relativeTarget = getTarget(target, codeOut.cursor());
                    if (relativeTarget != (byte) relativeTarget) {
                        if (relativeTarget == (short) relativeTarget) {
                            opcode = Opcodes.GOTO_16;
                        } else {
                            opcode = Opcodes.GOTO_32;
                        }
                    }
                    break;
                }
                case Opcodes.GOTO_16: {
                    int relativeTarget = getTarget(target, codeOut.cursor());
                    if (relativeTarget != (short) relativeTarget) {
                        opcode = Opcodes.GOTO_32;
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        }

        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = 0;
        currRegA = 0;
        currRegB = 0;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
        if (this.hasPromoter) {
            target = this.insnPromoter.getPromotedAddress(target);
        }

        if (opcode == Opcodes.CONST_STRING) {
            if (this.hasPromoter) {
                if (index > 0xFFFF) {
                    opcode = Opcodes.CONST_STRING_JUMBO;
                }
            } else {
                if (index > 0xFFFF) {
                    throw new DexException("string index out of bound: " + Hex.u4(index)
                            + ", perhaps you need to enable force jumbo mode.");
                }
            }
        }

        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = 1;
        currRegA = a;
        currRegB = 0;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
        if (this.hasPromoter) {
            target = this.insnPromoter.getPromotedAddress(target);
        }

        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = 2;
        currRegA = a;
        currRegB = b;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = 3;
        currRegA = a;
        currRegB = b;
        currRegC = c;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = 4;
        currRegA = a;
        currRegB = b;
        currRegC = c;
        currRegD = d;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = 5;
        currRegA = a;
        currRegB = b;
        currRegC = c;
        currRegD = d;
        currRegE = e;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
        currOpcode = opcode;
        currIndex = index;
        currTarget = target;
        currLiteral = literal;
        currRegisterCount = registerCount;
        currRegA = a;
        currRegB = 0;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    @Override
    public void visitInvokePolymorphicInstruction(int currentAddress, int opcode, int methodIndex, int indexType, int protoIndex, int[] registers) {
        currOpcode = opcode;
        currIndex = methodIndex;
        currProtoIndex = protoIndex;
        currRegisterCount = registers.length;
        currRegA = 0;
        currRegB = 0;
        currRegC = registers.length > 0 ? registers[0] : 0;
        currRegD = registers.length > 1 ? registers[1] : 0;
        currRegE = registers.length > 2 ? registers[2] : 0;
        currRegF = registers.length > 3 ? registers[3] : 0;
        currRegG = registers.length > 4 ? registers[4] : 0;

        InstructionCodec.encode(codeOut, this);
    }

    @Override
    public void visitInvokePolymorphicRangeInstruction(int currentAddress, int opcode, int methodIndex, int indexType, int c, int registerCount, int protoIndex) {
        currOpcode = opcode;
        currIndex = methodIndex;
        currRegisterCount = registerCount;
        currRegA = 0;
        currRegB = 0;
        currRegC = c;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;
        currProtoIndex = protoIndex;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitSparseSwitchPayloadInsn(int currentAddress, int opcode, int[] keys, int[] targets) {
        currOpcode = opcode;
        currKeys = keys;
        if (this.hasPromoter) {
            currTargets = new int[targets.length];
            for (int i = 0; i < targets.length; ++i) {
                currTargets[i] = this.insnPromoter.getPromotedAddress(targets[i]);
            }
        } else {
            currTargets = targets;
        }
        currRegisterCount = 0;
        currRegA = 0;
        currRegB = 0;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitPackedSwitchPayloadInsn(int currentAddress, int opcode, int firstKey, int[] targets) {
        currOpcode = opcode;
        currFirstKey = firstKey;
        if (this.hasPromoter) {
            currTargets = new int[targets.length];
            for (int i = 0; i < targets.length; ++i) {
                currTargets[i] = this.insnPromoter.getPromotedAddress(targets[i]);
            }
        } else {
            currTargets = targets;
        }
        currRegisterCount = 0;
        currRegA = 0;
        currRegB = 0;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }

    public void visitFillArrayDataPayloadInsn(int currentAddress, int opcode, Object data, int size, int elementWidth) {
        currOpcode = opcode;
        currData = data;
        currSize = size;
        currElementWidth = elementWidth;
        currRegisterCount = 0;
        currRegA = 0;
        currRegB = 0;
        currRegC = 0;
        currRegD = 0;
        currRegE = 0;
        currRegF = 0;
        currRegG = 0;

        InstructionCodec.encode(codeOut, this);
    }
}
