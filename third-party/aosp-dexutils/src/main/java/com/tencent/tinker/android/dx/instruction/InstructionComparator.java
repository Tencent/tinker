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
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dx.util.Hex;

import java.io.EOFException;
import java.util.HashSet;
import java.util.Set;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Created by tangyinsheng on 2016/7/12.
 */
public abstract class InstructionComparator {
    private final InstructionHolder[] insnHolders1;
    private final InstructionHolder[] insnHolders2;
    private final Set<String> visitedInsnAddrPairs;
    private final short[] insns1;
    private final short[] insns2;

    public InstructionComparator(short[] insns1, short[] insns2) {
        this.insns1 = insns1;
        this.insns2 = insns2;

        if (insns1 != null) {
            ShortArrayCodeInput codeIn1 = new ShortArrayCodeInput(insns1);
            this.insnHolders1 = readInstructionsIntoHolders(codeIn1, insns1.length);
        } else {
            this.insnHolders1 = null;
        }
        if (insns2 != null) {
            ShortArrayCodeInput codeIn2 = new ShortArrayCodeInput(insns2);
            this.insnHolders2 = readInstructionsIntoHolders(codeIn2, insns2.length);
        } else {
            this.insnHolders2 = null;
        }
        visitedInsnAddrPairs = new HashSet<>();
    }

    private InstructionHolder[] readInstructionsIntoHolders(ShortArrayCodeInput in, int length) {
        in.reset();
        final InstructionHolder[] result = new InstructionHolder[length];
        InstructionReader ir = new InstructionReader(in);
        try {
            ir.accept(new InstructionVisitor(null) {
                public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    result[currentAddress] = insnHolder;
                }

                public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    insnHolder.registerCount = 1;
                    insnHolder.a = a;
                    result[currentAddress] = insnHolder;
                }

                public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    insnHolder.registerCount = 2;
                    insnHolder.a = a;
                    insnHolder.b = b;
                    result[currentAddress] = insnHolder;
                }

                public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    insnHolder.registerCount = 3;
                    insnHolder.a = a;
                    insnHolder.b = b;
                    insnHolder.c = c;
                    result[currentAddress] = insnHolder;
                }

                public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    insnHolder.registerCount = 4;
                    insnHolder.a = a;
                    insnHolder.b = b;
                    insnHolder.c = c;
                    insnHolder.d = d;
                    result[currentAddress] = insnHolder;
                }

                public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    insnHolder.registerCount = 5;
                    insnHolder.a = a;
                    insnHolder.b = b;
                    insnHolder.c = c;
                    insnHolder.d = d;
                    insnHolder.e = e;
                    result[currentAddress] = insnHolder;
                }

                public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
                    InstructionHolder insnHolder = new InstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.index = index;
                    insnHolder.target = target;
                    insnHolder.literal = literal;
                    insnHolder.registerCount = registerCount;
                    insnHolder.a = a;
                    result[currentAddress] = insnHolder;
                }

                public void visitSparseSwitchPayloadInsn(int currentAddress, int opcode, int[] keys, int[] targets) {
                    SparseSwitchPayloadInsntructionHolder insnHolder = new SparseSwitchPayloadInsntructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.keys = keys;
                    insnHolder.targets = targets;
                    result[currentAddress] = insnHolder;
                }

                public void visitPackedSwitchPayloadInsn(int currentAddress, int opcode, int firstKey, int[] targets) {
                    PackedSwitchPayloadInsntructionHolder insnHolder = new PackedSwitchPayloadInsntructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.firstKey = firstKey;
                    insnHolder.targets = targets;
                    result[currentAddress] = insnHolder;
                }

                public void visitFillArrayDataPayloadInsn(int currentAddress, int opcode, Object data, int size, int elementWidth) {
                    FillArrayDataPayloadInstructionHolder insnHolder = new FillArrayDataPayloadInstructionHolder();
                    insnHolder.insnFormat = InstructionCodec.getInstructionFormat(opcode);
                    insnHolder.address = currentAddress;
                    insnHolder.opcode = opcode;
                    insnHolder.data = data;
                    insnHolder.size = size;
                    insnHolder.elementWidth = elementWidth;
                    result[currentAddress] = insnHolder;
                }
            });
        } catch (EOFException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public final boolean compare() {
        this.visitedInsnAddrPairs.clear();

        if (this.insnHolders1 == null && this.insnHolders2 == null) {
            return true;
        }

        if (this.insnHolders1 == null || this.insnHolders2 == null) {
            return false;
        }

        int currAddress1 = 0;
        int currAddress2 = 0;
        int insnHolderCount1 = 0;
        int insnHolderCount2 = 0;
        while (currAddress1 < insnHolders1.length && currAddress2 < insnHolders2.length) {
            InstructionHolder insnHolder1 = null;
            InstructionHolder insnHolder2 = null;
            while (currAddress1 < insnHolders1.length && insnHolder1 == null) {
                insnHolder1 = insnHolders1[currAddress1++];
            }
            if (insnHolder1 != null) {
                ++insnHolderCount1;
            } else {
                break;
            }
            while (currAddress2 < insnHolders2.length && insnHolder2 == null) {
                insnHolder2 = insnHolders2[currAddress2++];
            }
            if (insnHolder2 != null) {
                ++insnHolderCount2;
            } else {
                break;
            }
            if (!isSameInstruction(insnHolder1, insnHolder2)) {
                return false;
            }
        }
        while (currAddress1 < insnHolders1.length) {
            if (insnHolders1[currAddress1++] != null) {
                return false;
            }
        }
        while (currAddress2 < insnHolders2.length) {
            if (insnHolders2[currAddress2++] != null) {
                return false;
            }
        }
        return insnHolderCount1 == insnHolderCount2;
    }

    private int getPromotedOpCodeOnDemand(InstructionHolder insn) {
        final int opcode = insn.opcode;
        if (opcode == Opcodes.CONST_STRING || opcode == Opcodes.CONST_STRING_JUMBO) {
            return Opcodes.CONST_STRING_JUMBO;
        } else if (opcode == Opcodes.GOTO || opcode == Opcodes.GOTO_16 || opcode == Opcodes.GOTO_32) {
            return Opcodes.GOTO_32;
        }
        return opcode;
    }

    public boolean isSameInstruction(int insnAddress1, int insnAddress2) {
        InstructionHolder insnHolder1 = this.insnHolders1[insnAddress1];
        InstructionHolder insnHolder2 = this.insnHolders2[insnAddress2];
        return isSameInstruction(insnHolder1, insnHolder2);
    }

    public boolean isSameInstruction(InstructionHolder insnHolder1, InstructionHolder insnHolder2) {
        if (insnHolder1 == null && insnHolder2 == null) {
            return true;
        }
        if (insnHolder1 == null || insnHolder2 == null) {
            return false;
        }
        if (getPromotedOpCodeOnDemand(insnHolder1) != getPromotedOpCodeOnDemand(insnHolder2)) {
            return false;
        }
        int opcode = insnHolder1.opcode;
        int insnFormat = insnHolder1.insnFormat;
        switch (insnFormat) {
            case InstructionCodec.INSN_FORMAT_10T:
            case InstructionCodec.INSN_FORMAT_20T:
            case InstructionCodec.INSN_FORMAT_21T:
            case InstructionCodec.INSN_FORMAT_22T:
            case InstructionCodec.INSN_FORMAT_30T:
            case InstructionCodec.INSN_FORMAT_31T: {
                final String addrPairStr = insnHolder1.address + "-" + insnHolder2.address;
                if (this.visitedInsnAddrPairs.add(addrPairStr)) {
                    // If we haven't compared target insns, following the control flow
                    // and do further compare.
                    return isSameInstruction(insnHolder1.target, insnHolder2.target);
                } else {
                    // If we have already compared target insns, here we can return
                    // true directly.
                    return true;
                }
            }
            case InstructionCodec.INSN_FORMAT_21C:
            case InstructionCodec.INSN_FORMAT_22C:
            case InstructionCodec.INSN_FORMAT_31C:
            case InstructionCodec.INSN_FORMAT_35C:
            case InstructionCodec.INSN_FORMAT_3RC: {
                return compareIndex(opcode, insnHolder1.index, insnHolder2.index);
            }
            case InstructionCodec.INSN_FORMAT_PACKED_SWITCH_PAYLOAD: {
                PackedSwitchPayloadInsntructionHolder specInsnHolder1 = (PackedSwitchPayloadInsntructionHolder) insnHolder1;
                PackedSwitchPayloadInsntructionHolder specInsnHolder2 = (PackedSwitchPayloadInsntructionHolder) insnHolder2;
                if (specInsnHolder1.firstKey != specInsnHolder2.firstKey) {
                    return false;
                }
                if (specInsnHolder1.targets.length != specInsnHolder2.targets.length) {
                    return false;
                }
                int targetCount = specInsnHolder1.targets.length;
                for (int i = 0; i < targetCount; ++i) {
                    if (!isSameInstruction(specInsnHolder1.targets[i], specInsnHolder2.targets[i])) {
                        return false;
                    }
                }
                return true;
            }
            case InstructionCodec.INSN_FORMAT_SPARSE_SWITCH_PAYLOAD: {
                SparseSwitchPayloadInsntructionHolder specInsnHolder1 = (SparseSwitchPayloadInsntructionHolder) insnHolder1;
                SparseSwitchPayloadInsntructionHolder specInsnHolder2 = (SparseSwitchPayloadInsntructionHolder) insnHolder2;
                if (CompareUtils.uArrCompare(specInsnHolder1.keys, specInsnHolder2.keys) != 0) {
                    return false;
                }
                if (specInsnHolder1.targets.length != specInsnHolder2.targets.length) {
                    return false;
                }
                int targetCount = specInsnHolder1.targets.length;
                for (int i = 0; i < targetCount; ++i) {
                    if (!isSameInstruction(specInsnHolder1.targets[i], specInsnHolder2.targets[i])) {
                        return false;
                    }
                }
                return true;
            }
            case InstructionCodec.INSN_FORMAT_FILL_ARRAY_DATA_PAYLOAD: {
                FillArrayDataPayloadInstructionHolder specInsnHolder1 = (FillArrayDataPayloadInstructionHolder) insnHolder1;
                FillArrayDataPayloadInstructionHolder specInsnHolder2 = (FillArrayDataPayloadInstructionHolder) insnHolder2;
                if (specInsnHolder1.elementWidth != specInsnHolder2.elementWidth) {
                    return false;
                }
                if (specInsnHolder1.size != specInsnHolder2.size) {
                    return false;
                }

                int elementWidth = specInsnHolder1.elementWidth;
                switch (elementWidth) {
                    case 1: {
                        byte[] array1 = (byte[]) specInsnHolder1.data;
                        byte[] array2 = (byte[]) specInsnHolder2.data;
                        return CompareUtils.uArrCompare(array1, array2) == 0;
                    }
                    case 2: {
                        short[] array1 = (short[]) specInsnHolder1.data;
                        short[] array2 = (short[]) specInsnHolder2.data;
                        return CompareUtils.uArrCompare(array1, array2) == 0;
                    }
                    case 4: {
                        int[] array1 = (int[]) specInsnHolder1.data;
                        int[] array2 = (int[]) specInsnHolder2.data;
                        return CompareUtils.uArrCompare(array1, array2) == 0;
                    }
                    case 8: {
                        long[] array1 = (long[]) specInsnHolder1.data;
                        long[] array2 = (long[]) specInsnHolder2.data;
                        return CompareUtils.sArrCompare(array1, array2) == 0;
                    }
                    default: {
                        throw new DexException("bogus element_width: " + Hex.u2(elementWidth));
                    }
                }
            }
            default: {
                if (insnHolder1.literal != insnHolder2.literal) {
                    return false;
                }
                if (insnHolder1.registerCount != insnHolder2.registerCount) {
                    return false;
                }
                if (insnHolder1.a != insnHolder2.a) {
                    return false;
                }
                if (insnHolder1.b != insnHolder2.b) {
                    return false;
                }
                if (insnHolder1.c != insnHolder2.c) {
                    return false;
                }
                if (insnHolder1.d != insnHolder2.d) {
                    return false;
                }
                if (insnHolder1.e != insnHolder2.e) {
                    return false;
                }
                return true;
            }
        }
    }

    private boolean compareIndex(int opcode, int index1, int index2) {
        switch (InstructionCodec.getInstructionIndexType(opcode)) {
            case InstructionCodec.INDEX_TYPE_STRING_REF: {
                return compareString(index1, index2);
            }
            case InstructionCodec.INDEX_TYPE_TYPE_REF: {
                return compareType(index1, index2);
            }
            case InstructionCodec.INDEX_TYPE_FIELD_REF: {
                return compareField(index1, index2);
            }
            case InstructionCodec.INDEX_TYPE_METHOD_REF: {
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

    private static class InstructionHolder {
        int insnFormat = InstructionCodec.INSN_FORMAT_UNKNOWN;
        int address = -1;
        int opcode = -1;
        int index = 0;
        int target = 0;
        long literal = 0L;
        int registerCount = 0;
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;
        int e = 0;
    }

    private static class SparseSwitchPayloadInsntructionHolder extends InstructionHolder {
        int[] keys = null;
        int[] targets = null;
    }

    private static class PackedSwitchPayloadInsntructionHolder extends InstructionHolder {
        int firstKey = 0;
        int[] targets = null;
    }

    private static class FillArrayDataPayloadInstructionHolder extends InstructionHolder {
        Object data = null;
        int size = 0;
        int elementWidth = 0;
    }
}
