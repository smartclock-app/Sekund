// :app:sdk-testing: fakes for plugin tests (a fake InstanceContext, clock, storage, refresher...).
// Only ever a *test* dependency: `commonTest.dependencies { implementation(project(":app:sdk-testing")) }`.
plugins {
    id("sekund.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":app:sdk"))
            api(libs.ktor.client.mock)
        }
    }
}
