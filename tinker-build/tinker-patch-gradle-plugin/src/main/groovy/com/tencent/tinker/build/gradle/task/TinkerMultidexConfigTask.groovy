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
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerMultidexConfigTask extends DefaultTask {
    static final String MULTIDEX_CONFIG_PATH = TinkerPatchPlugin.TINKER_INTERMEDIATES + "tinker_multidexkeep.pro"
    static final String MULTIDEX_CONFIG_SETTINGS =
            "-keep public class * implements com.tencent.tinker.loader.app.ApplicationLifeCycle {\n" +
                    "    *;\n" +
                    "}\n" +
                    "\n" +
                    "-keep public class * extends com.tencent.tinker.loader.TinkerLoader {\n" +
                    "    *;\n" +
                    "}\n" +
                    "\n" +
                    "-keep public class * extends android.app.Application {\n" +
                    "    *;\n" +
                    "}\n"


    def applicationVariant

    public TinkerMultidexConfigTask() {
        group = 'tinker'
    }

    @TaskAction
    def updateTinkerProguardConfig() {
        File file = project.file(MULTIDEX_CONFIG_PATH)
        project.logger.error("try update tinker multidex keep proguard file with ${file}")

        // Create the directory if it doesn't exist already
        file.getParentFile().mkdirs()

        StringBuffer lines = new StringBuffer()
        lines.append("\n")
             .append("#tinker multidex keep patterns:\n")
             .append(MULTIDEX_CONFIG_SETTINGS)
             .append("\n")
             .append("#your dex.loader patterns here\n")

        Iterable<String> loader = project.extensions.tinkerPatch.dex.loader
        for (String pattern : loader) {
            if (pattern.endsWith("*")) {
                if (!pattern.endsWith("**")) {
                    pattern += "*"
                }
            }
            lines.append("-keep class " + pattern + " {\n" +
                    "    *;\n" +
                    "}\n")
                    .append("\n")
        }

        // Write our recommended proguard settings to this file
        FileWriter fr = new FileWriter(file.path)
        try {
            for (String line : lines) {
                fr.write(line)
            }
        } finally {
            fr.close()
        }

        File multiDexKeepProguard = null
        try {
            multiDexKeepProguard = applicationVariant.getVariantData().getScope().getManifestKeepListProguardFile()
        } catch (Throwable ignore) {
            try {
                multiDexKeepProguard = applicationVariant.getVariantData().getScope().getManifestKeepListFile()
            } catch (Throwable e) {
                project.logger.error("can't find getManifestKeepListFile method, exception:${e}")
            }
        }
        if (multiDexKeepProguard == null) {
            project.logger.error("auto add multidex keep pattern fail, you can only copy ${file} to your own multiDex keep proguard file yourself.")
            return
        }
        FileWriter manifestWriter = new FileWriter(multiDexKeepProguard, true)
        try {
            for (String line : lines) {
                manifestWriter.write(line)
            }
        } finally {
            manifestWriter.close()
        }
    }


}