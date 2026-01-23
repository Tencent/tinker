pluginManagement {
    includeBuild("build-config")
    includeBuild("tinker-runtime-internal/build-helper")
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

val projects = listOf(
    "tinker-commons",
    "tinker-runtime",
    "tinker-runtime-internal/test-helper",
    "tinker-runtime-internal/lint",
    "tinker-annotation-processor",
    "tinker-cli",
    "tinker-patch-commons",
)

projects.forEach { pro ->
    val name = pro.replace("/", "-")
    val path = pro.replace("/", File.separator)
    include(name)
    project(":$name").projectDir = file(path)
}

// FIXME: support latest AGP.
//include(":tinker-plugin")
