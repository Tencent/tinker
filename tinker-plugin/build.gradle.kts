plugins {
    groovy
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
}

description = "Tinker patch gradle plugin."

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
    implementation(project(":tinker-patch-commons"))
    implementation(libs.osdetector.gradle.plugin)
    implementation(libs.commons.codec)
    compileOnly(libs.agp)
}