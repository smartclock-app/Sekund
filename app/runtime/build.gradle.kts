// :app:runtime: the engine. Implements everything the SDK promises: plugin registry, config,
// auth registry, scheduler, storage, the LAN API server, mDNS, the relay client and platform services.
// Plugins must never depend on this module directly.
plugins {
    id("sekund.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":app:sdk"))
            api(libs.kotlinx.io.core)       // multiplatform file access (config.json, storage, logs)
            implementation(libs.kermit)     // logging
            implementation(libs.ktor.client.cio)
        }
        jvmMain.dependencies {
            implementation(libs.java.keyring) // OS keychain access for the secrets key (DesktopSecretCipher)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
