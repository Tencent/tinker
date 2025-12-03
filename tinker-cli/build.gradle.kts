plugins {
    application
    alias(libs.plugins.tinker.build.config)
}

application {
    mainClass.set("com.tencent.tinker.patch.CliMain")
}

dependencies {
    implementation(project(":tinker-patch-commons"))
}