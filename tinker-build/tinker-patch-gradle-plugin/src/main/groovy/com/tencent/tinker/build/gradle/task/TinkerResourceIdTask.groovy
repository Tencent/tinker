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
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.regex.Matcher
import java.util.regex.Pattern

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

    //it's parent dir must start with values
    static final String RESOURCE_TO_COMPILE_PUBLIC_XML = TinkerPatchPlugin.TINKER_INTERMEDIATES + "aapt2/res/values/tinker_public.xml"

    String resDir
    String variantName
    String applicationId

    //if you need add public flag, set tinker.aapt2.public = true in gradle.properties
    boolean addPublicFlagForAapt2 = false

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
        def aaptOptions
        try {
            aaptOptions = processAndroidResourceTask.getAaptOptions()
        } catch (Exception e) {
            //agp 3.5.0+
            aaptOptions = Class.forName("com.android.build.gradle.internal.res.LinkApplicationAndroidResourcesTask").metaClass.getProperty(processAndroidResourceTask, "aaptOptions")
        }
        List<String> additionalParameters = new ArrayList<>()
        List<String> originalAdditionalParameters = aaptOptions.getAdditionalParameters()
        if (originalAdditionalParameters != null) {
            additionalParameters.addAll(originalAdditionalParameters)
        }
        replaceFinalField(aaptOptions.getClass().getName(), "additionalParameters", aaptOptions, additionalParameters)
        additionalParameters.add("--stable-ids")
        additionalParameters.add(project.file(RESOURCE_PUBLIC_TXT).getAbsolutePath())
        project.logger.error("tinker add additionalParameters --stable-ids ${project.file(RESOURCE_PUBLIC_TXT).getAbsolutePath()}")
        return additionalParameters
    }

    /**
     * replace final field
     */
    private static void replaceFinalField(String className, String fieldName, Object instance, Object fieldValue) {
        final Class targetClazz = Class.forName(className)
        Class currClazz = targetClazz
        Field field = null
        while (true) {
            try {
                field = currClazz.getDeclaredField(fieldName)
                break
            } catch (NoSuchFieldException e) {
                if (currClazz.equals(Object.class)) {
                    throw e
                } else {
                    currClazz = currClazz.getSuperclass()
                }
            }
        }
        Field modifiersField = Field.class.getDeclaredField("modifiers")
        modifiersField.setAccessible(true)
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL)
        field.setAccessible(true)
        field.set(instance, fieldValue)
    }

    /**
     * get real name for all resources in R.txt by values files
     */
    Map<String, String> getRealNameMap() {
        Map<String, String> realNameMap = new HashMap<>()
        def mergeResourcesTask = project.tasks.findByName("merge${variantName.capitalize()}Resources")
        List<File> resDirCandidateList = new ArrayList<>()
        try {
            def output = mergeResourcesTask.outputDir
            if (output instanceof File) {
                resDirCandidateList.add(output)
            } else {
                resDirCandidateList.add(output.getAsFile().get())
            }
        } catch (Exception ignore) {

        }

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
                String originalName = "${it.@name}".toString()
                //replace . to _ for all types with the same converting rule
                if (originalName.contains('.') || originalName.contains(':')) {
                    // only record names with '.' or ':', for sake of memory
                    String sanitizeName = originalName.replaceAll("[.:]", "_");
                    realNameMap.put(sanitizeName, originalName)
                }
            }
        }
        return realNameMap
    }

    /**
     * get the sorted stable id lines
     */
    ArrayList<String> getSortedStableIds(Map<RDotTxtEntry.RType, Set<RDotTxtEntry>> rTypeResourceMap) {
        List<String> sortedLines = new ArrayList<>()
        Map<String, String> realNameMap = getRealNameMap()
        rTypeResourceMap?.each { key, entries ->
            entries.each {
                //the name in R.txt which has replaced . to _
                //so we should get the original name for it
                def name = realNameMap.get(it.name) ?: it.name
                if (it.type == RDotTxtEntry.RType.STYLEABLE) {
                    //ignore styleable type, also public.xml ignore it.
                    return
                } else {
                    sortedLines.add("${applicationId}:${it.type}/${name} = ${it.idValue}")

                    //there is a special resource type for drawable which called nested resource.
                    //such as avd_hide_password and avd_show_password resource in support design sdk.
                    //the nested resource is start with $, such as $avd_hide_password__0 and $avd_hide_password__1
                    //but there is none nested resource in R.txt, so ignore it just now.
                }
            }
        }
        //sort it and see the diff content conveniently
        Collections.sort(sortedLines)
        return sortedLines
    }

    /**
     * convert public.txt to public.xml
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    void convertPublicTxtToPublicXml(File publicTxtFile, File publicXmlFile, boolean withId) {
        if (publicTxtFile == null) {
            return
        }
        GFileUtils.deleteQuietly(publicXmlFile)
        GFileUtils.mkdirs(publicXmlFile.getParentFile())
        GFileUtils.touch(publicXmlFile)

        publicXmlFile.append("<!-- AUTO-GENERATED FILE.  DO NOT MODIFY -->")
        publicXmlFile.append("\n")
        publicXmlFile.append("<resources>")
        publicXmlFile.append("\n")
        Pattern linePattern = Pattern.compile(".*?:(.*?)/(.*?)\\s+=\\s+(.*?)")

        publicTxtFile?.eachLine { def line ->
            Matcher matcher = linePattern.matcher(line)
            if (matcher.matches() && matcher.groupCount() == 3) {
                String resType = matcher.group(1)
                String resName = matcher.group(2)
                if (resName.startsWith('$')) {
                    project.logger.error("ignore convert to public res ${resName} because it's a nested resource")
                } else if (resType.equalsIgnoreCase("styleable")) {
                    project.logger.error("ignore convert to public res ${resName} because it's a styleable resource")
                } else {
                    if (withId) {
                        publicXmlFile.append("\t<public type=\"${resType}\" name=\"${resName}\" id=\"${matcher.group(3)}\" />\n")
                    } else {
                        publicXmlFile.append("\t<public type=\"${resType}\" name=\"${resName}\" />\n")
                    }
                }
            }
        }
        publicXmlFile.append("</resources>")
    }

    /**
     * compile xml file to flat file
     */
    void compileXmlForAapt2(File xmlFile) {
        if (xmlFile == null || !xmlFile.exists()) {
            return
        }

        def foundVariant = null
        project.android.applicationVariants.all { def variant ->
            if (variant.getName() == variantName) {
                foundVariant = variant
            }
        }

        if (foundVariant == null) {
            throw new GradleException("variant ${variantName} not found")
        }

        def variantData = foundVariant.getMetaClass().getProperty(foundVariant, 'variantData')
        def variantScope = variantData.getScope()
        def globalScope = variantScope.getGlobalScope()
        def androidBuilder = globalScope.getAndroidBuilder()
        def targetInfo = androidBuilder.getTargetInfo()
        def buildTools = targetInfo.getBuildTools()
        Map paths = buildTools.getMetaClass().getProperty(buildTools, "mPaths")
        String aapt2Path = paths.get(resolveEnumValue("AAPT2", Class.forName('com.android.sdklib.BuildToolInfo$PathId')))

        try {
            //may be from maven, the flat magic number don't match. so we should also use the aapt2 from maven.
            Class aapt2MavenUtilsClass = Class.forName("com.android.build.gradle.internal.res.Aapt2MavenUtils")
            def getAapt2FromMavenMethod = aapt2MavenUtilsClass.getDeclaredMethod("getAapt2FromMaven", Class.forName("com.android.build.gradle.internal.scope.GlobalScope"))
            getAapt2FromMavenMethod.setAccessible(true)
            def aapt2FromMaven = getAapt2FromMavenMethod.invoke(null, globalScope)
            //noinspection UnnecessaryQualifiedReference
            aapt2Path = aapt2FromMaven.singleFile.toPath().resolve(com.android.SdkConstants.FN_AAPT2)
        } catch (Exception e) {
            //ignore
        }

        project.logger.error("tinker get aapt2 path ${aapt2Path}")
        def mergeResourcesTask = project.tasks.findByName("merge${variantName.capitalize()}Resources")
        if (xmlFile.exists()) {
            project.exec { def execSpec ->
                execSpec.executable "${aapt2Path}"
                execSpec.args("compile")
                execSpec.args("--legacy")
                execSpec.args("-o")
                execSpec.args("${mergeResourcesTask.outputDir}")
                execSpec.args("${xmlFile}")
            }
        }
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

                if (project.hasProperty("tinker.aapt2.public")) {
                    addPublicFlagForAapt2 = project.ext["tinker.aapt2.public"]?.toString()?.toBoolean()
                }

                if (addPublicFlagForAapt2) {
                    //if we need add public flag for resource, we need to compile public.xml to .flat file
                    //it's parent dir must start with values
                    File publicXmlFile = project.file(RESOURCE_TO_COMPILE_PUBLIC_XML)
                    //convert public.txt to public.xml
                    convertPublicTxtToPublicXml(stableIdsFile, publicXmlFile, false)
                    //dest file is mergeResourceTask output dir
                    compileXmlForAapt2(publicXmlFile)
                }
            }
        }
    }
}

