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

    // Collect target:
    //  Added classes;
    //  Changed classes;
    //  Subclasses of referrer-affected changed classes;
    //  Classes which refer to changed classes.
    public Set<DexClassInfo> doCollect(DexGroup oldDexGroup, DexGroup newDexGroup) {
        Set<DexClassInfo> classInfosInSmallDex = new HashSet<>();

        DexClassesComparator dexClassesCmp = new DexClassesComparator("*");
        dexClassesCmp.setIgnoredRemovedClassDescPattern(this.loaderClassPatterns);

        dexClassesCmp.setCompareMode(DexClassesComparator.COMPARE_MODE_CAUSE_REF_CHANGE_ONLY);
        dexClassesCmp.startCheck(oldDexGroup, newDexGroup);

        Set<String> referrerAffectedChangedClassDescs
                = dexClassesCmp.getChangedClassDescToInfosMap().keySet();

        Set<String> referrerAffectedChangedClassesChainSet = new HashSet<>();
        referrerAffectedChangedClassesChainSet.addAll(referrerAffectedChangedClassDescs);

        // Add added classes to small patched dex.
        Collection<DexClassInfo> addedClassInfos = dexClassesCmp.getAddedClassInfos();
        for (DexClassInfo addClassInfo : addedClassInfos) {
            logger.i(TAG, "Add class %s to small dex.", addClassInfo.classDesc);
            classInfosInSmallDex.add(addClassInfo);
        }

        // Use normal mode to compare again, then we get all changed class infos.
        dexClassesCmp.setCompareMode(DexClassesComparator.COMPARE_MODE_NORMAL);
        dexClassesCmp.startCheck(oldDexGroup, newDexGroup);

        Collection<DexClassInfo[]> changedOldNewClassInfos =
                dexClassesCmp.getChangedClassDescToInfosMap().values();

        // Add changed classes to small patched dex.
        // changedOldNewClassInfo[1] means changedNewClassInfo
        for (DexClassInfo[] changedOldNewClassInfo : changedOldNewClassInfos) {
            logger.i(TAG, "Add class %s to small dex.", changedOldNewClassInfo[1].classDesc);
            classInfosInSmallDex.add(changedOldNewClassInfo[1]);
        }

        Set<DexClassInfo> classInfosInNewDexGroup
                = newDexGroup.getClassInfosInDexesWithDuplicateCheck();

        Set<String> changedClassDescs = dexClassesCmp.getChangedClassDescToInfosMap().keySet();

        // Add subclasses of referrer-affected changed classes to small patched dex.
        // By the way, collect all subclasses to form referrer-affected changed classes chain.
        for (DexClassInfo patchedClassInfo : classInfosInNewDexGroup) {
            final String superClassDesc
                    = patchedClassInfo.classDef.supertypeIndex == ClassDef.NO_INDEX
                    ? ""
                    : patchedClassInfo.owner.typeNames().get(patchedClassInfo.classDef.supertypeIndex);

            if (referrerAffectedChangedClassesChainSet.contains(superClassDesc)) {
                referrerAffectedChangedClassesChainSet.add(patchedClassInfo.classDesc);
                logger.i(TAG, "Class %s is subclass of referrer-affected changed class %s.",
                        patchedClassInfo.classDesc, superClassDesc);

                logger.i(TAG, "Add class %s to small dex.", patchedClassInfo.classDesc);

                classInfosInSmallDex.add(patchedClassInfo);
            }
        }

        Set<String> classesToCheckReference = new HashSet<>();
        classesToCheckReference.addAll(changedClassDescs);
        classesToCheckReference.addAll(referrerAffectedChangedClassesChainSet);

        Set<String> addedClassDescs = new HashSet<>();
        for (DexClassInfo addedClassInfo : addedClassInfos) {
            addedClassDescs.add(addedClassInfo.classDesc);
        }

        // Add classes which refer to changed classes and referrer-affected
        // changed classes chain to small patched dex.
        for (DexClassInfo patchedClassInfo : classInfosInNewDexGroup) {
            if (!addedClassDescs.contains(patchedClassInfo.classDesc)
             && !changedClassDescs.contains(patchedClassInfo.classDesc)) {
                processMethodReference(
                        patchedClassInfo,
                        classesToCheckReference,
                        classInfosInSmallDex
                );
            }
        }

        return classInfosInSmallDex;
    }

    private void processMethodReference(
            DexClassInfo patchedClassInfo,
            Set<String> classDescsToCheck,
            Set<DexClassInfo> result
    ) {
        final ClassDef classDef = patchedClassInfo.classDef;
        if (classDef.classDataOffset == ClassDef.NO_OFFSET) {
            return;
        }

        ClassData patchedClassData
                = patchedClassInfo.owner.readClassData(classDef);

        boolean shouldAdd = isClassMethodReferenceToClasses(
                patchedClassInfo.owner,
                patchedClassData.directMethods,
                classDescsToCheck
        );

        if (!shouldAdd) {
            shouldAdd = isClassMethodReferenceToClasses(
                    patchedClassInfo.owner,
                    patchedClassData.virtualMethods,
                    classDescsToCheck
            );
        }

        if (shouldAdd) {
            logger.i(TAG, "Add class %s to small dex.", patchedClassInfo.classDesc);
            result.add(patchedClassInfo);
        }
    }

    private boolean isClassMethodReferenceToClasses(
            Dex owner,
            ClassData.Method[] methods,
            Collection<String> referredClassDescs
    ) {
        if (referredClassDescs.isEmpty() || methods == null || methods.length == 0) {
            return false;
        }

        for (ClassData.Method method : methods) {
            if (method.codeOffset == 0) {
                continue;
            }
            Code code = owner.readCode(method);
            ClassReferringInsnVisitor refInsnVisitor =
                    new ClassReferringInsnVisitor(owner, method, referredClassDescs, logger);
            InstructionReader insnReader =
                    new InstructionReader(new ShortArrayCodeInput(code.instructions));
            try {
                insnReader.accept(refInsnVisitor);
                if (refInsnVisitor.isMethodReferencedToAnyProvidedClasses) {
                    return true;
                }
            } catch (EOFException e) {
                throw new IllegalStateException(e);
            }
        }

        return false;
    }
}
