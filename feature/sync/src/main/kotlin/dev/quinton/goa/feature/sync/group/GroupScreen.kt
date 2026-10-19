package dev.quinton.goa.feature.sync.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.feature.sync.hub.HubActions
import dev.quinton.goa.feature.sync.hub.HubCard

class GroupActions(
    val onBack: () -> Unit = {},
    val onStart: (String) -> Unit = {},
    val onScan: () -> Unit = {},
    val onPaste: (String) -> Unit = {},
    val onClearJoinError: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onWhoAreYou: () -> Unit = {},
    val onShareFile: () -> Unit = {},
    val onImportFile: () -> Unit = {},
    /** "Invite a friend": who it's for, then the QR code (ADR-031). */
    val onInvite: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val hub: HubActions = HubActions(),
)

@Composable
fun GroupScreen(
    state: GroupUiState,
    actions: GroupActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    GoaScreen(
        title = "Group sync",
        onBack = actions.onBack,
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.inGroup -> InGroup(state, actions, padding)

            else -> NoGroup(state, actions, padding)
        }
    }
}

@Composable
private fun NoGroup(state: GroupUiState, actions: GroupActions, padding: PaddingValues) {
    var name by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Groups,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(32.dp)),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                "Play on every phone",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                },
            )
        }
        Text(
            "Each friend's phone keeps the same matches, players and ratings. Phones sync face to face at the " +
                "table over Bluetooth and Wi-Fi: no internet, no accounts, no server.",
            style = MaterialTheme.typography.bodyMedium,
        )
        GoaCard(Modifier.fillMaxWidth()) {
            Text(
                "Join a group",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            Text(
                "On a phone that's already in the group: Group sync → Invite. Scan its QR code here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(Spacing.md))
            Button(onClick = actions.onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.QrCodeScanner, null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Scan invite QR code")
            }
            Spacer(Modifier.size(Spacing.md))
            OutlinedTextField(
                value = invite,
                onValueChange = {
                    invite = it
                    actions.onClearJoinError()
                },
                label = { Text("Or paste an invite") },
                singleLine = true,
                isError = state.joinError != null,
                supportingText = state.joinError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { actions.onPaste(invite) }),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { actions.onPaste(invite) },
                enabled = invite.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Join") }
        }
        GoaCard(Modifier.fillMaxWidth()) {
            Text(
                "Start a group",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics {
                    heading()
                },
            )
            Text(
                "One phone starts the group, then invites the others.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(Spacing.md))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Group name") },
                placeholder = { Text("Friday nights") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { actions.onStart(name) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.size(Spacing.sm))
            OutlinedButton(
                onClick = { actions.onStart(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Start group") }
        }
    }
}

@Composable
private fun InGroup(state: GroupUiState, actions: GroupActions, padding: PaddingValues) {
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screen,
            end = Spacing.screen,
            top = padding.calculateTopPadding() + Spacing.sm,
            bottom = padding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            GoaCard(Modifier.fillMaxWidth()) {
                Text(
                    state.groupName.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        heading()
                    },
                )
                Text(
                    "${state.players} players · ${state.matches} matches on this phone" +
                        (state.lastSync?.let { " · last synced $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Spacing.md))
                Button(onClick = actions.onSyncNow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Sync, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Sync now")
                }
                Spacer(Modifier.size(Spacing.sm))
                OutlinedButton(onClick = actions.onInvite, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.QrCode2, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Invite a friend")
                }
            }
        }
        item { SectionHeader("Hub") }
        item { HubCard(state.hub, actions.hub) }
        item { SectionHeader("This phone") }
        item {
            GoaCard(Modifier.fillMaxWidth(), onClick = actions.onWhoAreYou) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.me != null) {
                        PlayerAvatar(state.me)
                    } else {
                        Icon(
                            Icons.Outlined.PersonOutline,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.me?.let { "You: ${it.name}" } ?: "Which player are you?",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (state.me != null) "Change" else "Choose yourself so the app can show your page first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionHeader("Phones synced with") }
        if (state.phones.isEmpty()) {
            item {
                Text(
                    "None yet. Tap Sync now with a friend's phone next to this one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            state.phones.forEach { phone ->
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(Spacing.md))
                        Text(phone.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            phone.lastSynced,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionHeader("Without Nearby") }
        item {
            GoaCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.sm)) {
                LinkRow(
                    Icons.Outlined.IosShare,
                    "Share sync file",
                    "Send this phone's data, locked with the group key, by Quick Share, a message or Drive",
                    enabled = !state.busy,
                    onClick = actions.onShareFile,
                )
                LinkRow(
                    Icons.Outlined.FileDownload,
                    "Import sync file",
                    "Merge a sync file from another phone in the group",
                    onClick = actions.onImportFile,
                )
            }
        }
        item {
            TextButton(onClick = { confirmLeave = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Logout, null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Leave group")
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave “${state.groupName}”?",
            message = "This phone stops syncing with the group and forgets its key. All matches and players stay " +
                "on this phone. To come back, scan an invite again.",
            confirmLabel = "Leave",
            destructive = true,
            onConfirm = {
                confirmLeave = false
                actions.onLeave()
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(scaledIconSize(24.dp)))
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
