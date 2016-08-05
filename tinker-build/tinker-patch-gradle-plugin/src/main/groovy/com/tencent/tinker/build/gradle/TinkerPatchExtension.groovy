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
     * if there is loader class changes,
     * or Activity, Service, Receiver, Provider change, it will terminal
     * if ignoreWarning is false
     * default: false
     */
    boolean ignoreWarning

    /**
     * if sign the patch file with the android signConfig
     * default: true
     */
    boolean useSign

    public TinkerPatchExtension() {
        oldApk = null
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