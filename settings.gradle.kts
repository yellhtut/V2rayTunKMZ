rootProject.name = "V2rayTunKMZ"

pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // No flatDir for the Xray core: it is not a published module and is not committed
        // (see app/libs/README.md). app/build.gradle.kts links it as a plain file dependency
        // when present, which needs no repository and fails loudly rather than silently
        // resolving something unexpected.
    }
}

include(":app")
