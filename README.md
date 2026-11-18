# Sekund

An always-on clock and dashboard for wall displays: Android tablets and TV boxes, Raspberry Pi, and desktop.
Built with Kotlin Multiplatform and Compose Multiplatform, with an optional Ktor relay server.

## Repository layout

| Path | What it is |
|---|---|
| `core/protocol` | Data types that cross the network (device API, relay frames, settings descriptors). Shared by the app **and** the server. |
| `app/sdk` | The public API plugins are written against. Plugins depend on this module only. |
| `app/ui` | Design system: theme tokens and reusable components. |
| `app/runtime` | The engine: plugin registry, config, auth registry, scheduler, LAN API, platform services. |
| `app/shared` | The root UI (shell, menus, settings) and `BuiltInPlugins.kt`. |
| `app/androidApp`, `app/desktopApp` | Entry points per platform. |
| `plugins/*` | One folder per plugin (clock, calendar, google, ...). Picked up automatically by `settings.gradle.kts`. |
| `server` | Optional relay server (webhooks, OAuth callbacks, remote access). |
| `build-logic` | Shared build configuration ("convention plugins"). See its README. |

## Running

- Desktop: `./gradlew :app:desktopApp:run` (hot reload: `./gradlew :app:desktopApp:hotRun --auto`)
- Android: `./gradlew :app:androidApp:installDebug`
- Server: `./gradlew :server:run`

## Tests

- Everything: `./gradlew check`
- One module, desktop tests: `./gradlew :app:sdk:jvmTest`
- Server: `./gradlew :server:test`
