import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "uk.dnpk.sekund.MainKt"

        nativeDistributions {
            // The app icon in each package format (the clock artwork from the old Tauri app): icons/ for
            // macOS and Windows, and Linux shares the window's PNG in src/main/resources.
            // .dmg for macOS, .msi for Windows, .deb for Linux and the Raspberry Pi. Each is built on its own
            // OS and CPU (jpackage can't cross-build): the arm64 .deb is built on an arm64 Linux machine.
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Sekund"
            packageVersion = providers.gradleProperty("sekund.version").get()
            description = "An always-on clock and dashboard"
            vendor = "Sekund"

            // The packages carry their own trimmed Java runtime with only these modules.
            // `./gradlew :app:desktopApp:suggestRuntimeModules` lists what the code uses directly; the rest are
            // loaded indirectly, so that task can't see them:
            // - jdk.crypto.ec: elliptic-curve crypto (update signatures, most HTTPS sites)
            // - java.naming: DNS lookups (mDNS, Ktor)
            // - jdk.localedata: month and day names for languages other than English
            modules(
                "java.instrument",
                "java.management",
                "jdk.security.auth",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "java.naming",
                "jdk.localedata",
            )

            linux {
                // Installs to /opt/sekund, with the program at /opt/sekund/bin/Sekund (see docs/desktop.md).
                packageName = "sekund"
                appCategory = "Utility"
                menuGroup = "Utility"
                shortcut = true
                iconFile.set(project.file("src/main/resources/sekund-icon.png")) // also the window icon
            }
            macOS {
                bundleID = "uk.dnpk.sekund"
                dockName = "Sekund"
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                menu = true
                shortcut = true
                // Lets a newer .msi replace an older one. Never change it.
                upgradeUuid = "8b0c6d0e-3f4a-4f61-9a55-6c1e2d7b9f13"
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}
