// Convention plugin: a Sekund plugin module (anything under plugins/).
//
// Apply it in a module with:   plugins { id("sekund.plugin") }
//
// Plugins may only depend on the public plugin SDK (:app:sdk), never on the runtime.
// That rule is what keeps plugins swappable and easy to test.

plugins {
    id("sekund.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:sdk"))
        }
    }
}
