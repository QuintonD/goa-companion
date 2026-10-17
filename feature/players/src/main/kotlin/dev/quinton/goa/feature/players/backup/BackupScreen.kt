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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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

data class BackupActions(
    val onBack: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmImport: (ImportMode) -> Unit = {},
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
                    body = "Save every player, match, draft and your rating settings to one JSON file. " +
                        "Keep it somewhere safe, or open it on a new phone.",
                    detail = "${state.players} players · ${state.matches} matches",
                ) {
                    Button(onClick = actions.onExport, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Export backup")
                    }
                }
                BackupCard(
                    icon = Icons.Outlined.FileDownload,
                    title = "Import",
                    body = "Load a backup file. You'll see what's inside and choose to merge it with this phone's data " +
                        "or replace everything.",
                    detail = null,
                ) {
                    OutlinedButton(onClick = actions.onImport, enabled = !state.working, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Choose backup file")
                    }
                }
                Note(Icons.Outlined.Lock, "No storage permission is needed: you pick the file, and nothing leaves the phone unless you share it.")
                Note(Icons.Outlined.CloudDone, "Android's own device backup also keeps a copy of your data if it's turned on.")
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
        ImportDialog(summary, onConfirm = actions.onConfirmImport, onCancel = actions.onCancelImport)
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
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(Spacing.xs))
            action()
        }
    }
}

@Composable
private fun Note(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ImportDialog(summary: BackupSummary, onConfirm: (ImportMode) -> Unit, onCancel: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(ImportMode.MERGE) }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
        title = { Text("Import backup?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "Exported ${Dates.day(summary.exportedAt)} · ${summary.players} players · ${summary.matches} matches · " +
                        "${summary.drafts} drafts" + if (summary.hasEloConfig) " · custom rating settings" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                        body = "Delete this phone's players, matches and drafts first. This can't be undone.",
                        onClick = { mode = ImportMode.REPLACE },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode) }) {
                Text(
                    if (mode == ImportMode.REPLACE) "Replace" else "Merge",
                    color = if (mode == ImportMode.REPLACE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
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
