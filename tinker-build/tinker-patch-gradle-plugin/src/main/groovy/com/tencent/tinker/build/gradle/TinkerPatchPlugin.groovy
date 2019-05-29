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

import com.android.build.api.transform.Transform
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
import org.jetbrains.annotations.NotNull

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Registers the plugin's tasks.
 *
 * @author zhangshaowen
 */

class TinkerPatchPlugin implements Plugin<Project> {
    public static final String TINKER_INTERMEDIATES = "build/intermediates/tinker_intermediates/"
    public static final String ISSUE_URL = "https://github.com/Tencent/tinker/issues"

    @Override
    public void apply(Project project) {
        //osdetector change its plugin name in 1.4.0
        try {
            project.apply plugin: 'osdetector'
        } catch (Throwable e) {
            project.apply plugin: 'com.google.osdetector'
        }

        project.extensions.create('tinkerPatch', TinkerPatchExtension)

        project.tinkerPatch.extensions.create('buildConfig', TinkerBuildConfigExtension, project)

        project.tinkerPatch.extensions.create('dex', TinkerDexExtension, project)
        project.tinkerPatch.extensions.create('lib', TinkerLibExtension)
        project.tinkerPatch.extensions.create('res', TinkerResourceExtension)
        project.tinkerPatch.extensions.create('packageConfig', TinkerPackageConfigExtension, project)
        project.tinkerPatch.extensions.create('sevenZip', TinkerSevenZipExtension, project)

        if (!project.plugins.hasPlugin('com.android.application')) {
            throw new GradleException('generateTinkerApk: Android Application plugin required')
        }

        def android = project.extensions.android

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

        project.afterEvaluate {
            def configuration = project.tinkerPatch

            if (!configuration.tinkerEnable) {
                project.logger.error("tinker tasks are disabled.")
                return
            }

            project.logger.error("----------------------tinker build warning ------------------------------------")
            project.logger.error("tinker auto operation: ")
            project.logger.error("excluding annotation processor and source template from app packaging. Enable dx jumboMode to reduce package size.")
            project.logger.error("enable dx jumboMode to reduce package size.")
            project.logger.error("disable preDexLibraries to prevent ClassDefNotFoundException when your app is booting.")
            project.logger.error("disable archive dex mode so far for keeping dex apply.")
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
            project.logger.error("and we will help you to put it in the MultiDexKeepProguardFile.")
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
                def variantData = variant.variantData

                def instantRunTask = getInstantRunTask(project, variantName)
                if (instantRunTask != null) {
                    throw new GradleException(
                            "Tinker does not support instant run mode, please trigger build"
                                    + " by assemble${variantName} or disable instant run"
                                    + " in 'File->Settings...'."
                    )
                }

                TinkerPatchSchemaTask tinkerPatchBuildTask = project.tasks.create("tinkerPatch${variantName}", TinkerPatchSchemaTask)

                tinkerPatchBuildTask.signConfig = variantData.variantConfiguration.signingConfig

                variant.outputs.each { output ->
                    setPatchNewApkPath(configuration, output, variant, tinkerPatchBuildTask)
                    setPatchOutputFolder(configuration, output, variant, tinkerPatchBuildTask)
                }

                // Create a task to add a build TINKER_ID to AndroidManifest.xml
                // This task must be called after "process${variantName}Manifest", since it
                // requires that an AndroidManifest.xml exists in `build/intermediates`.
                TinkerManifestTask manifestTask = project.tasks.create("tinkerProcess${variantName}Manifest", TinkerManifestTask)


                if (variantOutput.metaClass.hasProperty(variantOutput, 'processResourcesProvider')) {
                    manifestTask.manifestPath = variantOutput.processResourcesProvider.get().manifestFile
                } else if (variantOutput.processManifest.properties['manifestOutputFile'] != null) {
                    manifestTask.manifestPath = variantOutput.processManifest.manifestOutputFile
                } else if (variantOutput.processResources.properties['manifestFile'] != null) {
                    manifestTask.manifestPath = variantOutput.processResources.manifestFile
                }


                if (variantOutput.metaClass.hasProperty(variantOutput, 'processManifestProvider')) {
                    manifestTask.mustRunAfter variantOutput.processManifestProvider.get()
                } else {
                    manifestTask.mustRunAfter variantOutput.processManifest
                }


                if (variantOutput.metaClass.hasProperty(variantOutput, 'processResourcesProvider')) {
                    variantOutput.processResourcesProvider.get().dependsOn manifestTask
                } else {
                    variantOutput.processResources.dependsOn manifestTask
                }

                //resource id
                TinkerResourceIdTask applyResourceTask = project.tasks.create("tinkerProcess${variantName}ResourceId", TinkerResourceIdTask)
                applyResourceTask.applicationId = variantData.getApplicationId()
                applyResourceTask.variantName = variant.name


                if (variantOutput.metaClass.hasProperty(variantOutput, 'processResourcesProvider')) {
                    applyResourceTask.resDir = variantOutput.processResourcesProvider.get().inputResourcesDir.getFiles().first()
                } else if (variantOutput.processResources.properties['resDir'] != null) {
                    applyResourceTask.resDir = variantOutput.processResources.resDir
                } else if (variantOutput.processResources.properties['inputResourcesDir'] != null) {
                    applyResourceTask.resDir = variantOutput.processResources.inputResourcesDir.getFiles().first()
                }

                //let applyResourceTask run after manifestTask
                applyResourceTask.mustRunAfter manifestTask

                if (variantOutput.metaClass.hasProperty(variantOutput, 'processResourcesProvider')) {
                    variantOutput.processResourcesProvider.get().dependsOn applyResourceTask
                } else {
                    variantOutput.processResources.dependsOn applyResourceTask
                }

                // Fix issue-866.
                // We found some case that applyResourceTask run after mergeResourcesTask, it caused 'applyResourceMapping' config not work.
                // The task need merged resources to calculate ids.xml, it must depends on merge resources task.
                def mergeResourcesTask = project.tasks.findByName("merge${variantName.capitalize()}Resources")
                applyResourceTask.dependsOn mergeResourcesTask

                if (manifestTask.manifestPath == null || applyResourceTask.resDir == null) {
                    throw new RuntimeException("manifestTask.manifestPath or applyResourceTask.resDir is null.")
                }

                // Add this proguard settings file to the list
                boolean proguardEnable = variant.getBuildType().buildType.minifyEnabled

                if (proguardEnable) {
                    TinkerProguardConfigTask proguardConfigTask = project.tasks.create("tinkerProcess${variantName}Proguard", TinkerProguardConfigTask)
                    proguardConfigTask.applicationVariant = variant
                    proguardConfigTask.mustRunAfter manifestTask

                    def obfuscateTask = getObfuscateTask(project, variantName)
                    obfuscateTask.dependsOn proguardConfigTask
                }

                // Add this multidex proguard settings file to the list
                boolean multiDexEnabled = variantData.variantConfiguration.isMultiDexEnabled()

                if (multiDexEnabled) {
                    TinkerMultidexConfigTask multidexConfigTask = project.tasks.create("tinkerProcess${variantName}MultidexKeep", TinkerMultidexConfigTask)
                    multidexConfigTask.applicationVariant = variant
                    multidexConfigTask.multiDexKeepProguard = getManifestMultiDexKeepProguard(variant)
                    multidexConfigTask.mustRunAfter manifestTask

                    // for java.io.FileNotFoundException: app/build/intermediates/multi-dex/release/manifest_keep.txt
                    // for gradle 3.x gen manifest_keep move to processResources task

                    if (variantOutput.metaClass.hasProperty(variantOutput, 'processResourcesProvider')) {
                        multidexConfigTask.mustRunAfter variantOutput.processResourcesProvider.get()
                    } else {
                        multidexConfigTask.mustRunAfter variantOutput.processResources
                    }


                    def multidexTask = getMultiDexTask(project, variantName)
                    def r8Task = getR8Task(project, variantName)
                    if (multidexTask != null) {
                        multidexTask.dependsOn multidexConfigTask
                    } else if (multidexTask == null && r8Task != null) {
                        r8Task.dependsOn multidexConfigTask
                        Transform r8Transform = r8Task.getTransform()
                        //R8 maybe forget to add multidex keep proguard file in agp 3.4.0, it's a agp bug!
                        //If we don't do it, some classes will not keep in maindex such as loader's classes.
                        //So tinker will not remove loader's classes, it will crashed in dalvik and will check TinkerTestDexLoad.isPatch failed in art.
                        if (r8Transform.metaClass.hasProperty(r8Transform, "mainDexRulesFiles")) {
                            File manifestMultiDexKeepProguard = getManifestMultiDexKeepProguard(variant)
                            if (manifestMultiDexKeepProguard != null) {
                                //see difference between mainDexRulesFiles and mainDexListFiles in https://developer.android.com/studio/build/multidex?hl=zh-cn
                                FileCollection originalFiles = r8Transform.metaClass.getProperty(r8Transform, 'mainDexRulesFiles')
                                if (!originalFiles.contains(manifestMultiDexKeepProguard)) {
                                    FileCollection replacedFiles = project.files(originalFiles, manifestMultiDexKeepProguard)
                                    project.logger.error("R8Transform original mainDexRulesFiles: ${originalFiles.files}")
                                    project.logger.error("R8Transform replaced mainDexRulesFiles: ${replacedFiles.files}")
                                    //it's final, use reflect to replace it.
                                    replaceKotlinFinalField("com.android.build.gradle.internal.transforms.R8Transform", "mainDexRulesFiles", r8Transform, replacedFiles)
                                }
                            }
                        }
                    }
                    def collectMultiDexComponentsTask = getCollectMultiDexComponentsTask(project, variantName)
                    if (collectMultiDexComponentsTask != null) {
                        multidexConfigTask.mustRunAfter collectMultiDexComponentsTask
                    }
                }

                if (configuration.buildConfig.keepDexApply
                        && FileOperation.isLegalFile(project.tinkerPatch.oldApk)) {
                    com.tencent.tinker.build.gradle.transform.ImmutableDexTransform.inject(project, variant)
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
        File parentFile = null

        if (variant.metaClass.hasProperty(variant, 'packageApplicationProvider')) {
            def packageAndroidArtifact = variant.packageApplicationProvider.get()
            if (packageAndroidArtifact != null) {
                parentFile = new File(packageAndroidArtifact.outputDirectory, output.apkData.outputFileName)
            } else {
                parentFile = output.mainOutputFile.outputFile
            }
        } else {
            parentFile = output.outputFile
        }


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
                project.logger.error("reflectDexArchiveFlag error: ${thr.getMessage()}.")
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
            if (FileOperation.isLegalFile(newApkPath)) {
                tinkerPatchBuildTask.buildApkPath = newApkPath
                return
            }
        }

        if (variant.metaClass.hasProperty(variant, 'packageApplicationProvider')) {
            def packageAndroidArtifact = variant.packageApplicationProvider.get()
            if (packageAndroidArtifact != null) {
                tinkerPatchBuildTask.buildApkPath = new File(packageAndroidArtifact.outputDirectory, output.apkData.outputFileName)
            } else {
                tinkerPatchBuildTask.buildApkPath = output.mainOutputFile.outputFile
            }
        } else {
            tinkerPatchBuildTask.buildApkPath = output.outputFile
        }

        if (variant.metaClass.hasProperty(variant, 'assembleProvider')) {
            tinkerPatchBuildTask.dependsOn variant.assembleProvider.get()
        } else {
            tinkerPatchBuildTask.dependsOn variant.assemble
        }


    }

    Task getMultiDexTask(Project project, String variantName) {
        String multiDexTaskName = "transformClassesWithMultidexlistFor${variantName}"
        return project.tasks.findByName(multiDexTaskName)
    }

    Task getR8Task(Project project, String variantName) {
        String r8TaskName = "transformClassesAndResourcesWithR8For${variantName}"
        return project.tasks.findByName(r8TaskName)
    }

    @NotNull
    Task getObfuscateTask(Project project, String variantName) {
        String proguardTaskName = "transformClassesAndResourcesWithProguardFor${variantName}"
        def proguard = project.tasks.findByName(proguardTaskName)
        if (proguard != null) {
            return proguard
        }

        String r8TaskName = "transformClassesAndResourcesWithR8For${variantName}"
        def r8 = project.tasks.findByName(r8TaskName)
        if (r8 != null) {
            return r8
        }

        // in case that Google changes the task name in later versions
        throw new GradleException(String.format("The minifyEnabled is enabled for '%s', but " +
                "tinker cannot find the task, we have try '%s' and '%s'.\n" +
                "Please submit issue to us: %s", variant,
                proguardTaskName, r8TaskName, ISSUE_URL))
    }

    Task getInstantRunTask(Project project, String variantName) {
        String instantRunTask = "transformClassesWithInstantRunFor${variantName}"
        return project.tasks.findByName(instantRunTask)
    }

    Task getCollectMultiDexComponentsTask(Project project, String variantName) {
        String collectMultiDexComponents = "collect${variantName}MultiDexComponents"
        return project.tasks.findByName(collectMultiDexComponents)
    }

    void replaceKotlinFinalField(String className, String filedName, Object instance, Object fieldValue) {
        Field field = Class.forName(className).getDeclaredField(filedName)
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.setAccessible(true)
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
        field.setAccessible(true)
        field.set(instance, fieldValue)
    }

    File getManifestMultiDexKeepProguard(def applicationVariant) {
        File multiDexKeepProguard = null
        try {
            multiDexKeepProguard = applicationVariant.getVariantData().getScope().getManifestKeepListProguardFile()
        } catch (Throwable ignore) {
            try {
                def buildableArtifact = applicationVariant.getVariantData().getScope().getArtifacts().getFinalArtifactFiles(
                        Class.forName("com.android.build.gradle.internal.scope.InternalArtifactType")
                                .getDeclaredField("LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES")
                                .get(null)
                )

                //noinspection GroovyUncheckedAssignmentOfMemberOfRawType,UnnecessaryQualifiedReference
                multiDexKeepProguard = com.google.common.collect.Iterators.getOnlyElement(buildableArtifact.iterator())
            } catch (Throwable e) {

            }
            if (multiDexKeepProguard == null) {
                try {
                    multiDexKeepProguard = applicationVariant.getVariantData().getScope().getManifestKeepListFile()
                } catch (Throwable e) {
                    project.logger.error("can't find getManifestKeepListFile method, exception:${e}")
                }
            }
        }
        return multiDexKeepProguard
    }

}
