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
import org.gradle.api.Project

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */

public class TinkerDexExtension {
    /**
     * raw or jar, if you want to support below 4.0, you should use jar
     * default: raw, keep the orginal file type
     */
    String dexMode;

    /**
     * If mUsePreGeneratedPatchDex was enabled, tinker framework would generate
     * a dex file including all added and changed classes instead of patch info file.
     *
     * You can make this mode enabled if you're using any dex encrypting solutions or
     * maintaining patches that suitable for multi-channel base packages.
     *
     * Notice that although you use this mode, proguard mappings should still be applied
     * to base package and all patched packages.
     */
    boolean usePreGeneratedPatchDex

    /**
     * the dex file patterns, which dex or jar files will be deal to gen patch
     * such as [classes.dex, classes-*.dex, assets/multiDex/*.jar]
     */
    Iterable<String> pattern;
    /**
     * the loader files, they will be removed during gen patch main dex
     * and they should be at the primary dex
     * such as [com.tencent.tinker.loader.*, com.tinker.sample.MyApplication]
     */
    Iterable<String> loader;
    private Project project;

    public TinkerDexExtension(Project project) {
        dexMode = "jar"
        pattern = []
        loader = []
        usePreGeneratedPatchDex = false
        this.project = project
    }

    void checkDexMode() {
        if (!dexMode.equals("raw") && !dexMode.equals("jar")) {
            throw new GradleException("dexMode can be only one of 'jar' or 'raw'!")
        }
    }

    @Override
    public String toString() {
        """| dexMode = ${dexMode}
           | usePreGeneratedPatchDex = ${usePreGeneratedPatchDex}
           | pattern = ${pattern}
           | loader = ${loader}
        """.stripMargin()
    }
}