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

import com.tencent.tinker.build.apkparser.AndroidManifest
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */

public class TinkerPackageConfigExtension {
    /**
     * we can gen package config file while configField method
     */
    private Map<String, String> fields
    private Project project;
    private AndroidManifest androidManifest;


    public TinkerPackageConfigExtension(project) {
        fields = [:]
        this.project = project
    }

    void configField(String name, String value) {
        fields.put(name, value)
    }

    Map<String, String> getFields() {
        return fields
    }

    private void createApkMetaFile() {
        if (androidManifest == null) {
            File oldPakFile = new File(project.tinkerPatch.oldApk)

            if (!oldPakFile.exists()) {
                throw new GradleException(
                        String.format("old apk file %s is not exist, you can set the value directly!", oldPakFile)
                )
            }
            androidManifest = AndroidManifest.getAndroidManifest(oldPakFile);
        }
    }

    String getVersionCodeFromOldAPk() {
        createApkMetaFile()
        return androidManifest.apkMeta.versionCode;
    }

    String getVersionNameFromOldAPk() {
        createApkMetaFile()
        return androidManifest.apkMeta.versionName;
    }

    String getMinSdkVersionFromOldAPk() {
        createApkMetaFile()
        return androidManifest.apkMeta.minSdkVersion;
    }

    String getMetaDataFromOldApk(String name) {
        createApkMetaFile()
        String value = androidManifest.metaDatas.get(name);
        if (value == null) {
            throw new GradleException("can't find meta data " + name + " from the old apk manifest file!")
        }
        return value
    }

    @Override
    public String toString() {
        """| fields = ${fields}
        """.stripMargin()
    }
}