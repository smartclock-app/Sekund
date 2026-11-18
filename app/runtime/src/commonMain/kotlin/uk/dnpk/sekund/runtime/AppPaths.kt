package uk.dnpk.sekund.runtime

import kotlinx.io.files.Path

/**
 * Where Sekund keeps its files. Each platform works these out at startup (see `DesktopPaths` and
 * `AndroidPaths`) and passes them in.
 *
 * Everything here is deliberately somewhere people can open and edit (unlike the old Tauri app,
 * whose files were hidden in Android's private storage). Only secrets live elsewhere.
 *
 * @property configDir config.json, its schema, backups, user-added definitions. Meant for people to edit.
 * @property dataDir plugin storage. Readable, but people shouldn't normally need it.
 * @property cacheDir downloaded images and other things that can be thrown away at any time.
 * @property logDir log files.
 * @property secretsDir the encrypted secrets file. The one private place: on Android it's internal storage.
 */
data class AppPaths(
    val configDir: Path,
    val dataDir: Path,
    val cacheDir: Path,
    val logDir: Path,
    val secretsDir: Path,
) {
    val configFile: Path
        get() = Path(configDir, "config.json")

    val backupDir: Path
        get() = Path(configDir, "backups")

    val storageDir: Path
        get() = Path(dataDir, "storage")

    /** Connected accounts (labels and ids only; their credentials are in the secret store). */
    val accountsFile: Path
        get() = Path(dataDir, "accounts.json")

    /** Declarative widget and photo source definitions (`*.widget.json`, `*.photos.json`). */
    val widgetsDir: Path
        get() = Path(configDir, "widgets")

    /** User-added auth provider definitions (`*.auth.json`), which override the built-in ones. */
    val authProvidersDir: Path
        get() = Path(configDir, "auth-providers")

    companion object {
        /** Everything under one folder, e.g. for tests or when `SEKUND_HOME` is set. */
        fun underOneFolder(root: Path): AppPaths = AppPaths(
            configDir = Path(root, "config"),
            dataDir = Path(root, "data"),
            cacheDir = Path(root, "cache"),
            logDir = Path(root, "logs"),
            secretsDir = Path(root, "secrets"),
        )
    }
}
