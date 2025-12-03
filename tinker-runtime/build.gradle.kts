plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
}

description = "Tinker Android runtime."

android {
    namespace = "com.tencent.tinker"
    defaultConfig {
        buildConfigField("String", "TINKER_VERSION", "\"${version}\"")
        manifestPlaceholders["TINKER_VERSION"] = version
    }
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    buildFeatures {
        buildConfig = true
        aidl = true
    }
    testCoverage {
        jacocoVersion = "0.8.14"
    }
    lint {
        disable.add("LongLogTag")
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "stubs", "include" to "*.jar")))
    implementation(project(":tinker-commons"))
    implementation(project(":tinker-annotation-processor"))
    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
    androidTestImplementation(project(":tinker-runtime-test-helper"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit.ktx)
}
