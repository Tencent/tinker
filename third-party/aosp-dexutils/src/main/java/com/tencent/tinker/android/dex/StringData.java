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

/**
 * *** This file is NOT a part of AOSP. ***
 *
 * Structure of StringData element in Dex file.
 */
public class StringData extends SectionItem<StringData> {
    public String value;

    public StringData(Section owner, int offset, String value) {
        super(owner, offset);
        this.value = value;
    }

    @Override
    public StringData clone(Section newOwner, int newOffset) {
        return new StringData(newOwner, newOffset, value);
    }

    @Override
    public int compareTo(StringData o) {
        return value.compareTo(o.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        return compareTo((StringData) obj) == 0;
    }

    @Override
    public int getByteCountInDex() {
        return Leb128.unsignedLeb128Size(value.length()) + (int) Mutf8.countBytes(value, true) + SizeOf.UBYTE;
    }
}
