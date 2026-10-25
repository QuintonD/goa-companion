package dev.quinton.goa.web.catalog

import androidx.compose.runtime.Composable
import dev.quinton.goa.core.domain.catalog.ATTACK_STYLE_NOTE
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.CatalogResults
import dev.quinton.goa.core.domain.catalog.cleared
import dev.quinton.goa.core.domain.catalog.complexityName
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text

/**
 * The filter sheet (feature:catalog `CatalogFilterContent`): every change applies at once (the
 * count on the button follows), so "Show N heroes" just closes it. Within a facet the options are
 * OR-ed, across facets AND-ed.
 */
@Composable
internal fun FilterSheet(browse: CatalogBrowse, results: CatalogResults, onDone: () -> Unit) {
    val filter = results.filter
    val options = results.options
    fun set(next: CatalogFilter) {
        browse.filter = next
    }
    BottomSheet("Filters", onDismiss = onDone) {
        Div({ classes("goa-filter-clear") }) {
            GoaButton("Clear all", { set(filter.cleared()) }, ButtonKind.TEXT, enabled = filter.activeCount > 0)
        }
        if (options.complexities.isNotEmpty()) {
            FilterGroup("Complexity") {
                options.complexities.forEach { level ->
                    ToggleChip(
                        selected = level in filter.complexities,
                        label = "Complexity $level, ${complexityName(level).orEmpty()}",
                        onClick = { set(filter.copy(complexities = filter.complexities.toggle(level))) },
                    ) {
                        Div({ classes("goa-chip-stars") }) { ComplexityStars(level) }
                        complexityName(level)?.let { Text(it) }
                    }
                }
            }
        }
        if (options.roles.isNotEmpty()) {
            FilterGroup("Role") {
                options.roles.forEach { role ->
                    ToggleChip(role in filter.roles, onClick = {
                        set(filter.copy(roles = filter.roles.toggle(role)))
                    }) { Text(role.label) }
                }
            }
        }
        FilterGroup("Attack cards", note = ATTACK_STYLE_NOTE) {
            options.attackStyles.forEach { style ->
                val selected = filter.attackStyle == style
                ToggleChip(selected, onClick = {
                    set(filter.copy(attackStyle = if (selected) null else style))
                }) { Text(style.label) }
            }
        }
        if (options.products.size > 1) {
            FilterGroup("Product") {
                options.products.forEach { product ->
                    ToggleChip(product.id in filter.products, onClick = {
                        set(filter.copy(products = filter.products.toggle(product.id)))
                    }) { Text(product.name) }
                }
            }
        }
        if (options.hasOwnedProducts) {
            Div({ classes("goa-filter-divider") })
            SwitchRow(
                "Include products you don't own",
                "Owned products are set in Settings",
                filter.includeUnowned,
                onChange = { set(filter.copy(includeUnowned = it, products = emptySet())) },
            )
        }
        Div({ classes("goa-sheet-footer") }) {
            val count = results.heroes.size
            GoaButton(
                if (count == 0) "No heroes match" else "Show ${plural(count, "hero", "heroes")}",
                onDone,
                block = true,
            )
        }
    }
}

@Composable
private fun FilterGroup(title: String, note: String? = null, content: @Composable () -> Unit) {
    Div({
        classes("goa-filter-group")
        attr("role", "group")
        attr("aria-label", title)
    }) {
        H3({ classes("t-title-small", "c-variant") }) { Text(title) }
        note?.let { P({ classes("t-body-small", "c-variant") }) { Text(it) } }
        Div({ classes("goa-chip-row", "wrap") }) { content() }
    }
}

/** A filter chip with arbitrary content: pressed = selected (a check replaces nothing, it leads). */
@Composable
private fun ToggleChip(selected: Boolean, label: String? = null, onClick: () -> Unit, content: @Composable () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-chip")
        attr("aria-pressed", selected.toString())
        label?.let { attr("aria-label", it) }
        onClick { onClick() }
    }) {
        if (selected) Icon("check", IconSize.SMALL)
        content()
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
