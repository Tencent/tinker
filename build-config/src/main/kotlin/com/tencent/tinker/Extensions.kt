package com.tencent.tinker

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DslLifecycle
import com.android.build.api.variant.LibraryAndroidComponentsExtension
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
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal val String.capitalized: String
    get() = replaceFirstChar { it.uppercase() }

internal fun Project.publishingIfExists(action: PublishingExtension.() -> Unit) {
    extensions.findByType(PublishingExtension::class.java)?.action()
}

private const val JAVA_PLUGIN_ID = "org.gradle.java"

internal val Project.appliedJavaPlugin: Boolean
    get() = plugins.hasPlugin(JAVA_PLUGIN_ID)

private const val JAVA_GRADLE_PLUGIN_PLUGIN_ID = "org.gradle.java-gradle-plugin"

internal val Project.appliedJavaGradlePluginPlugin: Boolean
    get() = plugins.hasPlugin(JAVA_GRADLE_PLUGIN_PLUGIN_ID)

internal fun Project.javaIfExists(action: JavaPluginExtension.() -> Unit) {
    extensions.findByType(JavaPluginExtension::class.java)?.action()
}

internal fun Project.kotlinJvmIfExists(action: KotlinJvmProjectExtension.() -> Unit) {
    extensions.findByType(KotlinJvmProjectExtension::class.java)?.action()
}

private const val ANDROID_LIBRARY_PLUGIN_ID = "com.android.library"

internal val Project.appliedAndroidLibraryPlugin: Boolean
    get() = plugins.hasPlugin(ANDROID_LIBRARY_PLUGIN_ID)

internal fun Project.androidApplicationIfExists(action: ApplicationAndroidComponentsExtension.() -> Unit) {
    extensions.findByType(ApplicationAndroidComponentsExtension::class.java)?.action()
}

internal fun Project.androidLibraryIfExists(action: LibraryAndroidComponentsExtension.() -> Unit) {
    extensions.findByType(LibraryAndroidComponentsExtension::class.java)?.action()
}

internal fun Project.kotlinAndroidIfExists(action: KotlinAndroidProjectExtension.() -> Unit) {
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.action()
}

internal fun <T> DslLifecycle<T>.finalizeDslCompat(action: T.() -> Unit) {
    finalizeDsl { it.action() }
}

internal fun MavenPublication.dslPom(action: MavenPom.() -> Unit) {
    pom.action()
}

internal fun MavenPom.dslScm(action: MavenPomScm.() -> Unit) {
    scm { it.action() }
}

internal fun MavenPom.dslLicenses(action: MavenPomLicenseSpec.() -> Unit) {
    licenses { it.action() }
}

internal fun MavenPomLicenseSpec.dslLicense(action: MavenPomLicense.() -> Unit) {
    license { it.action() }
}

internal fun MavenPom.dslDevelopers(action: MavenPomDeveloperSpec.() -> Unit) {
    developers { it.action() }
}

internal fun MavenPomDeveloperSpec.dslDeveloper(action: MavenPomDeveloper.() -> Unit) {
    developer { it.action() }
}