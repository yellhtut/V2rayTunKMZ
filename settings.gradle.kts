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
        // The Xray core .aar is checked in under app/libs rather than pulled from a
        // repository — see app/libs/README.md for provenance.
        flatDir { dirs("app/libs") }
    }
}

include(":app")
