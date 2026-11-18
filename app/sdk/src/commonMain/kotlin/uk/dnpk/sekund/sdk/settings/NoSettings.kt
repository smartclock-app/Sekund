package uk.dnpk.sekund.sdk.settings

/** For plugins that don't have any settings. */
object NoSettings : SettingsSchema<Unit>() {
    override fun build(values: SettingsValues) = Unit
}
