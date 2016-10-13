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
import com.google.common.collect.Sets
import com.google.common.io.Files
import com.tencent.tinker.build.auxiliaryinject.AuxiliaryInjector
import com.tencent.tinker.build.util.MD5
import com.tencent.tinker.commons.ziputil.Streams
import groovy.io.FileType
import org.gradle.api.Project

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Transform for calling AuxiliaryInjector.
 *
 * @author tangyinsheng
 */
public class AuxiliaryInjectTransform extends Transform {
    private static final String TRANSFORM_NAME = 'AuxiliaryInject'

    private final Project project

    private AuxiliaryInjector auxiliaryInjector
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
        return Sets.immutableEnumSet(QualifiedContent.DefaultContentType.CLASSES)
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(
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
        def manifestTask = this.project.tasks.findByName("process${variantName.capitalize()}Manifest")
        this.manifestFile = manifestTask.outputs.files.files[0]

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
        if (!this.isEnabled) {
            return
        }

        String variantName = decapitalize(transformInvocation.context.path.split(getTaskNamePrefix(this))[1])
        initVariantRelatedParamsIfNeeded(variantName)

        File dirOutput = transformInvocation.outputProvider.getContentLocation(
                "classes", getOutputTypes(), getScopes(), Format.DIRECTORY)
        if (!dirOutput.exists()) {
            dirOutput.mkdirs()
        }

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

        transformInvocation.inputs.each { input ->
            input.directoryInputs.each { dirInput ->
                if (transformInvocation.incremental) {
                    dirInput.changedFiles.each { entry ->
                        File fileInput = entry.getKey()
                        File fileOutput = new File(fileInput.getAbsolutePath().replace(
                                dirInput.file.getAbsolutePath(), dirOutput.getAbsolutePath()))
                        if (!fileOutput.exists()) {
                            fileOutput.getParentFile().mkdirs()
                        }

                        Status fileStatus = entry.getValue()
                        switch(fileStatus) {
                            case Status.ADDED:
                            case Status.CHANGED:
                                // Skip application class.
                                if (dirInput.file.toPath().relativize(fileInput.toPath())
                                        .toString().replace('\\', '/').endsWith(this.appClassPathName)) {
                                    printWarnLog('Skipping Application class: %s',
                                            dirInput.file.toPath().relativize(fileInput.toPath()).toString())
                                    Files.copy(fileInput, fileOutput)
                                } else {
                                    printMsgLog('Processing %s file %s',
                                            fileStatus,
                                            dirInput.file.toPath().relativize(fileInput.toPath()).toString())
                                    AuxiliaryInjector.processClass(fileInput, fileOutput)
                                }
                                break
                            case Status.REMOVED:
                                printMsgLog('Removing %s file %s from result.', fileStatus,
                                        dirOutput.toPath().relativize(fileOutput.toPath()).toString())
                                if (fileOutput.exists()) {
                                    fileOutput.delete()
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

                        // Skip application class.
                        if (dirInput.file.toPath().relativize(fileInput.toPath())
                                .toString().replace('\\', '/').endsWith(this.appClassPathName)) {
                            printWarnLog('Skipping Application class: %s',
                                    dirInput.file.toPath().relativize(fileInput.toPath()).toString())
                            Files.copy(fileInput, fileOutput)
                        } else {
                            printMsgLog('Processing %s file %s',
                                    Status.ADDED,
                                    dirInput.file.toPath().relativize(fileInput.toPath()).toString())
                            AuxiliaryInjector.processClass(fileInput, fileOutput)
                        }
                    }
                }
            }

            List<File> jarsToMerge = new ArrayList<>()

            input.jarInputs.each { jarInput ->
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
                        printMsgLog('Processing %s file %s',
                                transformInvocation.incremental ? jarInput.status : Status.ADDED,
                                tempJarOutputDir.toPath().relativize(fileOutput.toPath()).toString())
                        AuxiliaryInjector.processJar(fileInput, fileOutput)
                        jarsToMerge.add(fileOutput)
                        break
                    case Status.REMOVED:
                        printMsgLog('Removing %s file %s from result.', fileStatus,
                                tempJarOutputDir.toPath().relativize(fileOutput.toPath()).toString())
                        if (fileOutput.exists()) {
                            fileOutput.delete()
                        }
                        break
                }
            }

            mergeJars(jarsToMerge, jarOutput)
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

