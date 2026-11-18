package uk.dnpk.sekund.runtime.api

import uk.dnpk.sekund.protocol.api.InstanceIssue
import uk.dnpk.sekund.protocol.config.AppConfig
import uk.dnpk.sekund.runtime.plugins.PluginRegistry
import kotlin.time.Duration

/**
 * Checks every instance's settings in a config without starting anything, so a config change can be
 * answered with its problems straight away (the running instances update a moment later).
 */
object ConfigIssues {
    fun of(config: AppConfig, registry: PluginRegistry): List<InstanceIssue> {
        val issues = mutableListOf<InstanceIssue>()
        val seen = mutableSetOf<String>()
        for (instance in config.instances) {
            if (!seen.add(instance.id)) {
                issues.add(InstanceIssue(instance.id, "id", "This id is used twice; the second instance is ignored"))
                continue
            }
            val contribution = registry.contribution(instance.type)
            if (contribution == null) {
                issues.add(InstanceIssue(instance.id, "type", "No installed plugin provides '${instance.type}'"))
                continue
            }
            for (issue in contribution.settings.decode(instance.settings).issues) {
                issues.add(InstanceIssue(instance.id, "settings.${issue.path}", issue.message))
            }
            val interval = instance.refreshInterval
            if (interval != null && Duration.parseOrNull(interval) == null) {
                issues.add(InstanceIssue(instance.id, "refreshInterval", "'$interval' isn't a duration like \"15m\""))
            }
        }
        val known = config.instances.map { instance -> instance.id }.toSet()
        for (id in config.layout.main + config.layout.sidebar) {
            if (id !in known) issues.add(InstanceIssue(id, "layout", "The layout mentions '$id', but no instance has that id"))
        }
        return issues
    }
}
