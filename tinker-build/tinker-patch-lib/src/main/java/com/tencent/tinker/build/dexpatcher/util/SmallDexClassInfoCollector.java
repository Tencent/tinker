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
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dx.instruction.InstructionReader;
import com.tencent.tinker.android.dx.instruction.ShortArrayCodeInput;
import com.tencent.tinker.build.util.DexClassesComparator;
import com.tencent.tinker.build.util.DexClassesComparator.DexClassInfo;
import com.tencent.tinker.build.util.DexClassesComparator.DexGroup;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger;

import java.io.EOFException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tangyinsheng on 2016/10/8.
 */

public final class SmallDexClassInfoCollector {
    private static final String TAG = "SmallDexClassInfoCollector";

    private static final DexPatcherLogger logger = new DexPatcherLogger();
    private final Set<String> loaderClassPatterns = new HashSet<>();

    public SmallDexClassInfoCollector setLoaderClassPatterns(Collection<String> loaderClassPatterns) {
        this.loaderClassPatterns.clear();
        this.loaderClassPatterns.addAll(loaderClassPatterns);
        return this;
    }

    public SmallDexClassInfoCollector addLoaderClassPattern(String loaderClassPattern) {
        this.loaderClassPatterns.add(loaderClassPattern);
        return this;
    }

    public SmallDexClassInfoCollector clearLoaderClassPattern() {
        this.loaderClassPatterns.clear();
        return this;
    }

    public SmallDexClassInfoCollector setLogger(DexPatcherLogger.IDexPatcherLogger loggerImpl) {
        this.logger.setLoggerImpl(loggerImpl);
        return this;
    }

    public Set<DexClassInfo> doCollect(DexGroup oldDexGroup, DexGroup newDexGroup) {
        DexClassesComparator dexClassesCmp = new DexClassesComparator("*");
        dexClassesCmp.setCompareMode(DexClassesComparator.COMPARE_MODE_CAUSE_REF_CHANGE_ONLY);
        dexClassesCmp.setIgnoredRemovedClassDescPattern(this.loaderClassPatterns);
        dexClassesCmp.startCheck(oldDexGroup, newDexGroup);

        Set<String> refAffectedClassDescs
                = dexClassesCmp.getChangedClassDescToInfosMap().keySet();

        Set<DexClassInfo> classInfosInNewDexGroup
                = newDexGroup.getClassInfosInDexesWithDuplicateCheck();

        Set<DexClassInfo> classInfosOfSmallDex = new HashSet<>();

        for (DexClassInfo patchedClassInfo : classInfosInNewDexGroup) {
            if (patchedClassInfo.classDef.classDataOffset == 0) {
                continue;
            }

            ClassData patchedClassData
                    = patchedClassInfo.owner.readClassData(patchedClassInfo.classDef);

            boolean shouldAdd = isClassMethodReferenceToRefAffectedClass(
                    patchedClassInfo.owner,
                    patchedClassData.directMethods,
                    refAffectedClassDescs
            );

            if (!shouldAdd) {
                shouldAdd = isClassMethodReferenceToRefAffectedClass(
                        patchedClassInfo.owner,
                        patchedClassData.virtualMethods,
                        refAffectedClassDescs
                );
            }

            if (shouldAdd) {
                logger.i(TAG, "Add class %s to small dex.", patchedClassInfo.classDesc);
                classInfosOfSmallDex.add(patchedClassInfo);
            }
        }

        // So far we get descriptors of classes we need to add additionally,
        // while we still need to do a fully compare to collect added classes
        // and replaced classes since they may use items in their owner dex which
        // is not modified.
        dexClassesCmp.setCompareMode(DexClassesComparator.COMPARE_MODE_NORMAL);
        dexClassesCmp.startCheck(oldDexGroup, newDexGroup);

        Collection<DexClassInfo> addedClassInfos = dexClassesCmp.getAddedClassInfos();
        for (DexClassInfo addClassInfo : addedClassInfos) {
            logger.i(TAG, "Add class %s to small dex.", addClassInfo.classDesc);
            classInfosOfSmallDex.add(addClassInfo);
        }

        Collection<DexClassInfo[]> changedOldPatchedClassInfos =
                dexClassesCmp.getChangedClassDescToInfosMap().values();

        // changedOldPatchedClassInfo[1] means changedPatchedClassInfo
        for (DexClassInfo[] changedOldPatchedClassInfo : changedOldPatchedClassInfos) {
            logger.i(TAG, "Add class %s to small dex.", changedOldPatchedClassInfo[1].classDesc);
            classInfosOfSmallDex.add(changedOldPatchedClassInfo[1]);
        }

        return classInfosOfSmallDex;
    }

    private boolean isClassMethodReferenceToRefAffectedClass(
            Dex owner,
            ClassData.Method[] methods,
            Collection<String> affectedClassDescs
    ) {
        if (affectedClassDescs.isEmpty() || methods == null || methods.length == 0) {
            return false;
        }

        for (ClassData.Method method : methods) {
            if (method.codeOffset == 0) {
                continue;
            }
            Code code = owner.readCode(method);
            RefToRefAffectedClassInsnVisitor refInsnVisitor =
                    new RefToRefAffectedClassInsnVisitor(owner, method, affectedClassDescs, logger);
            InstructionReader insnReader =
                    new InstructionReader(new ShortArrayCodeInput(code.instructions));
            try {
                insnReader.accept(refInsnVisitor);
                if (refInsnVisitor.isMethodReferencedToRefAffectedClass) {
                    return true;
                }
            } catch (EOFException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }
}
