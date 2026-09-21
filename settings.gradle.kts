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
    }
}

rootProject.name = "tailg-compose"
include(":app")
// Macrobenchmark harness: a separate com.android.test module that drives the
// `benchmark` build type of :app (see app/build.gradle.kts + macrobenchmark/).
include(":macrobenchmark")
