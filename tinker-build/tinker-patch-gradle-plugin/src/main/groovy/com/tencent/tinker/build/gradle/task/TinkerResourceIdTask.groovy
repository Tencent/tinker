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
import groovy.io.FileType
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils

/**
 * The configuration properties.
 *
 * @author zhangshaowen
 */
public class TinkerResourceIdTask extends DefaultTask {
    static final String RESOURCE_PUBLIC_XML = TinkerPatchPlugin.TINKER_INTERMEDIATES + "public.xml"
    static final String RESOURCE_IDX_XML = TinkerPatchPlugin.TINKER_INTERMEDIATES + "idx.xml"
    static final String RESOURCE_VALUES_BACKUP = TinkerPatchPlugin.TINKER_INTERMEDIATES + "values_backup"
    static final String RESOURCE_PUBLIC_TXT = TinkerPatchPlugin.TINKER_INTERMEDIATES + "public.txt"

    String resDir
    String variantName
    String applicationId

    TinkerResourceIdTask() {
        group = 'tinker'
    }

    /**
     * get android gradle plugin version by reflect
     */
    static String getAndroidGradlePluginVersionCompat() {
        String version = null
        try {
            Class versionModel = Class.forName("com.android.builder.model.Version")
            def versionFiled = versionModel.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            versionFiled.setAccessible(true)
            version = versionFiled.get(null)
        } catch (Exception e) {

        }
        return version
    }

    /**
     * get enum obj by reflect
     */
    static <T> T resolveEnumValue(String value, Class<T> type) {
        for (T constant : type.getEnumConstants()) {
            if (constant.toString().equalsIgnoreCase(value)) {
                return constant
            }
        }
        return null
    }

    /**
     * get com.android.build.gradle.options.ProjectOptions obj by reflect
     */
    static def getProjectOptions(Project project) {
        try {
            def basePlugin = project.getPlugins().hasPlugin('com.android.application') ? project.getPlugins().findPlugin('com.android.application') : project.getPlugins().findPlugin('com.android.library')
            return Class.forName("com.android.build.gradle.BasePlugin").getMetaClass().getProperty(basePlugin, 'projectOptions')
        } catch (Exception e) {
        }
        return null
    }

    /**
     * get whether aapt2 is enabled
     */
    static boolean isAapt2EnabledCompat(Project project) {
        if (getAndroidGradlePluginVersionCompat() >= '3.3.0') {
            //when agp' version >= 3.3.0, use aapt2 default and no way to switch to aapt.
            return true
        }
        boolean aapt2Enabled = false
        try {
            def projectOptions = getProjectOptions(project)
            Object enumValue = resolveEnumValue("ENABLE_AAPT2", Class.forName("com.android.build.gradle.options.BooleanOption"))
            aapt2Enabled = projectOptions.get(enumValue)
        } catch (Exception e) {
            try {
                //retry for agp <= 2.3.3
                //when agp <= 2.3.3, the field is store in com.android.build.gradle.AndroidGradleOptions
                Class classAndroidGradleOptions = Class.forName("com.android.build.gradle.AndroidGradleOptions")
                def isAapt2Enabled = classAndroidGradleOptions.getDeclaredMethod("isAapt2Enabled", Project.class)
                isAapt2Enabled.setAccessible(true)
                aapt2Enabled = isAapt2Enabled.invoke(null, project)
            } catch (Exception e1) {
                //if we can't get it, it means aapt2 is not support current.
                aapt2Enabled = false
            }
        }
        return aapt2Enabled
    }

    /**
     * add --stable-ids param to aaptOptions's additionalParameters
     */
    List<String> addStableIdsFileToAdditionalParameters(def processAndroidResourceTask) {
        def aaptOptions = processAndroidResourceTask.getAaptOptions()
        List<String> additionalParameters = new ArrayList<>()
        List<String> originalAdditionalParameters = aaptOptions.getAdditionalParameters()
        if (originalAdditionalParameters != null) {
            additionalParameters.addAll(originalAdditionalParameters)
        }
        aaptOptions.setAdditionalParameters(additionalParameters)
        additionalParameters.add("--stable-ids")
        additionalParameters.add(project.file(RESOURCE_PUBLIC_TXT).getAbsolutePath())
        project.logger.error("tinker add additionalParameters --stable-ids ${project.file(RESOURCE_PUBLIC_TXT).getAbsolutePath()}")
        return additionalParameters
    }

    /**
     * get real name for style type resources in R.txt by values files
     */
    Map<String, String> getStyles() {
        Map<String, String> styles = new HashMap<>()
        def mergeResourcesTask = project.tasks.findByName("merge${variantName.capitalize()}Resources")
        List<File> resDirCandidateList = new ArrayList<>()
        resDirCandidateList.add(mergeResourcesTask.outputDir)
        resDirCandidateList.add(new File(mergeResourcesTask.getIncrementalFolder(), "merged.dir"))
        resDirCandidateList.each {
            it.eachFileRecurse(FileType.FILES) {
                if (it.getParentFile().getName().startsWith("values") && it.getName().startsWith("values") && it.getName().endsWith(".xml")) {
                    File destFile = new File(project.file(RESOURCE_VALUES_BACKUP), "${it.getParentFile().getName()}/${it.getName()}")
                    GFileUtils.deleteQuietly(destFile)
                    GFileUtils.mkdirs(destFile.getParentFile())
                    GFileUtils.copyFile(it, destFile)
                }
            }
        }
        project.file(RESOURCE_VALUES_BACKUP).eachFileRecurse(FileType.FILES) {
            new XmlParser().parse(it).each {
                if ("style".equalsIgnoreCase("${it.name()}")) {
                    String originalStyle = "${it.@name}".toString()
                    //replace . to _
                    String sanitizeName = originalStyle.replaceAll("[.:]", "_");
                    styles.put(sanitizeName, originalStyle)
                }
            }
        }
        return styles
    }

    /**
     * get the sorted stable id lines
     */
    ArrayList<String> getSortedStableIds(Map<RDotTxtEntry.RType, Set<RDotTxtEntry>> rTypeResourceMap) {
        List<String> sortedLines = new ArrayList<>()
        Map<String, String> styles = getStyles()
        rTypeResourceMap?.each { key, entries ->
            entries.each {
                if (it.type == RDotTxtEntry.RType.STYLEABLE) {
                    //ignore styleable type, also public.xml ignore it.
                    return
                } else if (it.type == RDotTxtEntry.RType.STYLE) {
                    //the name in R.txt for style type which has replaced . to _
                    //so we should get the original name for it
                    sortedLines.add("${applicationId}:${it.type}/${styles.get(it.name)} = ${it.idValue}")
                } else if (it.type == RDotTxtEntry.RType.DRAWABLE) {
                    //there is a special resource type for drawable which called nested resource.
                    //such as avd_hide_password and avd_show_password resource in support design sdk.
                    //the nested resource is start with $, such as $avd_hide_password__0 and $avd_hide_password__1
                    //but there is none nested resource in R.txt, so ignore it just now.
                    sortedLines.add("${applicationId}:${it.type}/${it.name} = ${it.idValue}")
                } else {
                    //other resource type which format is packageName:resType/resName = resId
                    sortedLines.add("${applicationId}:${it.type}/${it.name} = ${it.idValue}")
                }
            }
        }
        //sort it and see the diff content conveniently
        Collections.sort(sortedLines)
        return sortedLines
    }

    @TaskAction
    def applyResourceId() {
        String resourceMappingFile = project.extensions.tinkerPatch.buildConfig.applyResourceMapping

        // Parse the public.xml and ids.xml
        if (!FileOperation.isLegalFile(resourceMappingFile)) {
            project.logger.error("apply resource mapping file ${resourceMappingFile} is illegal, just ignore")
            return
        }
        project.logger.error("we build ${project.getName()} apk with apply resource mapping file ${resourceMappingFile}")
        project.extensions.tinkerPatch.buildConfig.usingResourceMapping = true
        Map<RDotTxtEntry.RType, Set<RDotTxtEntry>> rTypeResourceMap = PatchUtil.readRTxt(resourceMappingFile)


        if (!isAapt2EnabledCompat(project)) {
            String idsXml = resDir + "/values/ids.xml";
            String publicXml = resDir + "/values/public.xml";
            FileOperation.deleteFile(idsXml);
            FileOperation.deleteFile(publicXml);
            List<String> resourceDirectoryList = new ArrayList<String>()
            resourceDirectoryList.add(resDir)

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
        } else {
            File stableIdsFile = project.file(RESOURCE_PUBLIC_TXT)
            FileOperation.deleteFile(stableIdsFile);
            ArrayList<String> sortedLines = getSortedStableIds(rTypeResourceMap)

            sortedLines?.each {
                stableIdsFile.append("${it}\n")
            }

            def processResourcesTask = project.tasks.findByName("process${variantName.capitalize()}Resources")
            processResourcesTask.doFirst {
                addStableIdsFileToAdditionalParameters(processResourcesTask)
            }
        }
    }
}

