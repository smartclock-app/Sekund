package uk.dnpk.sekund.protocol.update

import kotlinx.serialization.Serializable

/**
 * What a release offers, published next to it as `update.json`, with its signature in `update.json.sig`.
 * Sekund only believes a manifest whose signature matches the public key built into the app (see
 * [UpdateSource]), so a hacked download server can't push its own build.
 *
 * ```json
 * {
 *   "version": "2.1.0",
 *   "notes": "Home Assistant cameras",
 *   "assets": {
 *     "android":     { "url": "https://.../sekund-2.1.0.apk", "sha256": "9f2c...", "size": 41234567 },
 *     "linux-arm64": { "url": "https://.../sekund_2.1.0_arm64.deb", "sha256": "..." }
 *   }
 * }
 * ```
 *
 * @property assets one download per platform, keyed by [UpdatePlatforms] names. Platforms without an
 *   entry simply don't get this release.
 */
@Serializable
data class UpdateManifest(
    val version: String,
    val notes: String? = null,
    val assets: Map<String, UpdateAsset> = emptyMap(),
)

/**
 * One file to download.
 *
 * @property sha256 the file's SHA-256 as hex. The download is thrown away if it doesn't match.
 * @property size in bytes, if known; only used to show progress when the server doesn't say.
 */
@Serializable
data class UpdateAsset(
    val url: String,
    val sha256: String,
    val size: Long? = null,
)

/**
 * Where this build looks for updates, and the key their manifests must be signed with. Built into the
 * app (`files/updates/update-source.json` in `:app:runtime`); empty values turn updates off.
 *
 * @property publicKey an ECDSA P-256 public key: base64 of its DER form (`openssl ec -pubout -outform DER | base64`).
 */
@Serializable
data class UpdateSource(
    val manifestUrl: String = "",
    val publicKey: String = "",
)

/** The names used as keys in [UpdateManifest.assets]. */
object UpdatePlatforms {
    const val ANDROID = "android"
    const val LINUX_ARM64 = "linux-arm64"
    const val LINUX_X64 = "linux-x64"
    const val MACOS_ARM64 = "macos-arm64"
    const val MACOS_X64 = "macos-x64"
    const val WINDOWS_X64 = "windows-x64"
}
