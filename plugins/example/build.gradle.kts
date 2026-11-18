// The example plugin from docs/plugins/authoring.md: a "quote of the day" widget.
// It's built and tested with everything else (so the guide's code keeps compiling), but it isn't in
// BuiltInPlugins.kt, so it isn't part of the app.
plugins {
    id("sekund.plugin")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":app:sdk-testing"))
        }
    }
}
