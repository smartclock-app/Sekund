import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx) // WindowCompat, for hiding the system bars

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// The one app version, shared with the desktop packages (scripts/release.sh bumps it).
val sekundVersion = providers.gradleProperty("sekund.version").get()

/**
 * Android also needs a whole number that grows with every release: 1.2.3 becomes 1_002_003.
 * Each part may go up to 999.
 */
fun versionCodeOf(version: String): Int {
    val parts = version.substringBefore('-').split(".")
    require(parts.size == 3) { "sekund.version must look like 1.2.3, not '$version'" }
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    val patch = parts[2].toInt()
    require(minor < 1000 && patch < 1000) { "minor and patch must be below 1000 in '$version'" }
    return major * 1_000_000 + minor * 1_000 + patch
}

// Release signing comes from environment variables, so the keystore and its passwords never enter git.
// CI sets them from secrets (see docs/releasing.md). Without them, assembleRelease makes an unsigned APK.
val releaseKeystore = providers.environmentVariable("SEKUND_KEYSTORE_FILE").orNull

android {
    namespace = "uk.dnpk.sekund"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "uk.dnpk.sekund"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = versionCodeOf(sekundVersion)
        versionName = sekundVersion
    }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("SEKUND_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SEKUND_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SEKUND_KEY_PASSWORD").get()
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}