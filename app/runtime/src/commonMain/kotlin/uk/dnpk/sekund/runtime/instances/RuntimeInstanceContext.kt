package uk.dnpk.sekund.runtime.instances

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import uk.dnpk.sekund.sdk.auth.AuthAccess
import uk.dnpk.sekund.sdk.context.Alerts
import uk.dnpk.sekund.sdk.context.EventBus
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.KeyValueStore
import uk.dnpk.sekund.sdk.context.PluginLog
import uk.dnpk.sekund.sdk.context.Refresher
import uk.dnpk.sekund.sdk.context.TimeService

/** The real [InstanceContext]: just holds what InstanceManager prepared for one instance. */
class RuntimeInstanceContext<S>(
    override val instanceId: String,
    override val settings: S,
    override val scope: CoroutineScope,
    override val http: HttpClient,
    override val auth: AuthAccess,
    override val time: TimeService,
    override val refresher: Refresher,
    override val storage: KeyValueStore,
    override val alerts: Alerts,
    override val events: EventBus,
    override val log: PluginLog,
) : InstanceContext<S>
