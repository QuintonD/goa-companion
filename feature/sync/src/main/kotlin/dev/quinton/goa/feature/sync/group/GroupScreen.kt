package dev.quinton.goa.feature.sync.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.BattlefieldFoot
import dev.quinton.goa.core.designsystem.component.ConfirmDialog
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaDialog
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.PlateStyle
import dev.quinton.goa.core.designsystem.component.PlayerAvatar
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.feature.sync.invite.InviteQr
import dev.quinton.goa.feature.sync.server.SyncServerActions
import dev.quinton.goa.feature.sync.server.SyncServerCard

class GroupActions(
    val onBack: () -> Unit = {},
    val onStart: (String) -> Unit = {},
    val onScan: () -> Unit = {},
    val onPaste: (String) -> Unit = {},
    val onClearJoinError: () -> Unit = {},
    /** Through the server when it is on, else the face-to-face sheet. */
    val onSyncNow: () -> Unit = {},
    /** The face-to-face (Nearby) sheet, offered beside a server. */
    val onSyncAtTable: () -> Unit = {},
    val onWhoAreYou: () -> Unit = {},
    val onShareFile: () -> Unit = {},
    val onImportFile: () -> Unit = {},
    val onShareInvite: (String) -> Unit = {},
    val onLeave: () -> Unit = {},
    val server: SyncServerActions = SyncServerActions(),
)

@Composable
fun GroupScreen(
    state: GroupUiState,
    actions: GroupActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showInvite: Boolean = false,
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

            state.inGroup -> InGroup(state, actions, padding, showInvite)

            // Two plates leave the foot of the screen free: the rulebook's battlefield anchors it.
            else -> Box(Modifier.fillMaxSize()) {
                BattlefieldFoot(Modifier.padding(padding))
                NoGroup(state, actions, padding)
            }
        }
    }
}

/**
 * Not in a group yet: one line on what syncing is for, then the two ways in as equal plates (Scan an invite, Start a
 * group), one sentence and one button each; a form opens only in the plate you chose. How it works (Bluetooth and
 * Wi-Fi, the sync server, the group key) waits under "How syncing works".
 */
@Composable
private fun NoGroup(state: GroupUiState, actions: GroupActions, padding: PaddingValues) {
    var name by rememberSaveable { mutableStateOf("") }
    var invite by rememberSaveable { mutableStateOf("") }
    var pasting by rememberSaveable { mutableStateOf(false) }
    var starting by rememberSaveable { mutableStateOf(false) }
    var nameMissing by rememberSaveable { mutableStateOf(false) }
    var inviteMissing by rememberSaveable { mutableStateOf(false) }
    var howItWorks by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            // The keyboard shrinks the scrolling area (the insets the scaffold padded aren't counted twice).
            .consumeWindowInsets(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            "Keep the same matches, players and ratings on every friend's phone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        WayIn(
            icon = Icons.Outlined.QrCodeScanner,
            title = "Scan an invite",
            body = "Join a friend's group: their phone shows the invite under Group sync → Invite a friend.",
        ) {
            GoaButton(onClick = actions.onScan, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.QrCodeScanner, null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Scan invite QR code")
            }
            if (pasting || invite.isNotEmpty() || state.joinError != null) {
                Spacer(Modifier.size(Spacing.md))
                PasteInvite(
                    invite = invite,
                    onInvite = {
                        invite = it
                        inviteMissing = false
                        actions.onClearJoinError()
                    },
                    error = state.joinError ?: "Paste the invite your friend shared first.".takeIf { inviteMissing },
                    onJoin = { if (invite.isBlank()) inviteMissing = true else actions.onPaste(invite) },
                )
            } else {
                TextButton(onClick = { pasting = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(scaledIconSize(18.dp)))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Paste an invite instead")
                }
            }
        }
        WayIn(
            icon = Icons.Outlined.GroupAdd,
            title = "Start a group",
            body = "Start one on this phone, then invite the others to join it.",
        ) {
            if (!starting) {
                GoaButton(onClick = { starting = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.GroupAdd, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Start a group")
                }
            } else {
                val start = { if (name.isBlank()) nameMissing = true else actions.onStart(name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        nameMissing = false
                    },
                    label = { Text("Group name") },
                    placeholder = { Text("Friday nights") },
                    singleLine = true,
                    isError = nameMissing,
                    supportingText = if (nameMissing) {
                        { Text("Name the group first.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { start() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(Spacing.sm))
                GoaButton(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text("Start group")
                }
            }
        }
        HowSyncingWorks(expanded = howItWorks, onToggle = { howItWorks = !howItWorks })
    }
}

/** One way into a group: icon and title, one sentence, then its button (and the form it opens). */
@Composable
private fun WayIn(icon: ImageVector, title: String, body: String, content: @Composable () -> Unit) {
    GoaPlate(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(scaledIconSize(28.dp)))
            Spacer(Modifier.width(Spacing.md))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.size(Spacing.xs))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.md))
        content()
    }
}

/** The background, folded: how phones sync and what the server sees. */
@Composable
private fun HowSyncingWorks(expanded: Boolean, onToggle: () -> Unit) {
    Column {
        TextButton(
            onClick = onToggle,
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        ) {
            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(scaledIconSize(18.dp)))
            Spacer(Modifier.width(Spacing.sm))
            Text("How syncing works")
            Spacer(Modifier.width(Spacing.xs))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded) {
            GoaPlate(Modifier.fillMaxWidth(), style = PlateStyle.Secondary) {
                Text(
                    "Phones sync face to face at the table over Bluetooth and Wi-Fi, with no internet needed, and " +
                        "through the group's sync server when there's internet. There are no accounts: the group " +
                        "key in the invite locks the data, and the server only ever sees it locked.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun InGroup(state: GroupUiState, actions: GroupActions, padding: PaddingValues, showInviteInitially: Boolean) {
    var showInvite by rememberSaveable { mutableStateOf(showInviteInitially) }
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
                // With a server, Sync now syncs through it; face to face is the secondary way.
                val syncing = state.server.active && state.server.syncing
                GoaButton(
                    onClick = actions.onSyncNow,
                    enabled = !syncing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    if (syncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Sync, null)
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    Text(if (syncing) "Syncing…" else "Sync now")
                }
                if (state.server.active) {
                    Spacer(Modifier.size(Spacing.sm))
                    OutlinedButton(
                        onClick = actions.onSyncAtTable,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Groups, null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Sync at the table (no internet)")
                    }
                }
                Spacer(Modifier.size(Spacing.sm))
                OutlinedButton(onClick = {
                    showInvite = true
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.QrCode2, null)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Invite a friend")
                }
            }
        }
        item { SectionHeader("Sync server") }
        item { SyncServerCard(state.server, actions.server) }
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
                    if (state.server.active) {
                        "None face to face yet. Tap Sync at the table with a friend's phone next to this one."
                    } else {
                        "None yet. Tap Sync now with a friend's phone next to this one."
                    },
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
    if (showInvite) {
        InviteDialog(state, onShare = { state.invite?.let(actions.onShareInvite) }, onDismiss = { showInvite = false })
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

/**
 * The invite field and Join. While the field has focus both stay in view above the keyboard
 * (pasting an invite used to leave Join under it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PasteInvite(invite: String, onInvite: (String) -> Unit, error: String?, onJoin: () -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val keyboard = WindowInsets.ime.getBottom(LocalDensity.current)
    // Again as the keyboard slides in (the space shrinks while it does) and as the text changes.
    LaunchedEffect(focused, keyboard, invite, error) { if (focused) requester.bringIntoView() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        OutlinedTextField(
            value = invite,
            onValueChange = onInvite,
            label = { Text("Paste an invite") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onJoin() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        Spacer(Modifier.size(Spacing.sm))
        GoaOutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("Join", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The invite QR code, big and on white (scanners need the contrast), with what to do on the other phone. */
@Composable
fun InviteDialog(state: GroupUiState, onShare: () -> Unit, onDismiss: () -> Unit) {
    val invite = state.invite ?: return
    // A link to the web app with the invite in its fragment: the app's scanner finds the invite in
    // it, and an iPhone's Camera opens the web app ready to join (ADR-043).
    val qr = remember(invite) { InviteQr.bitmap(GroupInvite.link(invite)) }
    GoaDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite to “${state.groupName}”") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(Spacing.md)
                        .semantics { contentDescription = "Invite QR code for ${state.groupName}" },
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
                Text(
                    "On your friend's phone: install the app, open Settings → Group sync → Scan invite QR code. " +
                        "On an iPhone, point the Camera at it: it opens the web app, ready to join. Or share it " +
                        "as text and paste it on the other phone. Anyone with this code can join, so only show " +
                        "it at the table or send it to the group.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onShare) { Text("Share as text") } },
    )
}
