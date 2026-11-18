// build-logic is a small, separate Gradle build that holds our "convention plugins":
// reusable chunks of build configuration that every module would otherwise copy-paste.
// See build-logic/README.md.

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Re-use the main project's version catalog so versions live in one place.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
