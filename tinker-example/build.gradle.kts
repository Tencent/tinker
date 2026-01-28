import com.android.build.api.artifact.SingleArtifact
import com.tencent.tinker.patch.CliMain
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.tinker.build.config)
}

buildscript {
    dependencies {
        classpath(libs.tinker.cli)
    }
}

android {
    namespace = "com.tencent.tinker.example"
    defaultConfig {
        applicationId = "com.tencent.tinker.example"
        versionCode = 1
        versionName = "1.0"
        /**
         * Since we use composite build for using Tinker runtime, we need to specify using its production variant.
         *
         * If you are using Tinker runtime by prebuilt AAR from Maven, this configuration is unneeded.
         */
        missingDimensionStrategy("tinkerType", "production")
    }
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "tinker-example"
            keyAlias = "tinker-example"
            keyPassword = "tinker-example"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    flavorDimensions.add("type")
    productFlavors {
        create("original") {
            dimension = "type"
            isDefault = true
            @Suppress("UnstableApiUsage")
            externalNativeBuild.cmake.arguments.add("-DUPDATED=OFF")
        }
        create("updated") {
            dimension = "type"
            @Suppress("UnstableApiUsage")
            externalNativeBuild.cmake.arguments.add("-DUPDATED=ON")
        }
    }
}

dependencies {
    implementation(libs.tinker.runtime)
}

private object Helper {
    fun apkInAndroidGradlePluginOutputDirectory(directory: File): File? {
        val metadataFile = directory.resolve("output-metadata.json")
        val metadata = JsonSlurper().parse(metadataFile) as? Map<*, *>
            ?: throw GradleException("Cannot parse metadata from \"${metadataFile.absolutePath}\".")
        val elements = metadata["elements"] as? List<*>
            ?: throw GradleException("Cannot parse \"elements\" from \"${metadataFile.absolutePath}\".")
        return elements.asSequence()
            .mapIndexed { index, element ->
                val converted = element as? Map<*, *>
                    ?: throw GradleException("Cannot parse \"elements[${index}]\" from \"${metadataFile.absolutePath}\".")
                val filters = converted["filters"] as? List<*>
                    ?: throw GradleException("Cannot parse \"elements[${index}].filters\" from \"${metadataFile.absolutePath}\".")
                val outputFile = converted["outputFile"] as? String
                    ?: throw GradleException("Cannot parse \"elements[${index}].outputFile\" from \"${metadataFile.absolutePath}\".")
                outputFile to filters
            }
            .firstOrNull { it.second.isEmpty() }
            ?.first
            ?.let(directory::resolve)
    }
}

abstract class DiffPackageGenerateTask : DefaultTask() {

    @get:InputDirectory
    abstract val originalApkDirectory: DirectoryProperty

    @get:InputDirectory
    abstract val updatedApkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    private val originalApk by lazy {
        originalApkDirectory.get().asFile.let(Helper::apkInAndroidGradlePluginOutputDirectory)
            ?: throw GradleException("Cannot find original APK.")
    }

    private val updatedApk by lazy {
        updatedApkDirectory.get().asFile.let(Helper::apkInAndroidGradlePluginOutputDirectory)
            ?: throw GradleException("Cannot find updated APK.")
    }

    @TaskAction
    fun exec() {
        arrayOf(
            "-old",
            originalApk.absolutePath,
            "-new",
            updatedApk.absolutePath,
            "-config",
            project.layout.projectDirectory.file("tinker-config.xml").asFile.absolutePath,
            "-out",
            outputDirectory.get().asFile.absolutePath,
        ).let(CliMain::main)
    }
}

abstract class ExampleDeployTask : DefaultTask() {

    @get:InputFile
    abstract val adb: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val serial: Property<String>

    @get:Input
    abstract val applicationId: Property<String>

    @get:InputDirectory
    abstract val originalApkDirectory: DirectoryProperty

    @get:InputFile
    abstract val diffPackageFile: RegularFileProperty

    private val originalApk by lazy {
        originalApkDirectory.get().asFile.let(Helper::apkInAndroidGradlePluginOutputDirectory)
            ?: throw GradleException("Cannot find original APK.")
    }

    private fun run(vararg args: String) {
        buildList {
            add(adb.get().asFile.absolutePath)
            serial.orNull?.let {
                add("-s")
                add(it)
            }
            addAll(args)
        }.toTypedArray().let { Runtime.getRuntime().exec(it).waitFor() }
    }

    @TaskAction
    fun exec() {
        run(
            "uninstall",
            applicationId.get(),
        )
        run(
            "install",
            originalApk.absolutePath,
        )
        run(
            "push",
            diffPackageFile.get().asFile.absolutePath,
            "/data/local/tmp/tinker-example-diff.apk",
        )
    }
}

private val String.capitalized: String
    get() = this.replaceFirstChar { it.titlecase() }

androidComponents {
    val taskProviders =
        mutableMapOf<String, Pair<TaskProvider<DiffPackageGenerateTask>, TaskProvider<ExampleDeployTask>>>()
    onVariants { variant ->
        val buildType = variant.buildType
            ?: throw GradleException("Cannot get build type of variant \"${variant.name}\".")
        val flavorType = variant.productFlavors.firstOrNull { it.first == "type" }?.second
            ?: throw GradleException("Cannot get flavor of variant \"${variant.name}\".")
        val (generateTask, deployTask) = taskProviders.getOrPut(buildType) {
            val generateOutputDirectory = layout.buildDirectory.dir("intermediates")
                .map {
                    it.dir("tinker_diff_package").dir(buildType)
                }
            val generateOutputApk = generateOutputDirectory.map {
                it.file("patch_unsigned.apk")
            }
            val assembleOutputDirectory = layout.buildDirectory.dir("outputs")
                .map {
                    it.dir("diffPackage").dir(buildType)
                }
            val assembleOutputApk = assembleOutputDirectory.map {
                it.file("patch_unsigned.apk")
            }
            val generateTask =
                tasks.register<DiffPackageGenerateTask>("generateDiffPackageFor${variant.name.capitalized}") {
                    outputDirectory.set(generateOutputDirectory)
                }
            val assembleTask =
                tasks.register<Copy>("assembleDiffPackageFor${variant.name.capitalized}") {
                    dependsOn(generateTask)
                    from(generateOutputApk)
                    into(assembleOutputDirectory)
                }
            val deployTask =
                tasks.register<ExampleDeployTask>("deploy${variant.name.capitalized}Example") {
                    dependsOn(assembleTask)
                    adb.set(sdkComponents.adb)
                    applicationId.set(android.defaultConfig.applicationId!!)
                    project.properties["deviceSerial"]?.toString()?.let(serial::set)
                    diffPackageFile.set(assembleOutputApk)
                }
            Pair(generateTask, deployTask)
        }
        generateTask.configure {
            val value = variant.artifacts.get(SingleArtifact.APK)
            when (flavorType) {
                "original" -> originalApkDirectory.set(value)
                "updated" -> updatedApkDirectory.set(value)
                else -> throw GradleException("Unknown flavor type \"${flavorType}\".")
            }
        }
        if (flavorType == "original") {
            deployTask.configure {
                originalApkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            }
        }
    }
}