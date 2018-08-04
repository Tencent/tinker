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

    static final String RESOURCE_VALUES = TinkerPatchPlugin.TINKER_INTERMEDIATES + "values_backup"
    static final String RESOURCE_PUBLIC_TXT = TinkerPatchPlugin.TINKER_INTERMEDIATES + "public.txt"

    String resDir

    String variantName

    String applicationId


    TinkerResourceIdTask() {
        group = 'tinker'
    }

    /**
     * 获取Android Gradle Plugin版本号
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
     * 反射获取枚举类，静态方法
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
     * 获取ProjectOptions
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
     * 判断是否启用了aapt2
     */
    static boolean isAapt2Enabled(Project project) {
        if (getAndroidGradlePluginVersionCompat() >= '3.3.0') {
            //3.3.0以上默认开启，不再有开关
            return true
        }
        boolean aapt2Enabled = false
        try {
            def projectOptions = getProjectOptions(project)
            Object enumValue = resolveEnumValue("ENABLE_AAPT2", Class.forName("com.android.build.gradle.options.BooleanOption"))
            aapt2Enabled = projectOptions.get(enumValue)
        } catch (Exception e) {
            try {
                //gradle 2.3.3的方法
                Class classAndroidGradleOptions = Class.forName("com.android.build.gradle.AndroidGradleOptions")
                def isAapt2Enabled = classAndroidGradleOptions.getDeclaredMethod("isAapt2Enabled", Project.class)
                isAapt2Enabled.setAccessible(true)
                aapt2Enabled = isAapt2Enabled.invoke(null, project)
            } catch (Exception e1) {
                //都取不到表示还不支持
                aapt2Enabled = false
            }
        }
        return aapt2Enabled
    }

    /**
     * 获取style类型资源R.txt中名称和其原始名称的映射关系
     */
    private Map<String, String> getStyles() {
        Map<String, String> styles = new HashMap<>()
        def mergeResourcesTask = project.tasks.findByName("merge${variantName}Resources")
        List<File> resDirCandidateList = new ArrayList<>()
        resDirCandidateList.add(mergeResourcesTask.outputDir)
        resDirCandidateList.add(new File(mergeResourcesTask.getIncrementalFolder(), "merged.dir"))
        resDirCandidateList.each {
            it.eachFileRecurse(FileType.FILES) {
                if (it.getParentFile().getName().startsWith("values") && it.getName().startsWith("values") && it.getName().endsWith(".xml")) {
                    File destFile = new File(project.file(RESOURCE_VALUES), "${it.getParentFile().getName()}/${it.getName()}")
                    GFileUtils.deleteQuietly(destFile)
                    GFileUtils.mkdirs(destFile.getParentFile())
                    GFileUtils.copyFile(it, destFile)
                }
            }
        }
        project.file(RESOURCE_VALUES).eachFileRecurse(FileType.FILES) {
            new XmlParser().parse(it).each {
                if ("style".equalsIgnoreCase("${it.name()}")) {
                    String originalStyle = "${it.@name}".toString()
                    //将.替换为_
                    String sanitizeName = originalStyle.replaceAll("[.:]", "_");
                    styles.put(sanitizeName, originalStyle)
                }
            }
        }
        return styles
    }


    /**
     * 将--stable-ids参数追加到additionalParameters上
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

        if (isAapt2Enabled(project)) {
            File stableIdsFile = project.file(RESOURCE_PUBLIC_TXT)
            FileOperation.deleteFile(stableIdsFile);
            List<String> sortedLines = new ArrayList<>()
            Map<String, String> styles = getStyles()
            rTypeResourceMap?.each { key, entries ->
                entries.each {
                    if (it.type == RDotTxtEntry.RType.STYLEABLE) {
                        //styleable类型资源，直接忽略，public.xml中也不存在这种类型
                        return
                    } else if (it.type == RDotTxtEntry.RType.STYLE) {
                        //style类型资源，R.txt中将实际名称中的.替换为了_，如Theme.MyTheme会变成Theme_MyTheme
                        //但是--stable-ids中，必须是原始名称，即Theme.MyTheme，因此这里需要将其还原
                        //因此这里取其原始名，值来自合并后的values.xml文件
                        sortedLines.add("${applicationId}:${it.type}/${styles.get(it.name)} = ${it.idValue}")
                    } else if (it.type == RDotTxtEntry.RType.DRAWABLE) {
                        //drawable类型资源，存在内部类型资源，如design库中的avd_hide_password和avd_show_password
                        //内部资源其名称是以$符开头，如$avd_hide_password__0, $avd_hide_password__1
                        //但是R.txt中没有此类型资源，因此这里先忽略
                        //为了方便后续扩展，这里扔到else if里单独处理，实际逻辑目前同else
                        sortedLines.add("${applicationId}:${it.type}/${it.name} = ${it.idValue}")
                    } else {
                        //其他类型资源，直接按照  packageName:resType/resName = resId 进行拼接
                        sortedLines.add("${applicationId}:${it.type}/${it.name} = ${it.idValue}")
                    }
                }
            }
            //排序的目的仅仅是为了方便查看变更内容
            Collections.sort(sortedLines)

            sortedLines.each {
                stableIdsFile.append("${it}\n")
            }

            def processResourcesTask = project.tasks.findByName("process${variantName}Resources")
            processResourcesTask.doFirst {
                addStableIdsFileToAdditionalParameters(processResourcesTask)
            }

        } else {
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
        }
    }
}

