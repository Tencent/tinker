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

import com.tencent.tinker.build.util.TypedValue
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers the plugin's tasks.
 *
 * @author zhangshaowen
 */

class TinkerPatchPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.apply plugin: 'osdetector'

        project.extensions.create('tinkerPatch', TinkerPatchExtension)

        project.tinkerPatch.extensions.create('buildConfig', TinkerBuildConfigExtension, project)

        project.tinkerPatch.extensions.create('dex', TinkerDexExtension, project)
        project.tinkerPatch.extensions.create('lib', TinkerLibExtension)
        project.tinkerPatch.extensions.create('packageConfig', TinkerPackageConfigExtension, project)
        project.tinkerPatch.extensions.create('sevenZip', TinkerSevenZipExtension, project)


        project.afterEvaluate {
            if (!project.plugins.hasPlugin('com.android.application')) {
                throw new GradleException('generateTinkerApk: Android Application plugin required')
            }

            def configuration = project.tinkerPatch
            def android = project.extensions.android

            //add the tinker anno resource to the package exclude option
            android.packagingOptions.exclude("META-INF/services/javax.annotation.processing.Processor")
            android.packagingOptions.exclude("TinkerAnnoApplication.tmpl")

            project.logger.error("----------------------tinker build warning ------------------------------------")
            project.logger.error("tinker will change your build configs:");
            project.logger.error("we will add TINDER_ID=${configuration.buildConfig.tinkerId} in your build output manifest file build/intermediates/manifests/full/*")
            project.logger.error("")
            project.logger.error("if minifyEnabled is true")

            String tempMappingFile = configuration.buildConfig.applyMapping
            if (tempMappingFile != null && new File(tempMappingFile).exists()) {
                project.logger.error("we will build ${project.getName()} apk with apply mapping file ${tempMappingFile}")
            }
            project.logger.error("you will find the gen proguard rule file at ${TinkerProguardConfigTask.PROGUARD_CONFIG_PATH}")
            project.logger.error("and we will help you to put it in the proguardFiles.")
            project.logger.error("")
            project.logger.error("if multiDexEnabled is true")
            project.logger.error("you will find the gen multiDexKeepProguard file at ${TinkerMultidexConfigTask.MULTIDEX_CONFIG_PATH}")
            project.logger.error("and you should copy it to your own multiDex keep proguard file yourself.")
            project.logger.error("-----------------------------------------------------------------")

            android.applicationVariants.all { variant ->

                def variantOutput = variant.outputs.first()
                def variantName = variant.name.capitalize()

                TinkerPatchSchemaTask tinkerPatchBuildTask = project.tasks.create("tinkerPatch${variantName}", TinkerPatchSchemaTask)
                tinkerPatchBuildTask.dependsOn variant.assemble

                tinkerPatchBuildTask.signconfig = variant.apkVariantData.variantConfiguration.signingConfig

                variant.outputs.each { output ->
                    tinkerPatchBuildTask.buildApkPath = output.outputFile
                    File parentFile = output.outputFile
                    tinkerPatchBuildTask.outputFolder = "${parentFile.getParentFile().getParentFile().getAbsolutePath()}/" + TypedValue.PATH_DEFAULT_OUTPUT + "/" + variant.dirName
                }

                // Create a task to add a build TINKER_ID to AndroidManifest.xml
                // This task must be called after "process${variantName}Manifest", since it
                // requires that an AndroidManifest.xml exists in `build/intermediates`.
                TinkerManifestTask manifestTask = project.tasks.create("tinkerProcess${variantName}Manifest", TinkerManifestTask)
                manifestTask.manifestPath = variantOutput.processManifest.manifestOutputFile
                manifestTask.tinkerValue = configuration.buildConfig.tinkerId
                manifestTask.mustRunAfter variantOutput.processManifest

                variantOutput.processResources.dependsOn manifestTask

                // Add this proguard settings file to the list
                boolean proguardEnable = variant.getBuildType().buildType.minifyEnabled

                if (proguardEnable) {
                    TinkerProguardConfigTask proguardConfigTask = project.tasks.create("tinkerProcess${variantName}Proguard", TinkerProguardConfigTask)
                    proguardConfigTask.applicationVariant = variant
                    String mappingFile = configuration.buildConfig.applyMapping


                    if (mappingFile != null && new File(mappingFile).exists()) {
                        proguardConfigTask.applyMappingFile = mappingFile
                    }
                    variantOutput.packageApplication.dependsOn proguardConfigTask
                }

                // Add this multidex proguard settings file to the list
                boolean multiDexEnabled = variant.apkVariantData.variantConfiguration.isMultiDexEnabled()

                if (multiDexEnabled) {
                    TinkerMultidexConfigTask multidexConfigTask = project.tasks.create("tinkerProcess${variantName}MultidexKeep", TinkerMultidexConfigTask)
                    multidexConfigTask.applicationVariant = variant

                    variantOutput.packageApplication.dependsOn multidexConfigTask
                }
            }

        }
    }

}