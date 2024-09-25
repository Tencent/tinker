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

public class MethodHandle extends Item<MethodHandle> {
    public enum MethodHandleType {
        METHOD_HANDLE_TYPE_STATIC_PUT(0x00),
        METHOD_HANDLE_TYPE_STATIC_GET(0x01),
        METHOD_HANDLE_TYPE_INSTANCE_PUT(0x02),
        METHOD_HANDLE_TYPE_INSTANCE_GET(0x03),
        METHOD_HANDLE_TYPE_INVOKE_STATIC(0x04),
        METHOD_HANDLE_TYPE_INVOKE_INSTANCE(0x05),
        METHOD_HANDLE_TYPE_INVOKE_DIRECT(0x06),
        METHOD_HANDLE_TYPE_INVOKE_CONSTRUCTOR(0x07),
        METHOD_HANDLE_TYPE_INVOKE_INTERFACE(0x08);

        public final int value;

        MethodHandleType(int value) {
            this.value = value;
        }

        public static MethodHandleType fromValue(int value) {
            for (MethodHandleType methodHandleType : values()) {
                if (methodHandleType.value == value) {
                    return methodHandleType;
                }
            }
            throw new IllegalArgumentException(String.valueOf(value));
        }

        public boolean isField() {
            switch (this) {
                case METHOD_HANDLE_TYPE_STATIC_PUT:
                case METHOD_HANDLE_TYPE_STATIC_GET:
                case METHOD_HANDLE_TYPE_INSTANCE_PUT:
                case METHOD_HANDLE_TYPE_INSTANCE_GET:
                    return true;
                default:
                    return false;
            }
        }
    }

    public MethodHandleType methodHandleType;
    public int unused1;
    public int fieldOrMethodId;
    public int unused2;

    public MethodHandle(int off,
                        MethodHandleType methodHandleType,
                        int unused1,
                        int fieldOrMethodId,
                        int unused2) {
        super(off);
        this.methodHandleType = methodHandleType;
        this.unused1 = unused1;
        this.fieldOrMethodId = fieldOrMethodId;
        this.unused2 = unused2;
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.USHORT * 4;
    }

    @Override
    public int compareTo(MethodHandle o) {
        if (methodHandleType != o.methodHandleType) {
            return methodHandleType.compareTo(o.methodHandleType);
        }
        return CompareUtils.uCompare(fieldOrMethodId, o.fieldOrMethodId);
    }

    public void writeTo(Dex.Section out) {
        out.writeUnsignedShort(methodHandleType.value);
        out.writeUnsignedShort(unused1);
        out.writeUnsignedShort(fieldOrMethodId);
        out.writeUnsignedShort(unused2);
    }
}
