package uk.dnpk.sekund.runtime.plugins

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * Everything the installed plugins provide, checked and indexed by type.
 *
 * There are two kinds of plugin:
 * - **compiled** plugins, fixed when the app starts (the `plugins` list),
 * - **extra** plugins that can change while the app runs: declarative widgets loaded from JSON files.
 *   [setExtraPlugins] replaces them, and [version] goes up so the instance manager can recreate the
 *   instances whose plugin changed.
 *
 * A plugin (or one contribution) that breaks the rules is left out and listed in [problems]; it never
 * stops the app from starting. If an extra plugin uses the same id or type as a compiled one, the
 * compiled one wins.
 */
class PluginRegistry(compiled: List<SekundPlugin>) {
    private val log = Logger.withTag("Plugins")

    /** Everything worked out from one set of plugins. Replaced as a whole, never changed in place. */
    private class Index(
        val manifests: List<PluginManifest>,
        val contributionsByType: Map<String, Contribution<*, *>>,
        val pluginIdByType: Map<String, String>,
        val extraPluginIds: Set<String>,
        val problems: List<String>,
    )

    /**
     * The compiled plugins, registered once. Rebuilding with new extra plugins starts from this, so
     * compiled contributions stay the same objects and their instances keep running.
     */
    private val compiledIndex: Index = build(base = null, plugins = compiled, isExtra = false)

    private val current = MutableStateFlow(compiledIndex)
    private val currentVersion = MutableStateFlow(0)

    /** Goes up by one every time the set of plugins changes. Watch it to react to new or removed plugins. */
    val version: StateFlow<Int> = currentVersion

    /** The manifests of every plugin that loaded. */
    val manifests: List<PluginManifest>
        get() = current.value.manifests

    /** Everything that was left out, and why. Shown in settings and the device API. */
    val problems: List<String>
        get() = current.value.problems

    /** Replaces the extra (declarative) plugins. */
    fun setExtraPlugins(extra: List<SekundPlugin>) {
        current.value = build(base = compiledIndex, plugins = extra, isExtra = true)
        currentVersion.value += 1
    }

    /** True if [pluginId] is an extra (declarative) plugin rather than a compiled one. */
    fun isExtra(pluginId: String): Boolean = pluginId in current.value.extraPluginIds

    /** The contribution with this [type], e.g. `"weather"`, or null if no plugin provides it. */
    fun contribution(type: String): Contribution<*, *>? = current.value.contributionsByType[type]

    /** The id of the plugin that provides [type]. */
    fun pluginIdOf(type: String): String? = current.value.pluginIdByType[type]

    /** The manifest of the plugin that provides [type], or null if no plugin provides it. */
    fun manifestOf(type: String): PluginManifest? {
        val index = current.value
        val pluginId = index.pluginIdByType[type] ?: return null
        return index.manifests.firstOrNull { manifest -> manifest.id == pluginId }
    }

    /** Every contribution of every plugin, in registration order. */
    fun allContributions(): List<Contribution<*, *>> = current.value.contributionsByType.values.toList()

    /** Every contribution to [point], e.g. all widget types. */
    fun <T : Any> contributionsTo(point: ExtensionPoint<T>): List<Contribution<T, *>> {
        val result = mutableListOf<Contribution<T, *>>()
        for (contribution in current.value.contributionsByType.values) {
            if (contribution.point == point) {
                // Safe: a contribution's T always matches its point's T (Contribution enforces it),
                // and we just checked the point.
                @Suppress("UNCHECKED_CAST")
                result.add(contribution as Contribution<T, *>)
            }
        }
        return result
    }

    /**
     * Why instances of [type] can't run right now, or null if they can. Currently: the plugin needs a
     * sign-in provider that isn't in [installedProviders].
     */
    fun unavailableReason(type: String, installedProviders: Set<String>): String? {
        val manifest = manifestOf(type) ?: return "No installed plugin provides '$type'"
        for (requirement in manifest.requires.auth) {
            if (!requirement.optional && requirement.provider !in installedProviders) {
                return "Needs the '${requirement.provider}' sign-in provider, which isn't installed"
            }
        }
        return null
    }

    // ---- Building an index ----------------------------------------------------------------------

    /** Collects what's being built, so the helper functions below don't need long parameter lists. */
    private class Builder {
        val manifests = mutableListOf<PluginManifest>()
        val contributionsByType = linkedMapOf<String, Contribution<*, *>>()
        val pluginIdByType = mutableMapOf<String, String>()
        val extraPluginIds = mutableSetOf<String>()
        val problems = mutableListOf<String>()
    }

    /** Registers [plugins] on top of [base] (or from nothing if [base] is null). */
    private fun build(base: Index?, plugins: List<SekundPlugin>, isExtra: Boolean): Index {
        val builder = Builder()
        if (base != null) {
            builder.manifests.addAll(base.manifests)
            builder.contributionsByType.putAll(base.contributionsByType)
            builder.pluginIdByType.putAll(base.pluginIdByType)
            builder.extraPluginIds.addAll(base.extraPluginIds)
            builder.problems.addAll(base.problems)
        }
        for (plugin in plugins) {
            load(plugin, builder, isExtra)
        }
        return Index(builder.manifests, builder.contributionsByType, builder.pluginIdByType, builder.extraPluginIds, builder.problems)
    }

    private fun load(plugin: SekundPlugin, builder: Builder, isExtra: Boolean) {
        val manifest = plugin.manifest
        if (!isValidId(manifest.id)) {
            problem(builder, "Plugin '${manifest.id}' has an invalid id (use lowercase letters, digits and dashes)")
            return
        }
        if (builder.manifests.any { loaded -> loaded.id == manifest.id }) {
            problem(builder, "Two plugins use the id '${manifest.id}'; the second was skipped")
            return
        }

        // Collect the plugin's contributions first, so a crash halfway leaves nothing half-registered.
        val collected = mutableListOf<Contribution<*, *>>()
        val registrar = object : PluginRegistrar {
            override fun <T : Any, S> contribute(contribution: Contribution<T, S>) {
                collected.add(contribution)
            }
        }
        try {
            plugin.register(registrar)
        } catch (error: Exception) {
            problem(builder, "Plugin '${manifest.id}' failed while registering: ${error.message}")
            return
        }

        builder.manifests.add(manifest)
        if (isExtra) builder.extraPluginIds.add(manifest.id)
        for (contribution in collected) {
            addContribution(builder, manifest.id, contribution)
        }
    }

    private fun addContribution(builder: Builder, pluginId: String, contribution: Contribution<*, *>) {
        val type = contribution.type
        if (!isValidId(type)) {
            problem(builder, "'$pluginId' contributes the invalid type '$type' (use lowercase letters, digits and dashes)")
            return
        }
        val existingOwner = builder.pluginIdByType[type]
        if (existingOwner != null) {
            problem(builder, "'$pluginId' and '$existingOwner' both contribute the type '$type'; '$pluginId''s was skipped")
            return
        }
        val isWidget = contribution.point == ExtensionPoints.Widgets
        if (isWidget && contribution.widgetLocations.isEmpty()) {
            problem(builder, "Widget '$type' from '$pluginId' doesn't say where it can be placed (widgetLocations)")
            return
        }

        builder.contributionsByType[type] = contribution
        builder.pluginIdByType[type] = pluginId
    }

    private fun problem(builder: Builder, message: String) {
        log.w { message }
        builder.problems.add(message)
    }

    private fun isValidId(id: String): Boolean = id.isNotEmpty() && id.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
}
