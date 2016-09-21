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

import com.tencent.tinker.android.dex.Dex;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by tangyinsheng on 2016/9/11.
 */
public final class OffsetToIndexConverter {
    private final Map<Integer, Integer> typeListOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> classDataOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> encodedArrayOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> annotationOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> annotationSetOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> annotationSetRefListOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> annotationsDirectoryOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> codeOffsetToIndexMap = new HashMap<>();
    private final Map<Integer, Integer> debugInfoItemOffsetToIndexMap = new HashMap<>();

    public OffsetToIndexConverter(Dex dex) {
        if (dex == null) {
            throw new IllegalArgumentException("dex is null.");
        }

        if (dex.getTableOfContents().typeLists.exists()) {
            Dex.Section typeListSec = dex.openSection(dex.getTableOfContents().typeLists);
            int typeListCount = dex.getTableOfContents().typeLists.size;
            for (int i = 0; i < typeListCount; ++i) {
                typeListOffsetToIndexMap.put(typeListSec.readTypeList().off, i);
            }
        }

        if (dex.getTableOfContents().classDatas.exists()) {
            Dex.Section classDataSec = dex.openSection(dex.getTableOfContents().classDatas);
            int classDataCount = dex.getTableOfContents().classDatas.size;
            for (int i = 0; i < classDataCount; ++i) {
                classDataOffsetToIndexMap.put(classDataSec.readClassData().off, i);
            }
        }

        if (dex.getTableOfContents().encodedArrays.exists()) {
            Dex.Section encodedArraySec = dex.openSection(dex.getTableOfContents().encodedArrays);
            int encodedArrayCount = dex.getTableOfContents().encodedArrays.size;
            for (int i = 0; i < encodedArrayCount; ++i) {
                encodedArrayOffsetToIndexMap.put(encodedArraySec.readEncodedArray().off, i);
            }
        }

        if (dex.getTableOfContents().annotations.exists()) {
            Dex.Section annotationSec = dex.openSection(dex.getTableOfContents().annotations);
            int annotationCount = dex.getTableOfContents().annotations.size;
            for (int i = 0; i < annotationCount; ++i) {
                annotationOffsetToIndexMap.put(annotationSec.readAnnotation().off, i);
            }
        }

        if (dex.getTableOfContents().annotationSets.exists()) {
            Dex.Section annotationSetSec = dex.openSection(dex.getTableOfContents().annotationSets);
            int annotationSetCount = dex.getTableOfContents().annotationSets.size;
            for (int i = 0; i < annotationSetCount; ++i) {
                annotationSetOffsetToIndexMap.put(annotationSetSec.readAnnotationSet().off, i);
            }
        }

        if (dex.getTableOfContents().annotationSetRefLists.exists()) {
            Dex.Section annotationSetRefListSec = dex.openSection(dex.getTableOfContents().annotationSetRefLists);
            int annotationSetRefListCount = dex.getTableOfContents().annotationSetRefLists.size;
            for (int i = 0; i < annotationSetRefListCount; ++i) {
                annotationSetRefListOffsetToIndexMap.put(annotationSetRefListSec.readAnnotationSetRefList().off, i);
            }
        }

        if (dex.getTableOfContents().annotationsDirectories.exists()) {
            Dex.Section annotationsDirectorySec = dex.openSection(dex.getTableOfContents().annotationsDirectories);
            int annotationsDirectoryCount = dex.getTableOfContents().annotationsDirectories.size;
            for (int i = 0; i < annotationsDirectoryCount; ++i) {
                annotationsDirectoryOffsetToIndexMap.put(annotationsDirectorySec.readAnnotationsDirectory().off, i);
            }
        }

        if (dex.getTableOfContents().codes.exists()) {
            Dex.Section codeSec = dex.openSection(dex.getTableOfContents().codes);
            int codeCount = dex.getTableOfContents().codes.size;
            for (int i = 0; i < codeCount; ++i) {
                codeOffsetToIndexMap.put(codeSec.readCode().off, i);
            }
        }

        if (dex.getTableOfContents().debugInfos.exists()) {
            Dex.Section debugInfoItemSec = dex.openSection(dex.getTableOfContents().debugInfos);
            int debugInfoItemCount = dex.getTableOfContents().debugInfos.size;
            for (int i = 0; i < debugInfoItemCount; ++i) {
                debugInfoItemOffsetToIndexMap.put(debugInfoItemSec.readDebugInfoItem().off, i);
            }
        }
    }

    public int getTypeListIndexByOffset(int offset) {
        if (typeListOffsetToIndexMap.containsKey(offset)) {
            return typeListOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getClassDataIndexByOffset(int offset) {
        if (classDataOffsetToIndexMap.containsKey(offset)) {
            return classDataOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getEncodedArrayIndexByOffset(int offset) {
        if (encodedArrayOffsetToIndexMap.containsKey(offset)) {
            return encodedArrayOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getAnnotationIndexByOffset(int offset) {
        if (annotationOffsetToIndexMap.containsKey(offset)) {
            return annotationOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getAnnotationSetIndexByOffset(int offset) {
        if (annotationSetOffsetToIndexMap.containsKey(offset)) {
            return annotationSetOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getAnnotationSetRefListIndexByOffset(int offset) {
        if (annotationSetRefListOffsetToIndexMap.containsKey(offset)) {
            return annotationSetRefListOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getAnnotationsDirectoryIndexByOffset(int offset) {
        if (annotationsDirectoryOffsetToIndexMap.containsKey(offset)) {
            return annotationsDirectoryOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getCodeIndexByOffset(int offset) {
        if (codeOffsetToIndexMap.containsKey(offset)) {
            return codeOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }

    public int getDebugInfoItemIndexByOffset(int offset) {
        if (debugInfoItemOffsetToIndexMap.containsKey(offset)) {
            return debugInfoItemOffsetToIndexMap.get(offset);
        } else {
            throw new IllegalArgumentException("cannot find corresponding index of offset: " + offset);
        }
    }
}
