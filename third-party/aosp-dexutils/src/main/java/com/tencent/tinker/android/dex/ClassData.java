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
import com.tencent.tinker.android.dex.TableOfContents.Section.SectionItem;

/**
 * Modifications by tomystang:
 * Make this class derived from {@code SectionItem} so that
 * we can trace dex section this element belongs to easily.
 */
public final class ClassData extends SectionItem<ClassData> {
    public Field[]  staticFields;
    public Field[]  instanceFields;
    public Method[] directMethods;
    public Method[] virtualMethods;

    public ClassData(Section owner, int offset, Field[] staticFields, Field[] instanceFields,
                     Method[] directMethods, Method[] virtualMethods) {
        super(owner, offset);
        this.staticFields = staticFields;
        this.instanceFields = instanceFields;
        this.directMethods = directMethods;
        this.virtualMethods = virtualMethods;
    }

    public Field[] allFields() {
        Field[] result = new Field[staticFields.length + instanceFields.length];
        System.arraycopy(staticFields, 0, result, 0, staticFields.length);
        System.arraycopy(instanceFields, 0, result, staticFields.length, instanceFields.length);
        return result;
    }

    public Method[] allMethods() {
        Method[] result = new Method[directMethods.length + virtualMethods.length];
        System.arraycopy(directMethods, 0, result, 0, directMethods.length);
        System.arraycopy(virtualMethods, 0, result, directMethods.length, virtualMethods.length);
        return result;
    }

    @Override
    public ClassData clone(Section newOwner, int newOffset) {
        return new ClassData(newOwner, newOffset, staticFields, instanceFields, directMethods, virtualMethods);
    }

    @Override
    public int compareTo(ClassData o) {
        int staticFieldCount = staticFields.length;
        int othStaticFieldCount = o.staticFields.length;
        if (staticFieldCount != othStaticFieldCount) return staticFieldCount - othStaticFieldCount;
        for (int i = 0; i < staticFieldCount; ++i) {
            int cmpRes = staticFields[i].compareTo(o.staticFields[i]);
            if (cmpRes != 0) return cmpRes;
        }

        int instanceFieldCount = instanceFields.length;
        int othInstanceFieldCount = o.instanceFields.length;
        if (instanceFieldCount != othInstanceFieldCount) return instanceFieldCount - othInstanceFieldCount;
        for (int i = 0; i < instanceFieldCount; ++i) {
            int cmpRes = instanceFields[i].compareTo(o.instanceFields[i]);
            if (cmpRes != 0) return cmpRes;
        }

        int directMethodCount = directMethods.length;
        int othDirectMethodCount = o.directMethods.length;
        if (directMethodCount != othDirectMethodCount) return directMethodCount - othDirectMethodCount;
        for (int i = 0; i < directMethodCount; ++i) {
            int cmpRes = directMethods[i].compareTo(o.directMethods[i]);
            if (cmpRes != 0) return cmpRes;
        }


        int virtualMethodCount = virtualMethods.length;
        int othVirtualMethodCount = o.virtualMethods.length;
        if (virtualMethodCount != othVirtualMethodCount) return virtualMethodCount - othVirtualMethodCount;
        for (int i = 0; i < virtualMethodCount; ++i) {
            int cmpRes = virtualMethods[i].compareTo(o.virtualMethods[i]);
            if (cmpRes != 0) return cmpRes;
        }

        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((ClassData) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        int staticFieldsSize = staticFields.length;
        int instanceFieldsSize = instanceFields.length;
        int directMethodsSize = directMethods.length;
        int virtualMethodsSize = virtualMethods.length;
        int byteCount = Leb128.unsignedLeb128Size(staticFieldsSize)
            + Leb128.unsignedLeb128Size(instanceFieldsSize)
            + Leb128.unsignedLeb128Size(directMethodsSize)
            + Leb128.unsignedLeb128Size(virtualMethodsSize);

        byteCount += calculateFieldsSize(staticFields);
        byteCount += calculateFieldsSize(instanceFields);
        byteCount += calculateMethodsSize(directMethods);
        byteCount += calculateMethodsSize(virtualMethods);

        return byteCount;
    }

    private int calculateFieldsSize(Field[] fields) {
        int result = 0;
        int lastFieldIndex = 0;
        for (Field field : fields) {
            int deltaFieldIndex = field.fieldIndex - lastFieldIndex;
            lastFieldIndex = field.fieldIndex;
            result += Leb128.unsignedLeb128Size(deltaFieldIndex);
            result += Leb128.unsignedLeb128Size(field.accessFlags);
        }
        return result;
    }

    private int calculateMethodsSize(Method[] methods) {
        int result = 0;
        int lastMethodIndex = 0;
        for (Method method : methods) {
            int deltaMethodIndex = method.methodIndex - lastMethodIndex;
            lastMethodIndex = method.methodIndex;
            result += Leb128.unsignedLeb128Size(deltaMethodIndex);
            result += Leb128.unsignedLeb128Size(method.accessFlags);
            result += Leb128.unsignedLeb128Size(method.codeOffset);
        }
        return result;
    }

    public static class Field implements Comparable<Field> {
        public int fieldIndex;
        public int accessFlags;

        public Field(int fieldIndex, int accessFlags) {
            this.fieldIndex = fieldIndex;
            this.accessFlags = accessFlags;
        }

        @Override
        public int compareTo(Field o) {
            if (fieldIndex != o.fieldIndex) return fieldIndex - o.fieldIndex;
            if (accessFlags != o.accessFlags) return accessFlags - o.accessFlags;
            return 0;
        }
    }

    public static class Method implements Comparable<Method> {
        public int methodIndex;
        public int accessFlags;
        public int codeOffset;

        public Method(int methodIndex, int accessFlags, int codeOffset) {
            this.methodIndex = methodIndex;
            this.accessFlags = accessFlags;
            this.codeOffset = codeOffset;
        }

        @Override
        public int compareTo(Method o) {
            if (methodIndex != o.methodIndex) return methodIndex - o.methodIndex;
            if (accessFlags != o.accessFlags) return accessFlags - o.accessFlags;
            if (codeOffset != o.codeOffset) return codeOffset - o.codeOffset;
            return 0;
        }
    }

}
