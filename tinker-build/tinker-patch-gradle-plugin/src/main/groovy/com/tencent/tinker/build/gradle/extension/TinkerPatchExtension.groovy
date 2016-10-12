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

import org.gradle.api.GradleException;

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */

public class TinkerPatchExtension {
    /**
     * Specifies the old apk path to diff with the new apk
     */
    String oldApk

    /**
     * If there is loader class changes,
     * or Activity, Service, Receiver, Provider change, it will terminal
     * if ignoreWarning is false
     * default: false
     */
    boolean ignoreWarning

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
     * If sign the patch file with the android signConfig
     * default: true
     */
    boolean useSign

    public TinkerPatchExtension() {
        oldApk = ""
        ignoreWarning = false
        useSign = true
    }

    void checkParameter() {
        if (oldApk == null) {
            throw new GradleException("old apk is null, you must set the correct old apk value!")
        } else if (!new File(oldApk).exists()) {
            throw new GradleException("old apk ${oldApk} is not exist, you must set the correct old apk value!")
        }
    }

    @Override
    public String toString() {
        """| oldApk = ${oldApk}
           | ignoreWarning = ${ignoreWarning}
           | useSign = ${useSign}
        """.stripMargin()
    }
}