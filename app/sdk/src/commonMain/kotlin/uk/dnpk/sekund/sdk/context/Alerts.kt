package uk.dnpk.sekund.sdk.context

/**
 * Alert cards at the top of the sidebar. Each instance has at most one alert; showing a new one
 * replaces the previous one.
 */
interface Alerts {
    fun show(alert: Alert)

    fun clear()
}

/**
 * @property imageUrl optional picture shown next to the text (e.g. album art).
 * @property qrCode optional text to show as a QR code (e.g. a sign-in link).
 */
data class Alert(
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val qrCode: String? = null,
)
