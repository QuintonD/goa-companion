package dev.quinton.goa.feature.players.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.data.backup.BackupSummary
import dev.quinton.goa.core.data.backup.ImportMode
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.players.common.Dates
import dev.quinton.goa.feature.players.common.EngravedCard
import dev.quinton.goa.feature.players.common.plural

data class BackupActions(
    val onBack: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    /** Mode, and whether the file's settings are used (always for Replace). */
    val onConfirmImport: (ImportMode, Boolean) -> Unit = { _, _ -> },
    val onCancelImport: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun BackupScreen(state: BackupUiState, actions: BackupActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            actions.onMessageShown()
        }
    }
    GoaScreen(title = "Backup and restore", modifier = modifier, onBack = actions.onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                if (state.working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                }
                BackupCard(
                    icon = Icons.Outlined.FileUpload,
                    title = "Export",
                    body =
                        "Save every player, match and draft, the card trivia leaderboard, your boards, your " +
                            "settings, timer presets and custom draft methods to one JSON file. " +
                            "Keep it somewhere safe, or open it on a new phone.",
                    detail = "${plural(state.players, "player")} · ${plural(state.matches, "match", "matches")}",
                ) {
                    Button(
                        onClick = actions.onExport,
                        enabled = !state.working,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Export backup")
                    }
                }
                BackupCard(
                    icon = Icons.Outlined.FileDownload,
                    title = "Import",
                    body =
                        "Load a backup file. You'll see what's inside and choose to merge it with this phone's data " +
                            "or replace everything.",
                    detail = null,
                ) {
                    OutlinedButton(
                        onClick = actions.onImport,
                        enabled = !state.working,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Choose backup file")
                    }
                }
                Note(
                    Icons.Outlined.Lock,
                    "No storage permission is needed: you pick the file, and nothing leaves the phone " +
                        "unless you share it.",
                )
                Note(
                    Icons.Outlined.CloudDone,
                    "Android's own device backup also keeps a copy of your data if it's turned on.",
                )
                Text(
                    "Ratings aren't stored in the file; they're rebuilt from the match log after an import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Spacing.lg))
        }
    }
    state.pendingImport?.let { summary ->
        ImportDialog(
            summary,
            onConfirm = actions.onConfirmImport,
            onCancel = actions.onCancelImport,
            phoneIsEmpty = state.players == 0 && state.matches == 0,
        )
    }
}

@Composable
private fun BackupCard(
    icon: ImageVector,
    title: String,
    body: String,
    detail: String?,
    action: @Composable () -> Unit,
) {
    EngravedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.md))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(Spacing.xs))
            action()
        }
    }
}

@Composable
private fun Note(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Confirms an import. [phoneIsEmpty]: nothing to keep, so Replace is preselected and the file's
 * settings come along. In Merge the user decides whether the file's settings replace the phone's.
 */
@Composable
fun ImportDialog(
    summary: BackupSummary,
    onConfirm: (ImportMode, Boolean) -> Unit,
    onCancel: () -> Unit,
    phoneIsEmpty: Boolean = false,
) {
    var mode by rememberSaveable { mutableStateOf(if (phoneIsEmpty) ImportMode.REPLACE else ImportMode.MERGE) }
    var useSettings by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
        title = { Text("Import backup?") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "Exported ${Dates.day(summary.exportedAt)} · ${plural(summary.players, "player")} · " +
                        "${plural(summary.matches, "match", "matches")} · ${plural(summary.drafts, "draft")}" +
                        (if (summary.triviaRounds > 0) " · ${plural(summary.triviaRounds, "trivia round")}" else "") +
                        (summary.boards?.takeIf { it > 0 }?.let { " · ${plural(it, "board")}" } ?: "") +
                        if (summary.hasEloConfig) " · custom rating settings" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (summary.unknownHeroes.isNotEmpty()) {
                    val one = summary.unknownHeroes.size == 1
                    Text(
                        "${plural(summary.unknownHeroes.size, "hero", "heroes")} in this file " +
                            (if (one) "isn't" else "aren't") + " in this app's catalog " +
                            "(${summary.unknownHeroes.joinToString()}). " + (if (one) "Its" else "Their") +
                            " seats import as Unknown hero and count for their pilots only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.selectableGroup()) {
                    ModeOption(
                        selected = mode == ImportMode.MERGE,
                        title = "Merge",
                        body = "Keep this phone's data. Add what's new; items in both are taken from the file.",
                        onClick = { mode = ImportMode.MERGE },
                    )
                    ModeOption(
                        selected = mode == ImportMode.REPLACE,
                        title = "Replace everything",
                        body = replaceBody(summary, phoneIsEmpty),
                        onClick = { mode = ImportMode.REPLACE },
                    )
                }
                if (mode == ImportMode.MERGE && summary.hasSettings) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = useSettings, role = Role.Checkbox, onValueChange = { useSettings = it })
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(checked = useSettings, onCheckedChange = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text("Use the file's settings", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (summary.extras.isEmpty()) {
                                    // Only rating constants in the file (e.g. an older export).
                                    "Rating settings (custom in this file). Off keeps this phone's."
                                } else {
                                    "Rating settings" + (if (summary.hasEloConfig) " (custom in this file)" else "") +
                                        ", theme, owned products, sounds and the selected timer preset. " +
                                        "Off keeps this phone's."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode, mode == ImportMode.REPLACE || useSettings) }) {
                Text(
                    if (mode == ImportMode.REPLACE) "Replace" else "Merge",
                    color = if (mode == ImportMode.REPLACE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/**
 * What Replace does. A file without settings (e.g. a version 1 export) leaves the phone's app
 * settings, timer presets and draft methods alone; only the rating settings go back to the
 * defaults the file was made with (`BackupRepository.import`).
 */
internal fun replaceBody(summary: BackupSummary, phoneIsEmpty: Boolean): String {
    // Trivia rounds go with the match log; boards only when the file has a boards list (an older
    // file says nothing about them, so the phone's boards stay).
    val deleted = if (summary.boards != null) {
        "Delete this phone's players, matches, drafts, trivia rounds and boards first"
    } else {
        "Delete this phone's players, matches, drafts and trivia rounds first (its boards stay)"
    }
    return when {
        phoneIsEmpty && summary.hasSettings ->
            "This phone has no players or matches yet. Load the file as it is, settings included."

        phoneIsEmpty -> "This phone has no players or matches yet. Load the file as it is."

        summary.extras.isNotEmpty() -> "$deleted and use the file's settings. This can't be undone."

        summary.hasEloConfig ->
            "$deleted and use the file's rating settings; the phone keeps its other settings. This can't be undone."

        else ->
            "$deleted. This file has no settings: rating settings go back to the defaults and the phone " +
                "keeps its other settings. This can't be undone."
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, body: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
