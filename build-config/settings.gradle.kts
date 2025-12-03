dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(
                files(
                    rootDir
                        .parentFile
                        .resolve("libs.versions.toml")
                )
            )
        }
    }
}