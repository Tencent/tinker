@file:Suppress("unused")

package com.tencent.tinker.build

import org.gradle.api.Plugin
import org.gradle.api.Project

class TinkerRuntimeBuildHelperPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyTestAssets()
        project.applyPackageTransform()
    }
}