package uk.dnpk.sekund.protocol.auth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Where a credential goes on each request. In JSON: `{"type": "header", ...}` or `{"type": "query", ...}`.
 *
 * [format] is a template, e.g. `"Bearer {access_token}"` or `"{api_key}"`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class CredentialInjection {
    abstract val name: String
    abstract val format: String

    /** As an HTTP header, e.g. `Authorization: Bearer abc` or `x-api-key: abc`. */
    @Serializable
    @SerialName("header")
    data class Header(
        override val name: String,
        override val format: String,
    ) : CredentialInjection()

    /** As a URL query parameter, e.g. OpenWeather's `?appid=abc`. */
    @Serializable
    @SerialName("query")
    data class Query(
        override val name: String,
        override val format: String,
    ) : CredentialInjection()
}
