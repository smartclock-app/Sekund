// Alexa: what an Echo is playing (with synced lyrics), and its timers and alarms.
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
