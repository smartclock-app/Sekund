package uk.dnpk.sekund.protocol.auth

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uk.dnpk.sekund.protocol.SekundJson

/**
 * Reads and checks `*.auth.json` files.
 *
 * Never throws: a broken file gives a null [ParsedAuthDefinition.definition] plus issues saying why,
 * so one bad file is skipped and reported instead of breaking sign-in for everything else.
 */
object AuthDefinitionParser {

    /** Lenient about hand-editing mistakes that don't change the meaning: comments and trailing commas. */
    @OptIn(ExperimentalSerializationApi::class)
    private val json: Json = Json(from = SekundJson) {
        allowComments = true
        allowTrailingComma = true
    }

    /** Placeholders every definition may use, besides its own account fields. */
    val BUILT_IN_PLACEHOLDERS: Set<String> = setOf("access_token", "api_key", "client_id", "client_secret")

    /** Parses [text] and validates the result. */
    fun parse(text: String): ParsedAuthDefinition {
        val definition = try {
            json.decodeFromString(AuthProviderDefinition.serializer(), text)
        } catch (error: SerializationException) {
            return ParsedAuthDefinition(null, listOf(DefinitionIssue("", error.message ?: "Not a valid auth definition")))
        } catch (error: IllegalArgumentException) {
            return ParsedAuthDefinition(null, listOf(DefinitionIssue("", error.message ?: "Not a valid auth definition")))
        }

        val issues = validate(definition)
        if (issues.isEmpty()) return ParsedAuthDefinition(definition, issues)
        return ParsedAuthDefinition(null, issues)
    }

    /** Checks a definition that decoded fine for mistakes the JSON shape can't catch. Empty list = valid. */
    fun validate(definition: AuthProviderDefinition): List<DefinitionIssue> {
        val issues = mutableListOf<DefinitionIssue>()
        val known = BUILT_IN_PLACEHOLDERS + definition.fields.map { field -> field.key }

        if (!isValidId(definition.id)) {
            issues.add(DefinitionIssue("id", "Use lowercase letters, digits and dashes"))
        }
        if (definition.name.isBlank()) {
            issues.add(DefinitionIssue("name", "Must not be empty"))
        }
        if (definition.methods.isEmpty()) {
            issues.add(DefinitionIssue("methods", "List at least one way to sign in"))
        }
        if (definition.baseUrls.isEmpty()) {
            issues.add(DefinitionIssue("baseUrls", "List at least one URL that credentials may be sent to"))
        }
        for ((index, url) in definition.baseUrls.withIndex()) {
            checkUrl("baseUrls[$index]", url, known, issues)
        }
        for ((index, field) in definition.fields.withIndex()) {
            if (field.key.isBlank()) issues.add(DefinitionIssue("fields[$index].key", "Must not be empty"))
        }

        checkTemplate("inject.format", definition.inject.format, known, issues)
        for ((name, value) in definition.extraHeaders) {
            checkTemplate("extraHeaders.$name", value, known, issues)
        }
        val accountInfo = definition.accountInfo
        if (accountInfo != null) {
            checkUrl("accountInfo.url", accountInfo.url, known, issues)
            if (!accountInfo.label.startsWith("$.")) {
                issues.add(DefinitionIssue("accountInfo.label", "Use a path such as \"$.email\""))
            }
        }

        for ((index, method) in definition.methods.withIndex()) {
            checkMethod("methods[$index]", method, known, issues)
        }
        return issues
    }

    private fun checkMethod(path: String, method: AuthMethodDefinition, known: Set<String>, issues: MutableList<DefinitionIssue>) {
        when (method) {
            is AuthMethodDefinition.ApiKey -> Unit
            is AuthMethodDefinition.Bearer -> Unit
            is AuthMethodDefinition.Custom -> {
                if (method.handler.isBlank()) issues.add(DefinitionIssue("$path.handler", "Must name a handler"))
            }
            is AuthMethodDefinition.OAuthDeviceCode -> {
                checkUrl("$path.deviceCodeUrl", method.deviceCodeUrl, known, issues)
                checkUrl("$path.tokenUrl", method.tokenUrl, known, issues)
                checkOptionalUrl("$path.revokeUrl", method.revokeUrl, known, issues)
                checkClient("$path.client", method.client, known, issues)
                for (status in method.pollStatusMap.keys) {
                    if (status.toIntOrNull() == null) {
                        issues.add(DefinitionIssue("$path.pollStatusMap.$status", "Keys must be HTTP status codes like \"400\""))
                    }
                }
            }
            is AuthMethodDefinition.OAuthAuthorizationCode -> {
                checkUrl("$path.authorizeUrl", method.authorizeUrl, known, issues)
                checkUrl("$path.tokenUrl", method.tokenUrl, known, issues)
                checkOptionalUrl("$path.revokeUrl", method.revokeUrl, known, issues)
                checkClient("$path.client", method.client, known, issues)
            }
        }
    }

    private fun checkClient(path: String, client: OAuthClient, known: Set<String>, issues: MutableList<DefinitionIssue>) {
        if (client.mode != ClientMode.Fixed) return
        val id = client.id
        if (id.isNullOrBlank()) {
            issues.add(DefinitionIssue("$path.id", "A fixed client needs an id"))
        } else {
            checkTemplate("$path.id", id, known, issues)
        }
    }

    private fun checkOptionalUrl(path: String, url: String?, known: Set<String>, issues: MutableList<DefinitionIssue>) {
        if (url != null) checkUrl(path, url, known, issues)
    }

    /** A URL must be http(s), or start with a placeholder such as `{instance_url}` (self-hosted services). */
    private fun checkUrl(path: String, url: String, known: Set<String>, issues: MutableList<DefinitionIssue>) {
        val looksLikeUrl = url.startsWith("https://") || url.startsWith("http://") || url.startsWith("{")
        if (!looksLikeUrl) {
            issues.add(DefinitionIssue(path, "Must start with https://, http:// or a {placeholder}"))
        }
        checkTemplate(path, url, known, issues)
    }

    private fun checkTemplate(path: String, template: String, known: Set<String>, issues: MutableList<DefinitionIssue>) {
        for (name in Templates.placeholdersIn(template)) {
            if (name !in known) {
                issues.add(DefinitionIssue(path, "Unknown placeholder {$name}. Add a field with that key or use one of: ${known.joinToString()}"))
            }
        }
    }

    private fun isValidId(id: String): Boolean = id.isNotEmpty() && id.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
}

/** The result of [AuthDefinitionParser.parse]: the definition if it's usable, and every problem found. */
data class ParsedAuthDefinition(
    val definition: AuthProviderDefinition?,
    val issues: List<DefinitionIssue>,
)
