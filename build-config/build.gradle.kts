import org.jetbrains.kotlin.gradle.dsl.JvmTarget

version = "1.0.0"

plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

repositories {
    mavenCentral()
    google()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.agp.api)
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        create("plugin") {
            id = "com.tencent.tinker.build.config"
            implementationClass = "com.tencent.tinker.build.config.TinkerBuildConfigPlugin"
        }
    }
}