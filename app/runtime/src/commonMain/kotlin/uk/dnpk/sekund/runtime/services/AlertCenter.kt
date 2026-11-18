package uk.dnpk.sekund.runtime.services

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import uk.dnpk.sekund.sdk.context.Alert
import uk.dnpk.sekund.sdk.context.Alerts

/**
 * Every alert currently shown at the top of the sidebar. At most one per owner (an instance id, or
 * a core feature such as `"network"`), in the order they first appeared.
 */
class AlertCenter {
    private val current = MutableStateFlow<Map<String, Alert>>(emptyMap())

    /** Alerts to show, keyed by owner. The sidebar draws these. */
    val alerts: StateFlow<Map<String, Alert>> = current

    fun show(owner: String, alert: Alert) {
        current.update { alerts -> alerts + (owner to alert) }
    }

    fun clear(owner: String) {
        current.update { alerts -> alerts - owner }
    }

    /** The [Alerts] handle given to one instance, which can only touch its own alert. */
    fun forOwner(owner: String): Alerts = OwnerAlerts(owner)

    private inner class OwnerAlerts(private val owner: String) : Alerts {
        override fun show(alert: Alert) = show(owner, alert)

        override fun clear() = clear(owner)
    }
}
