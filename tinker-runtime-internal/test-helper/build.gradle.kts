plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.tinker.build.config)
}

android {
    namespace = "com.tencent.tinker.test"
    buildFeatures {
        aidl = true
    }
}