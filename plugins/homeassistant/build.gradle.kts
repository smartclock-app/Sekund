// Home Assistant: pops up a camera full screen when a sensor turns on (e.g. the doorbell rings).
plugins {
    id("sekund.plugin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Home Assistant pushes sensor changes over a WebSocket.
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(project(":app:sdk-testing"))
        }
    }
}
