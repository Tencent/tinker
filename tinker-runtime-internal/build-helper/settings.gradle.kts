dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(
                files(
                    rootDir
                        .parentFile
                        .parentFile
                        .resolve("libs.versions.toml")
                )
            )
        }
    }
}