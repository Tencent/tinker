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

package com.tencent.tinker.android.dex;

import com.tencent.tinker.android.dex.TableOfContents.Section;
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dex.util.HashCodeHelper;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Structure of AnnotationsDirectory element in Dex file.
 */
public class AnnotationsDirectory extends Section.Item<AnnotationsDirectory> {
    public int classAnnotationsOffset;

    /**
     * fieldAnnotations[][2];
     * fieldAnnotations[i][0]: fieldIndex, fieldAnnotations[i][1]: annotation set Offset
     */
    public int[][] fieldAnnotations;

    /**
     * methodAnnotations[][2];
     * methodAnnotations[i][0]: methodIndex, methodAnnotations[i][1]: annotation set Offset
     */
    public int[][] methodAnnotations;

    /**
     * parameterAnnotations[][2];
     * parameterAnnotations[i][0]: methodIndex, parameterAnnotations[i][1]: annotation set reflist Offset
     */
    public int[][] parameterAnnotations;

    public AnnotationsDirectory(
            int off,
            int classAnnotationsOffset,
            int[][] fieldAnnotations, int[][] methodAnnotations, int[][] parameterAnnotations
    ) {
        super(off);
        this.classAnnotationsOffset = classAnnotationsOffset;
        this.fieldAnnotations = fieldAnnotations;
        this.methodAnnotations = methodAnnotations;
        this.parameterAnnotations = parameterAnnotations;
    }

    @Override
    public int compareTo(AnnotationsDirectory other) {
        if (classAnnotationsOffset != other.classAnnotationsOffset) {
            return CompareUtils.uCompare(classAnnotationsOffset, other.classAnnotationsOffset);
        }

        int fieldsSize = fieldAnnotations.length;
        int methodsSize = methodAnnotations.length;
        int parameterListSize = parameterAnnotations.length;
        int oFieldsSize = other.fieldAnnotations.length;
        int oMethodsSize = other.methodAnnotations.length;
        int oParameterListSize = other.parameterAnnotations.length;

        if (fieldsSize != oFieldsSize) {
            return CompareUtils.sCompare(fieldsSize, oFieldsSize);
        }

        if (methodsSize != oMethodsSize) {
            return CompareUtils.sCompare(methodsSize, oMethodsSize);
        }

        if (parameterListSize != oParameterListSize) {
            return CompareUtils.sCompare(parameterListSize, oParameterListSize);
        }

        for (int i = 0; i < fieldsSize; ++i) {
            int fieldIdx = fieldAnnotations[i][0];
            int annotationOffset = fieldAnnotations[i][1];
            int othFieldIdx = other.fieldAnnotations[i][0];
            int othAnnotationOffset = other.fieldAnnotations[i][1];

            if (fieldIdx != othFieldIdx) {
                return CompareUtils.uCompare(fieldIdx, othFieldIdx);
            }

            if (annotationOffset != othAnnotationOffset) {
                return CompareUtils.sCompare(annotationOffset, othAnnotationOffset);
            }
        }

        for (int i = 0; i < methodsSize; ++i) {
            int methodIdx = methodAnnotations[i][0];
            int annotationOffset = methodAnnotations[i][1];
            int othMethodIdx = other.methodAnnotations[i][0];
            int othAnnotationOffset = other.methodAnnotations[i][1];

            if (methodIdx != othMethodIdx) {
                return CompareUtils.uCompare(methodIdx, othMethodIdx);
            }

            if (annotationOffset != othAnnotationOffset) {
                return CompareUtils.sCompare(annotationOffset, othAnnotationOffset);
            }
        }

        for (int i = 0; i < parameterListSize; ++i) {
            int methodIdx = parameterAnnotations[i][0];
            int annotationOffset = parameterAnnotations[i][1];
            int othMethodIdx = other.parameterAnnotations[i][0];
            int othAnnotationOffset = other.parameterAnnotations[i][1];

            if (methodIdx != othMethodIdx) {
                return CompareUtils.uCompare(methodIdx, othMethodIdx);
            }

            if (annotationOffset != othAnnotationOffset) {
                return CompareUtils.sCompare(annotationOffset, othAnnotationOffset);
            }
        }

        return 0;
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(classAnnotationsOffset, fieldAnnotations, methodAnnotations,
                parameterAnnotations);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AnnotationsDirectory)) {
            return false;
        }
        return this.compareTo((AnnotationsDirectory) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UINT * (4 + 2 * (fieldAnnotations.length + methodAnnotations.length + parameterAnnotations.length));
    }
}
