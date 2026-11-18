package uk.dnpk.sekund.runtime.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What an OAuth token endpoint answers (RFC 6749 section 5). Unknown fields are ignored. */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

/**
 * What a device-code endpoint answers (RFC 8628 section 3.2).
 * Google and Trakt say `verification_url`; the standard says `verification_uri`. Both are accepted.
 */
@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_url") val verificationUrl: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 600,
    @SerialName("interval") val interval: Long = 5,
)
