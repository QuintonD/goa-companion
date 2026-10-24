package dev.quinton.goa.feature.sync.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.SyncServerDefaults
import dev.quinton.goa.core.data.SyncServerResult
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.SyncServerAddress
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.sync.PhoneName
import dev.quinton.goa.feature.sync.SyncText
import dev.quinton.goa.feature.sync.server.SyncServerControl
import dev.quinton.goa.feature.sync.server.SyncServerText
import dev.quinton.goa.feature.sync.server.SyncServerUi
import dev.quinton.goa.feature.sync.share.SyncFiles
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneRow(val name: String, val lastSynced: String)

data class GroupUiState(
    val loading: Boolean = true,
    val groupName: String? = null,
    /** The invite the QR code shows (`goa-sync:1?…`, with `&s=` for a group's own sync server). */
    val invite: String? = null,
    val me: Player? = null,
    val deviceName: String = "",
    val phones: List<PhoneRow> = emptyList(),
    val lastSync: String? = null,
    val players: Int = 0,
    val matches: Int = 0,
    val busy: Boolean = false,
    /** Why an invite couldn't be used (shown under the paste field). */
    val joinError: String? = null,
    /** A one-off message for the snackbar. */
    val message: String? = null,
    /** The group's sync server and how its automatic sync went (ADR-036). */
    val server: SyncServerUi = SyncServerUi(),
) {
    val inGroup: Boolean get() = groupName != null
}

sealed interface GroupEvent {
    /** Joined: run the first sync right away. */
    data object OpenFirstSync : GroupEvent

    /** Hand a sync file to the share sheet. */
    class Share(val bytes: ByteArray, val fileName: String, val title: String) : GroupEvent
}

private data class Local(
    val busy: Boolean = false,
    val joinError: String? = null,
    val message: String? = null,
    val addressError: String? = null,
)

/**
 * The Group sync screen (ADR-025): start or join a group; invite, sync, share a file, leave; and
 * the group's sync server (ADR-036): status, sync now, on/off, address.
 */
@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groups: GroupRepository,
    private val players: PlayerRepository,
    matches: MatchRepository,
    private val sync: SyncRepository,
    private val clock: EditClock,
    private val phoneName: PhoneName,
    private val serverSync: SyncServerControl = SyncServerControl.Off,
    private val serverDefaults: SyncServerDefaults = SyncServerDefaults.NONE,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val events = Channel<GroupEvent>(Channel.BUFFERED)
    val navigation: Flow<GroupEvent> = events.receiveAsFlow()

    val uiState: StateFlow<GroupUiState> = combine(
        groups.state,
        players.observePlayers(includeArchived = true),
        matches.observeMatches(),
        local,
        serverSync.running,
    ) { group, people, log, l, serverRunning ->
        val now = clock.now()
        val me = people.firstOrNull { it.id == group.mePlayerId }
        GroupUiState(
            loading = false,
            groupName = group.group?.name,
            // The server travels in the invite only when it isn't the app's built-in one.
            invite = group.group?.let { GroupInvite.format(it, group.server.url.takeIf { group.server.custom }) },
            me = me,
            deviceName = me?.name ?: group.deviceName ?: phoneName.get(),
            phones = group.phones.map { PhoneRow(it.name, SyncText.ago(it.lastSyncedAt, now)) },
            lastSync = group.lastSyncAt?.let { SyncText.ago(it, now) },
            players = people.count { !it.archived },
            matches = log.size,
            busy = l.busy,
            joinError = l.joinError,
            message = l.message,
            server = SyncServerText.ui(group, serverRunning, now, l.addressError),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupUiState())

    fun start(name: String) {
        val clean = GroupCredentials.cleanName(name)
        if (clean.isEmpty()) {
            local.update { it.copy(message = "Give the group a name") }
            return
        }
        viewModelScope.launch {
            groups.start(clean)
            local.update { it.copy(message = "Group “$clean” started. Invite your friends with the QR code.") }
        }
    }

    /**
     * Joins the group in a scanned or pasted invite, then gets the group's data: from the sync
     * server right away when there is one (ADR-036); without one, or when the server has nothing
     * or can't be reached, by opening the first face-to-face sync as before.
     */
    fun join(invite: String) {
        GroupInvite.parseInvite(invite).fold(
            onSuccess = { parsed ->
                val group = parsed.group
                viewModelScope.launch {
                    val current = groups.state.first().group
                    groups.join(group, parsed.serverUrl)
                    if (current?.groupId != group.groupId) {
                        local.update { it.copy(joinError = null) }
                        if (!firstSyncFromServer(group.name)) events.send(GroupEvent.OpenFirstSync)
                    } else {
                        local.update {
                            it.copy(joinError = null, message = "This phone is already in “${group.name}”.")
                        }
                    }
                }
            },
            onFailure = { e -> local.update { it.copy(joinError = e.message) } },
        )
    }

    /** True when the group's data came from the server; false = sync face to face instead. */
    private suspend fun firstSyncFromServer(name: String): Boolean {
        if (!groups.state.first().serverSyncOn) return false
        local.update { it.copy(busy = true) }
        val result = try {
            serverSync.syncOnce()
        } finally {
            local.update { it.copy(busy = false) }
        }
        val changes = (result as? SyncServerResult.Synced)?.changes
        val message = when {
            changes == null -> "Joined “$name”. The sync server can't help right now, so sync face to face."

            changes.isEmpty ->
                "Joined “$name”. The sync server has nothing from the group yet, so sync face to face once."

            else -> "Joined “$name”. From the sync server: ${SyncText.summary(changes)}."
        }
        local.update { it.copy(message = message) }
        return changes != null && !changes.isEmpty
    }

    fun scanFailed(message: String) = local.update { it.copy(joinError = message) }

    fun leave() {
        viewModelScope.launch {
            val name = groups.state.first().group?.name
            groups.leave()
            local.update { it.copy(message = "Left “$name”. Everything you played stays on this phone.") }
        }
    }

    /** "Share sync file": this phone's data, sealed with the group key. */
    fun shareFile() {
        viewModelScope.launch {
            val group = groups.state.first().group ?: return@launch
            local.update { it.copy(busy = true) }
            try {
                val problem = SyncValidation.problem(sync.export())
                if (problem != null) {
                    local.update { it.copy(message = problem) }
                } else {
                    events.send(
                        GroupEvent.Share(
                            sync.exportFile(group),
                            SyncFiles.fileName(group.name),
                            "Sync file for “${group.name}”",
                        ),
                    )
                }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    /** "Sync with the server". */
    fun syncWithServer() = serverSync.syncNow()

    fun setServerSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { groups.setServerSyncEnabled(enabled) }
    }

    /** Saves the group's server address ([SyncServerAddress]); a new server syncs right away. */
    fun setServerAddress(address: String) {
        val url = SyncServerAddress.normalize(address, serverDefaults.allowLocalHttp)
        if (url == null) {
            local.update { it.copy(addressError = SyncServerText.NOT_AN_ADDRESS) }
            return
        }
        viewModelScope.launch {
            groups.setServerUrl(url)
            groups.setServerSyncEnabled(true)
            local.update {
                it.copy(addressError = null, message = "Server saved. Syncing with ${SyncServerAddress.display(url)}…")
            }
        }
    }

    /** Back to the app's built-in server. */
    fun useDefaultServer() {
        viewModelScope.launch {
            groups.setServerUrl(null)
            local.update { it.copy(addressError = null, message = "This group uses the app's sync server again.") }
        }
    }

    fun clearAddressError() = local.update { it.copy(addressError = null) }

    fun clearJoinError() = local.update { it.copy(joinError = null) }

    fun messageShown() = local.update { it.copy(message = null) }
}
