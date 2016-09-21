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

package com.tencent.tinker.android.dx.util;

import com.tencent.tinker.android.dex.DexException;
import com.tencent.tinker.android.dx.instruction.InstructionCodec;
import com.tencent.tinker.android.dx.instruction.InstructionPromoter;
import com.tencent.tinker.android.dx.instruction.InstructionReader;
import com.tencent.tinker.android.dx.instruction.InstructionVisitor;
import com.tencent.tinker.android.dx.instruction.InstructionWriter;
import com.tencent.tinker.android.dx.instruction.ShortArrayCodeInput;
import com.tencent.tinker.android.dx.instruction.ShortArrayCodeOutput;

import java.io.EOFException;

/**
 * Created by tangyinsheng on 2016/6/29.
 */
public final class InstructionTransformer {
    private final com.tencent.tinker.android.dx.util.IndexMap indexMap;

    public InstructionTransformer(com.tencent.tinker.android.dx.util.IndexMap indexMap) {
        this.indexMap = indexMap;
    }

    public short[] transform(short[] encodedInstructions) throws DexException {
        ShortArrayCodeOutput out = new ShortArrayCodeOutput(encodedInstructions.length);
        InstructionPromoter ipmo = new InstructionPromoter();
        InstructionWriter iw = new InstructionWriter(out, ipmo);
        InstructionReader ir = new InstructionReader(new ShortArrayCodeInput(encodedInstructions));

        try {
            // First visit, we collect mappings from original target address to promoted target address.
            ir.accept(new InstructionTransformVisitor(ipmo));

            // Then do the real transformation work.
            ir.accept(new InstructionTransformVisitor(iw));
        } catch (EOFException e) {
            throw new DexException(e);
        }

        return out.getArray();
    }

    private final class InstructionTransformVisitor extends InstructionVisitor {
        InstructionTransformVisitor(InstructionVisitor iv) {
            super(iv);
        }

        @Override
        public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitZeroRegisterInsn(currentAddress, opcode, mappedIndex, indexType, target, literal);
        }

        @Override
        public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitOneRegisterInsn(currentAddress, opcode, mappedIndex, indexType, target, literal, a);
        }

        @Override
        public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitTwoRegisterInsn(currentAddress, opcode, mappedIndex, indexType, target, literal, a, b);
        }

        @Override
        public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitThreeRegisterInsn(currentAddress, opcode, mappedIndex, indexType, target, literal, a, b, c);
        }

        @Override
        public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitFourRegisterInsn(currentAddress, opcode, mappedIndex, indexType, target, literal, a, b, c, d);
        }

        @Override
        public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitFiveRegisterInsn(currentAddress, opcode, mappedIndex, indexType, target, literal, a, b, c, d, e);
        }

        @Override
        public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
            int mappedIndex = transformIndexIfNeeded(index, indexType);
            super.visitRegisterRangeInsn(currentAddress, opcode, mappedIndex, indexType, target, literal, a, registerCount);
        }

        private int transformIndexIfNeeded(int index, int indexType) {
            switch (indexType) {
                case InstructionCodec.INDEX_TYPE_STRING_REF: {
                    return indexMap.adjustStringIndex(index);
                }
                case InstructionCodec.INDEX_TYPE_TYPE_REF: {
                    return indexMap.adjustTypeIdIndex(index);
                }
                case InstructionCodec.INDEX_TYPE_FIELD_REF: {
                    return indexMap.adjustFieldIdIndex(index);
                }
                case InstructionCodec.INDEX_TYPE_METHOD_REF: {
                    return indexMap.adjustMethodIdIndex(index);
                }
                default: {
                    return index;
                }
            }
        }

    }
}

