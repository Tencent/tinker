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

package com.tencent.tinker.build.immutable;

import java.util.HashSet;
import java.util.Set;

public class DexRefData {
    int methodNum;
    int fieldNum;
    public Set<String> refFields;
    public Set<String> refMtds;

    DexRefData() {
        this(0, 0);
    }

    DexRefData(int methodNum, int fieldNum) {
        this.methodNum = methodNum;
        this.fieldNum = fieldNum;
        refFields = new HashSet<>();
        refMtds = new HashSet<>();
    }
}