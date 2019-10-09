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
import com.tencent.tinker.commons.util.IOHelper
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
    static final String TINKER_ID = "TINKER_ID"
    static final String TINKER_ID_PREFIX = "tinker_id_"

    List<String> manifestPaths = []

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

        def agpIntermediatesDir = new File(project.buildDir, 'intermediates')
        for (String manifestPath : manifestPaths) {
            project.logger.error("tinker add ${tinkerValue} to your AndroidManifest.xml ${manifestPath}")

            writeManifestMeta(manifestPath, TINKER_ID, tinkerValue)
            addApplicationToLoaderPattern(manifestPath)
            File manifestFile = new File(manifestPath)
            if (manifestFile.exists()) {
                def manifestRelPath = agpIntermediatesDir.toPath().relativize(manifestFile.toPath()).toString()
                def manifestDestPath = new File(project.file(TinkerPatchPlugin.TINKER_INTERMEDIATES), manifestRelPath)
                FileOperation.copyFileUsingStream(manifestFile, manifestDestPath)
                project.logger.error("tinker gen AndroidManifest.xml in ${manifestDestPath}")
            }
        }
    }

    static void writeManifestMeta(String manifestPath, String name, String value) {
        def ns = new Namespace("http://schemas.android.com/apk/res/android", "android")
        def isr = null
        def pw = null
        try {
            isr = new InputStreamReader(new FileInputStream(manifestPath), "utf-8")
            def xml = new XmlParser().parse(isr)
            def application = xml.application[0]
            if (application) {
                def metaDataTags = application['meta-data']

                // remove any old TINKER_ID elements
                def tinkerId = metaDataTags.findAll {
                    it.attributes()[ns.name].equals(name)
                }.each {
                    it.parent().remove(it)
                }

                // Add the new TINKER_ID element
                application.appendNode('meta-data', [(ns.name): name, (ns.value): value])

                // Write the manifest file
                pw = new PrintWriter(manifestPath, "utf-8")
                def printer = new XmlNodePrinter(pw)
                printer.preserveWhitespace = true
                printer.print(xml)
            }
        } finally {
            IOHelper.closeQuietly(pw)
            IOHelper.closeQuietly(isr)
        }
    }

    void addApplicationToLoaderPattern(String manifestPath) {
        Iterable<String> loader = project.extensions.tinkerPatch.dex.loader
        String applicationName = readManifestApplicationName(manifestPath)

        if (applicationName != null && !loader.contains(applicationName)) {
            loader.add(applicationName)
            project.logger.error("tinker add ${applicationName} to dex loader pattern")
        }
        String loaderClass = "com.tencent.tinker.loader.*"
        if (!loader.contains(loaderClass)) {
            loader.add(loaderClass)
            project.logger.error("tinker add ${loaderClass} to dex loader pattern")
        }
    }

    static String readManifestApplicationName(String manifestPath) {
        def isr = null
        try {
            isr = new InputStreamReader(new FileInputStream(manifestPath), "utf-8")
            def xml = new XmlParser().parse(isr)
            def ns = new Namespace("http://schemas.android.com/apk/res/android", "android")

            def application = xml.application[0]
            if (application) {
                return application.attributes()[ns.name]
            } else {
                return null
            }
        } finally {
            IOHelper.closeQuietly(isr)
        }
    }
}

