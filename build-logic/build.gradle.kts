plugins {
    // Lets us write convention plugins as ordinary *.gradle.kts files in src/main/kotlin.
    `kotlin-dsl`
}

dependencies {
    // The Gradle plugins our conventions apply. They're `compileOnly` because the main
    // build already puts them on the classpath (see the root build.gradle.kts).
    compileOnly(libs.plugins.kotlinMultiplatform.toDependency())
    compileOnly(libs.plugins.kotlinSerialization.toDependency())
    compileOnly(libs.plugins.androidMultiplatformLibrary.toDependency())
    compileOnly(libs.plugins.composeMultiplatform.toDependency())
    compileOnly(libs.plugins.composeCompiler.toDependency())
}

/** Turns a version-catalog plugin entry into the Maven coordinates of its Gradle plugin artifact. */
fun Provider<PluginDependency>.toDependency(): Provider<String> = map { plugin ->
    "${plugin.pluginId}:${plugin.pluginId}.gradle.plugin:${plugin.version.requiredVersion}"
}
