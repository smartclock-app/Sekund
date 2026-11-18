package uk.dnpk.sekund.runtime.updates

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.protocol.update.AppVersion
import uk.dnpk.sekund.protocol.update.UpdateManifest
import uk.dnpk.sekund.protocol.update.UpdateSource
import uk.dnpk.sekund.runtime.platform.Sha256Hasher
import uk.dnpk.sekund.runtime.platform.verifyEcdsaSignature
import kotlin.io.encoding.Base64

/**
 * Finds, downloads, verifies and installs new versions of Sekund. [AppUpdater] decides when;
 * this does the work.
 *
 * The rules that keep updates safe:
 * - the manifest (`update.json`) must carry a valid signature (`update.json.sig`) by the public key in
 *   [UpdateSource], which is built into the app; no token or secret is ever needed or embedded,
 * - each download must match the SHA-256 the signed manifest lists, or it's deleted,
 * - only files in [downloadDir] that passed that check are handed to the [installer].
 *
 * @param source where to look and which key to trust; read lazily because it's a resource file.
 * @param platformKey which manifest asset fits this device (null = none).
 * @param installer the platform's installer, or null where installing isn't supported yet.
 */
class UpdateService(
    private val http: HttpClient,
    val currentVersion: String,
    private val platformKey: String?,
    private val downloadDir: Path,
    private val installer: UpdateInstaller?,
    private val source: suspend () -> UpdateSource,
) {
    private val log = Logger.withTag("Updates")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateCheck {
        val current = AppVersion.parse(currentVersion) ?: return UpdateCheck.Unavailable("This is a development build ($currentVersion)")
        val updateSource = source()
        if (updateSource.manifestUrl.isBlank() || updateSource.publicKey.isBlank()) {
            return UpdateCheck.Unavailable("This build has no update source")
        }
        val platform = platformKey ?: return UpdateCheck.Unavailable("Updates aren't built for this kind of device")

        val manifest = fetchVerifiedManifest(updateSource)
        val latest = AppVersion.parse(manifest.version) ?: throw IllegalStateException("The release has an unreadable version '${manifest.version}'")
        if (latest <= current) return UpdateCheck.UpToDate(manifest.version)

        val asset = manifest.assets[platform] ?: return UpdateCheck.Unavailable("Version ${manifest.version} has no download for $platform")
        val update = AvailableUpdate(
            version = manifest.version,
            notes = manifest.notes,
            url = asset.url,
            sha256 = asset.sha256.lowercase(),
            sizeBytes = asset.size,
        )
        return UpdateCheck.Available(update)
    }

    /** Fetches `update.json` and `update.json.sig`, and only parses the manifest if the signature is good. */
    private suspend fun fetchVerifiedManifest(updateSource: UpdateSource): UpdateManifest {
        val manifestBytes = fetchBytes(updateSource.manifestUrl)
        val signatureText = fetchBytes(updateSource.manifestUrl + ".sig").decodeToString()

        val publicKey = decodeBase64(updateSource.publicKey)
        val signature = decodeBase64(signatureText)
        if (publicKey == null || signature == null || !verifyEcdsaSignature(publicKey, manifestBytes, signature)) {
            throw UpdateRejectedException("The release manifest's signature doesn't match this build's key")
        }
        return json.decodeFromString<UpdateManifest>(manifestBytes.decodeToString())
    }

    /**
     * The key and signature are base64 text. Line breaks and spaces are ignored, because Linux's `base64`
     * wraps long output. Null if it isn't valid base64.
     */
    private fun decodeBase64(text: String): ByteArray? {
        val compact = text.filterNot { char -> char.isWhitespace() }
        try {
            return Base64.decode(compact)
        } catch (error: IllegalArgumentException) {
            return null
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray {
        val response = http.get(url)
        if (!response.status.isSuccess()) throw IllegalStateException("$url answered ${response.status}")
        return response.readRawBytes()
    }

    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit = {}): DownloadedUpdate {
        val file = fileFor(update)
        withContext(Dispatchers.IO) {
            SystemFileSystem.createDirectories(downloadDir)
            removeOtherDownloads(keep = file)
        }

        // A complete, verified download from earlier (e.g. before a restart) is reused.
        val alreadyThere = withContext(Dispatchers.IO) { SystemFileSystem.exists(file) && hashOf(file) == update.sha256 }
        if (!alreadyThere) {
            val hash = downloadTo(file, update, onProgress)
            if (hash != update.sha256) {
                withContext(Dispatchers.IO) { SystemFileSystem.delete(file, mustExist = false) }
                throw UpdateRejectedException("The download of ${update.version} doesn't match the release (SHA-256 differs)")
            }
        }
        onProgress(1f)
        log.i { "Update ${update.version} downloaded and verified" }
        return DownloadedUpdate(version = update.version, file = file.toString())
    }

    /** Streams the download into [file], hashing as it goes. Returns the SHA-256 as hex. */
    private suspend fun downloadTo(file: Path, update: AvailableUpdate, onProgress: (Float) -> Unit): String {
        val hasher = Sha256Hasher()
        http.prepareGet(update.url).execute { response ->
            if (!response.status.isSuccess()) throw IllegalStateException("The download answered ${response.status}")
            val total = response.contentLength() ?: update.sizeBytes
            val body = response.bodyAsChannel()
            val chunk = ByteArray(64 * 1024)
            var received = 0L
            withContext(Dispatchers.IO) {
                SystemFileSystem.sink(file).buffered().use { sink ->
                    while (true) {
                        val read = body.readAvailable(chunk, 0, chunk.size)
                        if (read < 0) break
                        sink.write(chunk, 0, read)
                        hasher.update(chunk, read)
                        received += read
                        if (total != null && total > 0) onProgress((received.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        return hasher.finishHex()
    }

    suspend fun install(update: DownloadedUpdate): InstallResult {
        val file = Path(update.file)
        // Only install what we downloaded and verified ourselves: the file must be in our folder.
        if (file.parent?.toString() != downloadDir.toString()) {
            throw UpdateRejectedException("${update.file} isn't one of Sekund's verified downloads")
        }
        val platformInstaller = installer ?: return InstallResult.NotSupported("Installing updates on this device comes in a later version")
        log.i { "Installing update ${update.version}" }
        return platformInstaller.install(file)
    }

    /** `<version>.<extension of the URL>`, e.g. `2.1.0.apk`. */
    private fun fileFor(update: AvailableUpdate): Path {
        val name = update.url.substringBefore('?').substringAfterLast('/')
        val extension = name.substringAfterLast('.', missingDelimiterValue = "bin")
        val safeVersion = update.version.filter { char -> char.isLetterOrDigit() || char == '.' || char == '-' }
        return Path(downloadDir, "$safeVersion.$extension")
    }

    /** Old versions' downloads are only taking up space. */
    private fun removeOtherDownloads(keep: Path) {
        for (path in SystemFileSystem.list(downloadDir)) {
            if (path.toString() != keep.toString()) SystemFileSystem.delete(path, mustExist = false)
        }
    }

    private fun hashOf(file: Path): String {
        val hasher = Sha256Hasher()
        val chunk = ByteArray(64 * 1024)
        SystemFileSystem.source(file).buffered().use { input ->
            while (true) {
                val read = input.readAtMostTo(chunk, 0, chunk.size)
                if (read < 0) break
                hasher.update(chunk, read)
            }
        }
        return hasher.finishHex()
    }
}
