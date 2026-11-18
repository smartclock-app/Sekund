// The slideshow clock face: photos from photo sources (e.g. Immich) behind the time.
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
