package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.ATTACK_STYLE_NOTE
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.FilterOptions
import dev.quinton.goa.core.domain.catalog.complexityName
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.plural

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
                style = GoaTextStyles.sectionHead.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = GoaTheme.colors.heading,
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
                        GoaFilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter.copy(complexities = filter.complexities.toggle(level))) },
                            modifier = Modifier.semantics {
                                contentDescription = "Complexity $level, ${complexityName(level).orEmpty()}"
                            },
                        ) {
                            Stars(level)
                            complexityName(level)?.let { Text("  $it") }
                        }
                    }
                }
            }
            if (options.roles.isNotEmpty()) {
                FilterGroup("Role") {
                    options.roles.forEach { role ->
                        val selected = role in filter.roles
                        GoaFilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter.copy(roles = filter.roles.toggle(role))) },
                        ) { Text(role.label) }
                    }
                }
            }
            FilterGroup("Attack cards", info = ATTACK_STYLE_NOTE) {
                options.attackStyles.forEach { style ->
                    val selected = filter.attackStyle == style
                    GoaFilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filter.copy(attackStyle = if (selected) null else style)) },
                    ) { Text(style.label) }
                }
            }
            if (options.products.size > 1) {
                FilterGroup("Product") {
                    options.products.forEach { product ->
                        val selected = product.id in filter.products
                        GoaFilterChip(
                            selected = selected,
                            onClick = { onFilterChange(filter.copy(products = filter.products.toggle(product.id))) },
                        ) { Text(product.name) }
                    }
                }
            }
            if (options.hasOwnedProducts) {
                HorizontalDivider(Modifier.padding(top = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    GoaSwitch(checked = filter.includeUnowned, onCheckedChange = null)
                }
            }
            Spacer(Modifier.size(Spacing.lg))
        }
        GoaButton(
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

/**
 * A group of the sheet's chips under the app's one section head. [info], how the app derives the group (the attack
 * style), stays a tap away behind the head's info button, so the chips come first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, info: String? = null, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    SectionHeader(
        title,
        padding = PaddingValues(top = Spacing.sm, bottom = Spacing.xs),
        trailing = info?.let {
            {
                IconButton(onClick = { open = !open }) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = if (open) "Hide how $title is worked out" else "How $title is worked out",
                        tint = if (open) GoaTheme.colors.selection else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
    if (info != null && open) {
        Text(
            info,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) { content() }
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

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
