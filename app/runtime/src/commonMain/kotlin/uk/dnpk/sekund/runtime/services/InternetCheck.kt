package uk.dnpk.sekund.runtime.services

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the internet works: asks [url], which answers `204 No Content` when it does.
 *
 * A real request is used, not the platform's "connected" flag, because being connected to Wi-Fi doesn't
 * mean the internet works (a router without internet, a captive portal that answers every address...).
 */
class InternetCheck(private val http: HttpClient, private val url: String) {
    suspend fun works(): Boolean {
        try {
            val status = withTimeoutOrNull(TIMEOUT) { http.get(url).status.value }
            return status == 204
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // No route, DNS failure, connection refused...: all mean "offline".
            return false
        }
    }

    private companion object {
        val TIMEOUT = 10.seconds
    }
}
