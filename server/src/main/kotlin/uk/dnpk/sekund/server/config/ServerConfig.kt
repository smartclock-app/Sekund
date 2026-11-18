package uk.dnpk.sekund.server.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import uk.dnpk.sekund.protocol.SekundJson
import java.io.File

/**
 * The server's settings, read from a JSON file (see docs/server.md for a full example):
 *
 * ```json
 * {
 *   "port": 8080,
 *   "publicUrl": "https://sekund.example.org",
 *   "database": { "url": "jdbc:sqlite:/data/sekund.db" },
 *   "allowSignups": false,
 *   "oauth": { "google": { "clientId": "...", "clientSecret": "...", ... } }
 * }
 * ```
 *
 * A few settings can also come from environment variables, which win over the file (handy in Docker):
 * `PORT`, `PUBLIC_URL`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `ALLOW_SIGNUPS`.
 *
 * @property publicUrl the address people and other services reach this server at, without a trailing
 *   slash. Needed for webhooks and OAuth redirects (both are off without it). Google only sends
 *   webhooks to `https://` addresses.
 * @property allowSignups whether anyone may create an account. The very first account can always be
 *   created, so a fresh server can be set up; after that only if this is true.
 * @property oauth the OAuth clients the server signs in with on devices' behalf, by auth provider id
 *   (the same ids as the app's `*.auth.json` files, e.g. `google`).
 */
@Serializable
data class ServerConfig(
    val port: Int = 8080,
    val publicUrl: String? = null,
    val database: DatabaseConfig = DatabaseConfig(),
    val allowSignups: Boolean = false,
    val oauth: Map<String, OAuthClientConfig> = emptyMap(),
) {
    companion object {
        /**
         * Reads [file] (if it exists) and applies environment variable overrides from [environment].
         *
         * @throws IllegalArgumentException if the file isn't valid.
         */
        fun load(file: File?, environment: Map<String, String> = System.getenv()): ServerConfig {
            var config = ServerConfig()
            if (file != null && file.exists()) {
                try {
                    config = SekundJson.decodeFromString(serializer(), file.readText())
                } catch (error: SerializationException) {
                    throw IllegalArgumentException("$file isn't a valid server config: ${error.message}", error)
                }
            }
            return withEnvironment(config, environment)
        }

        private fun withEnvironment(config: ServerConfig, environment: Map<String, String>): ServerConfig {
            var result = config
            val port = environment["PORT"]?.toIntOrNull()
            if (port != null) result = result.copy(port = port)
            val publicUrl = environment["PUBLIC_URL"]
            if (!publicUrl.isNullOrBlank()) result = result.copy(publicUrl = publicUrl)
            val databaseUrl = environment["DATABASE_URL"]
            if (!databaseUrl.isNullOrBlank()) {
                val database = DatabaseConfig(databaseUrl, environment["DATABASE_USER"], environment["DATABASE_PASSWORD"])
                result = result.copy(database = database)
            }
            val allowSignups = environment["ALLOW_SIGNUPS"]
            if (allowSignups != null) result = result.copy(allowSignups = allowSignups.toBoolean())
            return result.copy(publicUrl = result.publicUrl?.trimEnd('/'))
        }
    }
}

/**
 * Where the data is kept. SQLite (one file) by default; PostgreSQL works too, e.g.
 * `"url": "jdbc:postgresql://db:5432/sekund"` with [user] and [password].
 */
@Serializable
data class DatabaseConfig(
    val url: String = "jdbc:sqlite:sekund-server.db",
    val user: String? = null,
    val password: String? = null,
)

/**
 * One OAuth client the server owns, e.g. a Google Cloud "Web application" client whose authorized
 * redirect URI is `<publicUrl>/oauth/google/callback`.
 *
 * The URLs are set here, not taken from devices, so the client secret is only ever sent to the real
 * provider.
 *
 * @property clientAuth `body` (client id and secret as form fields, the default) or `basic` (an
 *   `Authorization: Basic` header).
 */
@Serializable
data class OAuthClientConfig(
    val clientId: String,
    val clientSecret: String,
    val authorizeUrl: String,
    val tokenUrl: String,
    val clientAuth: String = "body",
)
