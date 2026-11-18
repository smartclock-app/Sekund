// :app:sdk: the public API that plugins are written against.
// Plugins depend on this module ONLY. It re-exports :app:ui and :core:protocol (`api(...)`),
// so a plugin automatically sees the design system and the shared data types too.
plugins {
    id("sekund.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:protocol"))
            api(project(":app:ui"))
        }
    }
}
