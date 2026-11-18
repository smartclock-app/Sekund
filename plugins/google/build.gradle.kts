// Google Calendar as a calendar source (polling; push updates through the server come in phase 9).
plugins {
    id("sekund.plugin")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
