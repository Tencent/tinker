/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.build.dexpatcher.util;

import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dx.instruction.InstructionCodec;
import com.tencent.tinker.android.dx.instruction.InstructionVisitor;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger;

import java.util.Collection;

/**
 * Created by tangyinsheng on 2016/10/8.
 */

public class ClassReferringInsnVisitor extends InstructionVisitor {
    private static final String TAG = "ClassReferringInsnVisitor";

    private final Dex methodOwner;
    private final ClassData.Method method;
    private final Collection<String> classDescsToCheck;
    private final DexPatcherLogger logger;

    public boolean isMethodReferencedToAnyProvidedClasses;

    ClassReferringInsnVisitor(Dex methodOwner, ClassData.Method method, Collection<String> classDescsToCheck, DexPatcherLogger logger) {
        super(null);
        this.methodOwner = methodOwner;
        this.method = method;
        this.classDescsToCheck = classDescsToCheck;
        this.logger = logger;
        this.isMethodReferencedToAnyProvidedClasses = false;
    }

    @Override
    public void visitZeroRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal) {
        processIndexByType(index, indexType);
    }

    @Override
    public void visitOneRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a) {
        processIndexByType(index, indexType);
    }

    @Override
    public void visitTwoRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b) {
        processIndexByType(index, indexType);
    }

    @Override
    public void visitThreeRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c) {
        processIndexByType(index, indexType);
    }

    @Override
    public void visitFourRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d) {
        processIndexByType(index, indexType);
    }

    @Override
    public void visitFiveRegisterInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int b, int c, int d, int e) {
        processIndexByType(index, indexType);
    }

    @Override
    public void visitRegisterRangeInsn(int currentAddress, int opcode, int index, int indexType, int target, long literal, int a, int registerCount) {
        processIndexByType(index, indexType);
    }

    private void processIndexByType(int index, int indexType) {
        String typeName = null;
        String refInfoInLog = null;
        switch (indexType) {
            case InstructionCodec.INDEX_TYPE_TYPE_REF: {
                typeName = methodOwner.typeNames().get(index);
                refInfoInLog = "init class";
                break;
            }
            case InstructionCodec.INDEX_TYPE_FIELD_REF: {
                final FieldId fieldId = methodOwner.fieldIds().get(index);
                typeName = methodOwner.typeNames().get(fieldId.declaringClassIndex);
                refInfoInLog = "referencing to field: " + methodOwner.strings().get(fieldId.nameIndex);
                break;
            }
            case InstructionCodec.INDEX_TYPE_METHOD_REF: {
                final MethodId methodId = methodOwner.methodIds().get(index);
                typeName = methodOwner.typeNames().get(methodId.declaringClassIndex);
                refInfoInLog = "invoking method: " + getMethodProtoTypeStr(methodId);
                break;
            }
        }
        if (typeName != null && classDescsToCheck.contains(typeName)) {
            MethodId methodId = methodOwner.methodIds().get(method.methodIndex);
            logger.i(
                    TAG,
                    "Method %s in class %s referenced class %s by %s",
                    getMethodProtoTypeStr(methodId),
                    methodOwner.typeNames().get(methodId.declaringClassIndex),
                    typeName,
                    refInfoInLog
            );
            isMethodReferencedToAnyProvidedClasses = true;
        }
    }

    private String getMethodProtoTypeStr(MethodId methodId) {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append(methodOwner.strings().get(methodId.nameIndex));
        ProtoId protoId = methodOwner.protoIds().get(methodId.protoIndex);
        strBuilder.append('(');
        short[] paramTypeIds = methodOwner.parameterTypeIndicesFromMethodId(methodId);
        for (short typeId : paramTypeIds) {
            strBuilder.append(methodOwner.typeNames().get(typeId));
        }
        strBuilder.append(')').append(methodOwner.typeNames().get(protoId.returnTypeIndex));
        return strBuilder.toString();
    }
}
