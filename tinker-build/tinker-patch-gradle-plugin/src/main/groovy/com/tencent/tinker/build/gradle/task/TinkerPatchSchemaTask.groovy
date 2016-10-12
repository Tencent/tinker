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

package com.tencent.tinker.build.gradle.task
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
        configuration.res.checkParameter()
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
               .setUsePreGeneratedPatchDex(configuration.usePreGeneratedPatchDex)
               .setDexFilePattern(configuration.dex.pattern)
               .setDexLoaderPattern(configuration.dex.loader)
               .setDexMode(configuration.dex.dexMode)
               .setSoFilePattern(configuration.lib.pattern)
               .setResourceFilePattern(configuration.res.pattern)
               .setResourceIgnoreChangePattern(configuration.res.ignoreChange)
               .setResourceLargeModSize(configuration.res.largeModSize)
               .setUseApplyResource(configuration.buildConfig.usingResourceMapping)
               .setConfigFields(configuration.packageConfig.getFields())
               .setSevenZipPath(configuration.sevenZip.path)
               .setUseSign(configuration.useSign)

        InputParam inputParam = builder.create()
        Runner.gradleRun(inputParam);
    }
}