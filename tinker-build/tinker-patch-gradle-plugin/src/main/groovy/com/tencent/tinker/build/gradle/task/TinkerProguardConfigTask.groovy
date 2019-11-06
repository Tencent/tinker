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

import com.tencent.tinker.build.gradle.TinkerPatchPlugin
import com.tencent.tinker.build.util.FileOperation
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerProguardConfigTask extends DefaultTask {
    static final String PROGUARD_CONFIG_PATH =  TinkerPatchPlugin.TINKER_INTERMEDIATES + "tinker_proguard.pro"
    static final String PROGUARD_CONFIG_SETTINGS =
            "-keepattributes *Annotation* \n" +
                    "-dontwarn com.tencent.tinker.anno.AnnotationProcessor \n" +
                    "-keep @com.tencent.tinker.anno.DefaultLifeCycle public class *\n" +
                    "-keep public class * extends android.app.Application {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.entry.ApplicationLifeCycle {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class * implements com.tencent.tinker.entry.ApplicationLifeCycle {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.loader.TinkerLoader {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class * extends com.tencent.tinker.loader.TinkerLoader {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.loader.TinkerTestDexLoad {\n" +
                    "    *;\n" +
                    "}\n" +
                    "-keep public class com.tencent.tinker.entry.TinkerApplicationInlineFence {\n" +
                    "    *;\n" +
                    "}\n"


    def applicationVariant
    boolean shouldApplyMapping = true;


    public TinkerProguardConfigTask() {
        group = 'tinker'
    }

    @TaskAction
    def updateTinkerProguardConfig() {
        def file = project.file(PROGUARD_CONFIG_PATH)
        project.logger.error("try update tinker proguard file with ${file}")

        // Create the directory if it doesnt exist already
        file.getParentFile().mkdirs()

        // Write our recommended proguard settings to this file
        FileWriter fr = new FileWriter(file.path)

        String applyMappingFile = project.extensions.tinkerPatch.buildConfig.applyMapping

        //write applymapping
        if (shouldApplyMapping && FileOperation.isLegalFile(applyMappingFile)) {
            project.logger.error("try add applymapping ${applyMappingFile} to build the package")
            fr.write("-applymapping " + applyMappingFile)
            fr.write("\n")
        } else {
            project.logger.error("applymapping file ${applyMappingFile} is illegal, just ignore")
        }

        fr.write(PROGUARD_CONFIG_SETTINGS)

        fr.write("#your dex.loader patterns here\n")
        //they will removed when apply
        Iterable<String> loader = project.extensions.tinkerPatch.dex.loader
        for (String pattern : loader) {
            if (pattern.endsWith("*") && !pattern.endsWith("**")) {
                pattern += "*"
            }
            fr.write("-keep class " + pattern)
            fr.write("\n")
        }
        fr.close()
        // Add this proguard settings file to the list
        applicationVariant.getBuildType().buildType.proguardFiles(file)
        def files = applicationVariant.getBuildType().buildType.getProguardFiles()

        project.logger.error("now proguard files is ${files}")
    }
}