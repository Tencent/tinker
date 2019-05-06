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

package com.tencent.tinker.build.util;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.AnnotationsDirectory;
import com.tencent.tinker.android.dex.ClassData;
import com.tencent.tinker.android.dex.ClassData.Field;
import com.tencent.tinker.android.dex.ClassData.Method;
import com.tencent.tinker.android.dex.ClassDef;
import com.tencent.tinker.android.dex.Code;
import com.tencent.tinker.android.dex.DebugInfoItem;
import com.tencent.tinker.android.dex.Dex;
import com.tencent.tinker.android.dex.EncodedValue;
import com.tencent.tinker.android.dex.EncodedValueReader;
import com.tencent.tinker.android.dex.FieldId;
import com.tencent.tinker.android.dex.MethodId;
import com.tencent.tinker.android.dex.ProtoId;
import com.tencent.tinker.android.dex.TableOfContents;
import com.tencent.tinker.android.dex.TypeList;
import com.tencent.tinker.android.dex.io.DexDataBuffer;
import com.tencent.tinker.android.dx.instruction.InstructionComparator;
import com.tencent.tinker.build.dexpatcher.util.PatternUtils;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger;
import com.tencent.tinker.commons.dexpatcher.DexPatcherLogger.IDexPatcherLogger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Created by tangyinsheng on 2016/4/14.
 */
public final class DexClassesComparator {
    private static final String TAG = "DexClassesComparator";

    public static final int COMPARE_MODE_NORMAL = 0;
    public static final int COMPARE_MODE_REFERRER_AFFECTED_CHANGE_ONLY = 1;

    private static final int DBG_FIRST_SPECIAL = 0x0A;  // the smallest special opcode
    private static final int DBG_LINE_BASE   = -4;      // the smallest line number increment
    private static final int DBG_LINE_RANGE  = 15;      // the number of line increments represented

    private int compareMode = COMPARE_MODE_NORMAL;
    private final List<DexClassInfo> addedClassInfoList = new ArrayList<>();
    private final List<DexClassInfo> deletedClassInfoList = new ArrayList<>();
    // classDesc => [oldClassInfo, newClassInfo]
    private final Map<String, DexClassInfo[]> changedClassDescToClassInfosMap = new HashMap<>();
    private final Set<Pattern> patternsOfClassDescToCheck = new HashSet<>();
    private final Set<Pattern> patternsOfIgnoredRemovedClassDesc = new HashSet<>();
    private final Set<String> oldDescriptorOfClassesToCheck = new HashSet<>();
    private final Set<String> newDescriptorOfClassesToCheck = new HashSet<>();
    private final Map<String, DexClassInfo> oldClassDescriptorToClassInfoMap = new HashMap<>();
    private final Map<String, DexClassInfo> newClassDescriptorToClassInfoMap = new HashMap<>();

    // Record class descriptors whose references key (index or offset) of methods and fields
    // are changed.
    private final Set<String> refAffectedClassDescs = new HashSet<>();
    private final DexPatcherLogger logger = new DexPatcherLogger();

    public DexClassesComparator(String patternStringOfClassDescToCheck) {
        patternsOfClassDescToCheck.add(
                Pattern.compile(
                        PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStringOfClassDescToCheck)
                )
        );
    }

    public DexClassesComparator(String... patternStringsOfClassDescToCheck) {
        for (String patternStr : patternStringsOfClassDescToCheck) {
            patternsOfClassDescToCheck.add(
                    Pattern.compile(
                            PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStr)
                    )
            );
        }
    }

    public DexClassesComparator(Collection<String> patternStringsOfClassDescToCheck) {
        for (String patternStr : patternStringsOfClassDescToCheck) {
            patternsOfClassDescToCheck.add(
                    Pattern.compile(
                            PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStr)
                    )
            );
        }
    }

    public void setIgnoredRemovedClassDescPattern(String... patternStringsOfLoaderClassDesc) {
        patternsOfIgnoredRemovedClassDesc.clear();
        for (String patternStr : patternStringsOfLoaderClassDesc) {
            patternsOfIgnoredRemovedClassDesc.add(
                    Pattern.compile(
                            PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStr)
                    )
            );
        }
    }

    public void setIgnoredRemovedClassDescPattern(Collection<String> patternStringsOfLoaderClassDesc) {
        patternsOfIgnoredRemovedClassDesc.clear();
        for (String patternStr : patternStringsOfLoaderClassDesc) {
            patternsOfIgnoredRemovedClassDesc.add(
                    Pattern.compile(
                            PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStr)
                    )
            );
        }
    }

    public void setCompareMode(int mode) {
        if (mode == COMPARE_MODE_NORMAL || mode == COMPARE_MODE_REFERRER_AFFECTED_CHANGE_ONLY) {
            this.compareMode = mode;
        } else {
            throw new IllegalArgumentException("bad compare mode: " + mode);
        }
    }

    public void setLogger(IDexPatcherLogger logger) {
        this.logger.setLoggerImpl(logger);
    }

    public List<DexClassInfo> getAddedClassInfos() {
        return Collections.unmodifiableList(addedClassInfoList);
    }

    public List<DexClassInfo> getDeletedClassInfos() {
        return Collections.unmodifiableList(deletedClassInfoList);
    }

    public Map<String, DexClassInfo[]> getChangedClassDescToInfosMap() {
        return Collections.unmodifiableMap(changedClassDescToClassInfosMap);
    }

    public void startCheck(File oldDexFile, File newDexFile) throws IOException {
        startCheck(new Dex(oldDexFile), new Dex(newDexFile));
    }

    public void startCheck(Dex oldDex, Dex newDex) {
        startCheck(DexGroup.wrap(oldDex), DexGroup.wrap(newDex));
    }

    public void startCheck(DexGroup oldDexGroup, DexGroup newDexGroup) {
        // Init assist structures.
        addedClassInfoList.clear();
        deletedClassInfoList.clear();
        changedClassDescToClassInfosMap.clear();
        oldDescriptorOfClassesToCheck.clear();
        newDescriptorOfClassesToCheck.clear();
        oldClassDescriptorToClassInfoMap.clear();
        newClassDescriptorToClassInfoMap.clear();
        refAffectedClassDescs.clear();

        // Map classDesc and typeIndex to classInfo
        // and collect typeIndex of classes to check in oldDexes.
        for (Dex oldDex : oldDexGroup.dexes) {
            int classDefIndex = 0;
            for (ClassDef oldClassDef : oldDex.classDefs()) {
                String desc = oldDex.typeNames().get(oldClassDef.typeIndex);
                if (Utils.isStringMatchesPatterns(desc, patternsOfClassDescToCheck)) {
                    if (!oldDescriptorOfClassesToCheck.add(desc)) {
                        throw new IllegalStateException(
                                String.format(
                                        "duplicate class descriptor [%s] in different old dexes.",
                                        desc
                                )
                        );
                    }
                }
                DexClassInfo classInfo = new DexClassInfo(desc, classDefIndex, oldClassDef, oldDex);
                ++classDefIndex;
                oldClassDescriptorToClassInfoMap.put(desc, classInfo);
            }
        }

        // Map classDesc and typeIndex to classInfo
        // and collect typeIndex of classes to check in newDexes.
        for (Dex newDex : newDexGroup.dexes) {
            int classDefIndex = 0;
            for (ClassDef newClassDef : newDex.classDefs()) {
                String desc = newDex.typeNames().get(newClassDef.typeIndex);
                if (Utils.isStringMatchesPatterns(desc, patternsOfClassDescToCheck)) {
                    if (!newDescriptorOfClassesToCheck.add(desc)) {
                        throw new IllegalStateException(
                                String.format(
                                        "duplicate class descriptor [%s] in different new dexes.",
                                        desc
                                )
                        );
                    }
                }
                DexClassInfo classInfo = new DexClassInfo(desc, classDefIndex, newClassDef, newDex);
                ++classDefIndex;
                newClassDescriptorToClassInfoMap.put(desc, classInfo);
            }
        }

        Set<String> deletedClassDescs = new HashSet<>(oldDescriptorOfClassesToCheck);
        deletedClassDescs.removeAll(newDescriptorOfClassesToCheck);

        for (String desc : deletedClassDescs) {
            // These classes are deleted as we expect to, so we remove them
            // from result.
            if (Utils.isStringMatchesPatterns(desc, patternsOfIgnoredRemovedClassDesc)) {
                logger.i(TAG, "Ignored deleted class: %s", desc);
            } else {
                logger.i(TAG, "Deleted class: %s", desc);
                deletedClassInfoList.add(oldClassDescriptorToClassInfoMap.get(desc));
            }
        }

        Set<String> addedClassDescs = new HashSet<>(newDescriptorOfClassesToCheck);
        addedClassDescs.removeAll(oldDescriptorOfClassesToCheck);

        for (String desc : addedClassDescs) {
            if (Utils.isStringMatchesPatterns(desc, patternsOfIgnoredRemovedClassDesc)) {
                logger.i(TAG, "Ignored added class: %s", desc);
            } else {
                logger.i(TAG, "Added class: %s", desc);
                addedClassInfoList.add(newClassDescriptorToClassInfoMap.get(desc));
            }
        }

        Set<String> mayBeChangedClassDescs = new HashSet<>(oldDescriptorOfClassesToCheck);
        mayBeChangedClassDescs.retainAll(newDescriptorOfClassesToCheck);

        for (String desc : mayBeChangedClassDescs) {
            DexClassInfo oldClassInfo = oldClassDescriptorToClassInfoMap.get(desc);
            DexClassInfo newClassInfo = newClassDescriptorToClassInfoMap.get(desc);
            switch (compareMode) {
                case COMPARE_MODE_NORMAL: {
                    if (!isSameClass(
                            oldClassInfo.owner,
                            newClassInfo.owner,
                            oldClassInfo.classDef,
                            newClassInfo.classDef
                    )) {
                        if (Utils.isStringMatchesPatterns(desc, patternsOfIgnoredRemovedClassDesc)) {
                            logger.i(TAG, "Ignored changed class: %s", desc);
                        } else {
                            logger.i(TAG, "Changed class: %s", desc);
                            changedClassDescToClassInfosMap.put(
                                    desc, new DexClassInfo[]{oldClassInfo, newClassInfo}
                            );
                        }
                    }
                    break;
                }
                case COMPARE_MODE_REFERRER_AFFECTED_CHANGE_ONLY: {
                    if (isClassChangeAffectedToReferrer(
                            oldClassInfo.owner,
                            newClassInfo.owner,
                            oldClassInfo.classDef,
                            newClassInfo.classDef
                    )) {
                        if (Utils.isStringMatchesPatterns(desc, patternsOfIgnoredRemovedClassDesc)) {
                            logger.i(TAG, "Ignored referrer-affected changed class: %s", desc);
                        } else {
                            logger.i(TAG, "Referrer-affected change class: %s", desc);
                            changedClassDescToClassInfosMap.put(
                                    desc, new DexClassInfo[]{oldClassInfo, newClassInfo}
                            );
                        }
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        }
    }

    private boolean isClassChangeAffectedToReferrer(
            Dex oldDex,
            Dex newDex,
            ClassDef oldClassDef,
            ClassDef newClassDef
    ) {
        boolean result = false;

        String classDesc = oldDex.typeNames().get(oldClassDef.typeIndex);

        do {
            if (refAffectedClassDescs.contains(classDesc)) {
                result = true;
                return result;
            }

            // Any changes on superclass could affect refs of members in current class.
            if (isTypeChangeAffectedToReferrer(
                    oldDex, newDex, oldClassDef.supertypeIndex, newClassDef.supertypeIndex
            )) {
                result = true;
                break;
            }

            // Any changes on current class's interface list could affect refs
            // of members in current class.
            short[] oldInterfaceTypeIds = oldDex.interfaceTypeIndicesFromClassDef(oldClassDef);
            short[] newInterfaceTypeIds = newDex.interfaceTypeIndicesFromClassDef(newClassDef);
            if (isTypeIdsChangeAffectedToReferrer(
                    oldDex, newDex, oldInterfaceTypeIds, newInterfaceTypeIds, false
            )) {
                result = true;
                break;
            }

            // Any changes on current class's member lists could affect refs
            // of members in current class.
            ClassData oldClassData =
                    (oldClassDef.classDataOffset != 0 ? oldDex.readClassData(oldClassDef) : null);
            ClassData newClassData =
                    (newClassDef.classDataOffset != 0 ? newDex.readClassData(newClassDef) : null);
            if (isClassDataChangeAffectedToReferrer(
                    oldDex, newDex, oldClassData, newClassData
            )) {
                result = true;
                break;
            }
        } while (false);

        if (result) {
            refAffectedClassDescs.add(classDesc);
        }

        return result;
    }

    private boolean isTypeChangeAffectedToReferrer(
            Dex oldDex, Dex newDex, int oldTypeId, int newTypeId
    ) {
        if (oldTypeId != ClassDef.NO_INDEX && newTypeId != ClassDef.NO_INDEX) {
            String oldClassDesc = oldDex.typeNames().get(oldTypeId);
            String newClassDesc = newDex.typeNames().get(newTypeId);
            if (!oldClassDesc.equals(newClassDesc)) {
                return true;
            }

            final DexClassInfo oldClassInfo = oldClassDescriptorToClassInfoMap.get(oldClassDesc);
            final DexClassInfo newClassInfo = newClassDescriptorToClassInfoMap.get(newClassDesc);
            ClassDef oldClassDef = (oldClassInfo != null ? oldClassInfo.classDef : null);
            ClassDef newClassDef = (newClassInfo != null ? newClassInfo.classDef : null);
            if (oldClassDef != null && newClassDef != null) {
                return isClassChangeAffectedToReferrer(oldClassInfo.owner, newClassInfo.owner, oldClassDef, newClassDef);
            } else
            if (oldClassDef == null && newClassDef == null) {
                return false;
            } else {
                // If current comparing class is ignored, since it must be removed
                // in patched dexes as we expected, here we ignore this kind of changes.
                return !Utils.isStringMatchesPatterns(oldClassDesc, patternsOfIgnoredRemovedClassDesc);
            }
        } else {
            if (!(oldTypeId == ClassDef.NO_INDEX && newTypeId == ClassDef.NO_INDEX)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTypeIdsChangeAffectedToReferrer(
            Dex oldDex,
            Dex newDex,
            short[] oldTypeIds,
            short[] newTypeIds,
            boolean compareNameOnly
    ) {
        if (oldTypeIds.length != newTypeIds.length) {
            return true;
        }

        int typeIdCount = oldTypeIds.length;
        for (int i = 0; i < typeIdCount; ++i) {
            if (compareNameOnly) {
                String oldTypeName = oldDex.typeNames().get(oldTypeIds[i]);
                String newTypeName = newDex.typeNames().get(newTypeIds[i]);
                if (!oldTypeName.equals(newTypeName)) {
                    return true;
                }
            } else {
                if (isTypeChangeAffectedToReferrer(oldDex, newDex, oldTypeIds[i], newTypeIds[i])) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isClassDataChangeAffectedToReferrer(
            Dex oldDex,
            Dex newDex,
            ClassData oldClassData,
            ClassData newClassData
    ) {
        if (oldClassData != null && newClassData != null) {
            if (isFieldsChangeAffectedToReferrer(
                    oldDex, newDex, oldClassData.instanceFields, newClassData.instanceFields
            )) {
                return true;
            }

            if (isFieldsChangeAffectedToReferrer(
                    oldDex, newDex, oldClassData.staticFields, newClassData.staticFields
            )) {
                return true;
            }

            if (isMethodsChangeAffectedToReferrer(
                    oldDex, newDex, oldClassData.directMethods, newClassData.directMethods
            )) {
                return true;
            }

            if (isMethodsChangeAffectedToReferrer(
                    oldDex, newDex, oldClassData.virtualMethods, newClassData.virtualMethods
            )) {
                return true;
            }
        } else {
            if (!(oldClassData == null && newClassData == null)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFieldsChangeAffectedToReferrer(
            Dex oldDex,
            Dex newDex,
            Field[] oldFields,
            Field[] newFields
    ) {
        if (oldFields.length != newFields.length) {
            return true;
        }

        int fieldCount = oldFields.length;
        for (int i = 0; i < fieldCount; ++i) {
            Field oldField = oldFields[i];
            Field newField = newFields[i];

            if (oldField.accessFlags != newField.accessFlags) {
                return true;
            }

            FieldId oldFieldId = oldDex.fieldIds().get(oldField.fieldIndex);
            FieldId newFieldId = newDex.fieldIds().get(newField.fieldIndex);

            String oldFieldName = oldDex.strings().get(oldFieldId.nameIndex);
            String newFieldName = newDex.strings().get(newFieldId.nameIndex);
            if (!oldFieldName.equals(newFieldName)) {
                return true;
            }

            String oldFieldTypeName = oldDex.typeNames().get(oldFieldId.typeIndex);
            String newFieldTypeName = newDex.typeNames().get(newFieldId.typeIndex);
            if (!oldFieldTypeName.equals(newFieldTypeName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isMethodsChangeAffectedToReferrer(
            Dex oldDex,
            Dex newDex,
            Method[] oldMethods,
            Method[] newMethods
    ) {
        if (oldMethods.length != newMethods.length) {
            return true;
        }

        int methodCount = oldMethods.length;
        for (int i = 0; i < methodCount; ++i) {
            Method oldMethod = oldMethods[i];
            Method newMethod = newMethods[i];

            if (oldMethod.accessFlags != newMethod.accessFlags) {
                return true;
            }

            MethodId oldMethodId = oldDex.methodIds().get(oldMethod.methodIndex);
            MethodId newMethodId = newDex.methodIds().get(newMethod.methodIndex);

            String oldMethodName = oldDex.strings().get(oldMethodId.nameIndex);
            String newMethodName = newDex.strings().get(newMethodId.nameIndex);
            if (!oldMethodName.equals(newMethodName)) {
                return true;
            }

            ProtoId oldProtoId = oldDex.protoIds().get(oldMethodId.protoIndex);
            ProtoId newProtoId = newDex.protoIds().get(newMethodId.protoIndex);

            String oldMethodShorty = oldDex.strings().get(oldProtoId.shortyIndex);
            String newMethodShorty = newDex.strings().get(newProtoId.shortyIndex);
            if (!oldMethodShorty.equals(newMethodShorty)) {
                return true;
            }

            String oldMethodReturnTypeName = oldDex.typeNames().get(oldProtoId.returnTypeIndex);
            String newMethodReturnTypeName = newDex.typeNames().get(newProtoId.returnTypeIndex);
            if (!oldMethodReturnTypeName.equals(newMethodReturnTypeName)) {
                return true;
            }

            short[] oldParameterIds = oldDex.parameterTypeIndicesFromMethodId(oldMethodId);
            short[] newParameterIds = newDex.parameterTypeIndicesFromMethodId(newMethodId);
            if (isTypeIdsChangeAffectedToReferrer(
                    oldDex, newDex, oldParameterIds, newParameterIds, true
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean isSameClass(
            Dex oldDex,
            Dex newDex,
            ClassDef oldClassDef,
            ClassDef newClassDef
    ) {
        if (oldClassDef.accessFlags != newClassDef.accessFlags) {
            return false;
        }

        if (!isSameClassDesc(
                oldDex, newDex, oldClassDef.supertypeIndex, newClassDef.supertypeIndex
        )) {
            return false;
        }

        short[] oldInterfaceIndices = oldDex.interfaceTypeIndicesFromClassDef(oldClassDef);
        short[] newInterfaceIndices = newDex.interfaceTypeIndicesFromClassDef(newClassDef);
        if (oldInterfaceIndices.length != newInterfaceIndices.length) {
            return false;
        } else {
            for (int i = 0; i < oldInterfaceIndices.length; ++i) {
                if (!isSameClassDesc(oldDex, newDex, oldInterfaceIndices[i], newInterfaceIndices[i])) {
                    return false;
                }
            }
        }

        if (!isSameName(oldDex, newDex, oldClassDef.sourceFileIndex, newClassDef.sourceFileIndex)) {
            return false;
        }

        if (!isSameAnnotationDirectory(
                oldDex,
                newDex,
                oldClassDef.annotationsOffset,
                newClassDef.annotationsOffset
        )) {
            return false;
        }

        if (!isSameClassData(
                oldDex,
                newDex,
                oldClassDef.classDataOffset,
                newClassDef.classDataOffset
        )) {
            return false;
        }

        return isSameStaticValue(
                oldDex,
                newDex,
                oldClassDef.staticValuesOffset,
                newClassDef.staticValuesOffset
        );
    }

    private boolean isSameStaticValue(
            Dex oldDex,
            Dex newDex,
            int oldStaticValueOffset,
            int newStaticValueOffset
    ) {
        if (oldStaticValueOffset == 0 && newStaticValueOffset == 0) {
            return true;
        }

        if (oldStaticValueOffset == 0 || newStaticValueOffset == 0) {
            return false;
        }

        EncodedValue oldStaticValue =
                oldDex.openSection(oldStaticValueOffset).readEncodedArray();
        EncodedValue newStaticValue =
                newDex.openSection(newStaticValueOffset).readEncodedArray();
        EncodedValueReader oldReader =
                new EncodedValueReader(oldStaticValue, EncodedValueReader.ENCODED_ARRAY);
        EncodedValueReader newReader =
                new EncodedValueReader(newStaticValue, EncodedValueReader.ENCODED_ARRAY);

        return isSameEncodedValue(oldDex, newDex, oldReader, newReader);
    }

    private boolean isSameClassDesc(Dex oldDex, Dex newDex, int oldTypeId, int newTypeId) {
        String oldClassDesc = oldDex.typeNames().get(oldTypeId);
        String newClassDesc = newDex.typeNames().get(newTypeId);
        return oldClassDesc.equals(newClassDesc);
    }

    private boolean isSameName(Dex oldDex, Dex newDex, int oldStringId, int newStringId) {
        if (oldStringId == TableOfContents.Section.UNDEF_INDEX
                && newStringId == TableOfContents.Section.UNDEF_INDEX) {
            return true;
        }
        if (oldStringId == TableOfContents.Section.UNDEF_INDEX
                || newStringId == TableOfContents.Section.UNDEF_INDEX) {
            return false;
        }

        return oldDex.strings().get(oldStringId).equals(newDex.strings().get(newStringId));
    }

    private boolean isSameAnnotationDirectory(
            Dex oldDex,
            Dex newDex,
            int oldAnnotationDirectoryOffset,
            int newAnnotationDirectoryOffset
    ) {
        if (oldAnnotationDirectoryOffset == 0 && newAnnotationDirectoryOffset == 0) {
            return true;
        }

        if (oldAnnotationDirectoryOffset == 0 || newAnnotationDirectoryOffset == 0) {
            return false;
        }

        AnnotationsDirectory oldAnnotationsDirectory =
                oldDex.openSection(oldAnnotationDirectoryOffset).readAnnotationsDirectory();
        AnnotationsDirectory newAnnotationsDirectory =
                newDex.openSection(newAnnotationDirectoryOffset).readAnnotationsDirectory();

        if (!isSameAnnotationSet(
                oldDex,
                newDex,
                oldAnnotationsDirectory.classAnnotationsOffset,
                newAnnotationsDirectory.classAnnotationsOffset
        )) {
            return false;
        }

        int[][] oldFieldAnnotations = oldAnnotationsDirectory.fieldAnnotations;
        int[][] newFieldAnnotations = newAnnotationsDirectory.fieldAnnotations;
        if (oldFieldAnnotations.length != newFieldAnnotations.length) {
            return false;
        }
        for (int i = 0; i < oldFieldAnnotations.length; ++i) {
            if (!isSameFieldId(
                    oldDex, newDex, oldFieldAnnotations[i][0], newFieldAnnotations[i][0]
            )) {
                return false;
            }
            if (!isSameAnnotationSet(
                    oldDex, newDex, oldFieldAnnotations[i][1], newFieldAnnotations[i][1]
            )) {
                return false;
            }
        }

        int[][] oldMethodAnnotations = oldAnnotationsDirectory.methodAnnotations;
        int[][] newMethodAnnotations = newAnnotationsDirectory.methodAnnotations;
        if (oldMethodAnnotations.length != newMethodAnnotations.length) {
            return false;
        }
        for (int i = 0; i < oldMethodAnnotations.length; ++i) {
            if (!isSameMethodId(
                    oldDex, newDex, oldMethodAnnotations[i][0], newMethodAnnotations[i][0]
            )) {
                return false;
            }
            if (!isSameAnnotationSet(
                    oldDex, newDex, oldMethodAnnotations[i][1], newMethodAnnotations[i][1]
            )) {
                return false;
            }
        }

        int[][] oldParameterAnnotations = oldAnnotationsDirectory.parameterAnnotations;
        int[][] newParameterAnnotations = newAnnotationsDirectory.parameterAnnotations;
        if (oldParameterAnnotations.length != newParameterAnnotations.length) {
            return false;
        }
        for (int i = 0; i < oldParameterAnnotations.length; ++i) {
            if (!isSameMethodId(
                    oldDex, newDex, oldParameterAnnotations[i][0], newParameterAnnotations[i][0]
            )) {
                return false;
            }
            if (!isSameAnnotationSetRefList(
                    oldDex, newDex, oldParameterAnnotations[i][1], newParameterAnnotations[i][1]
            )) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameFieldId(Dex oldDex, Dex newDex, int oldFieldIdIdx, int newFieldIdIdx) {
        FieldId oldFieldId = oldDex.fieldIds().get(oldFieldIdIdx);
        FieldId newFieldId = newDex.fieldIds().get(newFieldIdIdx);

        if (!isSameClassDesc(
                oldDex, newDex, oldFieldId.declaringClassIndex, newFieldId.declaringClassIndex
        )) {
            return false;
        }

        if (!isSameClassDesc(
                oldDex, newDex, oldFieldId.typeIndex, newFieldId.typeIndex
        )) {
            return false;
        }

        String oldName = oldDex.strings().get(oldFieldId.nameIndex);
        String newName = newDex.strings().get(newFieldId.nameIndex);
        return oldName.equals(newName);
    }

    private boolean isSameMethodId(Dex oldDex, Dex newDex, int oldMethodIdIdx, int newMethodIdIdx) {
        MethodId oldMethodId = oldDex.methodIds().get(oldMethodIdIdx);
        MethodId newMethodId = newDex.methodIds().get(newMethodIdIdx);

        if (!isSameClassDesc(
                oldDex, newDex, oldMethodId.declaringClassIndex, newMethodId.declaringClassIndex
        )) {
            return false;
        }

        if (!isSameProtoId(oldDex, newDex, oldMethodId.protoIndex, newMethodId.protoIndex)) {
            return false;
        }

        String oldName = oldDex.strings().get(oldMethodId.nameIndex);
        String newName = newDex.strings().get(newMethodId.nameIndex);
        return oldName.equals(newName);
    }

    private boolean isSameProtoId(Dex oldDex, Dex newDex, int oldProtoIdIdx, int newProtoIdIdx) {
        ProtoId oldProtoId = oldDex.protoIds().get(oldProtoIdIdx);
        ProtoId newProtoId = newDex.protoIds().get(newProtoIdIdx);

        String oldShorty = oldDex.strings().get(oldProtoId.shortyIndex);
        String newShorty = newDex.strings().get(newProtoId.shortyIndex);

        if (!oldShorty.equals(newShorty)) {
            return false;
        }

        if (!isSameClassDesc(
                oldDex, newDex, oldProtoId.returnTypeIndex, newProtoId.returnTypeIndex
        )) {
            return false;
        }

        return isSameParameters(
                oldDex, newDex, oldProtoId.parametersOffset, newProtoId.parametersOffset
        );
    }

    private boolean isSameParameters(
            Dex oldDex, Dex newDex, int oldParametersOffset, int newParametersOffset
    ) {
        if (oldParametersOffset == 0 && newParametersOffset == 0) {
            return true;
        }

        if (oldParametersOffset == 0 || newParametersOffset == 0) {
            return false;
        }

        TypeList oldParameters = oldDex.openSection(oldParametersOffset).readTypeList();
        TypeList newParameters = newDex.openSection(newParametersOffset).readTypeList();

        if (oldParameters.types.length != newParameters.types.length) {
            return false;
        }

        for (int i = 0; i < oldParameters.types.length; ++i) {
            if (!isSameClassDesc(
                    oldDex, newDex, oldParameters.types[i], newParameters.types[i]
            )) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameAnnotationSetRefList(
            Dex oldDex,
            Dex newDex,
            int oldAnnotationSetRefListOffset,
            int newAnnotationSetRefListOffset
    ) {
        if (oldAnnotationSetRefListOffset == 0 && newAnnotationSetRefListOffset == 0) {
            return true;
        }

        if (oldAnnotationSetRefListOffset == 0 || newAnnotationSetRefListOffset == 0) {
            return false;
        }

        AnnotationSetRefList oldAnnotationSetRefList = oldDex.openSection(
                oldAnnotationSetRefListOffset
        ).readAnnotationSetRefList();

        AnnotationSetRefList newAnnotationSetRefList = newDex.openSection(
                newAnnotationSetRefListOffset
        ).readAnnotationSetRefList();

        int oldAnnotationSetRefListCount = oldAnnotationSetRefList.annotationSetRefItems.length;
        int newAnnotationSetRefListCount = newAnnotationSetRefList.annotationSetRefItems.length;
        if (oldAnnotationSetRefListCount != newAnnotationSetRefListCount) {
            return false;
        }

        for (int i = 0; i < oldAnnotationSetRefListCount; ++i) {
            if (!isSameAnnotationSet(
                    oldDex,
                    newDex,
                    oldAnnotationSetRefList.annotationSetRefItems[i],
                    newAnnotationSetRefList.annotationSetRefItems[i]
            )) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameAnnotationSet(
            Dex oldDex, Dex newDex, int oldAnnotationSetOffset, int newAnnotationSetOffset
    ) {
        if (oldAnnotationSetOffset == 0 && newAnnotationSetOffset == 0) {
            return true;
        }

        if (oldAnnotationSetOffset == 0 || newAnnotationSetOffset == 0) {
            return false;
        }

        AnnotationSet oldClassAnnotationSet =
                oldDex.openSection(oldAnnotationSetOffset).readAnnotationSet();
        AnnotationSet newClassAnnotationSet =
                newDex.openSection(newAnnotationSetOffset).readAnnotationSet();

        int oldAnnotationOffsetCount = oldClassAnnotationSet.annotationOffsets.length;
        int newAnnotationOffsetCount = newClassAnnotationSet.annotationOffsets.length;
        if (oldAnnotationOffsetCount != newAnnotationOffsetCount) {
            return false;
        }

        for (int i = 0; i < oldAnnotationOffsetCount; ++i) {
            if (!isSameAnnotation(
                    oldDex,
                    newDex,
                    oldClassAnnotationSet.annotationOffsets[i],
                    newClassAnnotationSet.annotationOffsets[i]
            )) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameAnnotation(
            Dex oldDex, Dex newDex, int oldAnnotationOffset, int newAnnotationOffset
    ) {
        Annotation oldAnnotation = oldDex.openSection(oldAnnotationOffset).readAnnotation();
        Annotation newAnnotation = newDex.openSection(newAnnotationOffset).readAnnotation();

        if (oldAnnotation.visibility != newAnnotation.visibility) {
            return false;
        }

        EncodedValueReader oldAnnoReader = oldAnnotation.getReader();
        EncodedValueReader newAnnoReader = newAnnotation.getReader();

        return isSameAnnotationByReader(oldDex, newDex, oldAnnoReader, newAnnoReader);
    }

    private boolean isSameAnnotationByReader(
            Dex oldDex,
            Dex newDex,
            EncodedValueReader oldAnnoReader,
            EncodedValueReader newAnnoReader
    ) {
        int oldFieldCount = oldAnnoReader.readAnnotation();
        int newFieldCount = newAnnoReader.readAnnotation();
        if (oldFieldCount != newFieldCount) {
            return false;
        }

        int oldAnnoType = oldAnnoReader.getAnnotationType();
        int newAnnoType = newAnnoReader.getAnnotationType();
        if (!isSameClassDesc(oldDex, newDex, oldAnnoType, newAnnoType)) {
            return false;
        }

        for (int i = 0; i < oldFieldCount; ++i) {
            int oldAnnoNameIdx = oldAnnoReader.readAnnotationName();
            int newAnnoNameIdx = newAnnoReader.readAnnotationName();
            if (!isSameName(oldDex, newDex, oldAnnoNameIdx, newAnnoNameIdx)) {
                return false;
            }
            if (!isSameEncodedValue(oldDex, newDex, oldAnnoReader, newAnnoReader)) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameEncodedValue(
            Dex oldDex,
            Dex newDex,
            EncodedValueReader oldAnnoReader,
            EncodedValueReader newAnnoReader
    ) {
        int oldAnnoItemType = oldAnnoReader.peek();
        int newAnnoItemType = newAnnoReader.peek();

        if (oldAnnoItemType != newAnnoItemType) {
            return false;
        }

        switch (oldAnnoItemType) {
            case EncodedValueReader.ENCODED_BYTE: {
                byte oldByte = oldAnnoReader.readByte();
                byte newByte = newAnnoReader.readByte();
                return oldByte == newByte;
            }
            case EncodedValueReader.ENCODED_SHORT: {
                short oldShort = oldAnnoReader.readShort();
                short newShort = newAnnoReader.readShort();
                return oldShort == newShort;
            }
            case EncodedValueReader.ENCODED_INT: {
                int oldInt = oldAnnoReader.readInt();
                int newInt = newAnnoReader.readInt();
                return oldInt == newInt;
            }
            case EncodedValueReader.ENCODED_LONG: {
                long oldLong = oldAnnoReader.readLong();
                long newLong = newAnnoReader.readLong();
                return oldLong == newLong;
            }
            case EncodedValueReader.ENCODED_CHAR: {
                char oldChar = oldAnnoReader.readChar();
                char newChar = newAnnoReader.readChar();
                return oldChar == newChar;
            }
            case EncodedValueReader.ENCODED_FLOAT: {
                float oldFloat = oldAnnoReader.readFloat();
                float newFloat = newAnnoReader.readFloat();
                return Float.compare(oldFloat, newFloat) == 0;
            }
            case EncodedValueReader.ENCODED_DOUBLE: {
                double oldDouble = oldAnnoReader.readDouble();
                double newDouble = newAnnoReader.readDouble();
                return Double.compare(oldDouble, newDouble) == 0;
            }
            case EncodedValueReader.ENCODED_STRING: {
                int oldStringIdx = oldAnnoReader.readString();
                int newStringIdx = newAnnoReader.readString();
                return isSameName(oldDex, newDex, oldStringIdx, newStringIdx);
            }
            case EncodedValueReader.ENCODED_TYPE: {
                int oldTypeId = oldAnnoReader.readType();
                int newTypeId = newAnnoReader.readType();
                return isSameClassDesc(oldDex, newDex, oldTypeId, newTypeId);
            }
            case EncodedValueReader.ENCODED_FIELD: {
                int oldFieldId = oldAnnoReader.readField();
                int newFieldId = newAnnoReader.readField();
                return isSameFieldId(oldDex, newDex, oldFieldId, newFieldId);
            }
            case EncodedValueReader.ENCODED_ENUM: {
                int oldFieldId = oldAnnoReader.readEnum();
                int newFieldId = newAnnoReader.readEnum();
                return isSameFieldId(oldDex, newDex, oldFieldId, newFieldId);
            }
            case EncodedValueReader.ENCODED_METHOD: {
                int oldMethodId = oldAnnoReader.readMethod();
                int newMethodId = newAnnoReader.readMethod();
                return isSameMethodId(oldDex, newDex, oldMethodId, newMethodId);
            }
            case EncodedValueReader.ENCODED_ARRAY: {
                int oldArrSize = oldAnnoReader.readArray();
                int newArrSize = newAnnoReader.readArray();
                if (oldArrSize != newArrSize) {
                    return false;
                }
                for (int i = 0; i < oldArrSize; ++i) {
                    if (!isSameEncodedValue(oldDex, newDex, oldAnnoReader, newAnnoReader)) {
                        return false;
                    }
                }
                return true;
            }
            case EncodedValueReader.ENCODED_ANNOTATION: {
                return isSameAnnotationByReader(oldDex, newDex, oldAnnoReader, newAnnoReader);
            }
            case EncodedValueReader.ENCODED_NULL: {
                oldAnnoReader.readNull();
                newAnnoReader.readNull();
                return true;
            }
            case EncodedValueReader.ENCODED_BOOLEAN: {
                boolean oldBool = oldAnnoReader.readBoolean();
                boolean newBool = newAnnoReader.readBoolean();
                return oldBool == newBool;
            }
            default: {
                throw new IllegalStateException(
                        "Unexpected annotation value type: " + Integer.toHexString(oldAnnoItemType)
                );
            }
        }
    }

    private boolean isSameClassData(
            Dex oldDex, Dex newDex, int oldClassDataOffset, int newClassDataOffset
    ) {
        if (oldClassDataOffset == 0 && newClassDataOffset == 0) {
            return true;
        }

        if (oldClassDataOffset == 0 || newClassDataOffset == 0) {
            return false;
        }

        ClassData oldClassData = oldDex.openSection(oldClassDataOffset).readClassData();
        ClassData newClassData = newDex.openSection(newClassDataOffset).readClassData();

        ClassData.Field[] oldInstanceFields = oldClassData.instanceFields;
        ClassData.Field[] newInstanceFields = newClassData.instanceFields;
        if (oldInstanceFields.length != newInstanceFields.length) {
            return false;
        }
        for (int i = 0; i < oldInstanceFields.length; ++i) {
            if (!isSameField(oldDex, newDex, oldInstanceFields[i], newInstanceFields[i])) {
                return false;
            }
        }

        ClassData.Field[] oldStaticFields = oldClassData.staticFields;
        ClassData.Field[] newStaticFields = newClassData.staticFields;
        if (oldStaticFields.length != newStaticFields.length) {
            return false;
        }
        for (int i = 0; i < oldStaticFields.length; ++i) {
            if (!isSameField(oldDex, newDex, oldStaticFields[i], newStaticFields[i])) {
                return false;
            }
        }

        ClassData.Method[] oldDirectMethods = oldClassData.directMethods;
        ClassData.Method[] newDirectMethods = newClassData.directMethods;
        if (oldDirectMethods.length != newDirectMethods.length) {
            return false;
        }
        for (int i = 0; i < oldDirectMethods.length; ++i) {
            if (!isSameMethod(oldDex, newDex, oldDirectMethods[i], newDirectMethods[i])) {
                return false;
            }
        }

        ClassData.Method[] oldVirtualMethods = oldClassData.virtualMethods;
        ClassData.Method[] newVirtualMethods = newClassData.virtualMethods;
        if (oldVirtualMethods.length != newVirtualMethods.length) {
            return false;
        }
        for (int i = 0; i < oldVirtualMethods.length; ++i) {
            if (!isSameMethod(oldDex, newDex, oldVirtualMethods[i], newVirtualMethods[i])) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameField(
            Dex oldDex, Dex newDex, ClassData.Field oldField, ClassData.Field newField
    ) {
        if (oldField.accessFlags != newField.accessFlags) {
            return false;
        }
        return isSameFieldId(oldDex, newDex, oldField.fieldIndex, newField.fieldIndex);
    }

    private boolean isSameMethod(
            Dex oldDex, Dex newDex, ClassData.Method oldMethod, ClassData.Method newMethod
    ) {
        if (oldMethod.accessFlags != newMethod.accessFlags) {
            return false;
        }

        if (!isSameMethodId(oldDex, newDex, oldMethod.methodIndex, newMethod.methodIndex)) {
            return false;
        }

        return isSameCode(oldDex, newDex, oldMethod.codeOffset, newMethod.codeOffset);
    }

    private boolean isSameCode(
            final Dex oldDex, final Dex newDex, int oldCodeOffset, int newCodeOffset
    ) {
        if (oldCodeOffset == 0 && newCodeOffset == 0) {
            return true;
        }

        if (oldCodeOffset == 0 || newCodeOffset == 0) {
            return false;
        }

        Code oldCode = oldDex.openSection(oldCodeOffset).readCode();
        Code newCode = newDex.openSection(newCodeOffset).readCode();

        if (oldCode.registersSize != newCode.registersSize) {
            return false;
        }

        if (oldCode.insSize != newCode.insSize) {
            return false;
        }

        final InstructionComparator insnComparator = new InstructionComparator(
                oldCode.instructions,
                newCode.instructions
        ) {
            @Override
            protected boolean compareString(int stringIndex1, int stringIndex2) {
                return isSameName(oldDex, newDex, stringIndex1, stringIndex2);
            }

            @Override
            protected boolean compareType(int typeIndex1, int typeIndex2) {
                return isSameClassDesc(oldDex, newDex, typeIndex1, typeIndex2);
            }

            @Override
            protected boolean compareField(int fieldIndex1, int fieldIndex2) {
                return isSameFieldId(oldDex, newDex, fieldIndex1, fieldIndex2);
            }

            @Override
            protected boolean compareMethod(int methodIndex1, int methodIndex2) {
                return isSameMethodId(oldDex, newDex, methodIndex1, methodIndex2);
            }
        };

        if (!insnComparator.compare()) {
            return false;
        }

        if (!isSameDebugInfo(
                oldDex, newDex, oldCode.debugInfoOffset, newCode.debugInfoOffset, insnComparator
        )) {
            return false;
        }

        if (!isSameTries(oldDex, newDex, oldCode.tries, newCode.tries, insnComparator)) {
            return false;
        }

        return isSameCatchHandlers(
                oldDex, newDex, oldCode.catchHandlers, newCode.catchHandlers, insnComparator
        );
    }

    private boolean isSameDebugInfo(
            Dex oldDex,
            Dex newDex,
            int oldDebugInfoOffset,
            int newDebugInfoOffset,
            InstructionComparator insnComparator
    ) {
        if (oldDebugInfoOffset == 0 && newDebugInfoOffset == 0) {
            return true;
        }

        if (oldDebugInfoOffset == 0 || newDebugInfoOffset == 0) {
            return false;
        }

        DebugInfoItem oldDebugInfoItem =
                oldDex.openSection(oldDebugInfoOffset).readDebugInfoItem();
        DebugInfoItem newDebugInfoItem =
                newDex.openSection(newDebugInfoOffset).readDebugInfoItem();

        if (oldDebugInfoItem.lineStart != newDebugInfoItem.lineStart) {
            return false;
        }

        if (oldDebugInfoItem.parameterNames.length != newDebugInfoItem.parameterNames.length) {
            return false;
        }

        for (int i = 0; i < oldDebugInfoItem.parameterNames.length; ++i) {
            int oldNameIdx = oldDebugInfoItem.parameterNames[i];
            int newNameIdx = newDebugInfoItem.parameterNames[i];
            if (!isSameName(oldDex, newDex, oldNameIdx, newNameIdx)) {
                return false;
            }
        }

        DexDataBuffer oldDbgInfoBuffer =
                new DexDataBuffer(ByteBuffer.wrap(oldDebugInfoItem.infoSTM));
        DexDataBuffer newDbgInfoBuffer =
                new DexDataBuffer(ByteBuffer.wrap(newDebugInfoItem.infoSTM));

        int oldLine = oldDebugInfoItem.lineStart;
        int oldAddress = 0;

        int newLine = newDebugInfoItem.lineStart;
        int newAddress = 0;

        while (oldDbgInfoBuffer.available() > 0 && newDbgInfoBuffer.available() > 0) {
            int oldOpCode = oldDbgInfoBuffer.readUnsignedByte();
            int newOpCode = newDbgInfoBuffer.readUnsignedByte();

            if (oldOpCode != newOpCode) {
                if (oldOpCode < DBG_FIRST_SPECIAL || newOpCode < DBG_FIRST_SPECIAL) {
                    return false;
                }
            }

            int currOpCode = oldOpCode;

            switch (currOpCode) {
                case DebugInfoItem.DBG_END_SEQUENCE: {
                    break;
                }
                case DebugInfoItem.DBG_ADVANCE_PC: {
                    int oldAddrDiff = oldDbgInfoBuffer.readUleb128();
                    int newAddrDiff = newDbgInfoBuffer.readUleb128();
                    oldAddress += oldAddrDiff;
                    newAddress += newAddrDiff;
                    if (!insnComparator.isSameInstruction(oldAddress, newAddress)) {
                        return false;
                    }
                    break;
                }
                case DebugInfoItem.DBG_ADVANCE_LINE: {
                    int oldLineDiff = oldDbgInfoBuffer.readSleb128();
                    int newLineDiff = newDbgInfoBuffer.readSleb128();
                    oldLine += oldLineDiff;
                    newLine += newLineDiff;
                    if (oldLine != newLine) {
                        return false;
                    }
                    break;
                }
                case DebugInfoItem.DBG_START_LOCAL:
                case DebugInfoItem.DBG_START_LOCAL_EXTENDED: {
                    int oldRegisterNum = oldDbgInfoBuffer.readUleb128();
                    int newRegisterNum = newDbgInfoBuffer.readUleb128();
                    if (oldRegisterNum != newRegisterNum) {
                        return false;
                    }

                    int oldNameIndex = oldDbgInfoBuffer.readUleb128p1();
                    int newNameIndex = newDbgInfoBuffer.readUleb128p1();
                    if (!isSameName(oldDex, newDex, oldNameIndex, newNameIndex)) {
                        return false;
                    }

                    int oldTypeIndex = oldDbgInfoBuffer.readUleb128p1();
                    int newTypeIndex = newDbgInfoBuffer.readUleb128p1();
                    if (!isSameClassDesc(oldDex, newDex, oldTypeIndex, newTypeIndex)) {
                        return false;
                    }

                    if (currOpCode == DebugInfoItem.DBG_START_LOCAL_EXTENDED) {
                        int oldSigIndex = oldDbgInfoBuffer.readUleb128p1();
                        int newSigIndex = newDbgInfoBuffer.readUleb128p1();
                        if (!isSameName(oldDex, newDex, oldSigIndex, newSigIndex)) {
                            return false;
                        }
                    }
                    break;
                }
                case DebugInfoItem.DBG_END_LOCAL:
                case DebugInfoItem.DBG_RESTART_LOCAL: {
                    int oldRegisterNum = oldDbgInfoBuffer.readUleb128();
                    int newRegisterNum = newDbgInfoBuffer.readUleb128();
                    if (oldRegisterNum != newRegisterNum) {
                        return false;
                    }

                    break;
                }
                case DebugInfoItem.DBG_SET_FILE: {
                    int oldNameIndex = oldDbgInfoBuffer.readUleb128p1();
                    int newNameIndex = newDbgInfoBuffer.readUleb128p1();
                    if (!isSameName(oldDex, newDex, oldNameIndex, newNameIndex)) {
                        return false;
                    }

                    break;
                }
                case DebugInfoItem.DBG_SET_PROLOGUE_END:
                case DebugInfoItem.DBG_SET_EPILOGUE_BEGIN: {
                    break;
                }
                default: {
                    int oldAdjustedOpcode = oldOpCode - DBG_FIRST_SPECIAL;
                    oldLine += DBG_LINE_BASE + (oldAdjustedOpcode % DBG_LINE_RANGE);
                    oldAddress += (oldAdjustedOpcode / DBG_LINE_RANGE);

                    int newAdjustedOpcode = newOpCode - DBG_FIRST_SPECIAL;
                    newLine += DBG_LINE_BASE + (newAdjustedOpcode % DBG_LINE_RANGE);
                    newAddress += (newAdjustedOpcode / DBG_LINE_RANGE);

                    if (oldLine != newLine) {
                        return false;
                    }
                    if (!insnComparator.isSameInstruction(oldAddress, newAddress)) {
                        return false;
                    }
                    break;
                }
            }
        }

        if (oldDbgInfoBuffer.available() > 0 || newDbgInfoBuffer.available() > 0) {
            return false;
        }

        return true;
    }

    private boolean isSameTries(
            Dex oldDex,
            Dex newDex,
            Code.Try[] oldTries,
            Code.Try[] newTries,
            InstructionComparator insnComparator
    ) {
        if (oldTries.length != newTries.length) {
            return false;
        }

        for (int i = 0; i < oldTries.length; ++i) {
            Code.Try oldTry = oldTries[i];
            Code.Try newTry = newTries[i];
            if (oldTry.instructionCount != newTry.instructionCount) {
                return false;
            }
            if (oldTry.catchHandlerIndex != newTry.catchHandlerIndex) {
                return false;
            }
            if (!insnComparator.isSameInstruction(oldTry.startAddress, newTry.startAddress)) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameCatchHandlers(
            Dex oldDex,
            Dex newDex,
            Code.CatchHandler[] oldCatchHandlers,
            Code.CatchHandler[] newCatchHandlers,
            InstructionComparator insnComparator
    ) {
        if (oldCatchHandlers.length != newCatchHandlers.length) {
            return false;
        }

        for (int i = 0; i < oldCatchHandlers.length; ++i) {
            Code.CatchHandler oldCatchHandler = oldCatchHandlers[i];
            Code.CatchHandler newCatchHandler = newCatchHandlers[i];

            int oldTypeAddrPairCount = oldCatchHandler.typeIndexes.length;
            int newTypeAddrPairCount = newCatchHandler.typeIndexes.length;
            if (oldTypeAddrPairCount != newTypeAddrPairCount) {
                return false;
            }

            if (oldCatchHandler.catchAllAddress != -1 && newCatchHandler.catchAllAddress != -1) {
                return insnComparator.isSameInstruction(
                        oldCatchHandler.catchAllAddress, newCatchHandler.catchAllAddress
                );
            } else {
                if (!(oldCatchHandler.catchAllAddress == -1 && newCatchHandler.catchAllAddress == -1)) {
                    return false;
                }
            }

            for (int j = 0; j < oldTypeAddrPairCount; ++j) {
                if (!isSameClassDesc(
                        oldDex,
                        newDex,
                        oldCatchHandler.typeIndexes[j],
                        newCatchHandler.typeIndexes[j]
                )) {
                    return false;
                }

                if (!insnComparator.isSameInstruction(
                        oldCatchHandler.addresses[j], newCatchHandler.addresses[j]
                )) {
                    return false;
                }
            }
        }

        return true;
    }

    public static final class DexClassInfo {
        public String classDesc = null;
        public int classDefIndex = ClassDef.NO_INDEX;
        public ClassDef classDef = null;
        public Dex owner = null;

        private DexClassInfo(String classDesc, int classDefIndex, ClassDef classDef, Dex owner) {
            this.classDesc = classDesc;
            this.classDef = classDef;
            this.classDefIndex = classDefIndex;
            this.owner = owner;
        }

        private DexClassInfo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return classDesc;
        }

        @Override
        public boolean equals(Object obj) {
            DexClassInfo other = (DexClassInfo) obj;
            if (!classDesc.equals(other.classDesc)) {
                return false;
            }
            return owner.computeSignature(false).equals(other.owner.computeSignature(false));
        }

        @Override
        public int hashCode() {
            return owner.computeSignature(false).hashCode();
        }
    }

    public static final class DexGroup {
        public final Dex[] dexes;

        private DexGroup(Dex... dexes) {
            if (dexes == null || dexes.length == 0) {
                throw new IllegalArgumentException("dexes is null or empty.");
            }
            this.dexes = new Dex[dexes.length];
            System.arraycopy(dexes, 0, this.dexes, 0, dexes.length);
        }

        private DexGroup(File... dexFiles) throws IOException {
            if (dexFiles == null || dexFiles.length == 0) {
                throw new IllegalArgumentException("dexFiles is null or empty.");
            }
            this.dexes = new Dex[dexFiles.length];
            for (int i = 0; i < dexFiles.length; ++i) {
                this.dexes[i] = new Dex(dexFiles[i]);
            }
        }

        private DexGroup(List<File> dexFileList) throws IOException {
            if (dexFileList == null || dexFileList.isEmpty()) {
                throw new IllegalArgumentException("dexFileList is null or empty.");
            }
            this.dexes = new Dex[dexFileList.size()];
            for (int i = 0; i < this.dexes.length; ++i) {
                this.dexes[i] = new Dex(dexFileList.get(i));
            }
        }

        private DexGroup() {
            throw new UnsupportedOperationException();
        }

        public static DexGroup wrap(Dex... dexes) {
            return new DexGroup(dexes);
        }

        public static DexGroup wrap(File... dexFiles) throws IOException {
            return new DexGroup(dexFiles);
        }

        public static DexGroup wrap(List<File> dexFileList) throws IOException {
            return new DexGroup(dexFileList);
        }

        public Set<DexClassInfo> getClassInfosInDexesWithDuplicateCheck() {
            Map<String, DexClassInfo> classDescToInfoMap = new HashMap<>();
            for (Dex dex : dexes) {
                int classDefIndex = 0;
                for (ClassDef classDef : dex.classDefs()) {
                    String classDesc = dex.typeNames().get(classDef.typeIndex);
                    if (!classDescToInfoMap.containsKey(classDesc)) {
                        classDescToInfoMap.put(classDesc, new DexClassInfo(classDesc, classDefIndex, classDef, dex));
                        ++classDefIndex;
                    } else {
                        throw new IllegalStateException(
                                String.format(
                                        "duplicate class descriptor [%s] in different dexes.", classDesc
                                )
                        );
                    }
                }
            }
            return new HashSet<>(classDescToInfoMap.values());
        }
    }
}

