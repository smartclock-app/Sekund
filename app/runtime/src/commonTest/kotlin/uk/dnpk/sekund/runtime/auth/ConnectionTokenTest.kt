package uk.dnpk.sekund.runtime.auth

import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import uk.dnpk.sekund.sdk.auth.ConnectionTokenNotAllowedException
import uk.dnpk.sekund.sdk.auth.CredentialHostException
import uk.dnpk.sekund.sdk.auth.ProviderNotDeclaredException
import uk.dnpk.sekund.sdk.settings.AccountRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `context.auth.connectionToken`: the narrow way to read a token, for Home Assistant's WebSocket. */
class ConnectionTokenTest {
    private val homeAssistant = """
        {"id": "ha", "name": "HA", "fields": [{"key": "instance_url", "label": "Server"}],
         "methods": [{"type": "bearer"}], "baseUrls": ["{instance_url}/"], "connectionToken": true}
    """

    @Test
    fun `an opted-in provider hands out its token for a websocket to its own server`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("ok") }
        rig.addProvider(homeAssistant)
        val account = rig.registry.connectWithKey("ha", 0, mapOf("instance_url" to "http://ha.local:8123"), "long-lived")
        val access = PluginAuthAccess(rig.registry, "homeassistant", setOf("ha"))
        val ref = AccountRef("ha", account.id)

        assertEquals("long-lived", access.connectionToken(ref, "ws://ha.local:8123/api/websocket"))
        assertFailsWith<CredentialHostException> { access.connectionToken(ref, "ws://elsewhere.test/api/websocket") }
        assertFailsWith<CredentialHostException> { access.connectionToken(ref, "ws://ha.local:8123.evil.test/") }
    }

    @Test
    fun `providers that didn't opt in refuse`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("ok") }
        rig.addProvider(homeAssistant.replace("\"connectionToken\": true", "\"connectionToken\": false"))
        val account = rig.registry.connectWithKey("ha", 0, mapOf("instance_url" to "http://ha.local:8123"), "long-lived")
        val access = PluginAuthAccess(rig.registry, "homeassistant", setOf("ha"))

        assertFailsWith<ConnectionTokenNotAllowedException> {
            access.connectionToken(AccountRef("ha", account.id), "ws://ha.local:8123/api/websocket")
        }
    }

    @Test
    fun `plugins can't ask for providers they didn't declare`() = runTest {
        val rig = AuthTestRig(backgroundScope) { respond("ok") }
        val access = PluginAuthAccess(rig.registry, "clock", emptySet())

        assertFailsWith<ProviderNotDeclaredException> { access.connectionToken(AccountRef("ha", "acc_1"), "ws://ha.local/") }
    }
}
