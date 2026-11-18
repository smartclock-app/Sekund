package uk.dnpk.sekund.protocol.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthDefinitionParserTest {

    @Test
    fun `an api key provider with a self-hosted address parses`() {
        val parsed = AuthDefinitionParser.parse(
            """
            {
              // Comments and trailing commas are allowed, for hand-edited files.
              "id": "immich",
              "name": "Immich",
              "fields": [{ "key": "instance_url", "label": "Server address" }],
              "methods": [{ "type": "apiKey" }],
              "inject": { "type": "header", "name": "x-api-key", "format": "{api_key}" },
              "baseUrls": ["{instance_url}"],
            }
            """,
        )

        assertEquals(emptyList(), parsed.issues)
        val definition = assertNotNull(parsed.definition)
        assertIs<AuthMethodDefinition.ApiKey>(definition.methods.single())
        assertEquals(CredentialInjection.Header("x-api-key", "{api_key}"), definition.inject)
    }

    @Test
    fun `a device code provider with a custom poll status map parses`() {
        val parsed = AuthDefinitionParser.parse(
            """
            {
              "id": "trakt",
              "name": "Trakt",
              "methods": [{
                "type": "oauth2.deviceCode",
                "deviceCodeUrl": "https://api.trakt.tv/oauth/device/code",
                "tokenUrl": "https://api.trakt.tv/oauth/device/token",
                "bodyEncoding": "json",
                "pollStatusMap": { "400": "pending", "429": "slowDown", "410": "expired" }
              }],
              "extraHeaders": { "trakt-api-key": "{client_id}" },
              "baseUrls": ["https://api.trakt.tv/"]
            }
            """,
        )

        val method = assertIs<AuthMethodDefinition.OAuthDeviceCode>(assertNotNull(parsed.definition).methods.single())
        assertEquals(BodyEncoding.Json, method.bodyEncoding)
        assertEquals(DevicePollStatus.SlowDown, method.pollStatusMap["429"])
        // Unset values get sensible defaults.
        assertEquals(ClientMode.Byo, method.client.mode)
        assertEquals(60, method.refresh.skewSeconds)
    }

    @Test
    fun `broken JSON is reported, not thrown`() {
        val parsed = AuthDefinitionParser.parse("{ not json")

        assertNull(parsed.definition)
        assertEquals(1, parsed.issues.size)
    }

    @Test
    fun `unknown method types are reported`() {
        val parsed = AuthDefinitionParser.parse(
            """{ "id": "x", "name": "X", "methods": [{ "type": "magic" }], "baseUrls": ["https://x/"] }""",
        )

        assertNull(parsed.definition)
    }

    @Test
    fun `mistakes are reported with their path`() {
        val parsed = AuthDefinitionParser.parse(
            """
            {
              "id": "Bad Id",
              "name": "",
              "methods": [{ "type": "oauth2.authorizationCode", "authorizeUrl": "ftp://x", "tokenUrl": "https://x/{nope}",
                            "client": { "mode": "fixed" } }],
              "baseUrls": []
            }
            """,
        )

        assertNull(parsed.definition)
        val paths = parsed.issues.map { issue -> issue.path }
        assertTrue("id" in paths)
        assertTrue("name" in paths)
        assertTrue("baseUrls" in paths)
        assertTrue("methods[0].authorizeUrl" in paths)
        assertTrue("methods[0].tokenUrl" in paths) // unknown placeholder {nope}
        assertTrue("methods[0].client.id" in paths) // fixed client without an id
    }

    @Test
    fun `templates are filled, and a missing value is an error`() {
        assertEquals("Bearer abc", Templates.fill("Bearer {access_token}", mapOf("access_token" to "abc")))
        assertEquals(listOf("instance_url", "api_key"), Templates.placeholdersIn("{instance_url}/api?key={api_key}"))

        assertFailsWith<MissingTemplateValueException> {
            Templates.fill("{instance_url}/api", emptyMap())
        }
    }
}
