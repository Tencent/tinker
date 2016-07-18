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

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * The configuration properties.
 *
 * @author shwenzhang
 */

public class TinkerBuildConfigExtension {

    /**
     * Specifies the old apk's mapping file for proguard to applymapping
     */
    String applyMapping

    /**
     * because we don't want to check the base apk with md5 in the runtime(it is slow)
     * tinkerId is use to identify the unique base apk when the patch is tried to apply.
     * we can use git rev, svn rev or simply versionCode.
     * we will gen the tinkerId in your manifest automatic
     */
    String tinkerId

    private Project project;

    public TinkerBuildConfigExtension(Project project) {

        this.project = project
        applyMapping = null
        tinkerId = null
    }

    void checkParameter() {
        if (tinkerId == null || tinkerId.isEmpty()) {
            throw new GradleException("you must set your tinkerId to identify the base apk!")
        }
    }


    @Override
    public String toString() {
        """| applyMapping = ${applyMapping}
           | tinkerId = ${tinkerId}
        """.stripMargin()
    }
}