package com.tencent.tinker.build.config

import com.android.build.api.variant.DslLifecycle
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPomDeveloper
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomLicense
import org.gradle.api.publish.maven.MavenPomLicenseSpec
import org.gradle.api.publish.maven.MavenPomScm
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private fun Project.publishingIfExists(action: PublishingExtension.() -> Unit) {
    extensions.findByType(PublishingExtension::class.java)?.action()
}

private const val JAVA_PLUGIN_ID = "org.gradle.java"

private val Project.appliedJavaPlugin: Boolean
    get() = plugins.hasPlugin(JAVA_PLUGIN_ID)

private const val JAVA_GRADLE_PLUGIN_PLUGIN_ID = "org.gradle.java-gradle-plugin"

private val Project.appliedJavaGradlePluginPlugin: Boolean
    get() = plugins.hasPlugin(JAVA_GRADLE_PLUGIN_PLUGIN_ID)

private fun Project.javaIfExists(action: JavaPluginExtension.() -> Unit) {
    extensions.findByType(JavaPluginExtension::class.java)?.action()
}

private fun Project.kotlinJvmIfExists(action: KotlinJvmProjectExtension.() -> Unit) {
    extensions.findByType(KotlinJvmProjectExtension::class.java)?.action()
}

private const val ANDROID_LIBRARY_PLUGIN_ID = "com.android.library"

private val Project.appliedAndroidLibraryPlugin: Boolean
    get() = plugins.hasPlugin(ANDROID_LIBRARY_PLUGIN_ID)

private fun Project.androidIfExists(action: LibraryAndroidComponentsExtension.() -> Unit) {
    extensions.findByType(LibraryAndroidComponentsExtension::class.java)?.action()
}

private fun Project.kotlinAndroidIfExists(action: KotlinAndroidProjectExtension.() -> Unit) {
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.action()
}

private fun <T> DslLifecycle<T>.finalizeDslCompat(action: T.() -> Unit) {
    finalizeDsl { it.action() }
}

private fun MavenPublication.dslPom(action: MavenPom.() -> Unit) {
    pom.action()
}

private fun MavenPom.dslScm(action: MavenPomScm.() -> Unit) {
    scm { it.action() }
}

private fun MavenPom.dslLicenses(action: MavenPomLicenseSpec.() -> Unit) {
    licenses { it.action() }
}

private fun MavenPomLicenseSpec.dslLicense(action: MavenPomLicense.() -> Unit) {
    license { it.action() }
}

private fun MavenPom.dslDevelopers(action: MavenPomDeveloperSpec.() -> Unit) {
    developers { it.action() }
}

private fun MavenPomDeveloperSpec.dslDeveloper(action: MavenPomDeveloper.() -> Unit) {
    developer { it.action() }
}

@Suppress("unused")
class TinkerBuildConfigPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        group = "com.tencent.tinker"
        version = "1.9.15.2"
        javaIfExists {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        kotlinJvmIfExists {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        }
        androidIfExists {
            finalizeDslCompat {
                compileSdk = 36
                defaultConfig {
                    minSdk = 21
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
                publishing {
                    singleVariant("release") {
                        withSourcesJar()
                    }
                }
                layout.projectDirectory.file("consumer-rules.pro").asFile.takeIf { it.isFile }
                    ?.let { ruleFile ->
                        buildTypes.getByName("release")
                            .consumerProguardFiles.add(ruleFile)
                    }
            }
        }
        kotlinAndroidIfExists {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
        }
        publishingIfExists {
            publications.create("maven", MavenPublication::class.java).apply {
                if (project.appliedAndroidLibraryPlugin) {
                    project.afterEvaluate { evaluated ->
                        from(evaluated.components.getByName("release"))
                    }
                } else if (project.appliedJavaPlugin && !project.appliedJavaGradlePluginPlugin) {
                    from(project.components.getByName("java"))
                }
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