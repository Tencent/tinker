pluginManagement {
    includeBuild("build-config")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(rootDir.resolve("libs.versions.toml")))
        }
    }
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        google()
    }
}

include(":third-party:aosp-dexutils")
include(":third-party:bsdiff-util")
include(":third-party:tinker-ziputils")
include(":tinker-commons")
include(":tinker-runtime")
include(":tinker-runtime-test-helper")
include(":tinker-annotation-processor")
include(":tinker-cli")
include(":tinker-patch-commons")

// FIXME: support latest AGP.
//include(":tinker-plugin")
