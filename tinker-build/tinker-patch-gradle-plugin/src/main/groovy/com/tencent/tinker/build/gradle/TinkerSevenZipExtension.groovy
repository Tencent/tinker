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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

/**
 * The configuration properties.
 * you should only set one of them, if path is Specified, it will overwrite the artifact param
 * @author shwenzhang
 */
public class TinkerSevenZipExtension {
    /**
     * Specifies an artifact spec for downloading the executable from
     * repositories. spec format: '<groupId>:<artifactId>:<version>'
     */
    String zipArtifact
    /**
     * Specifies a local path.
     * if path is Specified, it will overwrite the artifact param
     * such as/usr/local/bin/7za
     * if you do not set the zipArtifact and path, We will try to use 7za directly
     */
    String path

    private Project project;

    public TinkerSevenZipExtension(Project project) {
        zipArtifact = null
        path = null
        this.project = project
    }

    void resolveZipFinalPath() {
        if (path != null)
            return

        if (this.zipArtifact != null) {
            def groupId, finalArtifact, version
            Configuration config = project.configurations.create("sevenZipToolsLocator") {
                visible = false
                transitive = false
                extendsFrom = []
            }

            (groupId, finalArtifact, version) = this.zipArtifact.split(":")
            def notation = [group     : groupId,
                            name      : finalArtifact,
                            version   : version,
                            classifier: project.osdetector.classifier,
                            ext       : 'exe']
//            println "Resolving artifact: ${notation}"
            Dependency dep = project.dependencies.add(config.name, notation)
            File file = config.fileCollection(dep).singleFile
            if (!file.canExecute() && !file.setExecutable(true)) {
                throw new GradleException("Cannot set ${file} as executable")
            }
//            println "Resolved artifact: ${file}"
            this.path = file.path
        }
        //use system 7za
        if (this.path == null) {
            this.path = "7za"
        }
    }

    @Override
    public String toString() {
        """| zipArtifact = ${zipArtifact}
           | path = ${path}
        """.stripMargin()
    }
}