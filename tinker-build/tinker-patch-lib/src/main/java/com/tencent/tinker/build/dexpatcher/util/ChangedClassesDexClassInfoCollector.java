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
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dx.instruction.InstructionCodec;
import com.tencent.tinker.android.dx.instruction.InstructionReader;
import com.tencent.tinker.android.dx.instruction.InstructionVisitor;
import com.tencent.tinker.android.dx.instruction.ShortArrayCodeInput;
import com.tencent.tinker.build.util.DexClassesComparator;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger;

import java.io.EOFException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.tencent.tinker.build.util.DexClassesComparator.DexClassInfo;
import static com.tencent.tinker.build.util.DexClassesComparator.DexGroup;

/**
 * Created by tangyinsheng on 2017/2/26.
 */

public class ChangedClassesDexClassInfoCollector {
    private static final String TAG = "ChangedClassesDexClassInfoCollector";

    private static final DexPatcherLogger LOGGER = new DexPatcherLogger();
    private final Set<String> excludedClassPatterns = new HashSet<>();
    private boolean includeRefererToRefererAffectedClasses = false;

    public ChangedClassesDexClassInfoCollector setExcludedClassPatterns(Collection<String> loaderClassPatterns) {
        this.excludedClassPatterns.clear();
        this.excludedClassPatterns.addAll(loaderClassPatterns);
        return this;
    }

    public ChangedClassesDexClassInfoCollector clearExcludedClassPatterns() {
        this.excludedClassPatterns.clear();
        return this;
    }

    public ChangedClassesDexClassInfoCollector setLogger(DexPatcherLogger.IDexPatcherLogger loggerImpl) {
        LOGGER.setLoggerImpl(loggerImpl);
        return this;
    }

    public ChangedClassesDexClassInfoCollector setIncludeRefererToRefererAffectedClasses(boolean enabled) {
        this.includeRefererToRefererAffectedClasses = enabled;
        return this;
    }

    public Set<DexClassInfo> doCollect(DexGroup oldDexGroup, DexGroup newDexGroup) {
        final Set<String> classDescsInResult = new HashSet<>();
        final Set<DexClassInfo> result = new HashSet<>();

        DexClassesComparator dexClassCmptor = new DexClassesComparator("*");
        dexClassCmptor.setCompareMode(DexClassesComparator.COMPARE_MODE_NORMAL);
        dexClassCmptor.setIgnoredRemovedClassDescPattern(excludedClassPatterns);
        dexClassCmptor.setLogger(LOGGER.getLoggerImpl());
        dexClassCmptor.startCheck(oldDexGroup, newDexGroup);

        // So far we collected infos of all added, changed, and deleted classes.
        result.addAll(dexClassCmptor.getAddedClassInfos());

        final Collection<DexClassInfo[]> changedClassInfos = dexClassCmptor.getChangedClassDescToInfosMap().values();

        for (DexClassInfo[] oldAndNewInfoPair : changedClassInfos) {
            final DexClassInfo newClassInfo = oldAndNewInfoPair[1];

            LOGGER.i(TAG, "Add class %s to changed classes dex.", newClassInfo.classDesc);
            result.add(newClassInfo);
        }

        for (DexClassInfo classInfo : result) {
            classDescsInResult.add(classInfo.classDesc);
        }

        if (includeRefererToRefererAffectedClasses) {
            // Then we also need to add classes who refer to classes with referrer
            // affected changes to the result. (referrer affected change means the changes
            // that may cause referrer refer to wrong target.)
            dexClassCmptor.setCompareMode(DexClassesComparator.COMPARE_MODE_REFERRER_AFFECTED_CHANGE_ONLY);
            dexClassCmptor.startCheck(oldDexGroup, newDexGroup);

            Set<String> referrerAffectedChangedClassDescs = dexClassCmptor.getChangedClassDescToInfosMap().keySet();
            Set<DexClassInfo> oldClassInfos = oldDexGroup.getClassInfosInDexesWithDuplicateCheck();

            for (DexClassInfo oldClassInfo : oldClassInfos) {
                if (!classDescsInResult.contains(oldClassInfo.classDesc)
                        && isClassReferToAnyClasses(oldClassInfo, referrerAffectedChangedClassDescs)) {
                    LOGGER.i(TAG, "Add class %s in old dex to changed classes dex since it is affected by modified referee.", oldClassInfo.classDesc);
                    result.add(oldClassInfo);
                }
            }
        }

        return result;
    }

    private boolean isClassReferToAnyClasses(DexClassInfo classInfo, Set<String> refereeClassDescs) {
        if (classInfo.classDef.classDataOffset == ClassDef.NO_OFFSET) {
            return false;
        }
        ClassData classData = classInfo.owner.readClassData(classInfo.classDef);
        for (ClassData.Method method : classData.directMethods) {
            if (isMethodReferToAnyClasses(classInfo, method, refereeClassDescs)) {
                return true;
            }
        }
        for (ClassData.Method method : classData.virtualMethods) {
            if (isMethodReferToAnyClasses(classInfo, method, refereeClassDescs)) {
                return true;
            }
        }
        return false;
    }

    private boolean isMethodReferToAnyClasses(DexClassInfo classInfo, ClassData.Method method, Set<String> refereeClassDescs) {
        if (method.codeOffset == ClassDef.NO_OFFSET) {
            return false;
        }
        Code methodCode = classInfo.owner.readCode(method);
        InstructionReader ir = new InstructionReader(new ShortArrayCodeInput(methodCode.instructions));
        ReferToClassesCheckVisitor rtcv = new ReferToClassesCheckVisitor(classInfo.owner, method, refereeClassDescs);
        try {
            ir.accept(rtcv);
        } catch (EOFException e) {
            // Should not be here.
        }
        return rtcv.isReferToAnyRefereeClasses;
    }

    private static class ReferToClassesCheckVisitor extends InstructionVisitor {
        private final Dex owner;
        private final ClassData.Method method;
        private final Collection<String> refereeClassDescs;

        private boolean isReferToAnyRefereeClasses = false;

        ReferToClassesCheckVisitor(Dex owner, ClassData.Method method, Collection<String> refereeClassDescs) {
            super(null);
            this.owner = owner;
            this.method = method;
            this.refereeClassDescs = refereeClassDescs;
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
                    typeName = owner.typeNames().get(index);
                    refInfoInLog = "init referrer-affected class";
                    break;
                }
                case InstructionCodec.INDEX_TYPE_FIELD_REF: {
                    final FieldId fieldId = owner.fieldIds().get(index);
                    typeName = owner.typeNames().get(fieldId.declaringClassIndex);
                    refInfoInLog = "referencing to field: " + owner.strings().get(fieldId.nameIndex);
                    break;
                }
                case InstructionCodec.INDEX_TYPE_METHOD_REF: {
                    final MethodId methodId = owner.methodIds().get(index);
                    typeName = owner.typeNames().get(methodId.declaringClassIndex);
                    refInfoInLog = "invoking method: " + getMethodProtoTypeStr(methodId);
                    break;
                }
                default: {
                    break;
                }
            }
            if (typeName != null && refereeClassDescs.contains(typeName)) {
                MethodId methodId = owner.methodIds().get(method.methodIndex);
                LOGGER.i(
                        TAG,
                        "Method %s in class %s referenced referrer-affected class %s by %s",
                        getMethodProtoTypeStr(methodId),
                        owner.typeNames().get(methodId.declaringClassIndex),
                        typeName,
                        refInfoInLog
                );
                isReferToAnyRefereeClasses = true;
            }
        }

        private String getMethodProtoTypeStr(MethodId methodId) {
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(owner.strings().get(methodId.nameIndex));
            ProtoId protoId = owner.protoIds().get(methodId.protoIndex);
            strBuilder.append('(');
            short[] paramTypeIds = owner.parameterTypeIndicesFromMethodId(methodId);
            for (short typeId : paramTypeIds) {
                strBuilder.append(owner.typeNames().get(typeId));
            }
            strBuilder.append(')').append(owner.typeNames().get(protoId.returnTypeIndex));
            return strBuilder.toString();
        }
    }
}
