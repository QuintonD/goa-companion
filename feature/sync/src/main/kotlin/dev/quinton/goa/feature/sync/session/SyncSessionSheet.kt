package dev.quinton.goa.feature.sync.session

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhonelinkRing
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaProgressBar
import dev.quinton.goa.core.designsystem.component.GoaSwitch
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.ClientState
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.HostState
import dev.quinton.goa.core.domain.sync.protocol.PeerProgress
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncFailure
import dev.quinton.goa.core.domain.sync.protocol.SyncPeer
import dev.quinton.goa.core.navigation.NavSheet
import dev.quinton.goa.feature.sync.SyncText
import dev.quinton.goa.feature.sync.group.InviteDialog
import dev.quinton.goa.feature.sync.nearby.NearbyPermissions

/** Whether the Nearby permissions stand in the way of the role the user picked. */
enum class PermissionUi {
    /** Explain, then ask. */
    NEEDED,

    /** Refused once: Android can still ask again. Say so, and offer the sync file. */
    REFUSED,

    /** Denied for good: only system settings can turn it on. */
    DENIED,
}

class SyncSessionActions(
    val onChoose: (SessionRole) -> Unit = {},
    val onAllow: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    /** Host: arms or disarms "Start automatically when ready". */
    val onAutoStart: (Boolean) -> Unit = {},
    val onChooseHost: (SyncPeer) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onWhoAreYou: () -> Unit = {},
    val onUseFile: () -> Unit = {},
    val onOpenGroup: () -> Unit = {},
    /** Shares the group's invite as text (from the invite shown to a phone that needs it again). */
    val onShareInvite: (String) -> Unit = {},
)

/**
 * The sync sheet with the Nearby permission flow: picking Host or Join asks for the permissions
 * first (with an explanation), and a refusal offers system settings and the sync-file fallback.
 */
@Composable
fun SyncSessionEntry(state: SyncSessionUiState, viewModel: SyncSessionViewModel, actions: SyncSessionActions) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var pending by rememberSaveable { mutableStateOf<SessionRole?>(null) }
    var permission by rememberSaveable { mutableStateOf<PermissionUi?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val role = pending ?: return@rememberLauncherForActivityResult
        // What counts is what Nearby needs now (Android 12: "Approximate" leaves FINE refused).
        if (result.isNotEmpty() && NearbyPermissions.missing(context).isEmpty()) {
            permission = null
            pending = null
            viewModel.start(role)
        } else {
            val permanent = activity != null && NearbyPermissions.missing(context).none {
                activity.shouldShowRequestPermissionRationale(it)
            }
            permission = if (permanent) PermissionUi.DENIED else PermissionUi.REFUSED
        }
    }
    fun choose(role: SessionRole) {
        if (NearbyPermissions.missing(context).isEmpty()) {
            viewModel.start(role)
        } else {
            pending = role
            permission = PermissionUi.NEEDED
        }
    }
    // A role from the route ("Join" right after scanning an invite) starts on its own.
    LaunchedEffect(state.requestedRole) { state.requestedRole?.let(::choose) }
    // Back from system settings with the permission now on: checked on every resume, since nothing
    // in the sheet changes while the user is away.
    LifecycleResumeEffect(permission) {
        if (permission != null && NearbyPermissions.missing(context).isEmpty()) {
            pending?.let(viewModel::start)
            pending = null
            permission = null
        }
        onPauseOrDispose {}
    }
    KeepScreenOn()
    SyncSessionSheet(
        state = state,
        permission = permission,
        actions = SyncSessionActions(
            onChoose = ::choose,
            onAllow = { launcher.launch(NearbyPermissions.required().toTypedArray()) },
            onOpenSettings = { context.startActivity(NearbyPermissions.settingsIntent(context)) },
            onSyncNow = viewModel::syncNow,
            onAutoStart = viewModel::setAutoStart,
            onChooseHost = viewModel::chooseHost,
            onCancel = {
                if (permission != null) {
                    permission = null
                    pending = null
                } else {
                    viewModel.reset()
                    actions.onCancel()
                }
            },
            onRetry = {
                permission = null
                viewModel.reset()
            },
            onClose = actions.onClose,
            onWhoAreYou = actions.onWhoAreYou,
            onUseFile = actions.onUseFile,
            onOpenGroup = actions.onOpenGroup,
            onShareInvite = actions.onShareInvite,
        ),
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Stateless sheet content. */
@Composable
fun SyncSessionSheet(
    state: SyncSessionUiState,
    permission: PermissionUi?,
    actions: SyncSessionActions,
    modifier: Modifier = Modifier,
) {
    // Hold the sheet open while merged data is written or sent; Cancel stays available.
    val dismissible = NavSheet.LocalDismissible.current
    SideEffect { dismissible?.value = !state.busy }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // The title with a close X beside it (under the sheet's drag handle), so leaving never hunts for Cancel.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.firstSync) "First sync" else "Sync with nearby phones",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Hidden only while a joined phone writes the merged data: nothing can stop that safely.
            if (closable(state)) {
                IconButton(
                    onClick = if (permission == null && !state.finished) actions.onCancel else actions.onClose,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
            }
        }
        state.groupName?.let {
            Text(
                "Shows as ${state.deviceName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            permission != null -> PermissionContent(permission, actions)
            else -> StageContent(state, actions)
        }
    }
}

private fun closable(state: SyncSessionUiState): Boolean = when (val stage = state.stage) {
    is SessionStage.Joining -> stage.state.phase != ClientPhase.RECEIVING && stage.state.phase != ClientPhase.APPLYING
    else -> true
}

/** The sheet's way out under its actions: a full-width outlined button ("Cancel", "Stop"). */
@Composable
private fun SheetCancel(label: String, onClick: () -> Unit) {
    GoaOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun StageContent(state: SyncSessionUiState, actions: SyncSessionActions) {
    when (val stage = state.stage) {
        SessionStage.Choose -> ChooseRole(actions)

        SessionStage.NotInGroup -> Message(
            icon = Icons.Outlined.Groups,
            title = "Not in a group yet",
            body = "Start a group, or join one by scanning a friend's invite QR code.",
        ) { GoaButton(onClick = actions.onOpenGroup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Group sync") } }

        is SessionStage.LocalProblem -> Message(
            icon = Icons.Outlined.ErrorOutline,
            title = "This phone's data needs a fix first",
            body = stage.message + "\n\nOpen each match in the match log and correct it, then sync again.",
            error = true,
        ) { GoaOutlinedButton(onClick = actions.onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") } }

        is SessionStage.Hosting -> Hosting(stage.state, state, actions)

        is SessionStage.Joining -> Joining(stage.state, state, actions)
    }
}

@Composable
private fun ChooseRole(actions: SyncSessionActions) {
    Text("Phones close together, Bluetooth and Wi-Fi on.", style = MaterialTheme.typography.bodyMedium)
    RoleCard(
        icon = Icons.Outlined.WifiTethering,
        title = "Host",
        body = "Collects and merges everyone's data.",
        onClick = { actions.onChoose(SessionRole.HOST) },
    )
    RoleCard(
        icon = Icons.Outlined.PhoneAndroid,
        title = "Join",
        body = "Another phone hosts.",
        onClick = { actions.onChoose(SessionRole.JOIN) },
    )
    SheetCancel("Cancel", actions.onCancel)
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit) {
    GoaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(28.dp)),
            )
            Spacer(Modifier.width(Spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionContent(permission: PermissionUi, actions: SyncSessionActions) {
    val group = NearbyPermissions.groupName()
    when (permission) {
        PermissionUi.NEEDED -> Message(
            icon = Icons.Outlined.WifiTethering,
            title = "Allow “$group”",
            body = "To find the phones next to you, the app uses Bluetooth and Wi-Fi Direct. Android asks for " +
                "“$group” for that. Nothing goes on the internet: the data moves straight between the phones " +
                "at the table, locked with your group's key.",
        ) {
            GoaButton(onClick = actions.onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow") }
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") }
        }

        PermissionUi.REFUSED -> Message(
            icon = Icons.Outlined.ErrorOutline,
            title = "“$group” wasn't allowed",
            body = "Sync needs it to find the phones next to you. Ask again, or share a sync file instead: it " +
                "works through Quick Share, a message or Drive.",
            error = true,
        ) {
            GoaButton(onClick = actions.onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ask again") }
            GoaOutlinedButton(onClick = actions.onUseFile, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Use a sync file")
            }
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") }
        }

        PermissionUi.DENIED -> Message(
            icon = Icons.Outlined.ErrorOutline,
            title = "“$group” is off",
            body = "Turn it on in the app's settings (Permissions → $group), then come back. Or share a sync " +
                "file instead: it works through Quick Share, a message or Drive.",
            error = true,
        ) {
            GoaButton(onClick = actions.onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open settings")
            }
            GoaOutlinedButton(onClick = actions.onUseFile, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Use a sync file")
            }
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}

/**
 * Hosting, as an explicit state machine under a row of steps (Collect, Merge, Send back, Done). Collecting: the
 * count ("2 phones included · 1 excluded"), each phone's own state on its row (sending its data with its percent,
 * ready, receiving the result, synced; one left out says why on its row and offers the invite again), and one
 * command. While an included phone is still sending it is off and says whom it waits for ("Waiting for Cid · 60%");
 * once every included phone's data is in it is on: "Merge data from 2 phones". Starting by itself when ready is a
 * switch of its own under it, its armed state spelled out. Then "Merging…", sending back, and a final summary of
 * who is up to date. Every change of state is announced politely to TalkBack.
 */
@Composable
private fun Hosting(host: HostState, state: SyncSessionUiState, actions: SyncSessionActions) {
    val waiting = host.phase == HostPhase.WAITING
    val working = host.phase == HostPhase.MERGING || host.phase == HostPhase.SENDING
    var showInvite by rememberSaveable { mutableStateOf(false) }
    if (host.phase != HostPhase.FAILED) SyncSteps(SyncText.hostStep(host.phase), SyncText.hostSteps)
    when (host.phase) {
        HostPhase.DONE -> HostDone(host)

        else -> StatusLine(
            when {
                waiting && host.peers.isEmpty() ->
                    "Hosting. On each other phone: Group sync → Sync with nearby phones → Join."

                waiting -> SyncText.hostIncluded(host.peers)

                host.phase == HostPhase.MERGING -> SyncText.hostMerging(host.peers)

                host.phase == HostPhase.SENDING -> "Sending the merged data back…"

                else -> host.failure?.let { SyncText.failure(it, host.message) } ?: "Sync stopped"
            },
            working = working,
            error = host.phase == HostPhase.FAILED,
            // Waiting on people, not on work: a phone, not a spinner (a frozen spinner read as a stray mark).
            waitingIcon = Icons.Outlined.PhonelinkRing.takeIf { waiting },
        )
    }
    host.peers.forEach { peer ->
        val needsInvite = peer.stage == PeerStage.FAILED && peer.failure == SyncFailure.WRONG_KEY
        PeerRow(peer, onShowInvite = { showInvite = true }.takeIf { needsInvite && state.invite != null })
    }
    if (waiting && host.peers.isEmpty()) {
        GoaProgressBar(null, Modifier.fillMaxWidth())
    }
    state.duplicateHint?.let { Hint(it) }
    state.versionHint?.let { Hint(it) }
    when {
        waiting -> {
            val ready = SyncText.readyToMerge(host)
            GoaButton(
                onClick = actions.onSyncNow,
                enabled = ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) { Text(SyncText.hostAction(host.peers), textAlign = TextAlign.Center) }
            // Only while there is something to wait for: a phone still sending.
            if (!ready && SyncText.included(host.peers).isNotEmpty()) {
                AutoStartRow(state.autoStart, host.peers, actions.onAutoStart)
            }
            SheetCancel("Cancel", actions.onCancel)
        }

        working -> SheetCancel("Stop", actions.onCancel)

        host.phase == HostPhase.DONE -> Finish(state, actions)

        else -> Retry(actions)
    }
    if (showInvite) {
        InviteDialog(
            groupName = state.groupName.orEmpty(),
            invite = state.invite,
            onShare = { state.invite?.let(actions.onShareInvite) },
            onDismiss = { showInvite = false },
        )
    }
}

/**
 * "Start automatically when ready": a switch of its own, never the command's label. Armed, it says so under its
 * label in the accent colour and with the hourglass ("On · merges once Cid's data is in"); one TalkBack switch.
 */
@Composable
private fun AutoStartRow(armed: Boolean, peers: List<PeerProgress>, onAutoStart: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = armed, role = Role.Switch, onValueChange = onAutoStart)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(SyncText.AUTO_START, style = MaterialTheme.typography.bodyLarge)
            if (armed) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.HourglassTop,
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(scaledIconSize(16.dp)),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        SyncText.autoStartArmed(peers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        GoaSwitch(checked = armed, onCheckedChange = null)
    }
}

/** The finished sync's summary: who is up to date, what changed on this phone, who to sync again. */
@Composable
private fun HostDone(host: HostState) {
    GoaPlate(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(scaledIconSize(28.dp)),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                SyncText.hostDoneTitle(host.peers),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        Text(
            "This phone: ${host.changes?.let(SyncText::summary) ?: "Already up to date"}",
            style = MaterialTheme.typography.bodyMedium,
        )
        SyncText.hostDoneMissed(host.peers)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = GoaTheme.colors.unverified)
        }
    }
}

/**
 * The sync's steps in a row, the current one bronze, the ones behind it ticked in teal, the ones ahead hollow:
 * where the sync stands, in shape and word, never colour alone. One TalkBack node ("Step 2 of 4: Merge").
 */
@Composable
private fun SyncSteps(current: Int, steps: List<String>) {
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Step ${current + 1} of ${steps.size}: ${steps[current]}"
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        steps.forEachIndexed { i, label ->
            val done = i < current || (i == current && i == steps.lastIndex)
            val now = i == current && !done
            val primary = MaterialTheme.colorScheme.primary
            val teal = MaterialTheme.colorScheme.secondary
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(scaledIconSize(26.dp))
                        .clip(CircleShape)
                        .background(
                            when {
                                done -> teal
                                now -> primary
                                else -> Color.Transparent
                            },
                        )
                        .border(
                            1.5.dp,
                            if (done) {
                                teal
                            } else if (now) {
                                primary
                            } else {
                                muted
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            Icons.Filled.Check,
                            null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(scaledIconSize(16.dp)),
                        )
                    } else {
                        Text(
                            "${i + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (now) MaterialTheme.colorScheme.onPrimary else muted,
                        )
                    }
                }
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (now) primary else muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Joining(client: ClientState, state: SyncSessionUiState, actions: SyncSessionActions) {
    val failed = client.phase == ClientPhase.FAILED
    val status = when (client.phase) {
        ClientPhase.DONE -> "${SyncText.clientPhase(client.phase, client.hostName)}: " +
            (client.changes?.let(SyncText::summary) ?: "Already up to date")

        ClientPhase.FAILED -> client.failure?.let { SyncText.failure(it, client.message) } ?: "Sync stopped"

        else -> SyncText.clientPhase(client.phase, client.hostName)
    }
    val working = !failed && client.phase != ClientPhase.DONE && client.phase != ClientPhase.CHOOSING
    StatusLine(status, working = working && client.progress == null, error = failed)
    client.progress?.let { GoaProgressBar({ it }, Modifier.fillMaxWidth()) }
    if (client.phase == ClientPhase.CHOOSING) {
        client.hosts.forEach { option ->
            GoaOutlinedButton(
                onClick = { actions.onChooseHost(option.peer) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text("Join ${option.name}") }
        }
    }
    state.duplicateHint?.let { Hint(it) }
    state.versionHint?.let { Hint(it) }
    when (client.phase) {
        ClientPhase.DONE -> Finish(state, actions)

        ClientPhase.FAILED -> Retry(actions)

        ClientPhase.RECEIVING, ClientPhase.APPLYING -> Unit

        // seconds; nothing to stop safely mid-write
        else -> SheetCancel("Cancel", actions.onCancel)
    }
}

@Composable
private fun Finish(state: SyncSessionUiState, actions: SyncSessionActions) {
    if (state.firstSync) {
        GoaButton(onClick = actions.onWhoAreYou, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Next: which player are you?")
        }
    } else {
        GoaButton(onClick = actions.onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done") }
        // The first sync after joining may have failed (phones apart, Nearby off): ask here instead.
        if (!state.meChosen) {
            TextButton(onClick = actions.onWhoAreYou, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Which player are you?")
            }
        }
    }
}

@Composable
private fun Retry(actions: SyncSessionActions) {
    GoaButton(onClick = actions.onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Try again") }
    GoaOutlinedButton(onClick = actions.onUseFile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Use a sync file instead")
    }
    TextButton(onClick = actions.onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
}

@Composable
private fun StatusLine(text: String, working: Boolean, error: Boolean, waitingIcon: ImageVector? = null) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            // A full faint ring under the arc, so even a still frame reads as a spinner.
            working -> CircularProgressIndicator(
                Modifier.size(scaledIconSize(20.dp)),
                color = MaterialTheme.colorScheme.secondary,
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = SPINNER_TRACK),
            )

            waitingIcon != null -> Icon(
                waitingIcon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(20.dp)),
            )

            error -> Icon(
                Icons.Outlined.ErrorOutline,
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(scaledIconSize(20.dp)),
            )

            else -> Icon(
                Icons.Outlined.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(scaledIconSize(20.dp)),
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PeerRow(peer: PeerProgress, onShowInvite: (() -> Unit)? = null) {
    GoaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The state as a shape too: a tick when its data is in, a warning when it's out.
            val (icon, tint) = when (peer.stage) {
                PeerStage.READY, PeerStage.DONE -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.secondary
                PeerStage.FAILED -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
                else -> Icons.Outlined.PhoneAndroid to MaterialTheme.colorScheme.primary
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(scaledIconSize(24.dp)))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    SyncText.peerStage(peer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (peer.stage == PeerStage.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        peer.progress?.let {
            Spacer(Modifier.size(Spacing.sm))
            GoaProgressBar({ it }, Modifier.fillMaxWidth())
        }
        // The fix where the problem is: the group's invite, to scan on that phone.
        if (onShowInvite != null) {
            GoaOutlinedButton(
                onClick = onShowInvite,
                modifier = Modifier
                    .padding(start = scaledIconSize(24.dp) + Spacing.md, top = Spacing.sm)
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.QrCode2, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Show new invite")
            }
        }
    }
}

/** The faint full ring under a spinner's arc. */
private const val SPINNER_TRACK = 0.2f

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = GoaTheme.colors.unverified)
}

@Composable
private fun Message(
    icon: ImageVector,
    title: String,
    body: String,
    error: Boolean = false,
    buttons: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(28.dp)),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    }
    Text(body, style = MaterialTheme.typography.bodyMedium)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { buttons() }
}
