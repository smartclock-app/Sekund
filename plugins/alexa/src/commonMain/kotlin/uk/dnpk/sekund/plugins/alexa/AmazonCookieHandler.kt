package uk.dnpk.sekund.plugins.alexa

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.sdk.auth.CustomAuthHandler
import uk.dnpk.sekund.sdk.auth.CustomAuthRejectedException
import uk.dnpk.sekund.sdk.auth.CustomSession

/**
 * Amazon's sign-in, as the Alexa website does it (registered as the "amazon-cookie" handler, see
 * `amazon.auth.json`):
 *
 * 1. swap the long-lived refresh token for browser cookies (`/ap/exchangetoken/cookies`),
 * 2. load an Alexa page with those cookies, which hands out a `csrf` cookie,
 * 3. send both on every request: the cookies as `Cookie`, the CSRF token also as a `csrf` header.
 *
 * The session is stored as `cookie` and `csrf` values. When Amazon stops accepting it (a 401), the registry
 * calls [refresh] again.
 */
object AmazonCookieHandler : CustomAuthHandler {
    const val NAME = "amazon-cookie"

    private val json = Json { ignoreUnknownKeys = true }

    /** Pages that set the `csrf` cookie; the first that does wins (as in the old app). */
    private val csrfPages = listOf("/api/language", "/templates/oobe/d-device-pick.handlebars", "/api/devices-v2/device?cached=false")

    override suspend fun refresh(http: HttpClient, fields: Map<String, String>, session: CustomSession): CustomSession {
        val domain = domainOf(fields)
        val cookies = exchangeForCookies(http, domain, session.secret)
        val csrf = fetchCsrf(http, domain, cookies)
            ?: throw IllegalStateException("Amazon didn't hand out a CSRF token; try again later")
        return session.copy(values = mapOf("cookie" to "$cookies csrf=$csrf", "csrf" to csrf))
    }

    override fun sign(request: HttpRequestBuilder, fields: Map<String, String>, session: CustomSession) {
        val origin = "https://alexa.${domainOf(fields)}"
        request.headers.set(HttpHeaders.Cookie, session.values["cookie"] ?: "")
        request.headers.set("csrf", session.values["csrf"] ?: "")
        request.headers.set(HttpHeaders.Referrer, "$origin/spa/index.html")
        request.headers.set(HttpHeaders.Origin, origin)
    }

    /** "amazon.co.uk" from the account's `domain` field, tidied ("www.amazon.co.uk/" works too). */
    fun domainOf(fields: Map<String, String>): String {
        val domain = (fields["domain"] ?: "").trim().removePrefix("https://").removePrefix("www.").trimEnd('/')
        if (domain.isEmpty()) throw CustomAuthRejectedException("Enter your Amazon site (e.g. amazon.co.uk) in the account")
        return domain
    }

    @Serializable
    private data class ExchangeAnswer(val response: ExchangeResponse = ExchangeResponse())

    @Serializable
    private data class ExchangeResponse(val tokens: ExchangeTokens = ExchangeTokens())

    @Serializable
    private data class ExchangeTokens(val cookies: Map<String, List<AmazonCookie>> = emptyMap())

    @Serializable
    private data class AmazonCookie(@SerialName("Name") val name: String, @SerialName("Value") val value: String)

    /** Step 1. Returns the cookies as one `Cookie` header value ("a=1; b=2;"). */
    private suspend fun exchangeForCookies(http: HttpClient, domain: String, refreshToken: String): String {
        val response = http.submitForm(
            url = "https://api.$domain/ap/exchangetoken/cookies",
            formParameters = parameters {
                append("app_name", "Amazon Alexa")
                append("requested_token_type", "auth_cookies")
                append("domain", "www.$domain")
                append("source_token_type", "refresh_token")
                append("source_token", refreshToken)
            },
        ) {
            header("x-amzn-identity-auth-domain", "api.$domain")
        }
        val text = response.bodyAsText()
        val status = response.status.value
        // 400/401/403: the refresh token itself was refused. Only the user can fix that.
        if (status == 400 || status == 401 || status == 403) throw CustomAuthRejectedException("Amazon refused the refresh token")
        if (status !in 200..299) throw IllegalStateException("Amazon answered $status while signing in")

        val answer = json.decodeFromString<ExchangeAnswer>(text)
        val parts = mutableListOf<String>()
        for (cookies in answer.response.tokens.cookies.values) {
            for (cookie in cookies) {
                // Amazon wraps some values in quotes.
                parts.add("${cookie.name}=${cookie.value.trim('"', ' ')};")
            }
        }
        if (parts.isEmpty()) throw CustomAuthRejectedException("Amazon sent no cookies for the refresh token")
        return parts.joinToString(" ")
    }

    /** Step 2. The `csrf` cookie's value, or null if none of the pages set it. */
    private suspend fun fetchCsrf(http: HttpClient, domain: String, cookies: String): String? {
        for (page in csrfPages) {
            val response = http.get("https://alexa.$domain$page") {
                header(HttpHeaders.Cookie, cookies)
                header(HttpHeaders.Referrer, "https://alexa.$domain/spa/index.html")
                header(HttpHeaders.Origin, "https://alexa.$domain")
            }
            for (setCookie in response.headers.getAll(HttpHeaders.SetCookie).orEmpty()) {
                val csrf = csrfFrom(setCookie)
                if (csrf != null) return csrf
            }
        }
        return null
    }

    /** "csrf=1234; Path=/; ..." -> "1234". Null if it's a different cookie. */
    fun csrfFrom(setCookie: String): String? {
        val first = setCookie.substringBefore(';').trim()
        if (!first.startsWith("csrf=")) return null
        return first.removePrefix("csrf=")
    }
}
