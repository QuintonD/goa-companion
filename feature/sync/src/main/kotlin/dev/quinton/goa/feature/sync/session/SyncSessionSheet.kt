package dev.quinton.goa.feature.sync.session

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.ClientState
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.HostState
import dev.quinton.goa.core.domain.sync.protocol.PeerProgress
import dev.quinton.goa.core.domain.sync.protocol.PeerStage
import dev.quinton.goa.core.domain.sync.protocol.SyncPeer
import dev.quinton.goa.core.navigation.NavSheet
import dev.quinton.goa.feature.sync.SyncText
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
    val onChooseHost: (SyncPeer) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onWhoAreYou: () -> Unit = {},
    val onUseFile: () -> Unit = {},
    val onOpenGroup: () -> Unit = {},
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
    // Back from system settings with the permission now on.
    LaunchedEffect(permission) {
        if (permission != null && NearbyPermissions.missing(context).isEmpty()) {
            pending?.let(viewModel::start)
            pending = null
            permission = null
        }
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
        Text(
            if (state.firstSync) "First sync" else "Sync now",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        state.groupName?.let {
            Text(
                "Group “$it” · this phone shows as ${state.deviceName}",
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

@Composable
private fun StageContent(state: SyncSessionUiState, actions: SyncSessionActions) {
    when (val stage = state.stage) {
        SessionStage.Choose -> ChooseRole(actions)

        SessionStage.NotInGroup -> Message(
            icon = Icons.Outlined.Groups,
            title = "Not in a group yet",
            body = "Start a group, or join one by scanning a friend's invite QR code.",
        ) { Button(onClick = actions.onOpenGroup, modifier = Modifier.heightIn(min = 48.dp)) { Text("Group sync") } }

        is SessionStage.LocalProblem -> Message(
            icon = Icons.Outlined.ErrorOutline,
            title = "This phone's data needs a fix first",
            body = stage.message + "\n\nOpen each match in the match log and correct it, then sync again.",
            error = true,
        ) { OutlinedButton(onClick = actions.onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") } }

        is SessionStage.Hosting -> Hosting(stage.state, state, actions)

        is SessionStage.Joining -> Joining(stage.state, state, actions)
    }
}

@Composable
private fun ChooseRole(actions: SyncSessionActions) {
    Text(
        "Keep the phones close together, with Bluetooth and Wi-Fi on. No internet is needed. " +
            "One phone hosts; everyone else joins.",
        style = MaterialTheme.typography.bodyMedium,
    )
    RoleCard(
        icon = Icons.Outlined.WifiTethering,
        title = "Host",
        body = "This phone collects every phone's data, merges it and sends the result back to all of them.",
        onClick = { actions.onChoose(SessionRole.HOST) },
    )
    RoleCard(
        icon = Icons.Outlined.PhoneAndroid,
        title = "Join",
        body = "Another phone hosts. This one sends its data and gets everything back.",
        onClick = { actions.onChoose(SessionRole.JOIN) },
    )
    TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
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
            Button(onClick = actions.onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow") }
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") }
        }

        PermissionUi.REFUSED -> Message(
            icon = Icons.Outlined.ErrorOutline,
            title = "“$group” wasn't allowed",
            body = "Sync needs it to find the phones next to you. Ask again, or share a sync file instead: it " +
                "works through Quick Share, a message or Drive.",
            error = true,
        ) {
            Button(onClick = actions.onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text("Ask again") }
            OutlinedButton(onClick = actions.onUseFile, modifier = Modifier.heightIn(min = 48.dp)) {
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
            Button(onClick = actions.onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open settings")
            }
            OutlinedButton(onClick = actions.onUseFile, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Use a sync file")
            }
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}

@Composable
private fun Hosting(host: HostState, state: SyncSessionUiState, actions: SyncSessionActions) {
    val status = when (host.phase) {
        HostPhase.WAITING -> if (host.peers.isEmpty()) {
            "Hosting. On each other phone: Group sync → Sync now → Join."
        } else {
            "${host.peers.count { it.stage == PeerStage.READY }} of ${host.peers.size} phones ready. " +
                "Tap Sync when everyone is in."
        }

        HostPhase.MERGING -> "Merging everyone's data…"

        HostPhase.SENDING -> "Sending the merged data…"

        HostPhase.DONE -> "Done. This phone: ${host.changes?.let(SyncText::summary) ?: "Already up to date"}"

        HostPhase.FAILED -> host.failure?.let { SyncText.failure(it, host.message) } ?: "Sync stopped"
    }
    val working = host.phase != HostPhase.DONE && host.phase != HostPhase.FAILED
    StatusLine(status, working = working, error = host.phase == HostPhase.FAILED)
    host.peers.forEach { PeerRow(it) }
    if (host.phase == HostPhase.WAITING && host.peers.isEmpty()) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    state.duplicateHint?.let { Hint(it) }
    state.versionHint?.let { Hint(it) }
    when (host.phase) {
        HostPhase.WAITING -> {
            val ready = host.peers.count { it.stage == PeerStage.READY }
            Button(
                onClick = actions.onSyncNow,
                enabled = host.canSync,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(if (ready == 0) "Sync" else "Sync $ready ${if (ready == 1) "phone" else "phones"}") }
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
        }

        HostPhase.MERGING, HostPhase.SENDING ->
            TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Stop") }

        HostPhase.DONE -> Finish(state, actions)

        HostPhase.FAILED -> Retry(actions)
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
    client.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
    if (client.phase == ClientPhase.CHOOSING) {
        client.hosts.forEach { option ->
            OutlinedButton(
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
        else -> TextButton(onClick = actions.onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") }
    }
}

@Composable
private fun Finish(state: SyncSessionUiState, actions: SyncSessionActions) {
    if (state.firstSync) {
        Button(onClick = actions.onWhoAreYou, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("Next: which player are you?")
        }
    } else {
        Button(onClick = actions.onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Done") }
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
    Button(onClick = actions.onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Try again") }
    OutlinedButton(onClick = actions.onUseFile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Use a sync file instead")
    }
    TextButton(onClick = actions.onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close") }
}

@Composable
private fun StatusLine(text: String, working: Boolean, error: Boolean) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            working -> CircularProgressIndicator(Modifier.size(scaledIconSize(20.dp)), strokeWidth = 2.dp)

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
private fun PeerRow(peer: PeerProgress) {
    GoaCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(scaledIconSize(24.dp)),
            )
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
            if (peer.stage == PeerStage.DONE) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            }
        }
        peer.progress?.let {
            Spacer(Modifier.size(Spacing.sm))
            LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
        }
    }
}

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
