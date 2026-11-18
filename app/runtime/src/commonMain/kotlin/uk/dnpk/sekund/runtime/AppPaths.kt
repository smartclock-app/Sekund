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
 */
data class AppPaths(
    val configDir: Path,
    val dataDir: Path,
    val cacheDir: Path,
    val logDir: Path,
) {
    val configFile: Path
        get() = Path(configDir, "config.json")

    val backupDir: Path
        get() = Path(configDir, "backups")

    val storageDir: Path
        get() = Path(dataDir, "storage")

    companion object {
        /** Everything under one folder, e.g. for tests or when `SEKUND_HOME` is set. */
        fun underOneFolder(root: Path): AppPaths = AppPaths(
            configDir = Path(root, "config"),
            dataDir = Path(root, "data"),
            cacheDir = Path(root, "cache"),
            logDir = Path(root, "logs"),
        )
    }
}
