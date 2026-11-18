package uk.dnpk.sekund.runtime.plugins

import co.touchlab.kermit.Logger
import uk.dnpk.sekund.protocol.plugin.PluginManifest
import uk.dnpk.sekund.sdk.plugin.Contribution
import uk.dnpk.sekund.sdk.plugin.ExtensionPoint
import uk.dnpk.sekund.sdk.plugin.ExtensionPoints
import uk.dnpk.sekund.sdk.plugin.PluginRegistrar
import uk.dnpk.sekund.sdk.plugin.SekundPlugin

/**
 * Everything the installed plugins provide, checked and indexed by type.
 *
 * Built once at startup from the plugin list. A plugin (or one contribution) that breaks the rules is
 * left out and listed in [problems]; it never stops the app from starting.
 */
class PluginRegistry(plugins: List<SekundPlugin>) {
    private val log = Logger.withTag("Plugins")

    private val loadedManifests = mutableListOf<PluginManifest>()
    private val contributionsByType = linkedMapOf<String, Contribution<*, *>>()
    private val pluginIdByType = mutableMapOf<String, String>()
    private val foundProblems = mutableListOf<String>()

    /** The manifests of every plugin that loaded. */
    val manifests: List<PluginManifest>
        get() = loadedManifests

    /** Everything that was left out, and why. Shown in settings and the device API. */
    val problems: List<String>
        get() = foundProblems

    init {
        for (plugin in plugins) {
            load(plugin)
        }
    }

    /** The contribution with this [type], e.g. `"weather"`, or null if no plugin provides it. */
    fun contribution(type: String): Contribution<*, *>? = contributionsByType[type]

    /** The id of the plugin that provides [type]. */
    fun pluginIdOf(type: String): String? = pluginIdByType[type]

    /** The manifest of the plugin that provides [type], or null if no plugin provides it. */
    fun manifestOf(type: String): PluginManifest? {
        val pluginId = pluginIdByType[type] ?: return null
        return loadedManifests.firstOrNull { manifest -> manifest.id == pluginId }
    }

    /** Every contribution of every plugin, in registration order. */
    fun allContributions(): List<Contribution<*, *>> = contributionsByType.values.toList()

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

    /** Every contribution to [point], e.g. all widget types. */
    fun <T : Any> contributionsTo(point: ExtensionPoint<T>): List<Contribution<T, *>> {
        val result = mutableListOf<Contribution<T, *>>()
        for (contribution in contributionsByType.values) {
            if (contribution.point == point) {
                // Safe: a contribution's T always matches its point's T (Contribution enforces it),
                // and we just checked the point.
                @Suppress("UNCHECKED_CAST")
                result.add(contribution as Contribution<T, *>)
            }
        }
        return result
    }

    private fun load(plugin: SekundPlugin) {
        val manifest = plugin.manifest
        if (!isValidId(manifest.id)) {
            problem("Plugin '${manifest.id}' has an invalid id (use lowercase letters, digits and dashes)")
            return
        }
        if (loadedManifests.any { loaded -> loaded.id == manifest.id }) {
            problem("Two plugins use the id '${manifest.id}'; the second was skipped")
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
            problem("Plugin '${manifest.id}' failed while registering: ${error.message}")
            return
        }

        loadedManifests.add(manifest)
        for (contribution in collected) {
            addContribution(manifest.id, contribution)
        }
    }

    private fun addContribution(pluginId: String, contribution: Contribution<*, *>) {
        val type = contribution.type
        if (!isValidId(type)) {
            problem("'$pluginId' contributes the invalid type '$type' (use lowercase letters, digits and dashes)")
            return
        }
        val existingOwner = pluginIdByType[type]
        if (existingOwner != null) {
            problem("'$pluginId' and '$existingOwner' both contribute the type '$type'; '$pluginId''s was skipped")
            return
        }
        val isWidget = contribution.point == ExtensionPoints.Widgets
        if (isWidget && contribution.widgetLocations.isEmpty()) {
            problem("Widget '$type' from '$pluginId' doesn't say where it can be placed (widgetLocations)")
            return
        }

        contributionsByType[type] = contribution
        pluginIdByType[type] = pluginId
    }

    private fun problem(message: String) {
        log.w { message }
        foundProblems.add(message)
    }

    private fun isValidId(id: String): Boolean = id.isNotEmpty() && id.all { char -> char in 'a'..'z' || char in '0'..'9' || char == '-' }
}
