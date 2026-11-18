package uk.dnpk.sekund.runtime.instances

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.runtime.AppPaths
import uk.dnpk.sekund.runtime.auth.AuthRegistry
import uk.dnpk.sekund.runtime.services.AlertCenter
import uk.dnpk.sekund.runtime.services.OverlayCenter
import uk.dnpk.sekund.sdk.context.EventBus
import uk.dnpk.sekund.sdk.context.TimeService
import kotlin.time.Clock

/**
 * The app-wide services that every instance's context is built from.
 * Grouped into one class so InstanceManager's constructor stays short.
 *
 * @property wakeUps fires when instances should refresh early (network back, display on).
 * @property displayIsOn while false, instances stop refreshing.
 * @property auth connected accounts; each instance gets a [uk.dnpk.sekund.runtime.auth.PluginAuthAccess] view of it.
 */
class SharedServices(
    val paths: AppPaths,
    val http: HttpClient,
    val time: TimeService,
    val events: EventBus,
    val alerts: AlertCenter,
    val overlays: OverlayCenter,
    val auth: AuthRegistry,
    val wakeUps: Flow<Unit>,
    val displayIsOn: StateFlow<Boolean>,
    val clock: Clock = Clock.System,
)
