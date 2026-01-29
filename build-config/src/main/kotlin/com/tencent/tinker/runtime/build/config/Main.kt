@file:Suppress("unused")

package com.tencent.tinker.runtime.build.config

import com.tencent.tinker.kotlinAndroidIfExists
import com.tencent.tinker.kotlinJvmIfExists
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

class TinkerRuntimeBuildConfigPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyConfig()
        project.applyTestAssets()
        project.applyPackageTransform()
    }
}

private fun Project.applyConfig() {
    kotlinJvmIfExists {
        compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
    kotlinAndroidIfExists {
        compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}