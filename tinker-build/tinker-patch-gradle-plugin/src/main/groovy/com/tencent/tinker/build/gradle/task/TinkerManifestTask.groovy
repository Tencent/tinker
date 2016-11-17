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
import groovy.xml.Namespace
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerManifestTask extends DefaultTask {
    static final String MANIFEST_XML = TinkerPatchPlugin.TINKER_INTERMEDIATES + "AndroidManifest.xml"
    static final String TINKER_ID = "TINKER_ID"
    static final String TINKER_ID_PREFIX = "tinker_id_"

    String manifestPath
    TinkerManifestTask() {
        group = 'tinker'
    }

    @TaskAction
    def updateManifest() {
        // Parse the AndroidManifest.xml
        String tinkerValue = project.extensions.tinkerPatch.buildConfig.tinkerId
        if (tinkerValue == null || tinkerValue.isEmpty()) {
            throw new GradleException('tinkerId is not set!!!')
        }

        tinkerValue = TINKER_ID_PREFIX + tinkerValue

        project.logger.error("tinker add ${tinkerValue} to your AndroidManifest.xml ${manifestPath}")

        def ns = new Namespace("http://schemas.android.com/apk/res/android", "android")

        def xml = new XmlParser().parse(new InputStreamReader(new FileInputStream(manifestPath), "utf-8"))

        def application = xml.application[0]
        if (application) {
            def metaDataTags = application['meta-data']

            // remove any old TINKER_ID elements
            def tinkerId = metaDataTags.findAll {
                it.attributes()[ns.name].equals(TINKER_ID)
            }.each {
                it.parent().remove(it)
            }

            // Add the new TINKER_ID element
            application.appendNode('meta-data', [(ns.name): TINKER_ID, (ns.value): tinkerValue])

            // Write the manifest file
            def printer = new XmlNodePrinter(new PrintWriter(manifestPath, "utf-8"))
            printer.preserveWhitespace = true
            printer.print(xml)
        }
        File manifestFile = new File(manifestPath)
        if (manifestFile.exists()) {
            FileOperation.copyFileUsingStream(manifestFile, project.file(MANIFEST_XML))
            project.logger.error("tinker gen AndroidManifest.xml in ${MANIFEST_XML}")
        }

    }
}

