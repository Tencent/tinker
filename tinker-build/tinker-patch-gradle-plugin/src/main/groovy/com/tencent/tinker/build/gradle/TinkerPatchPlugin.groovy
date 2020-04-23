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
            mProject.logger.error("we will add TINKER_ID=${configuration.buildConfig.tinkerId} in your build output manifest file build/intermediates/manifests/full/*")
            mProject.logger.error("")
            mProject.logger.error("if minifyEnabled is true")

            String tempMappingPath = configuration.buildConfig.applyMapping

            if (FileOperation.isLegalFile(tempMappingPath)) {
                mProject.logger.error("we will build ${mProject.getName()} apk with apply mapping file ${tempMappingPath}")
            }

            mProject.logger.error("you will find the gen proguard rule file at ${TinkerProguardConfigTask.PROGUARD_CONFIG_PATH}")
            mProject.logger.error("and we will help you to put it in the proguardFiles.")
            mProject.logger.error("")
            mProject.logger.error("if multiDexEnabled is true")
            mProject.logger.error("you will find the gen multiDexKeepProguard file at ${TinkerMultidexConfigTask.MULTIDEX_CONFIG_PATH}")
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

            android.applicationVariants.all { variant ->
                def variantName = variant.name.capitalize()

                def instantRunTask = getInstantRunTask(variantName)
                if (instantRunTask != null) {
                    throw new GradleException(
                            "Tinker does not support instant run mode, please trigger build"
                                    + " by assemble${variantName} or disable instant run"
                                    + " in 'File->Settings...'."
                    )
                }

                TinkerPatchSchemaTask tinkerPatchBuildTask = mProject.tasks.create("tinkerPatch${variantName}", TinkerPatchSchemaTask)

                tinkerPatchBuildTask.signConfig = variant.signingConfig

                def agpProcessManifestTask = project.tasks.findByName("process${variantName}Manifest")
                String manifestOutputBaseDir
                try {
                    manifestOutputBaseDir = agpProcessManifestTask.manifestOutputDirectory.asFile.get()
                } catch (Throwable ignored) {
                    manifestOutputBaseDir = agpProcessManifestTask.manifestOutputDirectory
                }

                Set<String> manifestPaths = []
                variant.outputs.each { variantOutput ->
                    setPatchNewApkPath(configuration, variantOutput, variant, tinkerPatchBuildTask)
                    setPatchOutputFolder(configuration, variantOutput, variant, tinkerPatchBuildTask)

                    def manifestDir = new File(manifestOutputBaseDir, variantOutput.apkData.dirName)
                    manifestPaths.add(new File(manifestDir, 'AndroidManifest.xml'))
                }

                // Create a task to add a build TINKER_ID to AndroidManifest.xml
                // This task must be called after "process${variantName}Manifest", since it
                // requires that an AndroidManifest.xml exists in `build/intermediates`.
                TinkerManifestTask tinkerManifestTask = mProject.tasks.create("tinkerProcess${variantName}Manifest", TinkerManifestTask)
                tinkerManifestTask.manifestPaths.addAll(manifestPaths)
                tinkerManifestTask.mustRunAfter agpProcessManifestTask

                def agpProcessResourcesTask = project.tasks.findByName("process${variantName}Resources")
                agpProcessResourcesTask.dependsOn tinkerManifestTask

                String resDir = null
                try {
                    resDir = agpProcessResourcesTask.inputResourcesDir.getAsFile().get()
                } catch (Throwable ignored) {
                    try {
                        resDir = agpProcessResourcesTask.inputResourcesDir.getFiles().first()
                    } catch (Throwable ignored2) {
                        resDir = agpProcessResourcesTask.resDir
                    }
                }

                //resource id
                TinkerResourceIdTask applyResourceTask = mProject.tasks.create("tinkerProcess${variantName}ResourceId", TinkerResourceIdTask)
                applyResourceTask.applicationId = variantData.getApplicationId()
                applyResourceTask.variantName = variant.name
                applyResourceTask.resDir = resDir

                //let applyResourceTask run after manifestTask
                applyResourceTask.mustRunAfter tinkerManifestTask
                agpProcessResourcesTask.dependsOn applyResourceTask

                // Fix issue-866.
                // We found some case that applyResourceTask run after mergeResourcesTask, it caused 'applyResourceMapping' config not work.
                // The task need merged resources to calculate ids.xml, it must depends on merge resources task.
                def agpMergeResourcesTask = mProject.tasks.findByName("merge${variantName.capitalize()}Resources")
                applyResourceTask.dependsOn agpMergeResourcesTask

                if (tinkerManifestTask.manifestPaths == null
                        || tinkerManifestTask.manifestPaths.isEmpty()
                        || applyResourceTask.resDir == null) {
                    throw new RuntimeException("manifestTask.manifestPath or applyResourceTask.resDir is null.")
                }

                // Add this proguard settings file to the list
                boolean proguardEnable = variant.getBuildType().buildType.minifyEnabled

                if (proguardEnable) {
                    TinkerProguardConfigTask proguardConfigTask = mProject.tasks.create("tinkerProcess${variantName}Proguard", TinkerProguardConfigTask)
                    proguardConfigTask.applicationVariant = variant
                    proguardConfigTask.mustRunAfter tinkerManifestTask

                    def obfuscateTask = getObfuscateTask(variantName)
                    obfuscateTask.dependsOn proguardConfigTask
                }

                // Add this multidex proguard settings file to the list
                boolean multiDexEnabled = variant.mergedFlavor.multiDexEnabled

                if (multiDexEnabled) {
                    TinkerMultidexConfigTask multidexConfigTask = mProject.tasks.create("tinkerProcess${variantName}MultidexKeep", TinkerMultidexConfigTask)
                    multidexConfigTask.applicationVariant = variant
                    multidexConfigTask.multiDexKeepProguard = getManifestMultiDexKeepProguard(variant)
                    multidexConfigTask.mustRunAfter tinkerManifestTask

                    // for java.io.FileNotFoundException: app/build/intermediates/multi-dex/release/manifest_keep.txt
                    // for gradle 3.x gen manifest_keep move to processResources task
                    multidexConfigTask.mustRunAfter agpProcessResourcesTask

                    def agpMultidexTask = getMultiDexTask(variantName)
                    def agpR8Task = getR8Task(variantName)
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
                    def collectMultiDexComponentsTask = getCollectMultiDexComponentsTask(variantName)
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
        File parentFile = null

        if (variant.metaClass.hasProperty(variant, 'packageApplicationProvider')) {
            def packageAndroidArtifact = variant.packageApplicationProvider.get()
            if (packageAndroidArtifact != null) {
                try {
                    parentFile = new File(packageAndroidArtifact.outputDirectory.getAsFile().get(), output.apkData.outputFileName)
                } catch (Exception e) {
                    parentFile = new File(packageAndroidArtifact.outputDirectory, output.apkData.outputFileName)
                }
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
            if (FileOperation.isLegalFile(newApkPath)) {
                tinkerPatchBuildTask.buildApkPath = newApkPath
                return
            }
        }

        if (variant.metaClass.hasProperty(variant, 'packageApplicationProvider')) {
            def packageAndroidArtifact = variant.packageApplicationProvider.get()
            if (packageAndroidArtifact != null) {
                try {
                    tinkerPatchBuildTask.buildApkPath = new File(packageAndroidArtifact.outputDirectory.getAsFile().get(), output.apkData.outputFileName)
                } catch (Exception e) {
                    tinkerPatchBuildTask.buildApkPath = new File(packageAndroidArtifact.outputDirectory, output.apkData.outputFileName)
                }
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

    Task getMultiDexTask(String variantName) {
        String multiDexTaskName = "multiDexList${variantName}"
        String multiDexTaskTransformName = "transformClassesWithMultidexlistFor${variantName}"

        def multiDexTask = mProject.tasks.findByName(multiDexTaskName)
        if (multiDexTask == null) {
            multiDexTask = mProject.tasks.findByName(multiDexTaskTransformName)
        }

        return multiDexTask
    }

    Task getR8Task(String variantName) {
        String r8TransformTaskName = "transformClassesAndResourcesWithR8For${variantName}"
        def r8TransformTask = mProject.tasks.findByName(r8TransformTaskName)
        if (r8TransformTask != null) {
            return r8TransformTask
        }

        String r8TaskName = "minify${variantName.capitalize()}WithR8"
        def r8Task = mProject.tasks.findByName(r8TaskName)
        if (r8Task != null) {
            return r8Task
        }
    }


    @NotNull
    Task getObfuscateTask(String variantName) {
        String proguardTransformTaskName = "transformClassesAndResourcesWithProguardFor${variantName}"
        def proguardTransformTask = mProject.tasks.findByName(proguardTransformTaskName)
        if (proguardTransformTask != null) {
            return proguardTransformTask
        }

        String r8TransformTaskName = "transformClassesAndResourcesWithR8For${variantName}"
        def r8TransformTask = mProject.tasks.findByName(r8TransformTaskName)
        if (r8TransformTask != null) {
            return r8TransformTask
        }

        String r8TaskName = "minify${variantName.capitalize()}WithR8"
        def r8Task = mProject.tasks.findByName(r8TaskName)
        if (r8Task != null) {
            return r8Task
        }

        String proguardTaskName = "minify${variantName.capitalize()}WithProguard"
        def proguardTask = mProject.tasks.findByName(proguardTaskName)
        if (proguardTask != null) {
            return proguardTask
        }

        // in case that Google changes the task name in later versions
        throw new GradleException(String.format("The minifyEnabled is enabled for '%s', but " +
                "tinker cannot find the task. Please submit issue to us: %s", variantName, ISSUE_URL))
    }

    Task getInstantRunTask(String variantName) {
        String instantRunTask = "transformClassesWithInstantRunFor${variantName}"
        return mProject.tasks.findByName(instantRunTask)
    }

    Task getCollectMultiDexComponentsTask(String variantName) {
        String collectMultiDexComponents = "collect${variantName}MultiDexComponents"
        return mProject.tasks.findByName(collectMultiDexComponents)
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
