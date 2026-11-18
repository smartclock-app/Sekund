package uk.dnpk.sekund.runtime.auth

import io.ktor.client.request.HttpRequestBuilder
import uk.dnpk.sekund.protocol.auth.AuthProviderDefinition
import uk.dnpk.sekund.protocol.auth.CredentialInjection

/** Puts an account's credential on a request, as the provider's `inject` and `extraHeaders` say. */
object CredentialInjector {
    /**
     * Sets (not appends) the values, so calling it again before a retry replaces the old token.
     *
     * @throws uk.dnpk.sekund.protocol.auth.MissingTemplateValueException if the credential lacks a value
     *   the definition needs (e.g. `{access_token}` on an API-key account).
     */
    fun apply(request: HttpRequestBuilder, definition: AuthProviderDefinition, templates: AccountTemplates) {
        val injection = definition.inject
        when (injection) {
            is CredentialInjection.Header -> request.headers.set(injection.name, templates.fill(injection.format))
            is CredentialInjection.Query -> request.url.parameters.set(injection.name, templates.fill(injection.format))
        }
        for ((name, format) in definition.extraHeaders) {
            request.headers.set(name, templates.fill(format))
        }
    }
}
