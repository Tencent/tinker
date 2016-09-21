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
 * *** This file is NOT a part of AOSP. ***
 *
 * Created by tangyinsheng on 2016/5/26.
 */
public class InstructionVisitor {
    private final InstructionVisitor prevIv;

    public InstructionVisitor(InstructionVisitor iv) {
        this.prevIv = iv;
    }

    public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
        if (prevIv != null) {
            prevIv.visitZeroRegisterInsn(currentAddress, opcode, index, indexType, target, literal);
        }
    }

    public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
        if (prevIv != null) {
            prevIv.visitOneRegisterInsn(currentAddress, opcode, index, indexType, target, literal, a);
        }
    }

    public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
        if (prevIv != null) {
            prevIv.visitTwoRegisterInsn(currentAddress, opcode, index, indexType, target, literal, a, b);
        }
    }

    public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
        if (prevIv != null) {
            prevIv.visitThreeRegisterInsn(currentAddress, opcode, index, indexType, target, literal, a, b, c);
        }
    }

    public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
        if (prevIv != null) {
            prevIv.visitFourRegisterInsn(currentAddress, opcode, index, indexType, target, literal, a, b, c, d);
        }
    }

    public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
        if (prevIv != null) {
            prevIv.visitFiveRegisterInsn(currentAddress, opcode, index, indexType, target, literal, a, b, c, d, e);
        }
    }

    public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
        if (prevIv != null) {
            prevIv.visitRegisterRangeInsn(currentAddress, opcode, index, indexType, target, literal, a, registerCount);
        }
    }

    public void visitSparseSwitchPayloadInsn(int currentAddress, int opcode, int[] keys, int[] targets) {
        if (prevIv != null) {
            prevIv.visitSparseSwitchPayloadInsn(currentAddress, opcode, keys, targets);
        }
    }

    public void visitPackedSwitchPayloadInsn(int currentAddress, int opcode, int firstKey, int[] targets) {
        if (prevIv != null) {
            prevIv.visitPackedSwitchPayloadInsn(currentAddress, opcode, firstKey, targets);
        }
    }

    public void visitFillArrayDataPayloadInsn(int currentAddress, int opcode, Object data, int size, int elementWidth) {
        if (prevIv != null) {
            prevIv.visitFillArrayDataPayloadInsn(currentAddress, opcode, data, size, elementWidth);
        }
    }
}
