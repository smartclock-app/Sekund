// :app:runtime: the engine. Implements everything the SDK promises: plugin registry, config,
// auth registry, scheduler, storage, the LAN API server, mDNS, the relay client and platform services.
// Plugins must never depend on this module directly.
plugins {
    id("sekund.compose-library")
}

kotlin {
    android {
        // Lets the built-in auth provider files (composeResources/files) be packaged on Android.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":app:sdk"))
            api(libs.kotlinx.io.core)       // multiplatform file access (config.json, storage, logs)
            implementation(libs.kermit)     // logging
            implementation(libs.ktor.client.cio)

            // The LAN API (device API) the companion app talks to.
            implementation(libs.ktor.embedded.core)
            implementation(libs.ktor.embedded.cio)
            implementation(libs.ktor.embedded.websockets)
            implementation(libs.ktor.embedded.contentNegotiation)
            implementation(libs.ktor.embedded.statusPages)
            implementation(libs.ktor.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.java.keyring) // OS keychain access for the secrets key (DesktopSecretCipher)
            implementation(libs.jmdns)        // mDNS advertising (_sekund._tcp) on desktop
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        jvmTest.dependencies {
            implementation(libs.ktor.embedded.testHost)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.websockets)
        }
    }
}

// The generated `Res` class (used to read composeResources/files/auth-providers).
compose.resources {
    packageOfResClass = "uk.dnpk.sekund.runtime.resources"
}
