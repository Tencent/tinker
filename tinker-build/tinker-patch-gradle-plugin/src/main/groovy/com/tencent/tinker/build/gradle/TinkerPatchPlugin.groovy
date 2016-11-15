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

import com.tencent.tinker.build.gradle.extension.*
import com.tencent.tinker.build.gradle.task.*
import com.tencent.tinker.build.gradle.transform.AuxiliaryInjectTransform
import com.tencent.tinker.build.util.FileOperation
import com.tencent.tinker.build.util.TypedValue
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException

/**
 * Registers the plugin's tasks.
 *
 * @author zhangshaowen
 */

class TinkerPatchPlugin implements Plugin<Project> {
    public static final String TINKER_INTERMEDIATES = "build/intermediates/tinker_intermediates/"

    @Override
    public void apply(Project project) {
        project.apply plugin: 'osdetector'

        project.extensions.create('tinkerPatch', TinkerPatchExtension)

        project.tinkerPatch.extensions.create('buildConfig', TinkerBuildConfigExtension, project)

        project.tinkerPatch.extensions.create('dex', TinkerDexExtension, project)
        project.tinkerPatch.extensions.create('lib', TinkerLibExtension)
        project.tinkerPatch.extensions.create('res', TinkerResourceExtension)
        project.tinkerPatch.extensions.create('packageConfig', TinkerPackageConfigExtension, project)
        project.tinkerPatch.extensions.create('sevenZip', TinkerSevenZipExtension, project)

        def configuration = project.tinkerPatch

        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('generateTinkerApk: Android Application plugin required')
        }

        def android = project.extensions.android

        //add the tinker anno resource to the package exclude option
        android.packagingOptions.exclude("META-INF/services/javax.annotation.processing.Processor")
        android.packagingOptions.exclude("TinkerAnnoApplication.tmpl")

        //open jumboMode
        android.dexOptions.jumboMode = true

        //close preDexLibraries
        try {
            android.dexOptions.preDexLibraries = false
        } catch (Throwable e) {
            //no preDexLibraries field, just continue
        }

        android.registerTransform(new AuxiliaryInjectTransform(project))

        project.afterEvaluate {
            project.logger.error("----------------------tinker build warning ------------------------------------")
            project.logger.error("tinker auto operation: ")
            project.logger.error("excluding annotation processor and source template from app packaging. Enable dx jumboMode to reduce package size.")
            project.logger.error("enable dx jumboMode to reduce package size.")
            project.logger.error("disable preDexLibraries to prevent ClassDefNotFoundException when your app is booting.")
            project.logger.error("")
            project.logger.error("tinker will change your build configs:")
            project.logger.error("we will add TINKER_ID=${configuration.buildConfig.tinkerId} in your build output manifest file build/intermediates/manifests/full/*")
            project.logger.error("")
            project.logger.error("if minifyEnabled is true")

            String tempMappingPath = configuration.buildConfig.applyMapping

            if (FileOperation.isLegalFile(tempMappingPath)) {
                project.logger.error("we will build ${project.getName()} apk with apply mapping file ${tempMappingPath}")
            }

            project.logger.error("you will find the gen proguard rule file at ${TinkerProguardConfigTask.PROGUARD_CONFIG_PATH}")
            project.logger.error("and we will help you to put it in the proguardFiles.")
            project.logger.error("")
            project.logger.error("if multiDexEnabled is true")
            project.logger.error("you will find the gen multiDexKeepProguard file at ${TinkerMultidexConfigTask.MULTIDEX_CONFIG_PATH}")
            project.logger.error("and you should copy it to your own multiDex keep proguard file yourself.")
            project.logger.error("")
            project.logger.error("if applyResourceMapping file is exist")
            String tempResourceMappingPath = configuration.buildConfig.applyResourceMapping
            if (FileOperation.isLegalFile(tempResourceMappingPath)) {
                project.logger.error("we will build ${project.getName()} apk with resource R.txt ${tempResourceMappingPath} file")
            } else {
                project.logger.error("we will build ${project.getName()} apk with resource R.txt file")
            }
            project.logger.error("if resources.arsc has changed, you should use applyResource mode to build the new apk!")
            project.logger.error("-----------------------------------------------------------------")

            android.applicationVariants.all { variant ->

                def variantOutput = variant.outputs.first()
                def variantName = variant.name.capitalize()

                try {
                    def instantRunTask = project.tasks.getByName("transformClassesWithInstantRunFor${variantName}")
                    if (instantRunTask) {
                        throw new GradleException(
                                "Tinker does not support instant run mode, please trigger build"
                                        + " by assemble${variantName} or disable instant run"
                                        + " in 'File->Settings...'."
                        )
                    }
                } catch (UnknownTaskException e) {
                    // Not in instant run mode, continue.
                }

                TinkerPatchSchemaTask tinkerPatchBuildTask = project.tasks.create("tinkerPatch${variantName}", TinkerPatchSchemaTask)
                tinkerPatchBuildTask.dependsOn variant.assemble

                tinkerPatchBuildTask.signConfig = variant.apkVariantData.variantConfiguration.signingConfig

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
                manifestTask.mustRunAfter variantOutput.processManifest

                variantOutput.processResources.dependsOn manifestTask

                //resource id
                TinkerResourceIdTask applyResourceTask = project.tasks.create("tinkerProcess${variantName}ResourceId", TinkerResourceIdTask)
                applyResourceTask.resDir = variantOutput.processResources.resDir
                //let applyResourceTask run after manifestTask
                applyResourceTask.mustRunAfter manifestTask

                variantOutput.processResources.dependsOn applyResourceTask

                // Add this proguard settings file to the list
                boolean proguardEnable = variant.getBuildType().buildType.minifyEnabled

                if (proguardEnable) {
                    TinkerProguardConfigTask proguardConfigTask = project.tasks.create("tinkerProcess${variantName}Proguard", TinkerProguardConfigTask)
                    proguardConfigTask.applicationVariant = variant
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