package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.catalog.model.CatalogFilter
import dev.quinton.goa.feature.catalog.ui.complexityName
import dev.quinton.goa.feature.catalog.ui.label
import dev.quinton.goa.feature.catalog.ui.plural

/**
 * Body of the filter sheet. Stateless: every change is applied immediately (the result count on
 * the button updates live), so "Show N heroes" simply closes the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogFilterContent(
    filter: CatalogFilter,
    options: FilterOptions,
    resultCount: Int,
    onFilterChange: (CatalogFilter) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Filters",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TextButton(onClick = onClear, enabled = filter.activeCount > 0) { Text("Clear all") }
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            if (options.complexities.isNotEmpty()) {
                FilterGroup("Complexity") {
                    options.complexities.forEach { level ->
                        val selected = level in filter.complexities
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter.copy(complexities = filter.complexities.toggle(level))) },
                            label = {
                                Stars(level)
                                complexityName(level)?.let { Text("  $it") }
                            },
                            leadingIcon = selectedIcon(selected),
                            modifier = Modifier.semantics {
                                contentDescription = "Complexity $level, ${complexityName(level).orEmpty()}"
                            },
                        )
                    }
                }
            }
            if (options.roles.isNotEmpty()) {
                FilterGroup("Role") {
                    options.roles.forEach { role ->
                        val selected = role in filter.roles
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter.copy(roles = filter.roles.toggle(role))) },
                            label = { Text(role.label) },
                            leadingIcon = selectedIcon(selected),
                        )
                    }
                }
            }
            FilterGroup("Attack") {
                options.attackStyles.forEach { style ->
                    val selected = filter.attackStyle == style
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filter.copy(attackStyle = if (selected) null else style)) },
                        label = { Text(style.label) },
                        leadingIcon = selectedIcon(selected),
                    )
                }
            }
            if (options.products.size > 1) {
                FilterGroup("Product") {
                    options.products.forEach { product ->
                        val selected = product.id in filter.products
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter.copy(products = filter.products.toggle(product.id))) },
                            label = { Text(product.name) },
                            leadingIcon = selectedIcon(selected),
                        )
                    }
                }
            }
            if (options.hasOwnedProducts) {
                HorizontalDivider(Modifier.padding(top = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .toggleable(
                            value = filter.includeUnowned,
                            role = Role.Switch,
                            onValueChange = { onFilterChange(filter.copy(includeUnowned = it, products = emptySet())) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Include products you don't own", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Owned products are set in Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = filter.includeUnowned, onCheckedChange = null)
                }
            }
            Spacer(Modifier.size(Spacing.lg))
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .heightIn(min = 52.dp),
        ) {
            Text(if (resultCount == 0) "No heroes match" else "Show ${plural(resultCount, "hero", "heroes")}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs).semantics { heading() },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
}

@Composable
private fun Stars(count: Int) {
    Row {
        repeat(count) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun selectedIcon(selected: Boolean): (@Composable () -> Unit)? = if (selected) {
    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
} else {
    null
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
