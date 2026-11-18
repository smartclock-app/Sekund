package uk.dnpk.sekund.plugins.declarative

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import uk.dnpk.sekund.plugins.declarative.views.DeclarativeView
import uk.dnpk.sekund.plugins.declarative.views.ViewContext
import uk.dnpk.sekund.protocol.declarative.DeclarativeDefinition
import uk.dnpk.sekund.protocol.declarative.ViewDefinition
import uk.dnpk.sekund.sdk.context.InstanceContext
import uk.dnpk.sekund.sdk.context.Loadable
import uk.dnpk.sekund.sdk.widget.Widget
import uk.dnpk.sekund.sdk.widget.WidgetLocation
import uk.dnpk.sekund.ui.components.ErrorCard
import uk.dnpk.sekund.ui.components.MessageCard
import uk.dnpk.sekund.ui.theme.Sekund

/**
 * One instance of a declarative widget: polls its source on the definition's schedule and draws the
 * result with the definition's view. If the definition has an `expanded` view with its own source,
 * that source is polled too.
 */
class DeclarativeWidget(
    private val definition: DeclarativeDefinition,
    context: InstanceContext<JsonObject>,
) : Widget {
    private val settings = context.settings
    private val templates = TemplateEngine(
        lookups = definition.lookups,
        timeZone = { context.time.timeZone.value },
        preferences = { context.time.preferences.value },
    )
    private val runner = SourceRunner(definition, settings, context.http, context.auth, templates)
    private val images = ImageLoader(runner)

    private val main: StateFlow<Loadable<MappedData>> =
        context.refresher.poll(Refresh.policyFor(definition.source)) { runner.run(definition.source) }

    /** The expanded view's own data, if it names a separate source. Otherwise it reuses [main]. */
    private val expandedData: StateFlow<Loadable<MappedData>>? = expandedSourceFlow(context)

    private fun expandedSourceFlow(context: InstanceContext<JsonObject>): StateFlow<Loadable<MappedData>>? {
        val name = definition.expanded?.source ?: return null
        val source = definition.sources[name] ?: return null
        return context.refresher.poll(Refresh.policyFor(source)) { runner.run(source) }
    }

    override val hasExpandedView: Boolean
        get() = definition.expanded != null

    @Composable
    override fun Content(location: WidgetLocation) {
        val view = definition.view ?: return
        val state by main.collectAsState()
        Show(state, view, large = location == WidgetLocation.Main)
    }

    @Composable
    override fun ExpandedContent() {
        val expanded = definition.expanded ?: return
        val state by (expandedData ?: main).collectAsState()
        Show(state, expanded.view, large = true)
    }

    @Composable
    private fun Show(state: Loadable<MappedData>, view: ViewDefinition, large: Boolean) {
        when (state) {
            Loadable.Loading -> MessageCard(title = definition.manifest.name, message = "Loading…")
            is Loadable.Failed -> ErrorCard(title = "${definition.manifest.name} couldn't load", message = state.message)
            is Loadable.Ready -> Column {
                DeclarativeView(view, ViewContext(state.data, settings, templates, images), large)
                val problem = state.refreshError
                if (problem != null) {
                    // Last good data is still shown; say it may be out of date.
                    Text("Not updated: $problem", color = Sekund.theme.colors.warning)
                }
            }
        }
    }
}
