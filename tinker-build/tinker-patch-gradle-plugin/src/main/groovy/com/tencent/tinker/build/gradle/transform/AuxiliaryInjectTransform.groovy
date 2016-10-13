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

package com.tencent.tinker.build.gradle.transform

import com.android.build.api.transform.*
import com.google.common.collect.ImmutableSet
import com.google.common.io.Files
import com.tencent.tinker.build.auxiliaryclass.AuxiliaryClassGenerator
import com.tencent.tinker.build.auxiliaryclass.AuxiliaryClassInjector
import com.tencent.tinker.build.auxiliaryclass.AuxiliaryClassInjector.ProcessJarCallback
import com.tencent.tinker.build.util.MD5
import com.tencent.tinker.commons.ziputil.Streams
import groovy.io.FileType
import org.gradle.api.Project

import java.lang.reflect.Constructor
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Transform for calling AuxiliaryClassGenerator and AuxiliaryClassInjector.
 *
 * @author tangyinsheng
 */
public class AuxiliaryInjectTransform extends Transform {
    private static final String TRANSFORM_NAME = 'AuxiliaryInject'

    private final Project project
    private final String auxiliaryClassPathName

    private boolean isEnabled = false

    def applicationVariants

    /* ****** Variant related parameters start ****** */

    boolean isInitialized = false
    def manifestFile
    def appClassName
    def appClassPathName

    /* ******  Variant related parameters end  ****** */

    public AuxiliaryInjectTransform(Project project) {
        this.project = project
        this.auxiliaryClassPathName =
                AuxiliaryClassInjector.AUXILIARY_CLASSNAME.replace('.', '/') + '.class'

        project.afterEvaluate {
            this.isEnabled = project.tinkerPatch.dex.usePreGeneratedPatchDex

            this.applicationVariants = project.android.applicationVariants
        }
    }

    @Override
    String getName() {
        return TRANSFORM_NAME
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES)
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES
        )
    }

    @Override
    boolean isIncremental() {
        return true
    }

    private String getTaskNamePrefix(Transform transform) {
        StringBuilder sb = new StringBuilder(100);
        sb.append("transform");

        Iterator<QualifiedContent.ContentType> iterator = transform.getInputTypes().iterator();
        // there's always at least one
        sb.append(iterator.next().name().toLowerCase(Locale.getDefault()).capitalize());
        while (iterator.hasNext()) {
            sb.append("And").append(
                    iterator.next().name().toLowerCase(Locale.getDefault()).capitalize());
        }

        sb.append("With").append(transform.getName().capitalize()).append("For");

        return sb.toString();
    }

    private String decapitalize(String src) {
        char[] chars = src.toCharArray()
        chars[0] += (char) 32
        return new String(chars)
    }

    private void initVariantRelatedParamsIfNeeded(String variantName) {
        if (this.isInitialized) {
            return
        }

        // Get manifest file path.
        this.applicationVariants.any { variant ->
            if (variant.name.equals(variantName)) {
                def variantOutput = variant.outputs.first()
                this.manifestFile = variantOutput.processManifest.manifestOutputFile
                return true  // break out.
            }
        }

        // Get application classname from manifest file.
        def parsedManifest = new XmlParser().parse(this.manifestFile)
        def androidTag = new groovy.xml.Namespace(
                "http://schemas.android.com/apk/res/android", 'android')
        this.appClassName = parsedManifest.application[0].attribute(androidTag.name)
        this.appClassPathName = this.appClassName.replace('.', '/') + '.class'

        this.isInitialized = true
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        String variantName = decapitalize(transformInvocation.context.path.split(getTaskNamePrefix(this))[1])
        initVariantRelatedParamsIfNeeded(variantName)

        def dirInputs = new HashSet<>()
        def jarInputs = new HashSet<>()

        // Collecting inputs.
        transformInvocation.inputs.each { input ->
            input.directoryInputs.each { dirInput ->
                dirInputs.add(dirInput)
            }
            input.jarInputs.each { jarInput ->
                jarInputs.add(jarInput)
            }
        }

        if (!this.isEnabled) {
            printMsgLog("PreGeneratedPatchDex mode is disabled, skip transforming.")
        }

        // Auxiliary class may be exist if user create it manually in his project.
        boolean isAuxiliaryClassExists = false

        if (!dirInputs.isEmpty() || !jarInputs.isEmpty()) {
            File dirOutput = transformInvocation.outputProvider.getContentLocation(
                    "classes", getOutputTypes(), getScopes(), Format.DIRECTORY)
            if (!dirOutput.exists()) {
                dirOutput.mkdirs()
            }

            if (!dirInputs.isEmpty()) {
                dirInputs.each { dirInput ->
                    if (transformInvocation.incremental) {
                        dirInput.changedFiles.each { entry ->
                            File fileInput = entry.getKey()
                            File fileOutput = new File(fileInput.getAbsolutePath().replace(
                                    dirInput.file.getAbsolutePath(), dirOutput.getAbsolutePath()))
                            if (!fileOutput.exists()) {
                                fileOutput.getParentFile().mkdirs()
                            }
                            final String relativeInputClassPath =
                                    dirInput.file.toPath().relativize(fileInput.toPath())
                                            .toString().replace('\\', '/')

                            Status fileStatus = entry.getValue()
                            switch(fileStatus) {
                                case Status.ADDED:
                                case Status.CHANGED:
                                    if (fileInput.isDirectory()) {
                                        return // continue.
                                    }

                                    // If disabled, skip all classes.
                                    if (!this.isEnabled) {
                                        Files.copy(fileInput, fileOutput)
                                    } else {
                                        // Skip application class.
                                        if (relativeInputClassPath.equals(this.appClassPathName)) {
                                            printWarnLog('Skipping Application class: %s',
                                                    relativeInputClassPath)
                                            Files.copy(fileInput, fileOutput)
                                        } else
                                        // Skip and mark auxiliary class.
                                        if (relativeInputClassPath.equals(this.auxiliaryClassPathName)) {
                                            isAuxiliaryClassExists = true
                                            Files.copy(fileInput, fileOutput)
                                        } else {
                                            printMsgLog('Processing %s file %s',
                                                    fileStatus,
                                                    relativeInputClassPath)
                                            AuxiliaryClassInjector.processClass(fileInput, fileOutput)
                                        }
                                    }
                                    break
                                case Status.REMOVED:
                                    // Print log if it's enabled only.
                                    if (this.isEnabled) {
                                        printMsgLog('Removing %s file %s from result.', fileStatus,
                                                dirOutput.toPath().relativize(fileOutput.toPath()).toString())
                                    }

                                    if (fileOutput.exists()) {
                                        if (fileOutput.isDirectory()) {
                                            fileOutput.deleteDir()
                                        } else {
                                            fileOutput.delete()
                                        }
                                    }
                                    break
                            }
                        }
                    } else {
                        if (dirOutput.exists()) {
                            dirOutput.deleteDir()
                        }

                        dirInput.file.traverse(type: FileType.FILES, nameFilter: ~/.*\.class$/) { fileInput ->
                            File fileOutput = new File(fileInput.getAbsolutePath().replace(dirInput.file.getAbsolutePath(), dirOutput.getAbsolutePath()))
                            if (!fileOutput.exists()) {
                                fileOutput.getParentFile().mkdirs()
                            }
                            final String relativeInputClassPath =
                                    dirInput.file.toPath().relativize(fileInput.toPath())
                                            .toString().replace('\\', '/')

                            // If disabled, skip all classes.
                            if (!this.isEnabled) {
                                Files.copy(fileInput, fileOutput)
                            } else {
                                // Skip application class.
                                if (relativeInputClassPath.equals(this.appClassPathName)) {
                                    printWarnLog('Skipping Application class: %s',
                                            relativeInputClassPath)
                                    Files.copy(fileInput, fileOutput)
                                } else
                                // Skip and mark auxiliary class.
                                if (relativeInputClassPath.equals(this.auxiliaryClassPathName)) {
                                    isAuxiliaryClassExists = true
                                    Files.copy(fileInput, fileOutput)
                                } else {
                                    printMsgLog('Processing %s file %s',
                                            Status.ADDED,
                                            relativeInputClassPath)
                                    AuxiliaryClassInjector.processClass(fileInput, fileOutput)
                                }
                            }
                        }
                    }
                }
            }

            if (!jarInputs.isEmpty()) {
                File jarOutput = transformInvocation.outputProvider.getContentLocation(
                        "combined", getOutputTypes(), getScopes(), Format.JAR
                )
                if (!jarOutput.exists()) {
                    jarOutput.getParentFile().mkdirs()
                }

                File tempJarOutputDir = new File(transformInvocation.context.temporaryDir, "combined-jars")
                if (!tempJarOutputDir.exists()) {
                    tempJarOutputDir.mkdirs()
                }

                List<File> jarsToMerge = new ArrayList<>()

                jarInputs.each { jarInput ->
                    File fileInput = jarInput.file
                    File fileOutput = new File(tempJarOutputDir,
                            getUniqueHashName(fileInput))
                    if (!fileOutput.exists()) {
                        fileOutput.getParentFile().mkdirs()
                    }

                    switch (jarInput.status) {
                        case Status.NOTCHANGED:
                            if (transformInvocation.incremental) {
                                break
                            }
                        case Status.ADDED:
                        case Status.CHANGED:
                            // Print log if it's enabled only.
                            if (this.isEnabled) {
                                printMsgLog('Processing %s file %s',
                                        transformInvocation.incremental ? jarInput.status : Status.ADDED,
                                        tempJarOutputDir.toPath().relativize(fileOutput.toPath()).toString())
                            }

                            AuxiliaryClassInjector.processJar(fileInput, fileOutput, new ProcessJarCallback() {
                                @Override
                                boolean onProcessClassEntry(String entryName) {
                                    // If disabled, skip all classes.
                                    if (!this.isEnabled) {
                                        return false
                                    } else {
                                        // Skip application class.
                                        if (entryName.equals(AuxiliaryInjectTransform.this.appClassPathName)) {
                                            return false
                                        } else
                                        // Skip and mark auxiliary class.
                                        if (entryName.equals(AuxiliaryInjectTransform.this.auxiliaryClassPathName)) {
                                            isAuxiliaryClassExists = true
                                            return false
                                        } else {
                                            return true;
                                        }
                                    }
                                }
                            })
                            jarsToMerge.add(fileOutput)
                            break
                        case Status.REMOVED:
                            // Print log if it's enabled only.
                            if (this.isEnabled) {
                                printMsgLog('Removing %s file %s from result.', fileStatus,
                                        tempJarOutputDir.toPath().relativize(fileOutput.toPath()).toString())
                            }

                            if (fileOutput.exists()) {
                                fileOutput.delete()
                            }
                            break
                    }
                }

                mergeJars(jarsToMerge, jarOutput)
            }

            if (this.isEnabled) {
                if (!isAuxiliaryClassExists) {
                    printMsgLog('Generating auxiliary class %s.', this.auxiliaryClassPathName)
                    AuxiliaryClassGenerator.generateAuxiliaryClass(
                            dirOutput, AuxiliaryClassInjector.AUXILIARY_CLASSNAME)
                } else {
                    printWarnLog(
                            'Found auxiliary class %s in your source codes, skip generating.',
                            this.auxiliaryClassPathName
                    )
                }
            }
        }
    }

    private String getUniqueHashName(File fileInput) {
        final String fileInputName = fileInput.getName()
        if (fileInput.isDirectory()) {
            return fileInputName
        }
        final String parentDirPath = fileInput.getParentFile().getAbsolutePath()
        final String pathMD5 = MD5.getMessageDigest(parentDirPath.getBytes())
        final int extSepPos = fileInputName.lastIndexOf('.')
        final String fileInputNamePrefix =
                (extSepPos >= 0 ? fileInputName.substring(0, extSepPos) : fileInputName)
        final String fileInputNameSurfix =
                (extSepPos >= 0 ? fileInputName.substring(extSepPos) : '')
        return fileInputNamePrefix + '_' + pathMD5 + fileInputNameSurfix
    }

    private void mergeJars(Collection<File> jarsToMerge, File jarOutput) {
        if (jarsToMerge == null || jarsToMerge.size() == 0) {
            return
        }

        Set<String> addedEntries = new HashSet<>()
        ZipOutputStream zos = null
        try {
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(jarOutput)))
            jarsToMerge.each { jarInput ->
                ZipInputStream zis = null
                try {
                    zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(jarInput)))
                    ZipEntry entryIn = null
                    while ((entryIn = zis.getNextEntry()) != null) {
                        final String entryName = entryIn.getName()
                        if (!addedEntries.contains(entryName)) {
                            addedEntries.add(entryName)
                            ZipEntry entryOut = new ZipEntry(entryIn.getName())
                            zos.putNextEntry(entryOut)
                            if (!entryIn.isDirectory()) {
                                Streams.copy(zis, zos)
                            }
                            zos.closeEntry()
                        }
                    }
                } finally {
                    closeQuietly(zis)
                }
            }
        } finally {
            closeQuietly(zos)
        }
    }

    private void printMsgLog(String fmt, Object... vals) {
        final String title = TRANSFORM_NAME.capitalize()
        this.project.logger.lifecycle("[{}] {}", title,
                (vals == null || vals.length == 0 ? fmt : String.format(fmt, vals)))
    }

    private void printWarnLog(String fmt, Object... vals) {
        final String title = TRANSFORM_NAME.capitalize()
        this.project.logger.warn("[{}] {}", title,
                (vals == null || vals.length == 0 ? fmt : String.format(fmt, vals)))
    }

    private void closeQuietly(Closeable target) {
        if (target != null) {
            try {
                target.close()
            } catch (Exception e) {
                // Ignored.
            }
        }
    }
}

