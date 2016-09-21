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

package com.tencent.tinker.build.gradle.extension
/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */

public class TinkerLibExtension {
    /**
     * the library file patterns, which files will be deal to gen patch
     * such as [lib/armeabi/*.so, assets/libs/*.so]
     */
    Iterable<String> pattern;


    public TinkerLibExtension() {
        pattern = []
    }

    @Override
    public String toString() {
        """| pattern = ${pattern}
        """.stripMargin()
    }
}