/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.build.dexpatcher.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RelatedClassResolver {

    private RelatedClassResolver() {
    }

    public static Set<String> resolve(String className, Set<String> allClassNames) {
        if (className == null || className.isEmpty() || allClassNames == null || allClassNames.isEmpty()) {
            return Collections.emptySet();
        }

        final String outerName = getOuterClassName(className);
        final String prefix = outerName + "$";

        final Set<String> result = new HashSet<>();
        for (String name : allClassNames) {
            if (name.equals(outerName) || name.startsWith(prefix)) {
                result.add(name);
            }
        }
        return result;
    }

    private static String getOuterClassName(String className) {
        final int dollarIdx = className.indexOf('$');
        if (dollarIdx < 0) {
            return className;
        }
        return className.substring(0, dollarIdx);
    }
}
