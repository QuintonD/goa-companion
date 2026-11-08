package dev.quinton.goa.feature.draft.methods

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.ui.COIN_TEAM
import dev.quinton.goa.feature.draft.ui.OTHER_TEAM
import dev.quinton.goa.feature.draft.ui.PlateBar
import dev.quinton.goa.feature.draft.ui.SelectionRadio
import dev.quinton.goa.feature.draft.ui.SequenceStrip
import dev.quinton.goa.feature.draft.ui.TeamText
import dev.quinton.goa.feature.draft.ui.coinTeamWords
import dev.quinton.goa.feature.draft.ui.teamGlyph
import dev.quinton.goa.feature.draft.ui.teamName

interface MethodsActions {
    fun back() {}

    fun setLayout(layout: PreviewLayout) {}

    /** The draft setup will use this method. */
    fun select(id: String) {}

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
            override fun select(id: String) = viewModel.select(id)
            override fun create() = onEdit(null)
            override fun edit(id: String) = onEdit(id)
            override fun duplicate(id: String) = viewModel.duplicate(id)
            override fun delete(id: String) = viewModel.delete(id)
        }
    }
    DraftMethodsScreen(state, actions)
}

/**
 * Choose how heroes are drafted: each method a radio plate with its sequence (in team colours, as the
 * board shows it), the chosen one bronze, and "Continue with …" pinned below. Managing methods (Sources,
 * Duplicate, Edit, Delete) sits in each plate's menu, out of the choice's way; New method ends the list.
 */
@Composable
fun DraftMethodsScreen(state: MethodsUiState, actions: MethodsActions) {
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    GoaScreen(
        title = "Draft methods",
        onBack = actions::back,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f).selectableGroup(),
            ) {
                item { PreviewFor(state, actions) }
                item { SectionHeader("Built in") }
                items(state.builtIn, key = { it.method.id }) { row ->
                    MethodCard(row, row.method.id == state.selectedId, actions, onDelete = null)
                }
                item { SectionHeader("Your methods") }
                if (state.custom.isEmpty()) {
                    item {
                        Text(
                            "No custom methods yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.custom, key = { it.method.id }) { row ->
                    MethodCard(row, row.method.id == state.selectedId, actions, onDelete = { deleting = row.method.id })
                }
                item {
                    GoaOutlinedButton(onClick = actions::create, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.xs))
                        Text("New method")
                    }
                }
            }
            ContinueBar(state.selected, actions)
        }
    }
    deleting?.let { id ->
        val name = state.custom.firstOrNull { it.method.id == id }?.method?.name ?: "this method"
        GoaDialog(
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

/** Pinned under the list: the choice is saved as it is made, so this goes back to the setup with it. */
@Composable
private fun ContinueBar(selected: MethodRow?, actions: MethodsActions) {
    PlateBar {
        GoaButton(
            onClick = actions::back,
            enabled = selected != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
                .heightIn(min = 56.dp),
        ) {
            Text(
                selected?.let { "Continue with ${it.method.name}" } ?: "Choose a method",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The seat layout the sequences are shown for, and which colour they show going first. */
@Composable
private fun PreviewFor(state: MethodsUiState, actions: MethodsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "Preview for",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            PreviewLayout.OPTIONS.forEach { layout ->
                val chosen = state.layout == layout
                GoaFilterChip(
                    selected = chosen,
                    onClick = { actions.setLayout(layout) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("${layout.label} heroes") }
            }
        }
        // The methods' Team A and Team B, named as the table knows them: the coin's team and the other one.
        // The steps below show each in a team's colour and mark; this says which is which.
        val coin = state.previewTeamA
        val other = if (coin == Team.RED) Team.BLUE else Team.RED
        TeamText(
            "${teamGlyph(coin)} $COIN_TEAM    ${teamGlyph(other)} $OTHER_TEAM",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs).semantics {
                contentDescription = if (state.coinKnown) {
                    "The coin team is ${teamName(coin)}, the other team ${teamName(other)}."
                } else {
                    "Steps in ${teamName(coin)} are the coin team's, steps in ${teamName(other)} the other team's."
                }
            },
        )
    }
}

@Composable
private fun MethodCard(row: MethodRow, selected: Boolean, actions: MethodsActions, onDelete: (() -> Unit)?) {
    val method = row.method
    var notes by rememberSaveable(method.id) { mutableStateOf(false) }
    GoaPlate(
        Modifier
            .fillMaxWidth()
            .clip(PlateShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { actions.select(method.id) }),
        contentPadding = PaddingValues(0.dp),
        selected = selected,
    ) {
        // "Method details" (a 48 dp row) ends the plate: it brings its own room below, so the plate adds little.
        Row(Modifier.fillMaxWidth().padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs)) {
            SelectionRadio(selected, Modifier.padding(top = Spacing.md, end = Spacing.xs))
            Column(Modifier.weight(1f).padding(top = Spacing.sm)) {
                MethodTitle(row)
                Spacer(Modifier.height(Spacing.xs))
                Text(row.poolLine, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(Spacing.sm))
                // The steps carry the counts ("4 · ⚙ picks 2"), so the prose that spelled them out folds away.
                val previewError = row.preview.error
                when {
                    previewError != null -> Text(
                        previewError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    row.preview.tokens.isEmpty() -> Unit

                    else -> SequenceStrip(row.preview.tokens, named = false)
                }
                MethodDetails(coinTeamWords(method.summary))
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
            }
            MethodMenu(
                name = method.name,
                notes = if (method.notes.isEmpty()) null else notes,
                onNotes = { notes = !notes },
                onDuplicate = { actions.duplicate(method.id) },
                onEdit = if (onDelete != null) ({ actions.edit(method.id) }) else null,
                onDelete = onDelete,
            )
        }
    }
}

/** "Method details": the method's own description, folded under a quiet text button. */
@Composable
private fun MethodDetails(summary: String) {
    if (summary.isBlank()) return
    var open by rememberSaveable(summary) { mutableStateOf(false) }
    TextButton(
        onClick = { open = !open },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = Spacing.xs),
        modifier = Modifier.heightIn(min = 48.dp).semantics {
            stateDescription = if (open) "Expanded" else "Collapsed"
        },
    ) {
        Text("Method details")
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(if (open) HALF_TURN else 0f),
        )
    }
    if (open) {
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val HALF_TURN = 180f

@Composable
private fun MethodTitle(row: MethodRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A list item's title, as on every other list (Cinzel is for section heads).
        Text(
            row.method.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(Spacing.sm))
        Badge(row.badge)
    }
}

/** Managing a method, apart from choosing it: Sources, Duplicate and, for your own, Edit and Delete. */
@Composable
private fun MethodMenu(
    name: String,
    notes: Boolean?,
    onNotes: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More for $name")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val close = { open = false }
            if (notes != null) {
                MenuItem(if (notes) "Hide sources" else "Sources", Icons.AutoMirrored.Outlined.MenuBook, close, onNotes)
            }
            if (onEdit != null) MenuItem("Edit", Icons.Outlined.Edit, close, onEdit)
            MenuItem("Duplicate", Icons.Outlined.ContentCopy, close, onDuplicate)
            if (onDelete != null) MenuItem("Delete", Icons.Outlined.Delete, close, onDelete)
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, close: () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = {
            close()
            onClick()
        },
    )
}

@Composable
private fun Badge(text: String) {
    val secondary = MaterialTheme.colorScheme.secondary
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = secondary,
        modifier = Modifier
            .background(secondary.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, secondary.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = 2.dp),
    )
}
