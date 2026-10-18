package dev.quinton.goa.feature.timer.ui.presets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.presentation.timer.PresetOption

data class PresetsActions(
    val onBack: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onEdit: (String?) -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onRestoreDefaults: () -> Unit = {},
)

@Composable
fun PresetsRoute(viewModel: PresetsViewModel, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PresetsScreen(
        state,
        PresetsActions(
            onBack = onBack,
            onSelect = viewModel::select,
            onEdit = onEdit,
            onDuplicate = viewModel::duplicate,
            onDelete = viewModel::delete,
            onRestoreDefaults = viewModel::restoreDefaults,
        ),
    )
}

@Composable
fun PresetsScreen(state: PresetsUiState, actions: PresetsActions) {
    GoaScreen(
        title = "Timer presets",
        onBack = actions.onBack,
        actions = {
            IconButton(onClick = actions.onRestoreDefaults) {
                Icon(Icons.Outlined.Restore, contentDescription = "Restore default presets")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.onEdit(null) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New preset") },
                // M3 1.4 leaves the extended FAB's text out of the semantics tree.
                modifier = Modifier.semantics { contentDescription = "New preset" },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).selectableGroup(),
            contentPadding = PaddingValues(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.sm,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Text(
                    "The selected preset is used when you start the timer. " +
                        "Timers are table rules; the rulebook sets none.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            items(state.presets, key = { it.id }) { preset ->
                PresetRow(preset, preset.id == state.selectedId, state.canDelete, actions)
            }
        }
    }
}

@Composable
private fun PresetRow(preset: PresetOption, selected: Boolean, canDelete: Boolean, actions: PresetsActions) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondary.copy(alpha = 0.10f) else colors.surface,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) colors.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { actions.onSelect(preset.id) }, role = Role.RadioButton),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                Text(preset.summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = { actions.onEdit(preset.id) }) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${preset.name}")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${preset.name}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.onDuplicate(preset.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        enabled = canDelete,
                        onClick = {
                            menu = false
                            actions.onDelete(preset.id)
                        },
                    )
                }
            }
        }
    }
}
