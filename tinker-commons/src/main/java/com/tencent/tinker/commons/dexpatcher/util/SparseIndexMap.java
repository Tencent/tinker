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

package com.tencent.tinker.commons.dexpatcher.util;

import com.tencent.tinker.android.utils.SparseBoolArray;
import com.tencent.tinker.android.utils.SparseIntArray;

/**
 * Created by tangyinsheng on 2016/6/29.
 *
 * *** This file is renamed from IndexMap in dx project. ***
 */

public class SparseIndexMap extends AbstractIndexMap {
    private final SparseIntArray stringIdsMap = new SparseIntArray();
    private final SparseIntArray typeIdsMap = new SparseIntArray();
    private final SparseIntArray protoIdsMap = new SparseIntArray();
    private final SparseIntArray fieldIdsMap = new SparseIntArray();
    private final SparseIntArray methodIdsMap = new SparseIntArray();
    private final SparseIntArray typeListOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationSetOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationSetRefListOffsetsMap = new SparseIntArray();
    private final SparseIntArray annotationsDirectoryOffsetsMap = new SparseIntArray();
    private final SparseIntArray staticValuesOffsetsMap = new SparseIntArray();
    private final SparseIntArray classDataOffsetsMap = new SparseIntArray();
    private final SparseIntArray debugInfoItemOffsetsMap = new SparseIntArray();
    private final SparseIntArray codeOffsetsMap = new SparseIntArray();

    private final SparseBoolArray deletedStringIds = new SparseBoolArray();
    private final SparseBoolArray deletedTypeIds = new SparseBoolArray();
    private final SparseBoolArray deletedProtoIds = new SparseBoolArray();
    private final SparseBoolArray deletedFieldIds = new SparseBoolArray();
    private final SparseBoolArray deletedMethodIds = new SparseBoolArray();
    private final SparseBoolArray deletedTypeListOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedAnnotationOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedAnnotationSetOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedAnnotationSetRefListOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedAnnotationsDirectoryOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedStaticValuesOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedClassDataOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedDebugInfoItemOffsets = new SparseBoolArray();
    private final SparseBoolArray deletedCodeOffsets = new SparseBoolArray();

    public void mapStringIds(int oldIndex, int newIndex) {
        stringIdsMap.put(oldIndex, newIndex);
    }

    public void markStringIdDeleted(int index) {
        if (index < 0) return;
        deletedStringIds.put(index, true);
    }

    public void mapTypeIds(int oldIndex, int newIndex) {
        typeIdsMap.put(oldIndex, newIndex);
    }

    public void markTypeIdDeleted(int index) {
        if (index < 0) return;
        deletedTypeIds.put(index, true);
    }

    public void mapProtoIds(int oldIndex, int newIndex) {
        protoIdsMap.put(oldIndex, newIndex);
    }

    public void markProtoIdDeleted(int index) {
        if (index < 0) return;
        deletedProtoIds.put(index, true);
    }

    public void mapFieldIds(int oldIndex, int newIndex) {
        fieldIdsMap.put(oldIndex, newIndex);
    }

    public void markFieldIdDeleted(int index) {
        if (index < 0) return;
        deletedFieldIds.put(index, true);
    }

    public void mapMethodIds(int oldIndex, int newIndex) {
        methodIdsMap.put(oldIndex, newIndex);
    }

    public void markMethodIdDeleted(int index) {
        if (index < 0) return;
        deletedMethodIds.put(index, true);
    }

    public void mapTypeListOffset(int oldOffset, int newOffset) {
        typeListOffsetsMap.put(oldOffset, newOffset);
    }

    public void markTypeListDeleted(int offset) {
        if (offset < 0) return;
        deletedTypeListOffsets.put(offset, true);
    }

    public void mapAnnotationOffset(int oldOffset, int newOffset) {
        annotationOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationOffsets.put(offset, true);
    }

    public void mapAnnotationSetOffset(int oldOffset, int newOffset) {
        annotationSetOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationSetDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationSetOffsets.put(offset, true);
    }

    public void mapAnnotationSetRefListOffset(int oldOffset, int newOffset) {
        annotationSetRefListOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationSetRefListDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationSetRefListOffsets.put(offset, true);
    }

    public void mapAnnotationsDirectoryOffset(int oldOffset, int newOffset) {
        annotationsDirectoryOffsetsMap.put(oldOffset, newOffset);
    }

    public void markAnnotationsDirectoryDeleted(int offset) {
        if (offset < 0) return;
        deletedAnnotationsDirectoryOffsets.put(offset, true);
    }

    public void mapStaticValuesOffset(int oldOffset, int newOffset) {
        staticValuesOffsetsMap.put(oldOffset, newOffset);
    }

    public void markStaticValuesDeleted(int offset) {
        if (offset < 0) return;
        deletedStaticValuesOffsets.put(offset, true);
    }

    public void mapClassDataOffset(int oldOffset, int newOffset) {
        classDataOffsetsMap.put(oldOffset, newOffset);
    }

    public void markClassDataDeleted(int offset) {
        if (offset < 0) return;
        deletedClassDataOffsets.put(offset, true);
    }

    public void mapDebugInfoItemOffset(int oldOffset, int newOffset) {
        debugInfoItemOffsetsMap.put(oldOffset, newOffset);
    }

    public void markDebugInfoItemDeleted(int offset) {
        if (offset < 0) return;
        deletedDebugInfoItemOffsets.put(offset, true);
    }

    public void mapCodeOffset(int oldOffset, int newOffset) {
        codeOffsetsMap.put(oldOffset, newOffset);
    }

    public void markCodeDeleted(int offset) {
        if (offset < 0) return;
        deletedCodeOffsets.put(offset, true);
    }

    @Override
    public int adjustStringIndex(int stringIndex) {
        int index = stringIdsMap.indexOfKey(stringIndex);
        if (index < 0) {
            return (stringIndex >= 0 && deletedStringIds.containsKey(stringIndex) ? -1 : stringIndex);
        } else {
            return stringIdsMap.valueAt(index);
        }
    }

    @Override
    public int adjustTypeIdIndex(int typeIdIndex) {
        int index = typeIdsMap.indexOfKey(typeIdIndex);
        if (index < 0) {
            return (typeIdIndex >= 0 && deletedTypeIds.containsKey(typeIdIndex) ? -1 : typeIdIndex);
        } else {
            return typeIdsMap.valueAt(index);
        }
    }

    @Override
    public int adjustProtoIdIndex(int protoIndex) {
        int index = protoIdsMap.indexOfKey(protoIndex);
        if (index < 0) {
            return (protoIndex >= 0 && deletedProtoIds.containsKey(protoIndex) ? -1 : protoIndex);
        } else {
            return protoIdsMap.valueAt(index);
        }
    }

    @Override
    public int adjustFieldIdIndex(int fieldIndex) {
        int index = fieldIdsMap.indexOfKey(fieldIndex);
        if (index < 0) {
            return (fieldIndex >= 0 && deletedFieldIds.containsKey(fieldIndex) ? -1 : fieldIndex);
        } else {
            return fieldIdsMap.valueAt(index);
        }
    }

    @Override
    public int adjustMethodIdIndex(int methodIndex) {
        int index = methodIdsMap.indexOfKey(methodIndex);
        if (index < 0) {
            return (methodIndex >= 0 && deletedMethodIds.containsKey(methodIndex) ? -1 : methodIndex);
        } else {
            return methodIdsMap.valueAt(index);
        }
    }

    @Override
    public int adjustTypeListOffset(int typeListOffset) {
        int index = typeListOffsetsMap.indexOfKey(typeListOffset);
        if (index < 0) {
            return (typeListOffset >= 0 && deletedTypeListOffsets.containsKey(typeListOffset) ? -1 : typeListOffset);
        } else {
            return typeListOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustAnnotationOffset(int annotationOffset) {
        int index = annotationOffsetsMap.indexOfKey(annotationOffset);
        if (index < 0) {
            return (annotationOffset >= 0 && deletedAnnotationOffsets.containsKey(annotationOffset) ? -1 : annotationOffset);
        } else {
            return annotationOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustAnnotationSetOffset(int annotationSetOffset) {
        int index = annotationSetOffsetsMap.indexOfKey(annotationSetOffset);
        if (index < 0) {
            return (annotationSetOffset >= 0 && deletedAnnotationSetOffsets.containsKey(annotationSetOffset) ? -1 : annotationSetOffset);
        } else {
            return annotationSetOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustAnnotationSetRefListOffset(int annotationSetRefListOffset) {
        int index = annotationSetRefListOffsetsMap.indexOfKey(annotationSetRefListOffset);
        if (index < 0) {
            return (annotationSetRefListOffset >= 0 && deletedAnnotationSetRefListOffsets.containsKey(annotationSetRefListOffset) ? -1 : annotationSetRefListOffset);
        } else {
            return annotationSetRefListOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustAnnotationsDirectoryOffset(int annotationsDirectoryOffset) {
        int index = annotationsDirectoryOffsetsMap.indexOfKey(annotationsDirectoryOffset);
        if (index < 0) {
            return (annotationsDirectoryOffset >= 0 && deletedAnnotationsDirectoryOffsets.containsKey(annotationsDirectoryOffset) ? -1 : annotationsDirectoryOffset);
        } else {
            return annotationsDirectoryOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustStaticValuesOffset(int staticValuesOffset) {
        int index = staticValuesOffsetsMap.indexOfKey(staticValuesOffset);
        if (index < 0) {
            return (staticValuesOffset >= 0 && deletedStaticValuesOffsets.containsKey(staticValuesOffset) ? -1 : staticValuesOffset);
        } else {
            return staticValuesOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustClassDataOffset(int classDataOffset) {
        int index = classDataOffsetsMap.indexOfKey(classDataOffset);
        if (index < 0) {
            return (classDataOffset >= 0 && deletedClassDataOffsets.containsKey(classDataOffset) ? -1 : classDataOffset);
        } else {
            return classDataOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustDebugInfoItemOffset(int debugInfoItemOffset) {
        int index = debugInfoItemOffsetsMap.indexOfKey(debugInfoItemOffset);
        if (index < 0) {
            return (debugInfoItemOffset >= 0 && deletedDebugInfoItemOffsets.containsKey(debugInfoItemOffset) ? -1 : debugInfoItemOffset);
        } else {
            return debugInfoItemOffsetsMap.valueAt(index);
        }
    }

    @Override
    public int adjustCodeOffset(int codeOffset) {
        int index = codeOffsetsMap.indexOfKey(codeOffset);
        if (index < 0) {
            return (codeOffset >= 0 && deletedCodeOffsets.containsKey(codeOffset) ? -1 : codeOffset);
        } else {
            return codeOffsetsMap.valueAt(index);
        }
    }
}
