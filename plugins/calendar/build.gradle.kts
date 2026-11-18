// The calendar widget: merges events from calendar sources (Google, Trakt, ...) into one grouped list.
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
