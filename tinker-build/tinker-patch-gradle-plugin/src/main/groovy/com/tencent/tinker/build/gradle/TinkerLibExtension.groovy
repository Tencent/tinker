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

package com.tencent.tinker.build.gradle
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