// Convention plugin: a Kotlin Multiplatform library that builds for Desktop (JVM) and Android.
//
// Apply it in a module with:   plugins { id("sekund.kmp-library") }
//
// It's equivalent to pasting this file's contents into that module's build.gradle.kts.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
}

// Read versions from gradle/libs.versions.toml.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun version(name: String): String = libs.findVersion(name).get().requiredVersion
fun library(name: String) = libs.findLibrary(name).get()

kotlin {
    jvmToolchain(21)

    // Desktop: macOS, Windows and Linux (including the Raspberry Pi).
    jvm()

    android {
        // ":app:sdk" becomes the Android namespace "uk.dnpk.sekund.app.sdk".
        namespace = "uk.dnpk.sekund" + project.path.replace(':', '.').replace('-', '_')
        compileSdk = version("android-compileSdk").toInt()
        minSdk = version("android-minSdk").toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(library("kotlinx-coroutines-core"))
            implementation(library("kotlinx-serialization-json"))
            implementation(library("kotlinx-datetime"))
        }
        commonTest.dependencies {
            implementation(library("kotlin-test"))
            implementation(library("kotlinx-coroutines-test"))
        }
    }
}
