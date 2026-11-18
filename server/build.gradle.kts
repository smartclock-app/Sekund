// :server: the optional Sekund server (docs/server.md). Relays companion requests to devices,
// relays webhooks, and hosts OAuth sign-ins with server-managed clients. Nothing in the app needs it.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
}

group = "uk.dnpk.sekund"
version = providers.gradleProperty("sekund.version").get()

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "uk.dnpk.sekund.server.ServerMainKt"
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(libs.logback)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // HTTP + WebSockets (Netty).
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.embedded.websockets)
    implementation(libs.ktor.embedded.contentNegotiation)
    implementation(libs.ktor.embedded.statusPages)
    implementation(libs.ktor.serialization.json)

    // Talking to OAuth providers' token endpoints.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // Storage and passwords.
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.bouncycastle)

    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)
}
