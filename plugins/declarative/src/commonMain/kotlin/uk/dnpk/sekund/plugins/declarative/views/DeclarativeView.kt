package uk.dnpk.sekund.plugins.declarative.views

import androidx.compose.runtime.Composable
import uk.dnpk.sekund.protocol.declarative.ViewDefinition

/**
 * Draws [view] with the data in [context]. One branch per built-in template; definitions can't run code,
 * so this list is everything a declarative widget can look like.
 *
 * @param large true in the main square or the expanded view, where there's room for bigger text.
 */
@Composable
fun DeclarativeView(view: ViewDefinition, context: ViewContext, large: Boolean) {
    when (view) {
        is ViewDefinition.Stat -> StatView(view, context, large)
        is ViewDefinition.ListView -> ListRowsView(view, context)
        is ViewDefinition.KeyValue -> KeyValueView(view, context)
        is ViewDefinition.Image -> ImageView(view, context)
        is ViewDefinition.Progress -> ProgressView(view, context)
    }
}
