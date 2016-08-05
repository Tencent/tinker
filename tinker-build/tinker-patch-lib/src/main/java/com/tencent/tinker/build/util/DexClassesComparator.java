/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
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

package com.tencent.tinker.build.util;

import com.tencent.tinker.android.dex.Annotation;
import com.tencent.tinker.android.dex.AnnotationSet;
import com.tencent.tinker.android.dex.AnnotationSetRefList;
import com.tencent.tinker.android.dex.AnnotationsDirectory;
import com.tencent.tinker.android.dex.ClassData;
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
import com.tencent.tinker.android.dx.instruction.ShortArrayCodeInput;
import com.tencent.tinker.build.dexpatcher.util.PatternUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
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
    private final List<String> addedClassDescList   = new ArrayList<>();
    private final List<String> deletedClassDescList = new ArrayList<>();
    private final List<String> changedClassDescList = new ArrayList<>();

    private final Set<Pattern> patternsOfClassDescToCheck = new HashSet<>();
    private final Set<Pattern> patternsOfLoaderClassDesc  = new HashSet<>();

    private final Set<String> oldDescriptorOfClassesToCheck = new HashSet<>();
    private final Set<String> newDescriptorOfClassesToCheck = new HashSet<>();

    private Dex oldDex = null;
    private Dex newDex = null;

    private Map<String, ClassDef>  oldClassDescriptorToClassDefMap = new HashMap<>();
    private Map<Integer, ClassDef> oldTypeIdToClassDefMap          = new HashMap<>();
    private Map<String, ClassDef>  newClassDescriptorToClassDefMap = new HashMap<>();
    private Map<Integer, ClassDef> newTypeIdToClassDefMap          = new HashMap<>();

    public DexClassesComparator(String patternStringOfClassDescToCheck) {
        patternsOfClassDescToCheck.add(
            Pattern.compile(
                PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStringOfClassDescToCheck)
            )
        );
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

    public void setLoaderClassDescPattern(Collection<String> patternStringsOfLoaderClassDesc) {
        for (String patternStr : patternStringsOfLoaderClassDesc) {
            patternsOfLoaderClassDesc.add(
                Pattern.compile(
                    PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStr)
                )
            );
        }
    }

    public void startCheck(File oldDexFile, File newDexFile) throws IOException {
        startCheck(new Dex(oldDexFile), new Dex(newDexFile));
    }

    public void startCheck(Dex oldDex, Dex newDex) {
        this.oldDex = oldDex;
        this.newDex = newDex;

        addedClassDescList.clear();
        deletedClassDescList.clear();
        changedClassDescList.clear();
        oldDescriptorOfClassesToCheck.clear();
        newDescriptorOfClassesToCheck.clear();
        oldClassDescriptorToClassDefMap.clear();
        oldTypeIdToClassDefMap.clear();
        newClassDescriptorToClassDefMap.clear();
        newTypeIdToClassDefMap.clear();

        // Map typeIndex to classDef and collect typeIndex of classes to check in oldDex.
        for (ClassDef oldClassDef : oldDex.classDefs()) {
            String desc = oldDex.typeNames().get(oldClassDef.typeIndex);
            if (Utils.isStringMatchesPatterns(desc, patternsOfClassDescToCheck)) {
                oldDescriptorOfClassesToCheck.add(desc);
            }
            oldClassDescriptorToClassDefMap.put(desc, oldClassDef);
            oldTypeIdToClassDefMap.put(oldClassDef.typeIndex, oldClassDef);
        }

        // Map typeIndex to classDef and collect typeIndex of classes to check in newDex.
        for (ClassDef newClassDef : newDex.classDefs()) {
            String desc = newDex.typeNames().get(newClassDef.typeIndex);
            if (Utils.isStringMatchesPatterns(desc, patternsOfClassDescToCheck)) {
                newDescriptorOfClassesToCheck.add(desc);
            }
            newClassDescriptorToClassDefMap.put(desc, newClassDef);
            newTypeIdToClassDefMap.put(newClassDef.typeIndex, newClassDef);
        }

        Set<String> deletedClassDescs = new HashSet<>(oldDescriptorOfClassesToCheck);
        deletedClassDescs.removeAll(newDescriptorOfClassesToCheck);

        for (String desc : deletedClassDescs) {
            deletedClassDescList.add(desc);
        }

        Set<String> addedClassDescs = new HashSet<>(newDescriptorOfClassesToCheck);
        addedClassDescs.removeAll(oldDescriptorOfClassesToCheck);

        for (String desc : addedClassDescs) {
            addedClassDescList.add(desc);
        }

        Set<String> mayBeChangedClassDescs = new HashSet<>(oldDescriptorOfClassesToCheck);
        mayBeChangedClassDescs.retainAll(newDescriptorOfClassesToCheck);

        for (String desc : mayBeChangedClassDescs) {
            if (!isSameClass(oldClassDescriptorToClassDefMap.get(desc), newClassDescriptorToClassDefMap.get(desc))) {
                changedClassDescList.add(desc);
            }
        }
    }

    public Collection<String> getAddedClassDescriptors() {
        return new ArrayList<>(addedClassDescList);
    }

    public Collection<String> getDeletedClassDescriptors() {
        return new ArrayList<>(deletedClassDescList);
    }

    public Collection<String> getChangedClassDescriptors() {
        return new ArrayList<>(changedClassDescList);
    }

    private boolean isSameClass(ClassDef oldClassDef, ClassDef newClassDef) {
        if (oldClassDef.accessFlags != newClassDef.accessFlags) {
            return false;
        }

        short[] oldInterfaceIndices = oldDex.interfaceTypeIndicesFromClassDef(oldClassDef);
        short[] newInterfaceIndices = newDex.interfaceTypeIndicesFromClassDef(newClassDef);
        if (oldInterfaceIndices.length != newInterfaceIndices.length) {
            return false;
        } else {
            for (int i = 0; i < oldInterfaceIndices.length; ++i) {
                if (!isSameClass(oldInterfaceIndices[i], newInterfaceIndices[i])) {
                    return false;
                }
            }
        }

        if (!isSameName(oldClassDef.sourceFileIndex, newClassDef.sourceFileIndex)) {
            return false;
        }

        if (!isSameAnnotationDirectory(oldClassDef.annotationsOffset, newClassDef.annotationsOffset)) {
            return false;
        }

        if (!isSameClassData(oldClassDef.classDataOffset, newClassDef.classDataOffset)) {
            return false;
        }

        return isSameStaticValue(oldClassDef.staticValuesOffset, newClassDef.staticValuesOffset);
    }

    private boolean isSameStaticValue(int oldStaticValueOffset, int newStaticValueOffset) {
        if (oldStaticValueOffset == 0 && newStaticValueOffset == 0) {
            return true;
        }

        if (oldStaticValueOffset == 0 || newStaticValueOffset == 0) {
            return false;
        }

        EncodedValue oldStaticValue = oldDex.openSection(oldStaticValueOffset).readEncodedArray();
        EncodedValue newStaticValue = newDex.openSection(newStaticValueOffset).readEncodedArray();
        EncodedValueReader oldReader = new EncodedValueReader(oldStaticValue, EncodedValueReader.ENCODED_ARRAY);
        EncodedValueReader newReader = new EncodedValueReader(newStaticValue, EncodedValueReader.ENCODED_ARRAY);

        return isSameEncodedValue(oldReader, newReader);
    }

    private boolean isSameClassDesc(int oldTypeId, int newTypeId) {
        String oldClassDesc = oldDex.typeNames().get(oldTypeId);
        String newClassDesc = newDex.typeNames().get(newTypeId);
        return oldClassDesc.equals(newClassDesc);
    }

    private boolean isSameName(int oldStringId, int newStringId) {
        if (oldStringId == TableOfContents.Section.UNDEF_INDEX && newStringId == TableOfContents.Section.UNDEF_INDEX) {
            return true;
        }
        if (oldStringId == TableOfContents.Section.UNDEF_INDEX || newStringId == TableOfContents.Section.UNDEF_INDEX) {
            return false;
        }

        return oldDex.strings().get(oldStringId).equals(newDex.strings().get(newStringId));
    }

    private boolean isSameClass(int oldTypeId, int newTypeId) {
        ClassDef oldClassDef = oldTypeIdToClassDefMap.get(oldTypeId);
        ClassDef newClassDef = null;
        String tmpNewClassDesc = newDex.typeNames().get(newTypeId);
        if (Utils.isStringMatchesPatterns(tmpNewClassDesc, patternsOfLoaderClassDesc)) {
            // newClass is loader class, whose classdef structure is removed by dexdiff.
            // So we use classdef in oldDex to match the behavior in runtime.
            // Therefore, here we can just return true.
            return true;
        } else {
            newClassDef = newTypeIdToClassDefMap.get(newTypeId);
        }

        if (oldClassDef == null) {
            if (newClassDef == null) {
                String oldClassDesc = oldDex.typeNames().get(oldTypeId);
                String newClassDesc = newDex.typeNames().get(newTypeId);
                return oldClassDesc.equals(newClassDesc);
            } else {
                return false;
            }
        } else {
            if (newClassDef == null) {
                return false;
            } else {
                return isSameClass(oldClassDef, newClassDef);
            }
        }
    }

    private boolean isSameAnnotationDirectory(int oldAnnotationDirectoryOffset, int newAnnotationDirectoryOffset) {
        if (oldAnnotationDirectoryOffset == 0 && newAnnotationDirectoryOffset == 0) {
            return true;
        }

        if (oldAnnotationDirectoryOffset == 0 || newAnnotationDirectoryOffset == 0) {
            return false;
        }

        AnnotationsDirectory oldAnnotationsDirectory = oldDex.openSection(oldAnnotationDirectoryOffset).readAnnotationsDirectory();
        AnnotationsDirectory newAnnotationsDirectory = newDex.openSection(newAnnotationDirectoryOffset).readAnnotationsDirectory();

        if (!isSameAnnotationSet(
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
            if (!isSameFieldId(oldFieldAnnotations[i][0], newFieldAnnotations[i][0])) {
                return false;
            }
            if (!isSameAnnotationSet(oldFieldAnnotations[i][1], newFieldAnnotations[i][1])) {
                return false;
            }
        }

        int[][] oldMethodAnnotations = oldAnnotationsDirectory.methodAnnotations;
        int[][] newMethodAnnotations = newAnnotationsDirectory.methodAnnotations;
        if (oldMethodAnnotations.length != newMethodAnnotations.length) {
            return false;
        }
        for (int i = 0; i < oldMethodAnnotations.length; ++i) {
            if (!isSameMethodId(oldMethodAnnotations[i][0], newMethodAnnotations[i][0])) {
                return false;
            }
            if (!isSameAnnotationSet(oldMethodAnnotations[i][1], newMethodAnnotations[i][1])) {
                return false;
            }
        }

        int[][] oldParameterAnnotations = oldAnnotationsDirectory.parameterAnnotations;
        int[][] newParameterAnnotations = newAnnotationsDirectory.parameterAnnotations;
        if (oldParameterAnnotations.length != newParameterAnnotations.length) {
            return false;
        }
        for (int i = 0; i < oldParameterAnnotations.length; ++i) {
            if (!isSameMethodId(oldParameterAnnotations[i][0], newParameterAnnotations[i][0])) {
                return false;
            }
            if (!isSameAnnotationSetRefList(oldParameterAnnotations[i][1], newParameterAnnotations[i][1])) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameFieldId(int oldFieldIdIdx, int newFieldIdIdx) {
        FieldId oldFieldId = oldDex.fieldIds().get(oldFieldIdIdx);
        FieldId newFieldId = newDex.fieldIds().get(newFieldIdIdx);

        if (!isSameClassDesc(oldFieldId.declaringClassIndex, newFieldId.declaringClassIndex)) {
            return false;
        }

        if (!isSameClassDesc(oldFieldId.typeIndex, newFieldId.typeIndex)) {
            return false;
        }

        String oldName = oldDex.strings().get(oldFieldId.nameIndex);
        String newName = newDex.strings().get(newFieldId.nameIndex);
        return oldName.equals(newName);
    }

    private boolean isSameMethodId(int oldMethodIdIdx, int newMethodIdIdx) {
        MethodId oldMethodId = oldDex.methodIds().get(oldMethodIdIdx);
        MethodId newMethodId = newDex.methodIds().get(newMethodIdIdx);

        if (!isSameClassDesc(oldMethodId.declaringClassIndex, newMethodId.declaringClassIndex)) {
            return false;
        }

        if (!isSameProtoId(oldMethodId.protoIndex, newMethodId.protoIndex)) {
            return false;
        }

        String oldName = oldDex.strings().get(oldMethodId.nameIndex);
        String newName = newDex.strings().get(newMethodId.nameIndex);
        return oldName.equals(newName);
    }

    private boolean isSameProtoId(int oldProtoIdIdx, int newProtoIdIdx) {
        ProtoId oldProtoId = oldDex.protoIds().get(oldProtoIdIdx);
        ProtoId newProtoId = newDex.protoIds().get(newProtoIdIdx);

        String oldShorty = oldDex.strings().get(oldProtoId.shortyIndex);
        String newShorty = newDex.strings().get(newProtoId.shortyIndex);

        if (!oldShorty.equals(newShorty)) {
            return false;
        }

        if (!isSameClassDesc(oldProtoId.returnTypeIndex, newProtoId.returnTypeIndex)) {
            return false;
        }

        return isSameParameters(oldProtoId.parametersOffset, newProtoId.parametersOffset);
    }

    private boolean isSameParameters(int oldParametersOffset, int newParametersOffset) {
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
            if (!isSameClassDesc(oldParameters.types[i], newParameters.types[i])) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameAnnotationSetRefList(int oldAnnotationSetRefListOffset, int newAnnotationSetRefListOffset) {
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
                oldAnnotationSetRefList.annotationSetRefItems[i],
                newAnnotationSetRefList.annotationSetRefItems[i]
            )) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameAnnotationSet(int oldAnnotationSetOffset, int newAnnotationSetOffset) {
        if (oldAnnotationSetOffset == 0 && newAnnotationSetOffset == 0) {
            return true;
        }

        if (oldAnnotationSetOffset == 0 || newAnnotationSetOffset == 0) {
            return false;
        }

        AnnotationSet oldClassAnnotationSet = oldDex.openSection(oldAnnotationSetOffset).readAnnotationSet();
        AnnotationSet newClassAnnotationSet = newDex.openSection(newAnnotationSetOffset).readAnnotationSet();

        int oldAnnotationOffsetCount = oldClassAnnotationSet.annotationOffsets.length;
        int newAnnotationOffsetCount = newClassAnnotationSet.annotationOffsets.length;
        if (oldAnnotationOffsetCount != newAnnotationOffsetCount) {
            return false;
        }

        for (int i = 0; i < oldAnnotationOffsetCount; ++i) {
            if (!isSameAnnotation(oldClassAnnotationSet.annotationOffsets[i], newClassAnnotationSet.annotationOffsets[i])) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameAnnotation(int oldAnnotationOffset, int newAnnotationOffset) {
        Annotation oldAnnotation = oldDex.openSection(oldAnnotationOffset).readAnnotation();
        Annotation newAnnotation = newDex.openSection(newAnnotationOffset).readAnnotation();

        if (oldAnnotation.visibility != newAnnotation.visibility) {
            return false;
        }

        EncodedValueReader oldAnnoReader = oldAnnotation.getReader();
        EncodedValueReader newAnnoReader = newAnnotation.getReader();

        return isSameAnnotationByReader(oldAnnoReader, newAnnoReader);
    }

    private boolean isSameAnnotationByReader(EncodedValueReader oldAnnoReader, EncodedValueReader newAnnoReader) {
        int oldFieldCount = oldAnnoReader.readAnnotation();
        int newFieldCount = newAnnoReader.readAnnotation();
        if (oldFieldCount != newFieldCount) {
            return false;
        }

        int oldAnnoType = oldAnnoReader.getAnnotationType();
        int newAnnoType = newAnnoReader.getAnnotationType();
        if (!isSameClassDesc(oldAnnoType, newAnnoType)) {
            return false;
        }

        for (int i = 0; i < oldFieldCount; ++i) {
            int oldAnnoNameIdx = oldAnnoReader.readAnnotationName();
            int newAnnoNameIdx = newAnnoReader.readAnnotationName();
            if (!isSameName(oldAnnoNameIdx, newAnnoNameIdx)) {
                return false;
            }
            if (!isSameEncodedValue(oldAnnoReader, newAnnoReader)) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameEncodedValue(EncodedValueReader oldAnnoReader, EncodedValueReader newAnnoReader) {
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
                return oldFloat == newFloat;
            }
            case EncodedValueReader.ENCODED_DOUBLE: {
                double oldDouble = oldAnnoReader.readDouble();
                double newDouble = newAnnoReader.readDouble();
                return oldDouble == newDouble;
            }
            case EncodedValueReader.ENCODED_STRING: {
                int oldStringIdx = oldAnnoReader.readString();
                int newStringIdx = newAnnoReader.readString();
                return isSameName(oldStringIdx, newStringIdx);
            }
            case EncodedValueReader.ENCODED_TYPE: {
                int oldTypeId = oldAnnoReader.readType();
                int newTypeId = newAnnoReader.readType();
                return isSameClassDesc(oldTypeId, newTypeId);
            }
            case EncodedValueReader.ENCODED_FIELD: {
                int oldFieldId = oldAnnoReader.readField();
                int newFieldId = newAnnoReader.readField();
                return isSameFieldId(oldFieldId, newFieldId);
            }
            case EncodedValueReader.ENCODED_ENUM: {
                int oldFieldId = oldAnnoReader.readEnum();
                int newFieldId = newAnnoReader.readEnum();
                return isSameFieldId(oldFieldId, newFieldId);
            }
            case EncodedValueReader.ENCODED_METHOD: {
                int oldMethodId = oldAnnoReader.readMethod();
                int newMethodId = newAnnoReader.readMethod();
                return isSameMethodId(oldMethodId, newMethodId);
            }
            case EncodedValueReader.ENCODED_ARRAY: {
                int oldArrSize = oldAnnoReader.readArray();
                int newArrSize = newAnnoReader.readArray();
                if (oldArrSize != newArrSize) {
                    return false;
                }
                for (int i = 0; i < oldArrSize; ++i) {
                    if (!isSameEncodedValue(oldAnnoReader, newAnnoReader)) {
                        return false;
                    }
                }
                return true;
            }
            case EncodedValueReader.ENCODED_ANNOTATION: {
                return isSameAnnotationByReader(oldAnnoReader, newAnnoReader);
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
                throw new IllegalStateException("Unexpected annotation value type: " + Integer.toHexString(oldAnnoItemType));
            }
        }
    }

    private boolean isSameClassData(int oldClassDataOffset, int newClassDataOffset) {
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
            if (!isSameField(oldInstanceFields[i], newInstanceFields[i])) {
                return false;
            }
        }

        ClassData.Field[] oldStaticFields = oldClassData.staticFields;
        ClassData.Field[] newStaticFields = newClassData.staticFields;
        if (oldStaticFields.length != newStaticFields.length) {
            return false;
        }
        for (int i = 0; i < oldStaticFields.length; ++i) {
            if (!isSameField(oldStaticFields[i], newStaticFields[i])) {
                return false;
            }
        }

        ClassData.Method[] oldDirectMethods = oldClassData.directMethods;
        ClassData.Method[] newDirectMethods = newClassData.directMethods;
        if (oldDirectMethods.length != newDirectMethods.length) {
            return false;
        }
        for (int i = 0; i < oldDirectMethods.length; ++i) {
            if (!isSameMethod(oldDirectMethods[i], newDirectMethods[i])) {
                return false;
            }
        }

        ClassData.Method[] oldVirtualMethods = oldClassData.virtualMethods;
        ClassData.Method[] newVirtualMethods = newClassData.virtualMethods;
        if (oldVirtualMethods.length != newVirtualMethods.length) {
            return false;
        }
        for (int i = 0; i < oldVirtualMethods.length; ++i) {
            if (!isSameMethod(oldVirtualMethods[i], newVirtualMethods[i])) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameField(ClassData.Field oldField, ClassData.Field newField) {
        if (oldField.accessFlags != newField.accessFlags) {
            return false;
        }
        return isSameFieldId(oldField.fieldIndex, newField.fieldIndex);
    }

    private boolean isSameMethod(ClassData.Method oldMethod, ClassData.Method newMethod) {
        if (oldMethod.accessFlags != newMethod.accessFlags) {
            return false;
        }

        if (!isSameMethodId(oldMethod.methodIndex, newMethod.methodIndex)) {
            return false;
        }

        return isSameCode(oldMethod.codeOffset, newMethod.codeOffset);
    }

    private boolean isSameCode(int oldCodeOffset, int newCodeOffset) {
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

        if (!isSameDebugInfo(oldCode.debugInfoOffset, newCode.debugInfoOffset)) {
            return false;
        }

        if (!isSameInstruction(oldCode.instructions, newCode.instructions)) {
            return false;
        }

        if (!isSameTries(oldCode.tries, newCode.tries)) {
            return false;
        }

        return isSameCatchHandlers(oldCode.catchHandlers, newCode.catchHandlers);
    }

    private boolean isSameInstruction(short[] oldInstructions, short[] newInstructions) {
        if (oldInstructions == null && newInstructions == null) {
            return true;
        }
        if (oldInstructions == null || newInstructions == null) {
            return false;
        }

        return new InstructionComparator(
                new ShortArrayCodeInput(oldInstructions),
                new ShortArrayCodeInput(newInstructions)
        ) {

            @Override
            protected boolean compareString(int stringIndex1, int stringIndex2) {
                return isSameName(stringIndex1, stringIndex2);
            }

            @Override
            protected boolean compareType(int typeIndex1, int typeIndex2) {
                return isSameClassDesc(typeIndex1, typeIndex2);
            }

            @Override
            protected boolean compareField(int fieldIndex1, int fieldIndex2) {
                return isSameFieldId(fieldIndex1, fieldIndex2);
            }

            @Override
            protected boolean compareMethod(int methodIndex1, int methodIndex2) {
                return isSameMethodId(methodIndex1, methodIndex2);
            }

        }.compare();
    }

    private boolean isSameDebugInfo(int oldDebugInfoOffset, int newDebugInfoOffset) {
        if (oldDebugInfoOffset == 0 && newDebugInfoOffset == 0) {
            return true;
        }

        if (oldDebugInfoOffset == 0 || newDebugInfoOffset == 0) {
            return false;
        }

        DebugInfoItem oldDebugInfoItem = oldDex.openSection(oldDebugInfoOffset).readDebugInfoItem();
        DebugInfoItem newDebugInfoItem = newDex.openSection(newDebugInfoOffset).readDebugInfoItem();

        if (oldDebugInfoItem.lineStart != newDebugInfoItem.lineStart) {
            return false;
        }

        if (oldDebugInfoItem.parameterNames.length != newDebugInfoItem.parameterNames.length) {
            return false;
        }
        for (int i = 0; i < oldDebugInfoItem.parameterNames.length; ++i) {
            int oldNameIdx = oldDebugInfoItem.parameterNames[i];
            int newNameIdx = newDebugInfoItem.parameterNames[i];
            if (!isSameName(oldNameIdx, newNameIdx)) {
                return false;
            }
        }

        DexDataBuffer oldDbgInfoBuffer = new DexDataBuffer(ByteBuffer.wrap(oldDebugInfoItem.infoSTM));
        DexDataBuffer newDbgInfoBuffer = new DexDataBuffer(ByteBuffer.wrap(newDebugInfoItem.infoSTM));

        while (oldDbgInfoBuffer.available() > 0 && newDbgInfoBuffer.available() > 0) {
            int oldOpCode = oldDbgInfoBuffer.readByte();
            int newOpCode = newDbgInfoBuffer.readByte();
            if (oldOpCode != newOpCode) {
                return false;
            }

            int currOpCode = oldOpCode;

            switch (currOpCode) {
                case DebugInfoItem.DBG_END_SEQUENCE: {
                    break;
                }
                case DebugInfoItem.DBG_ADVANCE_PC: {
                    int oldAddrDiff = oldDbgInfoBuffer.readUleb128();
                    int newAddrDiff = newDbgInfoBuffer.readUleb128();
                    if (oldAddrDiff != newAddrDiff) {
                        return false;
                    }
                    break;
                }
                case DebugInfoItem.DBG_ADVANCE_LINE: {
                    int oldLineDiff = oldDbgInfoBuffer.readSleb128();
                    int newLineDiff = newDbgInfoBuffer.readSleb128();
                    if (oldLineDiff != newLineDiff) {
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
                    if (!isSameName(oldNameIndex, newNameIndex)) {
                        return false;
                    }

                    int oldTypeIndex = oldDbgInfoBuffer.readUleb128p1();
                    int newTypeIndex = newDbgInfoBuffer.readUleb128p1();
                    if (!isSameClassDesc(oldTypeIndex, newTypeIndex)) {
                        return false;
                    }

                    if (currOpCode == DebugInfoItem.DBG_START_LOCAL_EXTENDED) {
                        int oldSigIndex = oldDbgInfoBuffer.readUleb128p1();
                        int newSigIndex = newDbgInfoBuffer.readUleb128p1();
                        if (!isSameName(oldSigIndex, newSigIndex)) {
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
                    if (!isSameName(oldNameIndex, newNameIndex)) {
                        return false;
                    }

                    break;
                }
                case DebugInfoItem.DBG_SET_PROLOGUE_END:
                case DebugInfoItem.DBG_SET_EPILOGUE_BEGIN:
                default: {
                    break;
                }
            }
        }

        if (oldDbgInfoBuffer.available() > 0 || newDbgInfoBuffer.available() > 0) {
            return false;
        }

        return true;
    }

    private boolean isSameTries(Code.Try[] oldTries, Code.Try[] newTries) {
        if (oldTries.length != newTries.length) {
            return false;
        }

        for (int i = 0; i < oldTries.length; ++i) {
            if (oldTries[i].compareTo(newTries[i]) != 0) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameCatchHandlers(Code.CatchHandler[] oldCatchHandlers, Code.CatchHandler[] newCatchHandlers) {
        if (oldCatchHandlers.length != newCatchHandlers.length) {
            return false;
        }

        for (int i = 0; i < oldCatchHandlers.length; ++i) {
            Code.CatchHandler oldCatchHandler = oldCatchHandlers[i];
            Code.CatchHandler newCatchHandler = newCatchHandlers[i];

            if (oldCatchHandler.catchAllAddress != newCatchHandler.catchAllAddress) {
                return false;
            }

            int oldTypeAddrPairCount = oldCatchHandler.typeIndexes.length;
            int newTypeAddrPairCount = newCatchHandler.typeIndexes.length;
            if (oldTypeAddrPairCount != newTypeAddrPairCount) {
                return false;
            }

            for (int j = 0; j < oldTypeAddrPairCount; ++j) {
                if (oldCatchHandler.addresses[j] != newCatchHandler.addresses[j]) {
                    return false;
                }

                if (!isSameClassDesc(oldCatchHandler.typeIndexes[j], newCatchHandler.typeIndexes[j])) {
                    return false;
                }
            }
        }

        return true;
    }
}

