import com.tencent.tinker.build.config.tinkerBuildConfig

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    jacoco
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
    alias(libs.plugins.tinker.runtime.build.helper)
}

description = "Tinker Android runtime."

tinkerBuildConfig {
    publishVariant("productionRelease")
}

android {
    namespace = "com.tencent.tinker"
    defaultConfig {
        buildConfigField("String", "TINKER_VERSION", "\"${version}\"")
        manifestPlaceholders["TINKER_VERSION"] = version
        ndk {
            abiFilters.add("arm64-v8a")
        }
        testInstrumentationRunner = "com.tencent.tinker.test.TinkerTestRunner"
    }
    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    flavorDimensions.add("tinkerType")
    productFlavors {
        create("production") {
            dimension = "tinkerType"
            isDefault = true
        }
        create("independent") {
            dimension = "tinkerType"
        }
    }
    buildFeatures {
        buildConfig = true
        aidl = true
    }
    testCoverage {
        jacocoVersion = "0.8.14"
    }
    testOptions {
        // Isolates each test case to separate process to avoid interference between test cases that
        // modify system state.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    lint {
        baseline = file("lint-baseline.xml")
        disable.add("LongLogTag")
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "stubs", "include" to "*.jar")))
    transformImplementation(libs.androidx.annotation)
    transformImplementation(kotlin("stdlib"))
    implementation(project(":tinker-commons"))
    implementation(project(":tinker-annotation-processor"))
    testImplementation(kotlin("stdlib"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    lintChecks(project(":tinker-runtime-internal-lint"))
    androidTestImplementation(project(":tinker-runtime-internal-test-helper"))
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit.ktx)
    androidTestUtil(libs.androidx.orchestrator)
}