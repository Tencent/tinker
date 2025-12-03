plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
}

description = "Tinker patch common logic library."

dependencies {
    api(project(":tinker-commons"))
    api(libs.apk.parser.lib)
    implementation(libs.guava)
    implementation(libs.asm)
    implementation(libs.dexlib2) {
        exclude(mapOf("group" to "com.google.guava"))
    }
    implementation(libs.dom4j)
    implementation(libs.jetbrains.annotations)
}
