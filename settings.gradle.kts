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
    plugins {
        // Asegúrate de usar la versión correcta de los plugins
        id("com.android.application") version "8.5.2" // Versión compatible de AGP
        id("org.jetbrains.kotlin.android") version "1.9.10" // Versión de Kotlin compatible
        id("org.jetbrains.kotlin.plugin.compose") version "1.9.10" // Versión de Kotlin Compose
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Reproductor Musica"
include(":app")
