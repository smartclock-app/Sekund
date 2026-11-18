package uk.dnpk.sekund

import uk.dnpk.sekund.plugins.clock.ClockPlugin
import uk.dnpk.sekund.runtime.config.AppConfig
import uk.dnpk.sekund.runtime.config.InstanceConfig

/** The config.json written on first start: just the digital clock. */
object DefaultConfig {
    const val CLOCK_INSTANCE_ID = "clock"

    fun create(): AppConfig = AppConfig(
        clockFace = CLOCK_INSTANCE_ID,
        instances = listOf(
            InstanceConfig(id = CLOCK_INSTANCE_ID, type = ClockPlugin.DIGITAL_CLOCK),
        ),
    )
}
