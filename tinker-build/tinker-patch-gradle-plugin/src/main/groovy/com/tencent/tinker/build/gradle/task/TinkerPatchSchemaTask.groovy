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

import com.tencent.tinker.build.gradle.extension.TinkerPatchExtension
import com.tencent.tinker.build.patch.InputParam
import com.tencent.tinker.build.patch.Runner
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerPatchSchemaTask extends DefaultTask {
    @Internal
    TinkerPatchExtension configuration

    @Internal
    String buildApkPath

    @Internal
    def signConfig

    @Internal
    String outputFolder

    @Internal
    def android

    TinkerPatchSchemaTask() {
        description = 'Assemble Tinker Patch'
        group = 'tinker'
        outputs.upToDateWhen { false }
        configuration = project.tinkerPatch

        android = project.extensions.android
    }


    @TaskAction
    def tinkerPatch() {
        configuration.checkParameter()
        configuration.buildConfig.checkParameter()
        configuration.res.checkParameter()
        configuration.dex.checkDexMode()
        configuration.sevenZip.resolveZipFinalPath()

        InputParam.Builder builder = new InputParam.Builder()
        if (configuration.useSign) {
            if (signConfig == null) {
                throw new GradleException("can't the get signConfig for this build")
            }
            builder.setSignFile(signConfig.storeFile)
                    .setKeypass(signConfig.keyPassword)
                    .setStorealias(signConfig.keyAlias)
                    .setStorepass(signConfig.storePassword)
        }

        def buildApkFile = new File(buildApkPath)
        def oldApkFile = new File(configuration.oldApk)
        def newApks = [] as TreeSet<File>
        def oldApks = [] as TreeSet<File>
        def oldApkNames = [] as HashSet<String>
        def newApkNames = [] as HashSet<String>
        if (buildApkFile.isDirectory() && oldApkFile.isDirectory()) {
            // Directory mode
            oldApkFile.eachFile {
                if (it.name.endsWith('.apk')) {
                    oldApks << it
                    oldApkNames << it.getName()
                }
            }
            buildApkFile.eachFile {
                if (it.name.endsWith('.apk')) {
                    newApks << it
                    newApkNames << it.getName()
                }
            }

            def unmatchedOldApkNames = new HashSet<>(oldApkNames)
            unmatchedOldApkNames.removeAll(newApkNames)

            def unmatchedNewApkNames = new HashSet<>(newApkNames)
            unmatchedNewApkNames.removeAll(oldApkNames)

            if (!unmatchedOldApkNames.isEmpty() || !unmatchedNewApkNames.isEmpty()) {
                throw new GradleException("Both oldApk and newApk args are directories"
                        + " but apks inside them are not matched.\n"
                        + " unmatched old apks: ${unmatchedOldApkNames}\n"
                        + " unmatched new apks: ${unmatchedNewApkNames}."
                )
            }
        } else if (buildApkFile.isFile() && oldApkFile.isFile()) {
            // File mode
            newApks << buildApkFile
            oldApks << oldApkFile
        } else {
            throw new GradleException("oldApk [${oldApkFile.getAbsolutePath()}] and newApk [${buildApkFile.getAbsolutePath()}] must be both files or directories.")
        }

        def tmpDir = new File("${project.buildDir}/tmp/tinkerPatch")
        tmpDir.mkdirs()
        def outputDir = new File(outputFolder)
        outputDir.mkdirs()

        for (def i = 0; i < newApks.size(); ++i) {
            def oldApk = oldApks[i] as File
            def newApk = newApks[i] as File

            def packageConfigFields = new HashMap<String, String>(configuration.packageConfig.getFields())
            packageConfigFields.putAll(configuration.packageConfig.getApkSpecFields(newApk.getName()))

            builder.setOldApk(oldApk.getAbsolutePath())
                    .setNewApk(newApk.getAbsolutePath())
                    .setOutBuilder(tmpDir.getAbsolutePath())
                    .setIgnoreWarning(configuration.ignoreWarning)
                    .setAllowLoaderInAnyDex(configuration.allowLoaderInAnyDex)
                    .setRemoveLoaderForAllDex(configuration.removeLoaderForAllDex)
                    .setDexFilePattern(new ArrayList<String>(configuration.dex.pattern))
                    .setIsProtectedApp(configuration.buildConfig.isProtectedApp)
                    .setIsComponentHotplugSupported(configuration.buildConfig.supportHotplugComponent)
                    .setDexLoaderPattern(new ArrayList<String>(configuration.dex.loader))
                    .setDexIgnoreWarningLoaderPattern(new ArrayList<String>(configuration.dex.ignoreWarningLoader))
                    .setDexMode(configuration.dex.dexMode)
                    .setSoFilePattern(new ArrayList<String>(configuration.lib.pattern))
                    .setResourceFilePattern(new ArrayList<String>(configuration.res.pattern))
                    .setResourceIgnoreChangePattern(new ArrayList<String>(configuration.res.ignoreChange))
                    .setResourceIgnoreChangeWarningPattern(new ArrayList<String>(configuration.res.ignoreChangeWarning))
                    .setResourceLargeModSize(configuration.res.largeModSize)
                    .setUseApplyResource(configuration.buildConfig.usingResourceMapping)
                    .setConfigFields(packageConfigFields)
                    .setSevenZipPath(configuration.sevenZip.path)
                    .setUseSign(configuration.useSign)
                    .setArkHotPath(configuration.arkHot.path)
                    .setArkHotName(configuration.arkHot.name)

            InputParam inputParam = builder.create()
            Runner.gradleRun(inputParam)

            def prefix = newApk.name.take(newApk.name.lastIndexOf('.'))
            tmpDir.eachFile(FileType.FILES) {
                if (!it.name.endsWith(".apk")) {
                    return
                }
                final File dest = new File(outputDir, "${prefix}-${it.name}")
                it.renameTo(dest)
            }
        }
    }
}