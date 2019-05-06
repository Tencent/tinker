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

import com.tencent.tinker.android.dex.TableOfContents.Section.Item;
import com.tencent.tinker.android.dex.util.CompareUtils;
import com.tencent.tinker.android.dex.util.HashCodeHelper;

public final class ClassData extends Item<ClassData> {
    public Field[] staticFields;
    public Field[] instanceFields;
    public Method[] directMethods;
    public Method[] virtualMethods;

    public ClassData(int off, Field[] staticFields, Field[] instanceFields,
            Method[] directMethods, Method[] virtualMethods) {
        super(off);

        this.staticFields = staticFields;
        this.instanceFields = instanceFields;
        this.directMethods = directMethods;
        this.virtualMethods = virtualMethods;
    }

    @Override
    public int compareTo(ClassData other) {
        int res = CompareUtils.aArrCompare(staticFields, other.staticFields);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.aArrCompare(instanceFields, other.instanceFields);
        if (res != 0) {
            return res;
        }
        res = CompareUtils.aArrCompare(directMethods, other.directMethods);
        if (res != 0) {
            return res;
        }
        return CompareUtils.aArrCompare(virtualMethods, other.virtualMethods);
    }

    @Override
    public int hashCode() {
        return HashCodeHelper.hash(staticFields, instanceFields, directMethods, virtualMethods);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ClassData)) {
            return false;
        }
        return this.compareTo((ClassData) obj) == 0;
    }

    @Override
    public int byteCountInDex() {
        int res = Leb128.unsignedLeb128Size(staticFields.length);
        res += Leb128.unsignedLeb128Size(instanceFields.length);
        res += Leb128.unsignedLeb128Size(directMethods.length);
        res += Leb128.unsignedLeb128Size(virtualMethods.length);
        res += calcFieldsSize(staticFields);
        res += calcFieldsSize(instanceFields);
        res += calcMethodsSize(directMethods);
        res += calcMethodsSize(virtualMethods);
        return res;
    }

    private int calcFieldsSize(Field[] fields) {
        int res = 0;
        int prevFieldIndex = 0;
        for (Field field : fields) {
            int fieldIndexDelta = field.fieldIndex - prevFieldIndex;
            prevFieldIndex = field.fieldIndex;
            res += Leb128.unsignedLeb128Size(fieldIndexDelta) + Leb128.unsignedLeb128Size(field.accessFlags);
        }
        return res;
    }

    private int calcMethodsSize(Method[] methods) {
        int res = 0;
        int prevMethodIndex = 0;
        for (Method method : methods) {
            int methodIndexDelta = method.methodIndex - prevMethodIndex;
            prevMethodIndex = method.methodIndex;
            res += Leb128.unsignedLeb128Size(methodIndexDelta)
                 + Leb128.unsignedLeb128Size(method.accessFlags)
                 + Leb128.unsignedLeb128Size(method.codeOffset);
        }
        return res;
    }

    public static class Field implements Comparable<Field> {
        public int fieldIndex;
        public int accessFlags;

        public Field(int fieldIndex, int accessFlags) {
            this.fieldIndex = fieldIndex;
            this.accessFlags = accessFlags;
        }

        @Override
        public int compareTo(Field other) {
            int res = CompareUtils.uCompare(fieldIndex, other.fieldIndex);
            if (res != 0) {
                return res;
            }
            return CompareUtils.sCompare(accessFlags, other.accessFlags);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Field)) {
                return false;
            }
            return this.compareTo((Field) obj) == 0;
        }

        @Override
        public int hashCode() {
            return HashCodeHelper.hash(fieldIndex, accessFlags);
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
        public int compareTo(Method other) {
            int res = CompareUtils.uCompare(methodIndex, other.methodIndex);
            if (res != 0) {
                return res;
            }
            res = CompareUtils.sCompare(accessFlags, other.accessFlags);
            if (res != 0) {
                return res;
            }
            return CompareUtils.sCompare(codeOffset, other.codeOffset);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Method)) {
                return false;
            }
            return this.compareTo((Method) obj) == 0;
        }

        @Override
        public int hashCode() {
            return HashCodeHelper.hash(methodIndex, accessFlags, codeOffset);
        }
    }
}
