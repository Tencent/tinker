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

package com.tencent.tinker.android.dex;

import com.tencent.tinker.android.dex.TableOfContents.Section;
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;
import com.tencent.tinker.android.dex.util.Unsigned;

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Structure of AnnotationDirectory element in Dex file.
 */
public class AnnotationDirectory extends SectionItem<AnnotationDirectory> {
    @SuppressWarnings("unused")
    private final Dex dex;
    public        int classAnnotationsOffset;

    /**
     * fieldAnnotations[][2];
     * fieldAnnotations[i][0]: fieldIndex, fieldAnnotations[i][1]: annotationsOffset
     */
    public int[][] fieldAnnotations;

    /**
     * methodAnnotations[][2];
     * methodAnnotations[i][0]: methodIndex, methodAnnotations[i][1]: annotation set Offset
     */
    public int[][] methodAnnotations;

    /**
     * parameterAnnotations[][2];
     * parameterAnnotations[i][0]: methodIndex, parameterAnnotations[i][1]: annotationsOffset
     */
    public int[][] parameterAnnotations;

    public AnnotationDirectory(
        Section owner,
        int offset,
        int classAnnotationsOffset,
        int[][] fieldAnnotations, int[][] methodAnnotations, int[][] parameterAnnotations
    ) {
        super(owner, offset);
        this.dex = (owner != null ? owner.owner : null);
        this.classAnnotationsOffset = classAnnotationsOffset;
        this.fieldAnnotations = fieldAnnotations;
        this.methodAnnotations = methodAnnotations;
        this.parameterAnnotations = parameterAnnotations;
    }

    @Override
    public AnnotationDirectory clone(Section newOwner, int newOffset) {
        return new AnnotationDirectory(newOwner, newOffset, classAnnotationsOffset, fieldAnnotations, methodAnnotations, parameterAnnotations);
    }

    @Override
    public int compareTo(AnnotationDirectory o) {
        if (classAnnotationsOffset != o.classAnnotationsOffset) {
            return Unsigned.compare(classAnnotationsOffset, o.classAnnotationsOffset);
        }

        int fieldsSize = fieldAnnotations.length;
        int methodsSize = methodAnnotations.length;
        int parameterListSize = parameterAnnotations.length;
        int oFieldsSize = o.fieldAnnotations.length;
        int oMethodsSize = o.methodAnnotations.length;
        int oParameterListSize = o.parameterAnnotations.length;

        if (fieldsSize != oFieldsSize) {
            return Unsigned.compare(fieldsSize, oFieldsSize);
        }

        if (methodsSize != oMethodsSize) {
            return Unsigned.compare(methodsSize, oMethodsSize);
        }

        if (parameterListSize != oParameterListSize) {
            return Unsigned.compare(parameterListSize, oParameterListSize);
        }

        for (int i = 0; i < fieldsSize; ++i) {
            int fieldIdx = fieldAnnotations[i][0];
            int annotationOffset = fieldAnnotations[i][1];
            int othFieldIdx = o.fieldAnnotations[i][0];
            int othAnnotationOffset = o.fieldAnnotations[i][1];

            if (fieldIdx != othFieldIdx) {
                return Unsigned.compare(fieldIdx, othFieldIdx);
            }

            if (annotationOffset != othAnnotationOffset) {
                return Unsigned.compare(annotationOffset, othAnnotationOffset);
            }
        }

        for (int i = 0; i < methodsSize; ++i) {
            int methodIdx = methodAnnotations[i][0];
            int annotationOffset = methodAnnotations[i][1];
            int othMethodIdx = o.methodAnnotations[i][0];
            int othAnnotationOffset = o.methodAnnotations[i][1];

            if (methodIdx != othMethodIdx) {
                return Unsigned.compare(methodIdx, othMethodIdx);
            }

            if (annotationOffset != othAnnotationOffset) {
                return Unsigned.compare(annotationOffset, othAnnotationOffset);
            }
        }

        for (int i = 0; i < parameterListSize; ++i) {
            int methodIdx = parameterAnnotations[i][0];
            int annotationOffset = parameterAnnotations[i][1];
            int othMethodIdx = o.parameterAnnotations[i][0];
            int othAnnotationOffset = o.parameterAnnotations[i][1];

            if (methodIdx != othMethodIdx) {
                return Unsigned.compare(methodIdx, othMethodIdx);
            }

            if (annotationOffset != othAnnotationOffset) {
                return Unsigned.compare(annotationOffset, othAnnotationOffset);
            }
        }

        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((AnnotationDirectory) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        return SizeOf.UINT * (4 + 2 * (fieldAnnotations.length + methodAnnotations.length + parameterAnnotations.length));
    }
}
