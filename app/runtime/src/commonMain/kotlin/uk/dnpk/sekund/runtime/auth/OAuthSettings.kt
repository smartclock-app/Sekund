package uk.dnpk.sekund.runtime.auth

import uk.dnpk.sekund.protocol.auth.AuthMethodDefinition
import uk.dnpk.sekund.protocol.auth.BodyEncoding
import uk.dnpk.sekund.protocol.auth.ClientAuth
import uk.dnpk.sekund.protocol.auth.RefreshSettings

/**
 * The parts that the two OAuth methods (device code and authorization code) share, pulled out so the
 * refresh and revoke code doesn't care which one an account signed in with.
 */
data class OAuthSettings(
    val tokenUrl: String,
    val revokeUrl: String?,
    val refresh: RefreshSettings,
    val encoding: BodyEncoding,
    val clientAuth: ClientAuth,
) {
    /** Where refresh requests go: `refresh.url` if the definition sets one, else the token URL. */
    val refreshUrl: String
        get() = refresh.url ?: tokenUrl

    companion object {
        /** The OAuth settings of [method], or null if it isn't an OAuth method (API keys can't be refreshed). */
        fun of(method: AuthMethodDefinition): OAuthSettings? {
            if (method is AuthMethodDefinition.OAuthDeviceCode) {
                return OAuthSettings(method.tokenUrl, method.revokeUrl, method.refresh, method.bodyEncoding, method.clientAuth)
            }
            if (method is AuthMethodDefinition.OAuthAuthorizationCode) {
                return OAuthSettings(method.tokenUrl, method.revokeUrl, method.refresh, method.bodyEncoding, method.clientAuth)
            }
            return null
        }
    }
}
