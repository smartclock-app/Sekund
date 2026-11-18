package uk.dnpk.sekund.sdk.context

/** Writes to Sekund's log file. Every line is tagged with the instance id automatically. */
interface PluginLog {
    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String, error: Throwable? = null)

    fun error(message: String, error: Throwable? = null)
}
