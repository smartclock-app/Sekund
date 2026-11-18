package uk.dnpk.sekund.runtime

import kotlinx.io.files.Path

/**
 * Works out where Sekund keeps its files on desktop, following each OS's conventions:
 *
 * | OS      | config                                  | data / cache / logs                    |
 * |---------|-----------------------------------------|----------------------------------------|
 * | Linux   | ~/.config/sekund                        | ~/.local/share/sekund, ~/.cache/sekund, ~/.local/state/sekund/logs |
 * | macOS   | ~/Library/Application Support/Sekund    | same folder, ~/Library/Caches/Sekund, ~/Library/Logs/Sekund |
 * | Windows | %APPDATA%\Sekund                        | %LOCALAPPDATA%\Sekund                  |
 *
 * Setting the `SEKUND_HOME` environment variable, or passing `--data-dir=<folder>`, puts everything
 * in one folder instead (handy for testing or running two copies).
 */
object DesktopPaths {
    fun resolve(args: Array<String> = emptyArray()): AppPaths {
        val override = dataDirArgument(args) ?: System.getenv("SEKUND_HOME")
        if (!override.isNullOrBlank()) {
            return AppPaths.underOneFolder(Path(override))
        }

        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()

        if (os.contains("mac")) {
            return AppPaths(
                configDir = Path(home, "Library", "Application Support", "Sekund"),
                dataDir = Path(home, "Library", "Application Support", "Sekund", "data"),
                cacheDir = Path(home, "Library", "Caches", "Sekund"),
                logDir = Path(home, "Library", "Logs", "Sekund"),
                secretsDir = Path(home, "Library", "Application Support", "Sekund", "data", "secrets"),
            )
        }

        if (os.contains("win")) {
            val roaming = System.getenv("APPDATA") ?: Path(home, "AppData", "Roaming").toString()
            val local = System.getenv("LOCALAPPDATA") ?: Path(home, "AppData", "Local").toString()
            return AppPaths(
                configDir = Path(roaming, "Sekund"),
                dataDir = Path(local, "Sekund", "data"),
                cacheDir = Path(local, "Sekund", "cache"),
                logDir = Path(local, "Sekund", "logs"),
                secretsDir = Path(local, "Sekund", "data", "secrets"),
            )
        }

        // Linux (including the Raspberry Pi), using the XDG base directory spec.
        val linuxData = Path(xdg("XDG_DATA_HOME", Path(home, ".local", "share")), "sekund")
        return AppPaths(
            configDir = Path(xdg("XDG_CONFIG_HOME", Path(home, ".config")), "sekund"),
            dataDir = linuxData,
            cacheDir = Path(xdg("XDG_CACHE_HOME", Path(home, ".cache")), "sekund"),
            logDir = Path(xdg("XDG_STATE_HOME", Path(home, ".local", "state")), "sekund", "logs"),
            secretsDir = Path(linuxData, "secrets"),
        )
    }

    private fun dataDirArgument(args: Array<String>): String? {
        for (arg in args) {
            if (arg.startsWith("--data-dir=")) return arg.removePrefix("--data-dir=")
        }
        return null
    }

    private fun xdg(variable: String, fallback: Path): String {
        val value = System.getenv(variable)
        if (value.isNullOrBlank()) return fallback.toString()
        return value
    }
}
