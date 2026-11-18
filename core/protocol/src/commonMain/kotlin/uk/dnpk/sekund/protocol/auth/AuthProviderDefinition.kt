package uk.dnpk.sekund.protocol.auth

import kotlinx.serialization.Serializable

/**
 * How to sign in to one service (Google, Trakt, Immich...), described as data: one `<id>.auth.json` file.
 *
 * Because providers are data, adding a standard OAuth or API-key service needs no Kotlin. Sekund's
 * auth registry reads these files, runs the sign-in flows, stores the credentials and attaches them to
 * plugins' HTTP requests. Plugins never see tokens.
 *
 * Text values may contain `{placeholders}`, filled in when used:
 * - `{access_token}`, `{api_key}`: the account's credential,
 * - `{client_id}`, `{client_secret}`: the OAuth client in use,
 * - `{<field key>}`: any of the account's [fields], e.g. `{instance_url}` for self-hosted services.
 *
 * Example (`immich.auth.json`):
 * ```json
 * {
 *   "id": "immich",
 *   "name": "Immich",
 *   "fields": [{ "key": "instance_url", "label": "Server address", "placeholder": "https://photos.example.com" }],
 *   "methods": [{ "type": "apiKey" }],
 *   "inject": { "type": "header", "name": "x-api-key", "format": "{api_key}" },
 *   "baseUrls": ["{instance_url}"]
 * }
 * ```
 *
 * @property id unique, lowercase letters, digits and dashes. Plugins refer to providers by this id.
 * @property methods the ways to sign in, **most preferred first**. Prefer `oauth2.deviceCode` where the
 *   service supports it: a wall display has no good keyboard, and a code + phone is much easier.
 * @property inject how a credential is attached to each request.
 * @property extraHeaders sent with every signed-in request, e.g. Trakt's `trakt-api-key: {client_id}`.
 * @property accountInfo optional: how to find a readable label (an email address...) for a new account.
 * @property baseUrls credentials are only ever sent to URLs starting with one of these. A plugin asking
 *   for anything else gets an error instead of leaking the token to another site.
 * @property fields extra things to ask for when connecting an account (e.g. a server address).
 */
@Serializable
data class AuthProviderDefinition(
    val id: String,
    val name: String,
    val icon: String? = null,
    val methods: List<AuthMethodDefinition>,
    val inject: CredentialInjection = CredentialInjection.Header(name = "Authorization", format = "Bearer {access_token}"),
    val extraHeaders: Map<String, String> = emptyMap(),
    val accountInfo: AccountInfoDefinition? = null,
    val baseUrls: List<String>,
    val fields: List<AccountField> = emptyList(),
)

/**
 * Something to ask for when connecting an account, besides the credential itself.
 *
 * @property key used as `{key}` in templates, e.g. `instance_url`.
 */
@Serializable
data class AccountField(
    val key: String,
    val label: String,
    val description: String? = null,
    val placeholder: String? = null,
)

/**
 * How to find a label for a newly connected account (shown in the Accounts screen).
 *
 * @property url fetched with the new credential.
 * @property label where in the JSON response the label is, as a simple path like `$.email` or `$.user.name`.
 */
@Serializable
data class AccountInfoDefinition(
    val url: String,
    val label: String,
)
