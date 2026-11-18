// Trakt: air dates of shows and release dates of films from a list and/or the watchlist, as a calendar source.
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
