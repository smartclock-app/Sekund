# build-logic

Shared Gradle configuration, so each module's `build.gradle.kts` stays a few lines long.

Each file in `src/main/kotlin` named `<id>.gradle.kts` is a **convention plugin**. Applying `id("<id>")` in a module
is the same as pasting that file's contents into the module's build file.

| Plugin id | Use it for | What it sets up |
|---|---|---|
| `sekund.kmp-library` | Non-UI shared code (e.g. `core/protocol`) | Kotlin Multiplatform with Desktop (JVM) + Android targets, kotlinx serialization/coroutines/datetime, kotlin-test |
| `sekund.compose-library` | Modules with UI | Everything above + Compose Multiplatform |
| `sekund.plugin` | Every module in `plugins/` | Everything above + a dependency on `:app:sdk` |

Versions come from `gradle/libs.versions.toml`, the same catalog the main build uses.
