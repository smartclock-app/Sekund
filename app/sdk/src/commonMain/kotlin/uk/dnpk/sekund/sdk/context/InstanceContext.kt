package uk.dnpk.sekund.sdk.context

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import uk.dnpk.sekund.sdk.auth.AuthAccess

/**
 * Everything one plugin instance gets from Sekund. Passed to `Contribution.create`.
 *
 * This is the *only* way plugins talk to the rest of the app, which keeps plugins easy to test:
 * a test can hand in a fake context.
 *
 * @param S the plugin's settings class.
 */
interface InstanceContext<S> {
    /** Unique id of this instance, e.g. `"weather-1"`. Stable across restarts. */
    val instanceId: String

    /**
     * This instance's settings, already decoded. When someone changes them, Sekund throws the old
     * instance away and creates a new one, so this value never changes during an instance's life.
     */
    val settings: S

    /**
     * Run all background work in this scope (`scope.launch { ... }`). Sekund cancels it when the
     * instance is removed or recreated. (Comparable to a React effect's cleanup / an AbortController.)
     */
    val scope: CoroutineScope

    /** An HTTP client with sensible timeouts and logging. For signed-in requests use [auth] instead. */
    val http: HttpClient

    /** Signed-in HTTP clients for the accounts in this instance's settings. Tokens stay hidden. */
    val auth: AuthAccess

    /** The current time, ticking in step with the wall clock, plus the user's time preferences. */
    val time: TimeService

    /** Runs a fetch function on a schedule and keeps its latest result. The usual way to load data. */
    val refresher: Refresher

    /** A small key/value store private to this instance, for caching data across restarts. */
    val storage: KeyValueStore

    /** Shows or clears this instance's alert card at the top of the sidebar. */
    val alerts: Alerts

    /** Messages between plugins, and events from Sekund itself (display on/off, network...). */
    val events: EventBus

    /** Writes to Sekund's log, tagged with this instance's id. */
    val log: PluginLog

    /** Other running instances, e.g. the calendar sources picked in a calendar widget's settings. */
    val instances: InstanceLookup
}
