package uk.dnpk.sekund.plugins.declarative

import uk.dnpk.sekund.protocol.declarative.SourceDefinition
import uk.dnpk.sekund.sdk.context.RefreshPolicy
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Turns a source's `"refresh": "15m"` into a [RefreshPolicy]. Never more often than once a minute. */
internal object Refresh {
    fun policyFor(source: SourceDefinition): RefreshPolicy {
        val every = Duration.parseOrNull(source.refresh) ?: 15.minutes
        return RefreshPolicy(every = every, minimum = 1.minutes)
    }
}
