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

import com.tencent.tinker.build.apkparser.AndroidParser
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */

public class TinkerPackageConfigExtension {
    private static final String GLOBAL_PACKAGE_CONFIG = '__$GLOBAL_PACKAGE_CONFIG$__'
    /**
     * we can gen package config file while configField method
     */
    private Map<String, Map<String, String>> fields
    private Project project;
    private AndroidParser androidManifest;


    public TinkerPackageConfigExtension(project) {
        fields = [:]
        this.project = project
    }

    void configField(String name, String value) {
        configApkSpecField(GLOBAL_PACKAGE_CONFIG, name, value)
    }

    void configApkSpecField(String apkName, String name, String value) {
        def pkgFieldMap = fields.get(apkName)
        if (pkgFieldMap == null) {
            pkgFieldMap = [:]
            fields.put(apkName, pkgFieldMap)
        }
        pkgFieldMap.put(name, value)
    }

    Map<String, String> getFields() {
        return getApkSpecFields(GLOBAL_PACKAGE_CONFIG)
    }

    Map<String, String> getApkSpecFields(String apkName) {
        def result = fields.get(apkName)
        return result != null ? result : Collections.emptyMap()
    }

    private void createApkMetaFile() {
        if (androidManifest == null) {
            File oldPakFile = new File(project.tinkerPatch.oldApk)

            if (!oldPakFile.exists()) {
                throw new GradleException(
                        String.format("old apk file %s is not exist, you can set the value directly!", oldPakFile)
                )
            }
            androidManifest = AndroidParser.getAndroidManifest(oldPakFile);
        }
    }

    String getVersionCodeFromOldAPk() {
        createApkMetaFile()
        return androidManifest.apkMeta.versionCode;
    }

    String getVersionCodeFromApk(File apkPath) {
        return AndroidParser.getAndroidManifest(apkPath).apkMeta.versionCode
    }

    String getVersionNameFromOldAPk() {
        createApkMetaFile()
        return androidManifest.apkMeta.versionName;
    }

    String getVersionNameFromApk(File apkPath) {
        return AndroidParser.getAndroidManifest(apkPath).apkMeta.versionName
    }

    String getMinSdkVersionFromOldAPk() {
        createApkMetaFile()
        return androidManifest.apkMeta.minSdkVersion;
    }

    String getMinSdkVersionFromApk(File apkPath) {
        return AndroidParser.getAndroidManifest(apkPath).apkMeta.minSdkVersion
    }

    String getMetaDataFromOldApk(String name) {
        createApkMetaFile()
        String value = androidManifest.metaDatas.get(name);
        if (value == null) {
            throw new GradleException("can't find meta data ${name} from the old apk manifest file!")
        }
        return value
    }

    String getMetaDataFromApk(File apkPath, String name) {
        String value = AndroidParser.getAndroidManifest(apkPath).metaDatas.get(name)
        if (value == null) {
            throw new GradleException("can't find meta data ${name} from the manifest file in [${apkPath.getAbsolutePath()}]!")
        }
        return value
    }

    @Override
    public String toString() {
        """| fields = ${fields}
        """.stripMargin()
    }
}