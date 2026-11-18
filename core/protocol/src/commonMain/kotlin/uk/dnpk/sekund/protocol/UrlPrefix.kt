package uk.dnpk.sekund.protocol

/**
 * The one rule for "may a request go to this URL?", used wherever Sekund limits where data is sent:
 * an auth provider's `baseUrls`, and a declarative widget's `hosts`.
 */
object UrlPrefix {
    /**
     * True if [url] starts with [base], and if [base] doesn't end in `/` the match stops at a path, query or
     * port boundary. So `https://api.trakt.tv` allows `https://api.trakt.tv/x` but not
     * `https://api.trakt.tv.evil.com`.
     */
    fun matches(url: String, base: String): Boolean {
        if (!url.startsWith(base)) return false
        if (base.endsWith("/")) return true
        if (url.length == base.length) return true
        val next = url[base.length]
        return next == '/' || next == '?' || next == '#' || next == ':'
    }
}
