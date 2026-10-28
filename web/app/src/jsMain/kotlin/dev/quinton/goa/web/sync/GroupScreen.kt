package dev.quinton.goa.web.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncDocument
import dev.quinton.goa.core.domain.sync.SyncFileCodec
import dev.quinton.goa.core.domain.sync.SyncFileException
import dev.quinton.goa.core.domain.sync.SyncMerge
import dev.quinton.goa.core.domain.sync.SyncServerAddress
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.feature.sync.SyncWords
import dev.quinton.goa.feature.sync.group.GroupText
import dev.quinton.goa.feature.sync.group.GroupUiState
import dev.quinton.goa.feature.sync.server.SyncServerText
import dev.quinton.goa.feature.sync.server.SyncServerUi
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.group.WebCryptoCipher
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.players.SwitchRow
import dev.quinton.goa.web.players.TextField
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.GoaCard
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.SectionHeader
import kotlin.js.Date
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputMode
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The Group sync screen's own state, kept while its history entry exists. */
private class GroupForm {
    var groupName by mutableStateOf("")
    var invite by mutableStateOf("")
    var joinError by mutableStateOf<String?>(null)
    var addressError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
}

/**
 * An invite waiting for "Join?" (from an invite link, `#join=…`): the link's route hands it here and
 * is replaced at once, so the key never stays in the address bar or the history.
 */
object PendingInvite {
    var text by mutableStateOf<String?>(null)
}

/**
 * Group sync (GroupScreen, ADR-025/ADR-036/ADR-043): start or join a group, invite, sync now through
 * the group's sync server, sync files, "Which player are you?", leave. A browser syncs only through
 * the server (no Bluetooth or Wi-Fi Direct in a browser) or by sync file.
 */
@Composable
fun GroupScreen() {
    val services = LocalServices.current
    val group = services.data.group
    val form = rememberRetained("group-form") { GroupForm() }
    val state by remember {
        combine(
            group.group.state,
            group.players.observePlayers(includeArchived = true),
            group.matches.observeMatches(),
            group.syncServerControl.running,
        ) { g, people, log, running ->
            GroupText.state(g, people, log.size, running, Date.now().toLong(), BrowserName.get())
        }
    }.collectAsState(GroupUiState())
    val pending = PendingInvite.text
    Screen("Group sync") {
        when {
            state.loading -> LoadingState()
            state.inGroup -> InGroup(state, form)
            else -> NoGroup(form)
        }
    }
    if (pending != null) ConfirmJoin(pending, state, form)
}

/** Joins with [text] (an invite, a message with one, or an invite link), as the phone's GroupViewModel.join. */
fun join(services: AppServices, text: String, form: GroupFormHandle) {
    GroupInvite.parseInvite(text).fold(
        onSuccess = { parsed ->
            services.data.scope.launch {
                val groups = services.data.group.group
                val current = groups.state.first().group
                groups.join(parsed.group, parsed.serverUrl)
                form.setJoinError(null)
                form.clearInvite()
                if (current?.groupId == parsed.group.groupId) {
                    services.messages.show("This browser is already in “${parsed.group.name}”.")
                } else {
                    firstSyncFromServer(services, parsed.group.name, form)
                }
            }
        },
        onFailure = { form.setJoinError(it.message) },
    )
}

/** What the screens need to report a join. */
interface GroupFormHandle {
    fun setJoinError(message: String?)

    fun clearInvite()

    fun setBusy(busy: Boolean)
}

private fun GroupForm.handle() = object : GroupFormHandle {
    override fun setJoinError(message: String?) {
        joinError = message
    }

    override fun clearInvite() {
        invite = ""
    }

    override fun setBusy(busy: Boolean) {
        this@handle.busy = busy
    }
}

/**
 * Gets the group's data from the sync server right after joining. A browser can't sync face to
 * face, so when the server can't help it says what to do instead.
 */
private suspend fun firstSyncFromServer(services: AppServices, name: String, form: GroupFormHandle) {
    val group = services.data.group
    if (!group.group.state.first().serverSyncOn) {
        services.messages.show("Joined “$name”. Add the group's sync server under Advanced to get its data.")
        return
    }
    form.setBusy(true)
    val outcome = try {
        group.syncServerControl.syncAndWatch().first()
    } finally {
        form.setBusy(false)
    }
    val received = outcome.received
    services.messages.show(
        when {
            !outcome.synced ->
                "Joined “$name”. The sync server can't be reached right now; this browser tries again on its own."

            received == null ->
                "Joined “$name”. The sync server has nothing from the group yet: sync a phone that has its data."

            else -> "Joined “$name”. From the sync server: $received."
        },
    )
}

@Composable
private fun NoGroup(form: GroupForm) {
    val services = LocalServices.current
    var scanning by remember { mutableStateOf(false) }
    Div({ classes("goa-w2-form") }) {
        Div({ classes("goa-w2-line") }) {
            Span({ classes("c-primary") }) { Icon("g_groups", IconSize.LARGE) }
            H2({ classes("t-title-large", "goa-w2-tight0") }) { Text("Play on every phone") }
        }
        P({ classes("t-body-medium") }) {
            Text(
                "Each friend's phone keeps the same matches, players and ratings. This web app syncs through the " +
                    "group's sync server whenever there's internet; the Android app also syncs face to face at the " +
                    "table. No accounts, and the server only ever sees the data locked with the group key.",
            )
        }
        GoaCard {
            H3({ classes("t-title-medium", "goa-w2-tight0") }) { Text("Join a group") }
            P({ classes("t-body-medium", "c-variant") }) {
                Text(
                    "On a phone that's already in the group: Group sync → Invite a friend. On an iPhone, point the " +
                        "Camera at its QR code. Or scan it here, or paste the invite it shared as text.",
                )
            }
            GoaButton("Scan invite QR code", { scanning = true }, icon = "g_scan", block = true)
            TextField(
                label = "Or paste an invite",
                value = form.invite,
                onValue = {
                    form.invite = it
                    form.joinError = null
                },
                error = form.joinError,
                id = "group-invite",
                onEnter = { if (form.invite.isNotBlank()) join(services, form.invite, form.handle()) },
            )
            Div({ classes("goa-button-row", "goa-w2-start") }) {
                GoaButton(
                    "Join",
                    { join(services, form.invite, form.handle()) },
                    ButtonKind.TEXT,
                    enabled =
                        form.invite.isNotBlank() && !form.busy,
                )
                if (form.busy) Div({ classes("goa-spinner", "small") })
            }
        }
        GoaCard {
            H3({ classes("t-title-medium", "goa-w2-tight0") }) { Text("Start a group") }
            P({
                classes("t-body-medium", "c-variant")
            }) { Text("One phone starts the group, then invites the others.") }
            TextField(
                label = "Group name",
                value = form.groupName,
                onValue = { form.groupName = it.take(GroupCredentials.MAX_NAME_LENGTH) },
                placeholder = "Friday nights",
                maxLength = GroupCredentials.MAX_NAME_LENGTH,
                words = true,
                id = "group-name",
                onEnter = { start(services, form) },
            )
            GoaButton("Start group", {
                start(services, form)
            }, ButtonKind.OUTLINED, enabled = form.groupName.isNotBlank(), block = true)
        }
        StorageNote()
    }
    if (scanning) {
        BottomSheet("Scan invite QR code", onDismiss = { scanning = false }) {
            QrScanner(
                onFound = { text ->
                    scanning = false
                    join(services, text, form.handle())
                },
                onProblem = { message ->
                    scanning = false
                    form.joinError = message
                },
            )
        }
    }
}

private fun start(services: AppServices, form: GroupForm) {
    val clean = GroupCredentials.cleanName(form.groupName)
    if (clean.isEmpty()) {
        services.messages.show(GroupText.GIVE_A_NAME)
        return
    }
    services.data.scope.launch {
        services.data.group.group.start(clean)
        form.groupName = ""
        services.messages.show(GroupText.started(clean))
    }
}

@Composable
private fun InGroup(state: GroupUiState, form: GroupForm) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<ImportStage?>(null) }
    val server = state.server
    GoaCard {
        H2({ classes("t-headline-small", "goa-w2-tight0") }) { Text(state.groupName.orEmpty()) }
        P({ classes("t-body-medium", "c-variant") }) {
            Text(
                "${state.players} players · ${state.matches} matches in this browser" +
                    (state.lastSync?.let { " · last synced $it" } ?: ""),
            )
        }
        val syncing = server.active && server.syncing
        Div({ classes("goa-w2-stack") }) {
            GoaButton(
                if (syncing) "Syncing…" else "Sync now",
                {
                    if (server.active) {
                        services.data.group.syncServerControl.syncNow()
                    } else {
                        services.messages.show(SyncServerText.Browser.NONE)
                    }
                },
                icon = "sync",
                enabled = !syncing,
                block = true,
            )
            GoaButton("Invite a friend", { showInvite = true }, ButtonKind.OUTLINED, icon = "g_qr", block = true)
        }
    }
    SectionHeader("Sync server")
    ServerCard(server, form)
    SectionHeader("This browser")
    GoaCard(flush = true) {
        RowList {
            ListRow(
                state.me?.let { "You: ${it.name}" } ?: "Which player are you?",
                subtitle = if (state.me != null) "Change" else "Choose yourself so the app can show your page first",
                leading = { if (state.me != null) PlayerAvatar(state.me, 40) else Icon("person") },
                route = Route.WhoAreYou,
            )
        }
    }
    StorageNote()
    SectionHeader("Sync files")
    GoaCard(flush = true) {
        RowList {
            ListRow(
                "Share sync file",
                subtitle = "Send this browser's data, locked with the group key, by message or AirDrop",
                leadingIcon = "g_file_up",
                onClick = { if (!form.busy) scope.launch { shareFile(services, form) } },
            )
            ListRow(
                "Import sync file",
                subtitle = "Merge a sync file from another phone in the group",
                leadingIcon = "g_file_down",
                onClick = { SyncFiles.pick { bytes -> scope.launch { importing = readSyncFile(services, bytes) } } },
            )
        }
    }
    Div({ classes("goa-w2-spaced") }) {
        GoaButton("Leave group", { confirmLeave = true }, ButtonKind.TEXT, icon = "g_logout")
    }
    if (showInvite) InviteSheet(state) { showInvite = false }
    importing?.let { stage -> ImportDialog(stage) { importing = it } }
    if (confirmLeave) {
        AlertDialog("Leave “${state.groupName}”?", onDismiss = { confirmLeave = false }, buttons = {
            GoaButton("Cancel", { confirmLeave = false }, ButtonKind.TEXT)
            GoaButton("Leave", {
                confirmLeave = false
                scope.launch {
                    val name = services.data.group.group.state.first().group?.name
                    services.data.group.group.leave()
                    services.messages.show("Left “$name”. Everything you played stays in this browser.")
                }
            }, ButtonKind.DANGER)
        }) {
            P {
                Text(
                    "This browser stops syncing with the group and forgets its key. All matches and players stay " +
                        "here. To come back, open an invite again.",
                )
            }
        }
    }
}

/** Safari may clear a site's data after 7 days unused (not on the Home Screen): the server keeps a copy. */
@Composable
private fun StorageNote() {
    P({ classes("t-body-small", "c-variant", "goa-w2-note") }) {
        Text(
            "Safari can clear a website's data after a week without a visit, unless it's on the Home Screen " +
                "(Share → Add to Home Screen). Your group's sync server keeps a copy: joining again brings it back.",
        )
    }
}

/** The group's sync server (SyncServerCard): status, the error in words, the switch, the address. */
@Composable
private fun ServerCard(server: SyncServerUi, form: GroupForm) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    var advanced by remember { mutableStateOf(form.addressError != null) }
    var address by remember(server.url) { mutableStateOf(server.host.orEmpty()) }
    GoaCard {
        Div({
            classes("goa-w2-line", "goa-w2-server-head")
            attr("aria-live", "polite")
        }) {
            val (icon, tone) = when {
                server.url == null || !server.enabled -> "cloud_off" to "c-variant"
                server.syncing -> "g_cloud_sync" to "goa-w2-win"
                server.hasProblem -> "cloud_off" to "c-error"
                else -> "g_cloud_done" to "goa-w2-win"
            }
            Span({ classes(tone) }) { Icon(icon, IconSize.NORMAL) }
            Div({ classes("goa-w2-grow") }) {
                Div({ classes("t-title-medium", "goa-w2-break") }) { Text(server.host ?: SyncServerText.TITLE_NONE) }
                Div({ classes("t-body-medium", "c-variant") }) {
                    Text(
                        when {
                            server.url == null -> SyncServerText.Browser.NONE
                            !server.enabled -> SyncServerText.Browser.OFF
                            else -> server.status.orEmpty()
                        },
                    )
                }
            }
        }
        if (server.url != null) {
            SwitchRow("Sync through the server", server.enabled, { on ->
                scope.launch { services.data.group.group.setServerSyncEnabled(on) }
            }, id = "server-enabled")
        }
        if (server.active) {
            server.error?.let { error ->
                Div({
                    classes("goa-w2-server-error")
                    attr("role", "alert")
                }) {
                    Span({ classes("c-error") }) { Icon("g_error", IconSize.SMALL) }
                    Div {
                        P({ classes("t-body-medium", "c-error", "goa-w2-tight0", "goa-w2-pre") }) { Text(error) }
                        val detail = listOfNotNull(
                            server.errorAgo?.replaceFirstChar(Char::uppercase),
                            SyncServerText.RETRYING.takeIf { server.retrying },
                        ).joinToString(". ")
                        if (detail.isNotEmpty()) {
                            P({
                                classes("t-body-small", "c-variant", "goa-w2-tight0")
                            }) { Text(detail) }
                        }
                    }
                }
            }
            P({ classes("t-body-small", "c-variant") }) { Text(SyncServerText.Browser.AUTOMATIC) }
        }
        GoaButton("Advanced", { advanced = !advanced }, ButtonKind.TEXT, icon = if (advanced) "close" else "settings")
        if (advanced) {
            val canSave = address.isNotBlank() && address != server.host
            val saveAddress: () -> Unit = {
                val url = SyncServerAddress.normalize(address, services.data.group.allowLocalHttp)
                if (url == null) {
                    form.addressError = SyncServerText.NOT_AN_ADDRESS
                } else {
                    scope.launch {
                        services.data.group.group.setServerUrl(url)
                        services.data.group.group.setServerSyncEnabled(true)
                        form.addressError = null
                        services.messages.show(GroupText.serverSaved(url))
                    }
                }
            }
            TextField(
                label = "Server address",
                value = address,
                onValue = {
                    address = it.trim()
                    form.addressError = null
                },
                error = form.addressError,
                supporting = SyncServerText.Browser.ADVANCED_HINT,
                placeholder = server.defaultHost ?: "oldboi.example.ts.net:8443",
                inputMode = InputMode.Url,
                id = "server-address",
                // Return saves, as Done does on the phone.
                onEnter = { if (canSave) saveAddress() },
            )
            GoaButton("Save address", saveAddress, ButtonKind.OUTLINED, enabled = canSave, block = true)
            val defaultHost = server.defaultHost
            if (server.custom && defaultHost != null) {
                GoaButton("Use the app's server ($defaultHost)", {
                    scope.launch {
                        services.data.group.group.setServerUrl(null)
                        form.addressError = null
                        services.messages.show(GroupText.USE_DEFAULT_SERVER)
                    }
                }, ButtonKind.TEXT)
            }
        }
    }
}

/** The invite QR code (a link to the web app with the invite in its fragment) and ways to send it. */
@Composable
private fun InviteSheet(state: GroupUiState, onDismiss: () -> Unit) {
    val services = LocalServices.current
    val invite = state.invite ?: return
    val parsed = GroupInvite.parseInvite(invite).getOrNull()
    val link = GroupInvite.link(invite)
    val message = parsed?.let { GroupInvite.shareText(it.group, it.serverUrl) } ?: link
    BottomSheet("Invite to “${state.groupName}”", onDismiss = onDismiss) {
        QrCode(link, "Invite QR code for ${state.groupName}")
        P({ classes("t-body-medium") }) {
            Text(
                "On an iPhone: point the Camera at the code; it opens this web app, ready to join. On Android: " +
                    "install the app, open Settings → Group sync → Scan invite QR code. Or send the invite as text. " +
                    "Anyone with this code can join, so only show it at the table or send it to the group.",
            )
        }
        Div({ classes("goa-w2-stack") }) {
            GoaButton("Share as text", { shareText(services, message) }, icon = "g_share", block = true)
            GoaButton("Done", onDismiss, ButtonKind.OUTLINED, block = true)
        }
    }
}

/** "Join “Y”?" for an invite that arrived as a link (never joined without asking). */
@Composable
private fun ConfirmJoin(text: String, state: GroupUiState, form: GroupForm) {
    val services = LocalServices.current
    val parsed = GroupInvite.parseInvite(text)
    val invite = parsed.getOrNull()
    if (invite == null) {
        AlertDialog("This invite can't be used", onDismiss = { PendingInvite.text = null }, buttons = {
            GoaButton("OK", { PendingInvite.text = null }, ButtonKind.TEXT)
        }) { P { Text(parsed.exceptionOrNull()?.message ?: "Scan the QR code again.") } }
        return
    }
    val name = invite.group.name
    val switching = state.inGroup && state.groupName != null && GroupInvite.parseInvite(state.invite.orEmpty())
        .getOrNull()?.group?.groupId != invite.group.groupId
    AlertDialog("Join “$name”?", onDismiss = { PendingInvite.text = null }, buttons = {
        GoaButton("Cancel", { PendingInvite.text = null }, ButtonKind.TEXT)
        GoaButton("Join", {
            PendingInvite.text = null
            join(services, text, form.handle())
        }, ButtonKind.TEXT)
    }) {
        P {
            Text(
                if (switching) {
                    "This browser is in “${state.groupName}”. Joining “$name” stops syncing with it here; " +
                        "everything you played stays in this browser."
                } else {
                    "This browser gets the group's matches, players and ratings from its sync server and keeps them " +
                        "in sync. Only join groups you play with."
                },
            )
        }
    }
}

/** "Share sync file": this browser's data, sealed with the group key (the share sheet, else a download). */
private suspend fun shareFile(services: AppServices, form: GroupForm) {
    val sync = services.data.group.sync
    val group = services.data.group.group.state.first().group ?: return
    form.busy = true
    try {
        val problem = SyncValidation.problem(sync.export())
        if (problem != null) {
            services.messages.show(problem)
        } else {
            SyncFiles.share(sync.exportFile(group), SyncFiles.fileName(group.name), "Sync file for “${group.name}”")
        }
    } finally {
        form.busy = false
    }
}

/** Where "Import sync file" is (SyncFileImportViewModel's stages). */
private sealed interface ImportStage {
    class Ready(
        val groupName: String,
        val players: Int,
        val matches: Int,
        val document: SyncDocument,
        val summary: String,
        /** Merging would change nothing here: "Nothing to merge", disabled (as on the phone). */
        val nothingNew: Boolean,
    ) :
        ImportStage

    class Failed(val message: String) : ImportStage
}

/** Opens a picked file with the group key and says what merging it would change. */
private suspend fun readSyncFile(services: AppServices, bytes: ByteArray?): ImportStage {
    if (bytes == null) return ImportStage.Failed(UNREADABLE)
    val group = services.data.group.group.state.first().group
    return try {
        val doc = SyncFileCodec.decodeFile(bytes, group, WebCryptoCipher)
        val local = services.data.group.sync.export()
        val changes = SyncMerge.changes(local, SyncMerge.merge(local, doc))
        ImportStage.Ready(
            group?.name.orEmpty(),
            doc.players.size,
            doc.matches.size,
            doc,
            SyncWords.summary(changes),
            nothingNew = changes.isEmpty,
        )
    } catch (e: SyncFileException) {
        ImportStage.Failed(e.message.orEmpty())
    }
}

@Composable
private fun ImportDialog(stage: ImportStage, onStage: (ImportStage?) -> Unit) {
    val services = LocalServices.current
    when (stage) {
        is ImportStage.Failed -> AlertDialog("Can't import this file", onDismiss = { onStage(null) }, buttons = {
            GoaButton("Close", { onStage(null) }, ButtonKind.TEXT)
        }) { P { Text(stage.message) } }

        is ImportStage.Ready -> AlertDialog("From “${stage.groupName}”", onDismiss = { onStage(null) }, buttons = {
            GoaButton("Cancel", { onStage(null) }, ButtonKind.TEXT)
            GoaButton(if (stage.nothingNew) "Nothing to merge" else "Merge into this browser", {
                onStage(null)
                services.data.scope.launch {
                    val result = services.data.group.sync.apply(stage.document)
                    services.messages.show("Merged: ${SyncWords.summary(result.changes)}")
                }
            }, ButtonKind.TEXT, enabled = !stage.nothingNew)
        }) {
            P { Text("${stage.matches} matches and ${stage.players} players in the file.") }
            P { Text("In this browser: ${stage.summary}") }
            P({ classes("c-variant", "t-body-small") }) {
                Text(
                    "Merging works like a sync: anything new is added, and where both have the same match or " +
                        "player, the later edit wins. Matches deleted on the other phone are deleted here.",
                )
            }
        }
    }
}

private const val UNREADABLE = "This file can't be read. Save it again and pick it from the file list."
