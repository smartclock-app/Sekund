package uk.dnpk.sekund.runtime.auth

import uk.dnpk.sekund.protocol.UrlPrefix
import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.protocol.auth.ClientMode
import uk.dnpk.sekund.protocol.auth.OAuthClient
import uk.dnpk.sekund.protocol.auth.Templates

/**
 * Fills a definition's `{placeholders}` for one account: its fields (e.g. `{instance_url}`) plus
 * `{access_token}`, `{api_key}`, `{client_id}` and `{client_secret}`.
 *
 * Think of it as the account's "template context" (like props passed to a template in a web framework).
 */
class AccountTemplates(
    private val definition: AuthProviderDefinition,
    fields: Map<String, String>,
    private val credential: Credential,
) {
    /** Field values with a trailing `/` removed from addresses, so `{instance_url}/api` never has `//`. */
    private val cleanFields: Map<String, String> = cleanFields(fields)

    /** Every placeholder value known for this account. Missing ones are simply left out. */
    fun values(): Map<String, String> {
        val result = cleanFields.toMutableMap()
        putIfPresent(result, "access_token", credential.accessToken)
        putIfPresent(result, "api_key", credential.apiKey)
        putIfPresent(result, "client_id", clientId())
        putIfPresent(result, "client_secret", clientSecret())
        return result
    }

    fun fill(template: String): String = Templates.fill(template, values())

    /** The OAuth client id: the user's own (saved with the credential), or the definition's fixed one. */
    fun clientId(): String? {
        if (credential.clientId != null) return credential.clientId
        val client = oauthClient() ?: return null
        val fixedId = client.id
        if (client.mode != ClientMode.Fixed || fixedId == null) return null
        return Templates.fill(fixedId, cleanFields)
    }

    fun clientSecret(): String? {
        if (credential.clientSecret != null) return credential.clientSecret
        val client = oauthClient() ?: return null
        val fixedSecret = client.secret
        if (client.mode != ClientMode.Fixed || fixedSecret == null) return null
        return Templates.fill(fixedSecret, cleanFields)
    }

    /**
     * True if credentials may be sent to [url]: it must start with one of the provider's `baseUrls`.
     * This stops a plugin (or a redirect) from leaking a token to another server.
     */
    fun isAllowedUrl(url: String): Boolean {
        for (base in definition.baseUrls) {
            val filledBase = Templates.fill(base, cleanFields)
            if (startsWithUrl(url, filledBase)) return true
        }
        return false
    }

    /** The first OAuth method's client settings (providers have at most one OAuth method in practice). */
    private fun oauthClient(): OAuthClient? {
        for (method in definition.methods) {
            if (method is AuthMethodDefinition.OAuthDeviceCode) return method.client
            if (method is AuthMethodDefinition.OAuthAuthorizationCode) return method.client
        }
        return null
    }

    private fun putIfPresent(map: MutableMap<String, String>, key: String, value: String?) {
        if (value != null) map[key] = value
    }

    companion object {
        /** Removes trailing slashes from address-like field values. */
        fun cleanFields(fields: Map<String, String>): Map<String, String> {
            val result = mutableMapOf<String, String>()
            for ((key, value) in fields) {
                val trimmed = value.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    result[key] = trimmed.trimEnd('/')
                } else {
                    result[key] = trimmed
                }
            }
            return result
        }

        /** See [UrlPrefix.matches]. */
        fun startsWithUrl(url: String, base: String): Boolean = UrlPrefix.matches(url, base)
    }
}
