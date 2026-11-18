package uk.dnpk.sekund.runtime.api

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import uk.dnpk.sekund.protocol.api.AccountSummary
import uk.dnpk.sekund.protocol.api.DeviceEvent
import uk.dnpk.sekund.runtime.config.etagOf
import uk.dnpk.sekund.sdk.context.Alert

/**
 * Everything that happens on the device, as one stream of [DeviceEvent]s for `WS /v1/events`.
 *
 * A new listener first gets the current state (config ETag, instances, accounts, alerts) and then
 * every change. (Each source is a StateFlow, and a StateFlow always starts with its current value.)
 */
object EventStream {
    fun of(api: DeviceApi): Flow<DeviceEvent> = merge(
        api.graph.config.config
            .map { config -> etagOf(config) }
            .distinctUntilChanged()
            .map { etag -> DeviceEvent.ConfigChanged(etag) },
        api.graph.instances.instances.map { _ -> DeviceEvent.InstancesChanged(api.instanceStatuses()) },
        accountEvents(api),
        alertEvents(api),
        api.graph.logBuffer.lines.map { line -> DeviceEvent.Log(line) },
        api.actions.results.map { result -> DeviceEvent.ActionDone(result) },
    )

    /** Re-sends the account list whenever an account is added or removed, or any account's state changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun accountEvents(api: DeviceApi): Flow<DeviceEvent> {
        val auth = api.graph.auth
        return auth.accounts
            .flatMapLatest { accounts ->
                if (accounts.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val perAccount: List<Flow<AccountSummary>> = accounts.map { account ->
                        auth.state(account.id).map { state -> AccountFlows.summaryOf(account, state) }
                    }
                    combine(perAccount) { summaries -> summaries.toList() }
                }
            }
            .distinctUntilChanged()
            .map { summaries -> DeviceEvent.AccountsChanged(summaries) }
    }

    /** Turns the "alerts showing now" map into raised/cleared events by comparing it with the last one. */
    private fun alertEvents(api: DeviceApi): Flow<DeviceEvent> = flow {
        var previous = emptyMap<String, Alert>()
        api.graph.alerts.alerts.collect { all ->
            // Pairing alerts show a PIN; it's only for whoever is standing at the screen.
            val current = all.filterKeys { owner -> !owner.startsWith("pairing:") }
            for ((owner, alert) in current) {
                if (previous[owner] != alert) emit(DeviceEvent.AlertRaised(owner, alert.title, alert.subtitle))
            }
            for (owner in previous.keys) {
                if (owner !in current) emit(DeviceEvent.AlertCleared(owner))
            }
            previous = current
        }
    }
}
