import com.android.build.api.artifact.SingleArtifact
import kotlin.text.replace

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
}

description = "Tinker Android runtime."

android {
    namespace = "com.tencent.tinker"
    defaultConfig {
        buildConfigField("String", "TINKER_VERSION", "\"${version}\"")
        manifestPlaceholders["TINKER_VERSION"] = version
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    externalNativeBuild {
        cmake {
            path(
                project.layout.projectDirectory
                    .dir("src")
                    .dir("main")
                    .dir("cpp")
                    .file("CMakeLists.txt")
            )
        }
    }
    buildFeatures {
        buildConfig = true
        aidl = true
    }
    testCoverage {
        jacocoVersion = "0.8.14"
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        // Isolates each test case to separate process to avoid interference between test cases that
        // modify system state.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    lint {
        baseline = file("lint-baseline.xml")
        disable.add("LongLogTag")
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "stubs", "include" to "*.jar")))
    implementation(project(":tinker-commons"))
    implementation(project(":tinker-annotation-processor"))
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    lintChecks(project(":tinker-runtime-internal-lint"))
    androidTestImplementation(project(":tinker-runtime-internal-test-helper"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit.ktx)
    androidTestUtil(libs.androidx.orchestrator)
}


/*
    Tasks for creating test class as assets, which is used to test if dex loader can load patches
    as expected.
 */

private val testClassSourceTemplate =
    layout.projectDirectory
        .dir("src")
        .dir("main")
        .dir("java")
        .dir("com")
        .dir("tencent")
        .dir("tinker")
        .dir("internal")
        .dir("load")
        .dir("dex")
        .dir("test")
        .file("TestClass.java")

private val testClassSource =
    layout.buildDirectory
        .map { buildDirectory ->
            buildDirectory
                .dir("intermediates")
                .dir("tinker_test_class_source")
                .file("TestClass.java")
        }

private val d8 = androidComponents.sdkComponents.sdkDirectory
    .map {
        it.dir("build-tools").dir(android.buildToolsVersion).file("d8")
    }

abstract class CreateTestClassSourceTask : DefaultTask() {

    @get:InputFile
    abstract val input: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun exec() {
        input.get().asFile.readText()
            .replace("false", "true")
            .let {
                output.get().asFile
                    .apply {
                        parentFile.mkdirs()
                    }
                    .writeText(it)
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

val createTestClassSourceTask =
    tasks.register<CreateTestClassSourceTask>("createTestClassSource") {
        group = "build"
        description = "Creates source file of test class."
        input.set(testClassSourceTemplate)
        output.set(testClassSource)
    }

androidComponents.onVariants { variant ->
    val capitalizedVariantName =
        variant.name.replaceFirstChar { it.titlecase() }
    val testClassDirectory =
        layout.buildDirectory
            .map { buildDirectory ->
                buildDirectory
                    .dir("intermediates")
                    .dir("tinker_test_class")
                    .dir(variant.name)
            }
    val testDexDirectory =
        layout.buildDirectory
            .map { buildDirectory ->
                buildDirectory
                    .dir("intermediates")
                    .dir("tinker_test_dex")
                    .dir(variant.name)
            }
    val compileTestClassTask =
        tasks.register<JavaCompile>("compile${capitalizedVariantName}TestClass") {
            group = "build"
            description = "Compiles test class source file."
            source(testClassSource)
            destinationDirectory.set(testClassDirectory)
            classpath = project.files()
            dependsOn(createTestClassSourceTask)
        }
    val buildTestDexTask =
        tasks.register<BuildTestDexTask>("build${capitalizedVariantName}TestDex") {
            group = "build"
            description = "Builds test class to dex file."
            compiler.set(d8)
            minSdk.set(variant.minSdk.apiLevel)
            input.set(testClassDirectory)
            output.set(testDexDirectory)
            dependsOn(compileTestClassTask)
        }
    val renameTestDexTest =
        tasks.register<RenameTestDexTask>("rename${capitalizedVariantName}TestDex") {
            group = "build"
            description = "Renames built test dex for assets."
            input.set(testDexDirectory)
            dependsOn(buildTestDexTask)
        }
    variant.sources.assets
        ?.addGeneratedSourceDirectory(
            renameTestDexTest,
            RenameTestDexTask::output,
        )

    val createTestLibrariesTask =
        tasks.register<CreateTestLibrariesTask>("create${capitalizedVariantName}TestLibraries") {
            group = "build"
            description = "Creates test libraries for assets."
            @Suppress("UnstableApiUsage")
            input.set(variant.artifacts.get(SingleArtifact.MERGED_NATIVE_LIBS))
        }
    variant.sources.assets
        ?.addGeneratedSourceDirectory(
            createTestLibrariesTask,
            CreateTestLibrariesTask::output,
        )
}