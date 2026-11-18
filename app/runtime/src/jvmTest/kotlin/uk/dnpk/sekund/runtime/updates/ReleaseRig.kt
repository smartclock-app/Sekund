package uk.dnpk.sekund.runtime.updates

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.io.files.Path
import uk.dnpk.sekund.protocol.update.UpdateSource
import uk.dnpk.sekund.runtime.newTestPaths
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * A pretend release server for update tests. It signs manifests with a throwaway key, the same way the
 * release script will (ECDSA P-256 over SHA-256, base64), and serves them with [apk] as the download.
 */
class ReleaseRig {
    val keys: KeyPair = newKeyPair()
    val otherKeys: KeyPair = newKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)

    val apk = "pretend apk contents".encodeToByteArray()
    val apkHash = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }

    /** What the server answers; tests change these. */
    var manifest: String = manifest("2.0.0")
    var signature: String = sign(manifest)

    /** Publishes [version], correctly signed. */
    fun release(version: String) {
        manifest = manifest(version)
        signature = sign(manifest)
    }

    fun sign(text: String, private: PrivateKey = keys.private): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(private)
        signer.update(text.encodeToByteArray())
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    fun manifest(version: String, hash: String = apkHash) =
        """{"version": "$version", "notes": "Cameras", "assets": {"android": {"url": "https://dl.test/sekund.apk", "sha256": "$hash"}}}"""

    fun service(
        version: String = "2.0.0",
        installer: UpdateInstaller? = null,
        downloadDir: Path = Path(newTestPaths().cacheDir, "updates"),
    ): UpdateService {
        val http = HttpClient(MockEngine { request ->
            when (request.url.toString()) {
                "https://releases.test/update.json" -> respond(manifest)
                "https://releases.test/update.json.sig" -> respond(signature)
                "https://dl.test/sekund.apk" -> respond(apk)
                else -> respondError(HttpStatusCode.NotFound)
            }
        })
        return UpdateService(http, version, "android", downloadDir, installer) {
            UpdateSource(manifestUrl = "https://releases.test/update.json", publicKey = publicKey)
        }
    }

    private fun newKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }
}
