package uk.dnpk.sekund.runtime.services

import co.touchlab.kermit.Logger
import uk.dnpk.sekund.sdk.context.PluginLog

/** The [PluginLog] given to one instance: writes to the app log, tagged with the instance id. */
class TaggedPluginLog(instanceId: String) : PluginLog {
    private val logger = Logger.withTag(instanceId)

    override fun debug(message: String) = logger.d { message }

    override fun info(message: String) = logger.i { message }

    override fun warn(message: String, error: Throwable?) = logger.w(error) { message }

    override fun error(message: String, error: Throwable?) = logger.e(error) { message }
}
