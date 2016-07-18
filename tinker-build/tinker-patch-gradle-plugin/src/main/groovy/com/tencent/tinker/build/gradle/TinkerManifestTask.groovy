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

import groovy.xml.Namespace
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author shwenzhang
 */
public class TinkerManifestTask extends DefaultTask {
    static final String TINKER_ID = "TINKER_ID"
    String manifestPath
    String tinkerValue

    TinkerManifestTask() {
        group = 'tinker'
    }

    @TaskAction
    def updateManifest() {
        // Parse the AndroidManifest.xml
        if (tinkerValue == null || tinkerValue.isEmpty()) {
            throw new GradleException('tinkerId is not set!!!')
        }
        def ns = new Namespace("http://schemas.android.com/apk/res/android", "android")
        def xml = new XmlParser().parse(manifestPath)

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
            def writer = new FileWriter(manifestPath)
            def printer = new XmlNodePrinter(new PrintWriter(writer))
            printer.preserveWhitespace = true
            printer.print(xml)
        }
    }
}

