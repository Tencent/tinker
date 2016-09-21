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

import org.gradle.api.GradleException

/**
 * The configuration properties.
 *
 * @author liangwenxiang
 */

public class TinkerResourceExtension {
    /**
     * the resource file patterns, which files will be deal to gen patch
     * such as [res/*, assets/*, resources.arsc]
     */
    Iterable<String> pattern
    /**
     * the resource file ignoreChange patterns, ignore add, delete or modify resource change
     * Warning, we can only use for files no relative with resources.arsc
     */
    Iterable<String> ignoreChange

    /**
     * default 100kb
     * for modify resource, if it is larger than 'largeModSize'
     * we would like to use bsdiff algorithm to reduce patch file size
     */
    int largeModSize

    public TinkerResourceExtension() {
        pattern = []
        ignoreChange = []
        largeModSize = 100
    }
    void checkParameter() {
        if (largeModSize <= 0) {
            throw new GradleException("largeModSize must be larger than 0")
        }
    }

    @Override
    public String toString() {
        """| pattern = ${pattern}
           | exclude = ${ignoreChange}
           | largeModSize = ${largeModSize}kb
        """.stripMargin()
    }
}