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

rootProject.name = "unidocs"

include(":app")
include(":core:model")
include(":core:storage")
include(":core:ui")
include(":feature:markdown")
include(":feature:pdf")
include(":feature:pdf-edit")
include(":core:webview")
include(":feature:books")
include(":feature:office")
