// The declarative widget definitions that ship with Sekund (weather, Actual Budget, Immich photos).
// They're plain JSON files in composeResources/files/definitions/, in exactly the format users write.
plugins {
    id("sekund.plugin")
}

// The generated `Res` class, used to read the JSON files.
compose.resources {
    packageOfResClass = "uk.dnpk.sekund.plugins.declarativebuiltins.resources"
}

kotlin {
    android {
        // Without this the JSON files aren't packaged into the Android app, and reading them crashes.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(project(":plugins:declarative"))
            implementation(libs.ktor.client.mock)
        }
    }
}
