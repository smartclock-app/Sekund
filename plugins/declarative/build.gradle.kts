// The engine for declarative widgets and photo sources: JSON definitions that call an API, map the
// answer and show it with a built-in template. Definitions themselves are loaded by the runtime.
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
