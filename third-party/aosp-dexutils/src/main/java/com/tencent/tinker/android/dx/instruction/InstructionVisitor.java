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
 * Created by tangyinsheng on 2016/5/26.
 */
public class InstructionVisitor {
    private final InstructionVisitor prevIv;

    public InstructionVisitor(InstructionVisitor iv) {
        this.prevIv = iv;
    }

    public void visitZeroRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal) {
        if (prevIv != null) {
            prevIv.visitZeroRegisterInsn(opcode, index, indexType, target, literal);
        }
    }

    public void visitOneRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a) {
        if (prevIv != null) {
            prevIv.visitOneRegisterInsn(opcode, index, indexType, target, literal, a);
        }
    }

    public void visitTwoRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b) {
        if (prevIv != null) {
            prevIv.visitTwoRegisterInsn(opcode, index, indexType, target, literal, a, b);
        }
    }

    public void visitThreeRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b, int c) {
        if (prevIv != null) {
            prevIv.visitThreeRegisterInsn(opcode, index, indexType, target, literal, a, b, c);
        }
    }

    public void visitFourRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b, int c, int d) {
        if (prevIv != null) {
            prevIv.visitFourRegisterInsn(opcode, index, indexType, target, literal, a, b, c, d);
        }
    }

    public void visitFiveRegisterInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int b, int c, int d, int e) {
        if (prevIv != null) {
            prevIv.visitFiveRegisterInsn(opcode, index, indexType, target, literal, a, b, c, d, e);
        }
    }

    public void visitRegisterRangeInsn(int opcode, int index, IndexType indexType, int target, long literal, int a, int registerCount) {
        if (prevIv != null) {
            prevIv.visitRegisterRangeInsn(opcode, index, indexType, target, literal, a, registerCount);
        }
    }

    public void visitSparseSwitchPayloadInsn(int opcode, int[] keys, int[] targets) {
        if (prevIv != null) {
            prevIv.visitSparseSwitchPayloadInsn(opcode, keys, targets);
        }
    }

    public void visitPackedSwitchPayloadInsn(int opcode, int firstKey, int[] targets) {
        if (prevIv != null) {
            prevIv.visitPackedSwitchPayloadInsn(opcode, firstKey, targets);
        }
    }

    public void visitFillArrayDataPayloadInsn(int opcode, Object data, int size, int elementWidth) {
        if (prevIv != null) {
            prevIv.visitFillArrayDataPayloadInsn(opcode, data, size, elementWidth);
        }
    }
}
