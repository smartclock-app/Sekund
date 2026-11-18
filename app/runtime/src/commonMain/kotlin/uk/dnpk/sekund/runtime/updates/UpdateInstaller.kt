package uk.dnpk.sekund.runtime.updates

import kotlinx.io.files.Path

/**
 * The platform's way of installing a verified update file. Each entry point passes one to AppGraph
 * (or none, while that platform can't install yet).
 */
fun interface UpdateInstaller {
    suspend fun install(file: Path): InstallResult
}
