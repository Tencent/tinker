package com.tencent.tinker.build.config

import com.tencent.tinker.androidApplicationIfExists
import com.tencent.tinker.androidLibraryIfExists
import com.tencent.tinker.appliedAndroidLibraryPlugin
import com.tencent.tinker.dslDeveloper
import com.tencent.tinker.dslDevelopers
import com.tencent.tinker.dslLicense
import com.tencent.tinker.dslLicenses
import com.tencent.tinker.dslPom
import com.tencent.tinker.dslScm
import com.tencent.tinker.finalizeDslCompat
import com.tencent.tinker.javaIfExists
import com.tencent.tinker.kotlinAndroidIfExists
import com.tencent.tinker.kotlinJvmIfExists
import com.tencent.tinker.publishingIfExists
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.internal.extensions.core.extra
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun Project.tinkerBuildConfig(action: TinkerBuildConfigExtension.() -> Unit) {
    extensions.findByType(TinkerBuildConfigExtension::class.java)?.action()
}

abstract class TinkerBuildConfigExtension {

    abstract val publishVariant: Property<String>

    fun publishVariant(variant: String) {
        publishVariant.set(variant)
    }
}

private val Project.buildByWconan: Boolean
    get() = extra.has("wconanBuild") && extra.get("wconanBuild") == true

@Suppress("unused")
class TinkerBuildConfigPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {

        val extension =
            extensions.create("tinkerBuildConfig", TinkerBuildConfigExtension::class.java)

        group = "com.tencent.tinker"
        version = "2.0.0-alpha"
        javaIfExists {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        kotlinJvmIfExists {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
        androidApplicationIfExists {
            finalizeDslCompat {
                compileSdk = 36
                defaultConfig {
                    minSdk = 24
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                project.layout.projectDirectory
                    .dir("src")
                    .dir("main")
                    .dir("cpp")
                    .file("CMakeLists.txt")
                    .asFile
                    .takeIf { it.isFile }
                    ?.let(externalNativeBuild.cmake::path)
                layout.projectDirectory
                    .file("proguard-rules.pro")
                    .asFile
                    .takeIf { it.isFile }
                    ?.let { ruleFile ->
                        buildTypes.getByName("release").apply {
                            isMinifyEnabled = true
                            proguardFiles.add(ruleFile)
                        }
                    }
            }
        }
        androidLibraryIfExists {
            finalizeDslCompat {
                compileSdk = 36
                defaultConfig {
                    minSdk = 21
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                project.layout.projectDirectory
                    .dir("src")
                    .dir("main")
                    .dir("cpp")
                    .file("CMakeLists.txt")
                    .asFile
                    .takeIf { it.isFile }
                    ?.let(externalNativeBuild.cmake::path)
                layout.projectDirectory
                    .file("consumer-rules.pro")
                    .asFile
                    .takeIf { it.isFile }
                    ?.let { ruleFile ->
                        buildTypes.getByName("release")
                            .consumerProguardFiles.add(ruleFile)
                    }
                if (!buildByWconan) {
                    publishing {
                        singleVariant(extension.publishVariant.orNull ?: "release") {
                            withSourcesJar()
                        }
                    }
                }
            }
        }
        kotlinAndroidIfExists {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
        publishingIfExists {
            if (!buildByWconan) {
                publications.register("maven", MavenPublication::class.java) { publication ->
                    if (appliedAndroidLibraryPlugin) {
                        afterEvaluate {
                            publication.from(components.getByName(extension.publishVariant.orNull ?: "release"))
                        }
                    } else {
                        publication.from(components.getByName("java"))
                    }
                }
            }
            afterEvaluate {
                publications.withType(MavenPublication::class.java) { publication ->
                    publication.apply {
                        dslPom {
                            url.set("https://github.com/Tencent/tinker")
                            afterEvaluate { evaluated ->
                                evaluated.name.let(name::set)
                                evaluated.description?.let(description::set)
                            }
                            dslScm {
                                url.set("https://github.com/Tencent/tinker.git")
                            }
                            dslLicenses {
                                dslLicense {
                                    name.set("BSD License")
                                    url.set("https://opensource.org/licenses/BSD-3-Clause")
                                    distribution.set("repo")
                                }
                            }
                            dslDevelopers {
                                dslDeveloper {
                                    id.set("Tencent Wechat")
                                    name.set("Tencent Wechat, Inc.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}