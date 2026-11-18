# plugins

One folder per plugin. Any folder here with a `build.gradle.kts` is included in the build automatically
(see `settings.gradle.kts`). To add one:

1. Create `plugins/<name>/build.gradle.kts` containing `plugins { id("sekund.plugin") }`.
2. Write the plugin against `:app:sdk`.
3. Add it to `BuiltInPlugins.kt` in `:app:shared`.
