plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
}

description = "Tinker common logic library."

dependencies {
    api(project(":third-party:aosp-dexutils"))
    api(project(":third-party:bsdiff-util"))
    api(project(":third-party:tinker-ziputils"))
}