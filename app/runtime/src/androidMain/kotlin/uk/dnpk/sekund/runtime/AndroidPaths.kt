package uk.dnpk.sekund.runtime

import android.content.Context
import kotlinx.io.files.Path

/**
 * Works out where Sekund keeps its files on Android.
 *
 * Uses the app's folder on shared storage, `/sdcard/Android/data/uk.dnpk.sekund/files/`, which needs no
 * permission and can be reached with `adb pull` / `adb push` or a USB cable. That makes config.json easy
 * to inspect and edit. (The old app used private storage, which you can't open without root.)
 * Falls back to private storage if shared storage isn't available.
 */
object AndroidPaths {
    fun resolve(context: Context): AppPaths {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val cache = context.externalCacheDir ?: context.cacheDir
        return AppPaths(
            configDir = Path(root.absolutePath),
            dataDir = Path(root.absolutePath, "data"),
            cacheDir = Path(cache.absolutePath),
            logDir = Path(root.absolutePath, "logs"),
        )
    }
}
