package uk.dnpk.sekund.runtime.auth

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode
import uk.dnpk.sekund.sdk.auth.CredentialHostException

/**
 * Builds an [HttpClient] that signs every request as one account.
 *
 * It's a copy of the app's shared client plus an interceptor (like an axios/fetch interceptor in JS)
 * that runs around each request:
 * 1. get the account's credential, refreshing first if it's about to expire,
 * 2. refuse URLs outside the provider's `baseUrls`, so tokens can't leak to other servers,
 * 3. add the credential (header or query parameter) and send,
 * 4. on `401 Unauthorized`: get a fresh credential (one refresh, shared with concurrent requests)
 *    and send once more. If there's no fresh credential the 401 is returned as it is.
 */
object SignedInClient {
    fun create(registry: AuthRegistry, accountId: String): HttpClient {
        val client = registry.baseHttp.config { }
        client.plugin(HttpSend).intercept { request ->
            val details = registry.credentialForRequest(accountId)
            val templates = AccountTemplates(details.definition, details.account.fields, details.credential)

            val url = request.url.buildString()
            if (!templates.isAllowedUrl(url)) throw CredentialHostException(url, details.definition.id)

            CredentialInjector.apply(request, details.definition, templates)
            val firstCall = execute(request)
            if (firstCall.response.status != HttpStatusCode.Unauthorized) return@intercept firstCall

            val fresh = registry.credentialAfterRejection(accountId, details.credential) ?: return@intercept firstCall
            CredentialInjector.apply(request, details.definition, AccountTemplates(details.definition, details.account.fields, fresh))
            execute(request)
        }
        return client
    }
}
