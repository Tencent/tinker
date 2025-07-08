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

import com.android.build.gradle.api.ApkVariant
import com.tencent.tinker.build.gradle.extension.*
import com.tencent.tinker.build.gradle.task.*
import com.tencent.tinker.build.util.FileOperation
import com.tencent.tinker.build.util.TypedValue
import com.tencent.tinker.build.util.Utils
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import sun.misc.Unsafe

import java.lang.reflect.Field

/**
 * Registers the plugin's tasks.
 *
 * @author zhangshaowen
 */

class TinkerPatchPlugin implements Plugin<Project> {
    public static final String ISSUE_URL = "https://github.com/Tencent/tinker/issues"

    private Project mProject = null

    @Override
    public void apply(Project project) {
        mProject = project

        //osdetector change its plugin name in 1.4.0
        try {
            mProject.apply plugin: 'osdetector'
        } catch (Throwable e) {
            mProject.apply plugin: 'com.google.osdetector'
        }

        mProject.extensions.create('tinkerPatch', TinkerPatchExtension)

        mProject.tinkerPatch.extensions.create('buildConfig', TinkerBuildConfigExtension, mProject)

        mProject.tinkerPatch.extensions.create('dex', TinkerDexExtension, mProject)
        mProject.tinkerPatch.extensions.create('lib', TinkerLibExtension)
        mProject.tinkerPatch.extensions.create('res', TinkerResourceExtension)
        mProject.tinkerPatch.extensions.create("arkHot", TinkerArkHotExtension)
        mProject.tinkerPatch.extensions.create('packageConfig', TinkerPackageConfigExtension, mProject)
        mProject.tinkerPatch.extensions.create('sevenZip', TinkerSevenZipExtension, mProject)

        if (!mProject.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('generateTinkerApk: Android Application plugin required')
        }

        def android = mProject.extensions.android

        try {
            //close preDexLibraries
            android.dexOptions.preDexLibraries = false

            //open jumboMode
            android.dexOptions.jumboMode = true

            //disable dex archive mode
            disableArchiveDex()
            //禁止打了运行时注解的类全部打到主dex中
            android.dexOptions.keepRuntimeAnnotatedClasses = false
        } catch (Throwable e) {
            //no preDexLibraries field, just continue
        }

        mProject.afterEvaluate {
            def configuration = mProject.tinkerPatch

            if (!configuration.tinkerEnable) {
                mProject.logger.error("tinker tasks are disabled.")
                return
            }

            mProject.logger.error("----------------------tinker build warning ------------------------------------")
            mProject.logger.error("tinker auto operation: ")
            mProject.logger.error("excluding annotation processor and source template from app packaging. Enable dx jumboMode to reduce package size.")
            mProject.logger.error("enable dx jumboMode to reduce package size.")
            mProject.logger.error("disable preDexLibraries to prevent ClassDefNotFoundException when your app is booting.")
            mProject.logger.error("disable archive dex mode so far for keeping dex apply.")
            mProject.logger.error("")
            mProject.logger.error("tinker will change your build configs:")
            mProject.logger.error("we will add TINKER_ID=${configuration.buildConfig.tinkerId} in your build output manifest file ${project.buildDir}/intermediates/manifests/full/*")
            mProject.logger.error("")
            mProject.logger.error("if minifyEnabled is true")

            String tempMappingPath = configuration.buildConfig.applyMapping

            if (FileOperation.isLegalFile(tempMappingPath)) {
                mProject.logger.error("we will build ${mProject.getName()} apk with apply mapping file ${tempMappingPath}")
            }

            mProject.logger.error("you will find the gen proguard rule file at ${TinkerBuildPath.getProguardConfigPath(project)}")
            mProject.logger.error("and we will help you to put it in the proguardFiles.")
            mProject.logger.error("")
            mProject.logger.error("if multiDexEnabled is true")
            mProject.logger.error("you will find the gen multiDexKeepProguard file at ${TinkerBuildPath.getMultidexConfigPath(project)}")
            mProject.logger.error("and we will help you to put it in the MultiDexKeepProguardFile.")
            mProject.logger.error("")
            mProject.logger.error("if applyResourceMapping file is exist")
            String tempResourceMappingPath = configuration.buildConfig.applyResourceMapping
            if (FileOperation.isLegalFile(tempResourceMappingPath)) {
                mProject.logger.error("we will build ${mProject.getName()} apk with resource R.txt ${tempResourceMappingPath} file")
            } else {
                mProject.logger.error("we will build ${mProject.getName()} apk with resource R.txt file")
            }
            mProject.logger.error("if resources.arsc has changed, you should use applyResource mode to build the new apk!")
            mProject.logger.error("-----------------------------------------------------------------")

            android.applicationVariants.all { ApkVariant variant ->
                def variantName = variant.name
                def capitalizedVariantName = variantName.capitalize()

                def instantRunTask = Compatibilities.getInstantRunTask(project, variant)
                if (instantRunTask != null) {
                    throw new GradleException(
                            "Tinker does not support instant run mode, please trigger build"
                                    + " by assemble${capitalizedVariantName} or disable instant run"
                                    + " in 'File->Settings...'."
                    )
                }

                TinkerPatchSchemaTask tinkerPatchBuildTask = mProject.tasks.create("tinkerPatch${capitalizedVariantName}", TinkerPatchSchemaTask)
                tinkerPatchBuildTask.signConfig = variant.signingConfig

                // Create a task to add a build TINKER_ID to AndroidManifest.xml
                // This task must be called after "process${variantName}Manifest", since it
                // requires that an AndroidManifest.xml exists in `build/intermediates`.
                def agpProcessManifestTask = Compatibilities.getProcessManifestTask(project, variant)
                def tinkerManifestAction = new TinkerManifestAction(project)
                agpProcessManifestTask.doLast tinkerManifestAction

                def agpProcessManifestForPackageTask = Compatibilities.getProcessManifestForPackageTask(project, variant)
                agpProcessManifestForPackageTask.mustRunAfter tinkerManifestTask

                variant.outputs.each { variantOutput ->
                    setPatchNewApkPath(configuration, variantOutput, variant, tinkerPatchBuildTask)
                    setPatchOutputFolder(configuration, variantOutput, variant, tinkerPatchBuildTask)

                    def outputName = variantOutput.dirName
                    if (outputName.endsWith("/")) {
                        outputName = outputName.substring(0, outputName.length() - 1)
                    }
                    if (tinkerManifestAction.outputNameToManifestMap.containsKey(outputName)) {
                        throw new GradleException("Duplicate tinker manifest output name: '${outputName}'")
                    }
                    def manifestPath = Compatibilities.getOutputManifestPath(project, agpProcessManifestTask, variantOutput)
                    tinkerManifestAction.outputNameToManifestMap.put(outputName, manifestPath)
                }

                def agpProcessResourcesTask = Compatibilities.getProcessResourcesTask(project, variant)

                //resource id
                TinkerResourceIdTask applyResourceTask = mProject.tasks.create("tinkerProcess${capitalizedVariantName}ResourceId", TinkerResourceIdTask)
                applyResourceTask.variant = variant
                applyResourceTask.applicationId = Compatibilities.getApplicationId(project, variant)
                applyResourceTask.resDir = Compatibilities.getInputResourcesDirectory(project, agpProcessResourcesTask)

                applyResourceTask.mustRunAfter agpProcessManifestTask
                agpProcessResourcesTask.dependsOn applyResourceTask

                // Fix issue-866.
                // We found some case that applyResourceTask run after mergeResourcesTask, it caused 'applyResourceMapping' config not work.
                // The task need merged resources to calculate ids.xml, it must depends on merge resources task.
                def agpMergeResourcesTask = Compatibilities.getMergeResourcesTask(project, variant)
                applyResourceTask.dependsOn agpMergeResourcesTask

                if (tinkerManifestAction.outputNameToManifestMap == null
                        || tinkerManifestAction.outputNameToManifestMap.isEmpty()) {
                    throw new GradleException('No manifest output path was found.')
                }

                if (applyResourceTask.resDir == null) {
                    throw new GradleException("applyResourceTask.resDir is null.")
                }

                // Add this proguard settings file to the list
                boolean proguardEnable = variant.getBuildType().buildType.minifyEnabled

                if (proguardEnable) {
                    def obfuscateTask = Compatibilities.getObfuscateTask(project, variant)
                    obfuscateTask.doFirst new TinkerProguardConfigAction(variant)
                }

                // Add this multidex proguard settings file to the list
                boolean multiDexEnabled = variant.mergedFlavor.multiDexEnabled

                if (multiDexEnabled) {
                    TinkerMultidexConfigTask multidexConfigTask = mProject.tasks.create("tinkerProcess${capitalizedVariantName}MultidexKeep", TinkerMultidexConfigTask)
                    multidexConfigTask.applicationVariant = variant
                    multidexConfigTask.multiDexKeepProguard = getManifestMultiDexKeepProguard(variant)

                    // for java.io.FileNotFoundException: app/build/intermediates/multi-dex/release/manifest_keep.txt
                    // for gradle 3.x gen manifest_keep move to processResources task
                    multidexConfigTask.mustRunAfter agpProcessResourcesTask

                    def agpMultidexTask = Compatibilities.getMultiDexTask(project, variant)
                    def agpR8Task = Compatibilities.getR8Task(project, variant)
                    if (agpMultidexTask != null) {
                        agpMultidexTask.dependsOn multidexConfigTask
                    } else if (agpMultidexTask == null && agpR8Task != null) {
                        agpR8Task.dependsOn multidexConfigTask
                        try {
                            Object r8Transform = agpR8Task.getTransform()
                            //R8 maybe forget to add multidex keep proguard file in agp 3.4.0, it's a agp bug!
                            //If we don't do it, some classes will not keep in maindex such as loader's classes.
                            //So tinker will not remove loader's classes, it will crashed in dalvik and will check TinkerTestDexLoad.isPatch failed in art.
                            if (r8Transform.metaClass.hasProperty(r8Transform, "mainDexRulesFiles")) {
                                File manifestMultiDexKeepProguard = getManifestMultiDexKeepProguard(variant)
                                if (manifestMultiDexKeepProguard != null) {
                                    //see difference between mainDexRulesFiles and mainDexListFiles in https://developer.android.com/studio/build/multidex?hl=zh-cn
                                    FileCollection originalFiles = r8Transform.metaClass.getProperty(r8Transform, 'mainDexRulesFiles')
                                    if (!originalFiles.contains(manifestMultiDexKeepProguard)) {
                                        FileCollection replacedFiles = mProject.files(originalFiles, manifestMultiDexKeepProguard)
                                        mProject.logger.error("R8Transform original mainDexRulesFiles: ${originalFiles.files}")
                                        mProject.logger.error("R8Transform replaced mainDexRulesFiles: ${replacedFiles.files}")
                                        //it's final, use reflect to replace it.
                                        replaceKotlinFinalField("com.android.build.gradle.internal.transforms.R8Transform", "mainDexRulesFiles", r8Transform, replacedFiles)
                                    }
                                }
                            }
                        } catch (Exception ignore) {
                            //Maybe it's not a transform task after agp 3.6.0 so try catch it.
                        }
                    }
                    def collectMultiDexComponentsTask = Compatibilities.getCollectMultiDexComponentsTask(project, variant)
                    if (collectMultiDexComponentsTask != null) {
                        multidexConfigTask.mustRunAfter collectMultiDexComponentsTask
                    }
                }

                if (configuration.buildConfig.keepDexApply
                        && FileOperation.isLegalFile(mProject.tinkerPatch.oldApk)) {
                    com.tencent.tinker.build.gradle.transform.ImmutableDexTransform.inject(mProject, variant)
                }
            }
        }
    }

    /**
     * Specify the output folder of tinker patch result.
     *
     * @param configuration the tinker configuration 'tinkerPatch'
     * @param output the output of assemble result
     * @param variant the variant
     * @param tinkerPatchBuildTask the task that tinker patch uses
     */
    void setPatchOutputFolder(configuration, output, variant, tinkerPatchBuildTask) {
        File parentFile = output.outputFile
        String outputFolder = "${configuration.outputFolder}";
        if (!Utils.isNullOrNil(outputFolder)) {
            outputFolder = "${outputFolder}/${TypedValue.PATH_DEFAULT_OUTPUT}/${variant.dirName}"
        } else {
            outputFolder =
                    "${parentFile.getParentFile().getParentFile().getAbsolutePath()}/${TypedValue.PATH_DEFAULT_OUTPUT}/${variant.dirName}"
        }
        tinkerPatchBuildTask.outputFolder = outputFolder
    }

    void disableArchiveDex() {
        try {
            def booleanOptClazz = Class.forName('com.android.build.gradle.options.BooleanOption')
            def enableDexArchiveField = booleanOptClazz.getDeclaredField('ENABLE_DEX_ARCHIVE')
            enableDexArchiveField.setAccessible(true)
            def enableDexArchiveEnumObj = enableDexArchiveField.get(null)
            def defValField = enableDexArchiveEnumObj.getClass().getDeclaredField('defaultValue')
            defValField.setAccessible(true)
            defValField.set(enableDexArchiveEnumObj, false)
        } catch (Throwable thr) {
            // To some extends, class not found means we are in lower version of android gradle
            // plugin, so just ignore that exception.
            if (!(thr instanceof ClassNotFoundException)) {
                mProject.logger.error("reflectDexArchiveFlag error: ${thr.getMessage()}.")
            }
        }
    }

    /**
     * Specify the new apk path. If the new apk file is specified by {@code tinkerPatch.buildConfig.newApk},
     * just use it as the new apk input for tinker patch, otherwise use the assemble output.
     *
     * @param project the project which applies this plugin
     * @param configuration the tinker configuration 'tinkerPatch'
     * @param output the output of assemble result
     * @param variant the variant
     * @param tinkerPatchBuildTask the task that tinker patch uses
     */
    void setPatchNewApkPath(configuration, output, variant, tinkerPatchBuildTask) {
        def newApkPath = configuration.newApk
        if (!Utils.isNullOrNil(newApkPath)) {
            if (FileOperation.isLegalFileOrDirectory(newApkPath)) {
                tinkerPatchBuildTask.buildApkPath = newApkPath
                return
            }
        }

        tinkerPatchBuildTask.buildApkPath = output.outputFile

        tinkerPatchBuildTask.dependsOn Compatibilities.getAssembleTask(mProject, variant)
    }

    void replaceKotlinFinalField(String className, String filedName, Object instance, Object fieldValue) {
        Field field = Class.forName(className).getDeclaredField(filedName)
        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe")
        unsafeField.setAccessible(true)
        final Unsafe unsafe = (Unsafe) unsafeField.get(null)
        final long fieldOffset = unsafe.objectFieldOffset(field)
        unsafe.putObject(instance, fieldOffset, fieldValue)
    }

    File getManifestMultiDexKeepProguard(def applicationVariant) {
        File multiDexKeepProguard = null

        try {
            def file = applicationVariant.variantData.artifacts.get(
                    Class.forName('com.android.build.gradle.internal.scope.InternalArtifactType$LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES')
                            .getDeclaredField("INSTANCE")
                            .get(null)
            ).getOrNull()?.getAsFile()
            if (file != null && file.getName() != '__EMPTY_DIR__') {
                multiDexKeepProguard = file
            }
        } catch (Throwable ignore) {
            // Ignored.
        }

        if (multiDexKeepProguard == null) {
            try {
                //for kotlin
                def file = applicationVariant.getVariantData().getScope().getArtifacts().getFinalProduct(
                        Class.forName('com.android.build.gradle.internal.scope.InternalArtifactType$LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES')
                                .getDeclaredField("INSTANCE")
                                .get(null)
                ).getOrNull()?.getAsFile()
                if (file != null && file.getName() != '__EMPTY_DIR__') {
                    multiDexKeepProguard = file
                }
            } catch (Throwable ignore) {
                // Ignored.
            }
        }

        if (multiDexKeepProguard == null) {
            try {
                File file = applicationVariant.getVariantData().getScope().getArtifacts().getFinalProduct(
                        Class.forName("com.android.build.gradle.internal.scope.InternalArtifactType")
                                .getDeclaredField("LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES")
                                .get(null)
                ).getOrNull()?.getAsFile()
                if (file != null && file.getName() != '__EMPTY_DIR__') {
                    multiDexKeepProguard = file
                }
            } catch (Throwable ignore) {
                // Ignored.
            }
        }

        if (multiDexKeepProguard == null) {
            try {
                def buildableArtifact = applicationVariant.getVariantData().getScope().getArtifacts().getFinalArtifactFiles(
                        Class.forName("com.android.build.gradle.internal.scope.InternalArtifactType")
                                .getDeclaredField("LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES")
                                .get(null)
                )

                //noinspection GroovyUncheckedAssignmentOfMemberOfRawType,UnnecessaryQualifiedReference
                multiDexKeepProguard = com.google.common.collect.Iterators.getOnlyElement(buildableArtifact.iterator())
            } catch (Throwable ignore) {

            }
        }

        if (multiDexKeepProguard == null) {
            try {
                multiDexKeepProguard = applicationVariant.getVariantData().getScope().getManifestKeepListProguardFile()
            } catch (Throwable ignore) {

            }
        }

        if (multiDexKeepProguard == null) {
            try {
                multiDexKeepProguard = applicationVariant.getVariantData().getScope().getManifestKeepListFile()
            } catch (Throwable ignore) {

            }
        }

        if (multiDexKeepProguard == null) {
            mProject.logger.error("can't get multiDexKeepProguard file")
        }

        return multiDexKeepProguard
    }
}
