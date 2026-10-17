package dev.quinton.goa.feature.draft.methods

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.draft.MethodOrigin
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.goaFilterChipColors

interface MethodsActions {
    fun back() {}

    fun setLayout(layout: PreviewLayout) {}

    fun create() {}

    fun edit(id: String) {}

    fun duplicate(id: String) {}

    fun delete(id: String) {}

    companion object {
        val None = object : MethodsActions {}
    }
}

@Composable
fun DraftMethodsDestination(viewModel: DraftMethodsViewModel, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.methodEvents.collect { if (it is MethodsEvent.Edit) onEdit(it.methodId) }
    }
    val actions = remember(viewModel) {
        object : MethodsActions {
            override fun back() = onBack()
            override fun setLayout(layout: PreviewLayout) = viewModel.setLayout(layout)
            override fun create() = onEdit(null)
            override fun edit(id: String) = onEdit(id)
            override fun duplicate(id: String) = viewModel.duplicate(id)
            override fun delete(id: String) = viewModel.delete(id)
        }
    }
    DraftMethodsScreen(state, actions)
}

@Composable
fun DraftMethodsScreen(state: MethodsUiState, actions: MethodsActions) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    GoaScreen(
        title = "Draft methods",
        onBack = actions::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions::create,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New method") },
                // M3 1.4 leaves the extended FAB's text out of the semantics tree.
                modifier = Modifier.semantics { contentDescription = "New method" },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.sm,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Column {
                    Text(
                        "Preview for",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PreviewLayout.OPTIONS.forEach { layout ->
                            FilterChip(
                                colors = goaFilterChipColors(),
                                selected = state.layout == layout,
                                onClick = { actions.setLayout(layout) },
                                label = { Text("${layout.label} heroes") },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Built in") }
            items(state.builtIn, key = { it.method.id }) { row -> MethodCard(row, actions, onDelete = null) }
            item { SectionHeader("Your methods") }
            if (state.custom.isEmpty()) {
                item {
                    Text(
                        "Duplicate a method to tweak it, or create one from scratch. Pool size, bans and the pick " +
                            "pattern are all editable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.custom, key = { it.method.id }) { row ->
                MethodCard(row, actions, onDelete = {
                    deleting =
                        row.method.id
                })
            }
        }
    }
    deleting?.let { id ->
        val name = state.custom.firstOrNull { it.method.id == id }?.method?.name ?: "this method"
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = { Text("Past drafts keep their record; the method just won't be offered again.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.delete(id)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

@Composable
private fun MethodCard(row: MethodRow, actions: MethodsActions, onDelete: (() -> Unit)?) {
    val method = row.method
    var notes by rememberSaveable(method.id) { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (row.isDefault) MaterialTheme.colorScheme.primary else GoaTheme.colors.engravedBorder,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(method.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Badge(
                    when {
                        row.isDefault -> "Default"
                        method.origin == MethodOrigin.RULEBOOK -> "Rulebook"
                        method.origin == MethodOrigin.HOUSE -> "House"
                        else -> "Custom"
                    },
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                method.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(poolText(method.pool) + heroesText(row.preview), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(Spacing.sm))
            when {
                row.preview.error != null -> Text(
                    row.preview.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                row.preview.tokens.isEmpty() -> Unit

                else -> SequenceStrip(row.preview.tokens)
            }
            if (notes && method.notes.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sm))
                method.notes.forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (method.notes.isNotEmpty()) {
                    TextButton(onClick = { notes = !notes }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (notes) "Hide sources" else "Sources")
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Delete")
                    }
                    TextButton(onClick = { actions.edit(method.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Edit")
                    }
                } else {
                    TextButton(onClick = { actions.duplicate(method.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Duplicate")
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
    }
}

internal fun poolText(pool: PoolRule): String = when (pool) {
    PoolRule.All -> "Pool: every eligible hero"
    is PoolRule.Random -> "Pool: ${pool.size} at random"
    is PoolRule.SeatsPlus -> if (pool.extra == 0) "Pool: one per hero seat" else "Pool: heroes + ${pool.extra} revealed"
    is PoolRule.DealPerSeat -> "Pool: ${pool.perSeat} dealt to each seat"
}

private fun heroesText(preview: SequencePreview): String =
    if (preview.error == null && preview.heroesNeeded > 0) " · needs ${preview.heroesNeeded}" else ""
