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

import com.tencent.tinker.build.aapt.AaptResourceCollector
import com.tencent.tinker.build.aapt.AaptUtil
import com.tencent.tinker.build.aapt.PatchUtil
import com.tencent.tinker.build.aapt.RDotTxtEntry
import com.tencent.tinker.build.gradle.TinkerPatchPlugin
import com.tencent.tinker.build.util.FileOperation
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerResourceIdTask extends DefaultTask {
    static final String RESOURCE_PUBLIC_XML = TinkerPatchPlugin.TINKER_INTERMEDIATES + "public.xml"
    static final String RESOURCE_IDX_XML = TinkerPatchPlugin.TINKER_INTERMEDIATES + "idx.xml"

    String resDir

    TinkerResourceIdTask() {
        group = 'tinker'
    }

    @TaskAction
    def applyResourceId() {
        // Parse the public.xml and ids.xml
        String idsXml = resDir + "/values/ids.xml";
        String publicXml = resDir + "/values/public.xml";
        FileOperation.deleteFile(idsXml);
        FileOperation.deleteFile(publicXml);
        List<String> resourceDirectoryList = new ArrayList<String>()
        resourceDirectoryList.add(resDir)
        Map<RDotTxtEntry.RType, Set<RDotTxtEntry>> rTypeResourceMap = null

        String resourceMappingFile = project.extensions.tinkerPatch.buildConfig.applyResourceMapping

        if (FileOperation.isLegalFile(resourceMappingFile)) {
            project.logger.error("we build ${project.getName()} apk with apply resource mapping file ${resourceMappingFile}")
            project.extensions.tinkerPatch.buildConfig.usingResourceMapping = true
            rTypeResourceMap = PatchUtil.readRTxt(resourceMappingFile);
        } else {
            project.logger.error("apply resource mapping file ${resourceMappingFile} is illegal, just ignore")
        }
        AaptResourceCollector aaptResourceCollector = AaptUtil.collectResource(resourceDirectoryList, rTypeResourceMap)
        PatchUtil.generatePublicResourceXml(aaptResourceCollector, idsXml, publicXml)
        File publicFile = new File(publicXml)
        if (publicFile.exists()) {
            FileOperation.copyFileUsingStream(publicFile, project.file(RESOURCE_PUBLIC_XML))
            project.logger.error("tinker gen resource public.xml in ${RESOURCE_PUBLIC_XML}")
        }
        File idxFile = new File(idsXml)
        if (idxFile.exists()) {
            FileOperation.copyFileUsingStream(idxFile, project.file(RESOURCE_IDX_XML))
            project.logger.error("tinker gen resource idx.xml in ${RESOURCE_IDX_XML}")
        }
    }
}

