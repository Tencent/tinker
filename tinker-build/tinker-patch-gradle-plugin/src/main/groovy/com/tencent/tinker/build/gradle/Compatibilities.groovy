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

    static def getAssembleTask(project, variant) {
        return project.tasks.findByName("assemble${variant.name.capitalize()}")
    }
}