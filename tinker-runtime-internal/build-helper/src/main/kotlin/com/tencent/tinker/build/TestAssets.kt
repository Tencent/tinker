package com.tencent.tinker.build

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile

@Suppress("UnstableApiUsage")
internal fun Project.applyTestAssets() {
    val androidComponents =
        project.extensions.findByType(LibraryAndroidComponentsExtension::class.java)
            ?: run {
                logger.warn("wtf?")
                return
            }
    val testClassSourcesFrom =
        project.layout.projectDirectory
            .dir("src")
            .dir("main")
            .dir("java")
            .dir("com")
            .dir("tencent")
            .dir("tinker")
            .dir("internal")
            .dir("load")
            .dir("code")
            .dir("test")
    val testClassSources =
        project.layout.buildDirectory
            .map { buildDirectory ->
                buildDirectory
                    .dir("intermediates")
                    .dir("tinker_test_class_source")
            }
    val d8 = project.objects.fileProperty()

    androidComponents.finalizeDsl { android ->
        androidComponents.sdkComponents.sdkDirectory
            .map {
                it.dir("build-tools").dir(android.buildToolsVersion).file("d8")
            }
            .let(d8::set)
    }

    val createTestClassSourceTask =
        project.tasks.register("createTestClassSource", CreateTestClassSourceTask::class.java) {
            it.group = "build"
            it.description = "Creates source file of test class."
            it.from.set(testClassSourcesFrom)
            it.target.set(testClassSources)
        }

    androidComponents.onVariants { variant ->
        val testClassDirectory =
            project.layout.buildDirectory
                .map { buildDirectory ->
                    buildDirectory
                        .dir("intermediates")
                        .dir("tinker_test_class")
                        .dir(variant.name)
                }
        val testDexDirectory =
            project.layout.buildDirectory
                .map { buildDirectory ->
                    buildDirectory
                        .dir("intermediates")
                        .dir("tinker_test_dex")
                        .dir(variant.name)
                }
        val compileTestClassTask =
            project.tasks.register("compile${variant.name.capitalized}TestClass", JavaCompile::class.java) {
                it.group = "build"
                it.description = "Compiles test class source file."
                it.source(testClassSources)
                it.destinationDirectory.set(testClassDirectory)
                it.classpath = project.files()
                it.dependsOn(createTestClassSourceTask)
            }
        val buildTestDexTask =
            project.tasks.register("build${variant.name.capitalized}TestDex", BuildTestDexTask::class.java) {
                it.group = "build"
                it.description = "Builds test class to dex file."
                it.compiler.set(d8)
                it.minSdk.set(variant.minSdk.apiLevel)
                it.input.set(testClassDirectory)
                it.output.set(testDexDirectory)
                it.dependsOn(compileTestClassTask)
            }
        val renameTestDexTest =
            project.tasks.register("rename${variant.name.capitalized}TestDex", RenameTestDexTask::class.java) {
                it.group = "build"
                it.description = "Renames built test dex for assets."
                it.input.set(testDexDirectory)
                it.dependsOn(buildTestDexTask)
            }
        variant.sources.assets
            ?.addGeneratedSourceDirectory(
                renameTestDexTest,
                RenameTestDexTask::output,
            )

        val createTestLibrariesTask =
            project.tasks.register(
                "create${variant.name.capitalized}TestLibraries",
                CreateTestLibrariesTask::class.java
            ) {
                it.group = "build"
                it.description = "Creates test libraries for assets."
                it.input.set(variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS))
            }
        variant.sources.assets
            ?.addGeneratedSourceDirectory(
                createTestLibrariesTask,
                CreateTestLibrariesTask::output,
            )

        val createTestAssetsTask =
            project.tasks.register("create${variant.name.capitalized}TestAssets", CreateTestAssetsTask::class.java)
        variant.sources.assets
            ?.addGeneratedSourceDirectory(
                createTestAssetsTask,
                CreateTestAssetsTask::output,
            )
    }
}

abstract class CreateTestClassSourceTask : DefaultTask() {

    companion object {
        private val testClassPattern = "Test.*Class".toRegex()
    }

    @get:InputDirectory
    abstract val from: DirectoryProperty

    private val inputBase by lazy {
        from.get().asFile
    }

    @get:OutputDirectory
    abstract val target: DirectoryProperty

    private val outputBase by lazy {
        target.get().asFile
    }

    @TaskAction
    fun exec() {
        inputBase.walk()
            .filter { it.isFile && (it.extension == "java" || it.extension == "java-template") }
            .forEach { input ->
                val content = input.readText()
                    .let {
                        if (input.nameWithoutExtension.matches(testClassPattern)) {
                            it.replace("false", "true")
                        } else {
                            it
                        }
                    }
                val output = outputBase.resolve(input.parentFile.relativeTo(inputBase))
                    .apply {
                        mkdirs()
                    }
                    .resolve("${input.nameWithoutExtension}.java")
                output.writeText(content)
            }
    }
}

abstract class BuildTestDexTask : Exec() {

    @get:InputFile
    abstract val compiler: RegularFileProperty

    @get:Input
    abstract val minSdk: Property<Int>

    @get:InputDirectory
    abstract val input: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    override fun exec() {
        buildList {
            compiler.get()
                .asFile
                .absolutePath
                .let(::add)
            "--min-api"
                .let(::add)
            minSdk.get()
                .toString()
                .let(::add)
            input.get()
                .asFile
                .walk()
                .filter {
                    it.isFile && it.extension == "class"
                }
                .forEach {
                    it.absolutePath.let(::add)
                }
            "--output"
                .let(::add)
            output.get()
                .asFile
                .apply {
                    mkdirs()
                }
                .absolutePath
                .let(::add)
        }.run { commandLine(*toTypedArray()) }
        super.exec()
    }
}

abstract class RenameTestDexTask : DefaultTask() {
    @get:InputDirectory
    abstract val input: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun exec() {
        val source = input.get()
            .asFile
            .resolve("classes.dex")
        val target = output.get()
            .asFile
            .resolve("tinker")
            .apply {
                mkdirs()
            }
            .resolve("test.dex")
        source.copyTo(target, overwrite = true)
    }
}

abstract class CreateTestLibrariesTask : DefaultTask() {

    companion object {
        private val originalArray = "<_B_>".toByteArray(Charsets.US_ASCII)
        private val updatedArray = "<_P_>".toByteArray(Charsets.US_ASCII)
    }

    private fun ByteArray.replace(original: ByteArray, updated: ByteArray) {
        require(original.size == updated.size) {
            "original and updated must have the same size"
        }
        require(original.size < this.size) {
            "original size must be smaller than self size"
        }
        var index = 0
        val endIndex = this.size - original.size
        while (index < endIndex) {
            val match = originalArray.indices.all { originalIndex ->
                this[index + originalIndex] == originalArray[originalIndex]
            }
            if (match) {
                updatedArray.copyInto(this, index)
                index += updated.size
            } else {
                index++
            }
        }
    }

    @get:InputDirectory
    abstract val input: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun exec() {
        val sourceDir = input.get().asFile
        val targetDir = output.get().asFile
            .resolve("tinker")
        sourceDir.walk()
            .filter { it.name == "libtinker.test.jni.so" || it.name == "libtinker.test.dep.so" }
            .forEach { source ->
                val target = targetDir.resolve(source.relativeTo(sourceDir))
                val content = source.readBytes().apply {
                    replace(originalArray, updatedArray)
                }
                target
                    .apply {
                        parentFile.mkdirs()
                    }
                    .writeBytes(content)
            }
    }
}

abstract class CreateTestAssetsTask : DefaultTask() {
    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun exec() {
        output.get().asFile
            .resolve("tinker")
            .apply {
                mkdirs()
                resolve("test_added_asset.txt").writeText("patched")
                resolve("test_modified_asset.txt").writeText("patched")
            }
    }
}