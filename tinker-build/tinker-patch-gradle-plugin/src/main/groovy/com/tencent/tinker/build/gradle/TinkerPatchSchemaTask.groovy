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

import com.tencent.tinker.build.patch.InputParam
import com.tencent.tinker.build.patch.Runner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerPatchSchemaTask extends DefaultTask {
    def configuration
    def android
    String buildApkPath
    String outputFolder
    def signconfig

    public TinkerPatchSchemaTask() {
        description = 'Assemble Tinker Patch'
        group = 'tinker'
        outputs.upToDateWhen { false }
        configuration = project.tinkerPatch

        android = project.extensions.android
    }


    @TaskAction
    def tinkerPatch() {
//        println configuration.toString()

        configuration.checkParameter()
        configuration.buildConfig.checkParameter()

        configuration.dex.checkDexMode()
        configuration.sevenZip.resolveZipFinalPath()

        InputParam.Builder builder = new InputParam.Builder()
        if (configuration.useSign) {
            if (signconfig == null) {
                throw new GradleException("can't the get signconfig for ${taskName} build")
            }
            builder.setSignFile(signconfig.storeFile)
                    .setKeypass(signconfig.keyPassword)
                    .setStorealias(signconfig.keyAlias)
                    .setStorepass(signconfig.storePassword)

        }

        builder.setOldApk(configuration.oldApk)
                .setNewApk(buildApkPath)
                .setOutBuilder(outputFolder)
                .setIgnoreWarning(configuration.ignoreWarning)
                .setDexFilePattern(configuration.dex.pattern)
                .setDexLoaderPattern(configuration.dex.loader)
                .setDexMode(configuration.dex.dexMode)
                .setSoFilePattern(configuration.lib.pattern)
                .setConfigFields(configuration.packageConfig.getFields())
                .setSevenZipPath(configuration.sevenZip.path)
                .setUseSign(configuration.useSign)

        InputParam inputParam = builder.create()
        Runner.gradleRun(inputParam);
    }
}