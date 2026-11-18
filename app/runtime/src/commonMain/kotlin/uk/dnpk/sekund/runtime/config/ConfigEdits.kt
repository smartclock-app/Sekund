package uk.dnpk.sekund.runtime.config

import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.protocol.config.LayoutConfig
import uk.dnpk.sekund.protocol.config.InstanceConfig
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.sdk.widget.WidgetLocation

/**
 * The everyday changes to a config, as plain functions: each takes a config and returns a changed copy.
 *
 * Shared by the on-device settings screens and the LAN API, so both behave the same way (e.g. removing
 * an instance always removes it from the layout too). Use them inside [ConfigRepository.update]:
 *
 * ```kotlin
 * config.update { current -> ConfigEdits.removeInstance(current, "weather-2") }
 * ```
 *
 * (Comparable to reducer functions in Redux: no side effects, easy to test.)
 */
object ConfigEdits {

    /**
     * A fresh instance id for a new instance of [type]: `"weather"`, or `"weather-2"`, `"weather-3"`...
     * if that's taken.
     */
    fun newInstanceId(config: AppConfig, type: String): String {
        val taken = config.instances.map { instance -> instance.id }.toSet()
        if (type !in taken) return type
        var number = 2
        while ("$type-$number" in taken) {
            number += 1
        }
        return "$type-$number"
    }

    /**
     * Adds [instance]. If [location] is given, it's also added to the end of that part of the layout.
     *
     * @throws IllegalArgumentException if an instance with the same id already exists.
     */
    fun addInstance(config: AppConfig, instance: InstanceConfig, location: WidgetLocation? = null): AppConfig {
        require(config.instances.none { existing -> existing.id == instance.id }) { "An instance called '${instance.id}' already exists" }
        val withInstance = config.copy(instances = config.instances + instance)
        if (location == null) return withInstance
        return addToLayout(withInstance, location, instance.id)
    }

    /** Replaces the instance with the same id as [instance]. Does nothing if there isn't one. */
    fun replaceInstance(config: AppConfig, instance: InstanceConfig): AppConfig {
        val updated = config.instances.map { existing -> if (existing.id == instance.id) instance else existing }
        return config.copy(instances = updated)
    }

    /** Sets one instance's settings (and optionally its refresh interval). */
    fun setSettings(config: AppConfig, instanceId: String, settings: JsonObject, refreshInterval: String?): AppConfig {
        val existing = config.instances.firstOrNull { instance -> instance.id == instanceId } ?: return config
        return replaceInstance(config, existing.copy(settings = settings, refreshInterval = refreshInterval))
    }

    /** Removes an instance and every place the layout mentions it. */
    fun removeInstance(config: AppConfig, instanceId: String): AppConfig {
        val layout = LayoutConfig(
            main = config.layout.main.filter { id -> id != instanceId },
            sidebar = config.layout.sidebar.filter { id -> id != instanceId },
        )
        return config.copy(
            instances = config.instances.filter { instance -> instance.id != instanceId },
            layout = layout,
        )
    }

    /** Adds [instanceId] to the end of [location]. Does nothing if it's already there. */
    fun addToLayout(config: AppConfig, location: WidgetLocation, instanceId: String): AppConfig {
        val ids = idsAt(config.layout, location)
        if (instanceId in ids) return config
        return withIds(config, location, ids + instanceId)
    }

    /** Removes [instanceId] from [location] (the instance itself stays configured). */
    fun removeFromLayout(config: AppConfig, location: WidgetLocation, instanceId: String): AppConfig {
        val ids = idsAt(config.layout, location)
        return withIds(config, location, ids.filter { id -> id != instanceId })
    }

    /**
     * Moves [instanceId] up (negative [steps]) or down (positive) within [location].
     * Moving past either end just stops at that end.
     */
    fun move(config: AppConfig, location: WidgetLocation, instanceId: String, steps: Int): AppConfig {
        val ids = idsAt(config.layout, location).toMutableList()
        val from = ids.indexOf(instanceId)
        if (from < 0) return config
        val to = (from + steps).coerceIn(0, ids.size - 1)
        ids.removeAt(from)
        ids.add(to, instanceId)
        return withIds(config, location, ids)
    }

    /** The instance ids placed at [location], in order. */
    fun idsAt(layout: LayoutConfig, location: WidgetLocation): List<String> = when (location) {
        WidgetLocation.Main -> layout.main
        WidgetLocation.Sidebar -> layout.sidebar
    }

    private fun withIds(config: AppConfig, location: WidgetLocation, ids: List<String>): AppConfig {
        val layout = when (location) {
            WidgetLocation.Main -> config.layout.copy(main = ids)
            WidgetLocation.Sidebar -> config.layout.copy(sidebar = ids)
        }
        return config.copy(layout = layout)
    }
}
