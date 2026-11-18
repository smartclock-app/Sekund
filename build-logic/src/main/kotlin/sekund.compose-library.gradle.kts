// Convention plugin: a multiplatform library that also contains Compose UI.
//
// Apply it in a module with:   plugins { id("sekund.compose-library") }
//
// Builds on sekund.kmp-library (Desktop + Android) and adds Compose Multiplatform.

plugins {
    id("sekund.kmp-library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun library(name: String) = libs.findLibrary(name).get()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(library("compose-runtime"))
            implementation(library("compose-foundation"))
            implementation(library("compose-ui"))
            implementation(library("compose-material3"))
            implementation(library("compose-components-resources"))
            implementation(library("compose-uiToolingPreview"))
        }
    }
}
