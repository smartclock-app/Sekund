rootProject.name = "Sekund"

pluginManagement {
    // Our shared build conventions (sekund.kmp-library, sekund.compose-library, sekund.plugin).
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Shared by the app AND the server: API data types (no UI).
include(":core:protocol")

// The app.
include(":app:sdk")         // public API that plugins are written against
include(":app:sdk-testing") // fakes for plugin tests (test dependency only)
include(":app:ui")          // design system: theme + reusable components
include(":app:runtime")     // the engine that loads plugins, config, auth, API server...
include(":app:shared")      // the root UI (shell, menus, settings screens)
include(":app:androidApp")  // Android entry point
include(":app:desktopApp")  // Desktop / Raspberry Pi entry point

// Every folder in plugins/ that has a build file is a plugin module, e.g. plugins/clock -> ":plugins:clock".
// Adding a plugin = create the folder, then add it to BuiltInPlugins.kt in :app:shared.
file("plugins").listFiles()
    ?.filter { folder -> File(folder, "build.gradle.kts").exists() }
    ?.sortedBy { folder -> folder.name }
    ?.forEach { folder -> include(":plugins:${folder.name}") }

// The optional relay server.
include(":server")
