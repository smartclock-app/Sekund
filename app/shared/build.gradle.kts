// :app:shared: the root UI that both the Android and Desktop apps show (shell, menus, settings
// screens), plus BuiltInPlugins.kt: the list of plugins compiled into the app.
plugins {
    id("sekund.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":app:runtime"))

            // Built-in plugins. Each one is also listed in BuiltInPlugins.kt.
            implementation(project(":plugins:clock"))

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
        }
    }
}
