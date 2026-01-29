plugins {
    alias(libs.plugins.android.library)
    jacoco
    `maven-publish`
    alias(libs.plugins.tinker.build.config)
    alias(libs.plugins.tinker.runtime.build.config)
}

description = "Tinker Android runtime."

android {
    namespace = "com.tencent.tinker"
    defaultConfig {
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
    testCoverage {
        jacocoVersion = "0.8.14"
    }
    testOptions {
        // Isolates each test case to separate process to avoid interference between test cases that
        // modify system state.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
}

dependencies {
    transformImplementation(libs.androidx.annotation)
    transformImplementation(kotlin("stdlib"))
    transformImplementation(project(":tinker-commons"))
    testImplementation(kotlin("stdlib"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    lintChecks(project(":tinker-runtime-internal-lint"))
    androidTestImplementation(project(":tinker-runtime-internal-test-helper"))
    androidTestImplementation(project(":third-party:tinker-ziputils"))
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit.ktx)
    androidTestUtil(libs.androidx.orchestrator)
}