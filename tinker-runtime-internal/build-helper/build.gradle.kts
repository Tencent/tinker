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
    implementation(libs.kotlinx.coroutines.core)
}

gradlePlugin {
    plugins {
        create("plugin") {
            id = "com.tencent.tinker.runtime.build.helper"
            implementationClass = "com.tencent.tinker.build.TinkerRuntimeBuildHelperPlugin"
        }
    }
}