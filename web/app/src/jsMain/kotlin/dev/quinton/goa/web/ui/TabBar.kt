package dev.quinton.goa.web.ui

import androidx.compose.runtime.Composable
import dev.quinton.goa.web.nav.Tab
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Nav
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * The bottom tab bar: the Android app's four tabs in its order, the selected one filled in bronze
 * on a soft pill. Sits above the home indicator (safe area). Another tab reopens where it was;
 * the current tab returns to its first screen ([dev.quinton.goa.web.nav.Router.selectTab]).
 */
@Composable
fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Nav({
        classes("goa-tabbar")
        attr("aria-label", "Main")
    }) {
        Div({ classes("goa-tabbar-inner") }) {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                Button({
                    type(ButtonType.Button)
                    classes("goa-tab")
                    attr("data-tab", tab.name.lowercase())
                    if (selected) attr("aria-current", "page")
                    onClick { onSelect(tab) }
                }) {
                    Span({ classes("goa-tab-pill") }) {
                        Icon(if (selected) "nav_${tab.icon}_filled" else "nav_${tab.icon}")
                    }
                    Span({ classes("goa-tab-label") }) { Text(tab.label) }
                }
            }
        }
    }
}
