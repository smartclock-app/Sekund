plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
}

group = "uk.dnpk.sekund"
version = "1.0.0"
application {
    mainClass = "uk.dnpk.sekund.ApplicationKt"
}

dependencies {
    implementation(project(":core:protocol"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}