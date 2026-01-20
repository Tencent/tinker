pluginManagement {
    includeBuild("..")
    includeBuild("../build-config")
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
    }
}

includeBuild("..")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files(rootDir.parentFile.resolve("libs.versions.toml")))
        }
    }
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "tinker-example"
