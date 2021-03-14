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

package com.tencent.tinker.build.gradle

import org.gradle.api.Project

/**
 * define the tinker build path.
 *
 * @author yanbo
 */

class TinkerBuildPath {
    private static final String TINKER_INTERMEDIATES = "/intermediates/tinker_intermediates/"

    private static final String MULTIDEX_CONFIG_FILE = "tinker_multidexkeep.pro"
    private static final String PROGUARD_CONFIG_FILE =  "tinker_proguard.pro"

    private static final String RESOURCE_PUBLIC_XML = "public.xml"
    private static final String RESOURCE_IDX_XML = "idx.xml"
    private static final String RESOURCE_VALUES_BACKUP = "values_backup"
    private static final String RESOURCE_PUBLIC_TXT = "public.txt"

    //it's parent dir must start with values
    private static final String RESOURCE_TO_COMPILE_PUBLIC_XML = "aapt2/res/values/tinker_public.xml"


    static String getTinkerIntermediates(Project project) {
        return "${project.buildDir}$TINKER_INTERMEDIATES"
    }

    static String getMultidexConfigPath(Project project) {
        return "${getTinkerIntermediates(project)}$MULTIDEX_CONFIG_FILE"
    }

    static String getProguardConfigPath(Project project) {
        return "${getTinkerIntermediates(project)}$PROGUARD_CONFIG_FILE"
    }

    static String getResourcePublicXml(Project project) {
        return "${getTinkerIntermediates(project)}$RESOURCE_PUBLIC_XML"
    }

    static String getResourceIdxXml(Project project) {
        return "${getTinkerIntermediates(project)}$RESOURCE_IDX_XML"
    }

    static String getResourceValuesBackup(Project project) {
        return "${getTinkerIntermediates(project)}$RESOURCE_VALUES_BACKUP"
    }

    static String getResourcePublicTxt(Project project) {
        return "${getTinkerIntermediates(project)}$RESOURCE_PUBLIC_TXT"
    }

    static String getResourceToCompilePublicXml(Project project) {
        return "${getTinkerIntermediates(project)}$RESOURCE_TO_COMPILE_PUBLIC_XML"
    }
}
