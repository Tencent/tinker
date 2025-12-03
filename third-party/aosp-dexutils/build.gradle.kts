plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
}

description = "Dex utils from AOSP."