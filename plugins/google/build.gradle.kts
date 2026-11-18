// Google Calendar as a calendar source: polled, plus instant push updates through the Sekund server
// when one is connected (GoogleCalendarPush).
plugins {
    id("sekund.plugin")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(project(":app:sdk-testing"))
        }
    }
}
