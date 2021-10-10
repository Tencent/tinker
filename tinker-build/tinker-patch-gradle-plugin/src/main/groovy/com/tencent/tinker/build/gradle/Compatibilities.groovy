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

import org.gradle.api.GradleException
import org.gradle.api.Task
import org.jetbrains.annotations.NotNull

import java.lang.reflect.Field

/**
 * For AGP Compatibilities
 *
 * @author tangyinsheng
 */

class Compatibilities {
    static def getApplicationId(project, variant) {
        return variant.getApplicationId()
    }

    static def getOutputManifestPath(project, manifestTask, variantOutput) {
        try {
            return new File(manifestTask.multiApkManifestOutputDirectory.get().asFile, "${variantOutput.dirName}/AndroidManifest.xml")
        } catch (Throwable ignored) {
            // Ignored.
        }
        try {
            return new File(manifestTask.manifestOutputDirectory.get().asFile, "${variantOutput.dirName}/AndroidManifest.xml")
        } catch (Throwable ignored) {
            // Ignored.
        }
        try {
            return new File(manifestTask.manifestOutputDirectory, "${variantOutput.dirName}/AndroidManifest.xml")
        } catch (Throwable ignored) {
            // Ignored
        }
        return manifestTask.manifestOutputFile
    }

    static def getInputResourcesDirectory(project, resourcesTask) {
        try {
            return resourcesTask.inputResourcesDir.getAsFile().get()
        } catch (Throwable ignored) {
            // Ignored.
        }
        try {
            return resourcesTask.inputResourcesDir.getFiles().first()
        } catch (Throwable ignored) {
            // Ignored.
        }
        return resourcesTask.resDir
    }

    static def getProcessManifestTask(project, variant) {
        return project.tasks.findByName("process${variant.name.capitalize()}Manifest")
    }

    static def getMergeResourcesTask(project, variant) {
        return project.tasks.findByName("merge${variant.name.capitalize()}Resources")
    }

    static def getProcessResourcesTask(project, variant) {
        return project.tasks.findByName("process${variant.name.capitalize()}Resources")
    }

    static def getAssembleTask(project, variant) {
        return project.tasks.findByName("assemble${variant.name.capitalize()}")
    }

    static def getMultiDexTask(project, variant) {
        def capitalizedVariantName = variant.name.capitalize()
        def multiDexTask = project.tasks.findByName("multiDexList${capitalizedVariantName}")
        if (multiDexTask != null) {
            return multiDexTask
        }
        return project.tasks.findByName("transformClassesWithMultidexlistFor${capitalizedVariantName}")
    }

    static def getR8Task(project, variant) {
        def capitalizedVariantName = variant.name.capitalize()
        def r8TransformTask = project.tasks.findByName("transformClassesAndResourcesWithR8For${capitalizedVariantName}")
        if (r8TransformTask != null) {
            return r8TransformTask
        }
        return project.tasks.findByName("minify${capitalizedVariantName}WithR8")
    }

    static def getObfuscateTask(project, variant) {
        def capitalizedVariantName = variant.name.capitalize()
        def proguardTransformTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${capitalizedVariantName}")
        if (proguardTransformTask != null) {
            return proguardTransformTask
        }

        def r8TransformTask = project.tasks.findByName("transformClassesAndResourcesWithR8For${capitalizedVariantName}")
        if (r8TransformTask != null) {
            return r8TransformTask
        }

        def r8Task = project.tasks.findByName("minify${capitalizedVariantName}WithR8")
        if (r8Task != null) {
            return r8Task
        }

        def proguardTask = project.tasks.findByName("minify${capitalizedVariantName}WithProguard")
        if (proguardTask != null) {
            return proguardTask
        }

        // in case that Google changes the task name in later versions
        throw new GradleException(String.format("The minifyEnabled is enabled for '%s', but " +
                "tinker cannot find the task. Please submit issue to us: %s", variant.name, TinkerPatchPlugin.ISSUE_URL))
    }

    static def getInstantRunTask(project, variant) {
        return project.tasks.findByName("transformClassesWithInstantRunFor${variant.name.capitalize()}")
    }

    static def getCollectMultiDexComponentsTask(project, variant) {
        return project.tasks.findByName("collect${variant.name.capitalize()}MultiDexComponents")
    }

    static def getValueOfFieldRecursively(owner, name) {
        def field = getFieldRecursively(owner.getClass(), name)
        return field.get(owner)
    }

    static def getValueOfStaticFieldRecursively(ownerClazz, name) {
        def field = getFieldRecursively(ownerClazz, name)
        return field.get(null)
    }

    private static def getFieldRecursively(ownerClazz, name) {
        def currClazz = ownerClazz as Class<?>
        while (true) {
            try {
                def field = currClazz.getDeclaredField(name)
                if (!field.isAccessible()) {
                    field.setAccessible(true)
                }
                return field
            } catch (NoSuchFieldException e) {
                if (currClazz != Object.class) {
                    currClazz = currClazz.getSuperclass()
                } else {
                    throw new NoSuchFieldException("Cannot find field ${name} in ${ownerClazz.getName()} and its super classes.")
                }
            }
        }
        // Should not be here.
    }
}