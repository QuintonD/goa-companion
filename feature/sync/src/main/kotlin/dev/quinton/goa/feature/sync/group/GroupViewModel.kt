package dev.quinton.goa.feature.sync.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.domain.sync.GroupCredentials
import dev.quinton.goa.core.domain.sync.GroupInvite
import dev.quinton.goa.core.domain.sync.HubAddress
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.sync.PhoneName
import dev.quinton.goa.feature.sync.SyncText
import dev.quinton.goa.feature.sync.hub.HubSyncControl
import dev.quinton.goa.feature.sync.hub.HubText
import dev.quinton.goa.feature.sync.hub.HubUi
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
    /** The invite the QR code shows (`goa-sync:1?…`, or `<hub>/join#goa-sync:1?…` with a hub, ADR-030). */
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
    /** The group's hub and how its automatic sync went (ADR-030 §5). */
    val hub: HubUi = HubUi(),
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
    val hubAddressError: String? = null,
)

/** The Group sync screen (ADR-025): start or join a group; invite, sync, share a file, leave. */
@HiltViewModel
class GroupViewModel @Inject constructor(
    private val groups: GroupRepository,
    private val players: PlayerRepository,
    matches: MatchRepository,
    private val sync: SyncRepository,
    private val clock: EditClock,
    private val phoneName: PhoneName,
    private val hubSync: HubSyncControl = HubSyncControl.Off,
) : ViewModel() {
    private val local = MutableStateFlow(Local())
    private val events = Channel<GroupEvent>(Channel.BUFFERED)
    val navigation: Flow<GroupEvent> = events.receiveAsFlow()

    val uiState: StateFlow<GroupUiState> = combine(
        groups.state,
        players.observePlayers(includeArchived = true),
        matches.observeMatches(),
        local,
        hubSync.running,
    ) { group, people, log, l, hubRunning ->
        val now = clock.now()
        val me = people.firstOrNull { it.id == group.mePlayerId }
        GroupUiState(
            loading = false,
            groupName = group.group?.name,
            invite = group.group?.let { GroupInvite.format(it, hubUrl = group.hubUrl) },
            me = me,
            deviceName = me?.name ?: group.deviceName ?: phoneName.get(),
            phones = group.phones.map { PhoneRow(it.name, SyncText.ago(it.lastSyncedAt, now)) },
            lastSync = group.lastSyncAt?.let { SyncText.ago(it, now) },
            players = people.count { !it.archived },
            matches = log.size,
            busy = l.busy,
            joinError = l.joinError,
            message = l.message,
            hub = HubText.ui(group, hubRunning, now, l.hubAddressError),
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

    /** Joins the group in a scanned or pasted invite, then opens the first sync. */
    fun join(invite: String) {
        GroupInvite.parseInvite(invite).fold(
            onSuccess = { parsed ->
                val group = parsed.group
                viewModelScope.launch {
                    val current = groups.state.first().group
                    groups.join(group, hubUrl = parsed.hubUrl, invitedPlayerId = parsed.playerId)
                    if (current?.groupId != group.groupId && parsed.hubUrl != null) {
                        // The hub sync starts on its own (HubSyncScheduler): no Nearby session needed.
                        local.update {
                            it.copy(
                                joinError = null,
                                message = "Joined “${group.name}”. Getting the group's data from its hub…",
                            )
                        }
                    } else if (current?.groupId != group.groupId) {
                        local.update { it.copy(joinError = null) }
                        events.send(GroupEvent.OpenFirstSync)
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

    private var linkOpened = false

    /**
     * An invite link opened from another app (App Links, ADR-030 §6): joins like a pasted invite.
     * A phone in another group is told to leave it first; nothing is replaced without asking.
     */
    fun openLink(link: String) {
        if (linkOpened) return
        linkOpened = true
        val parsed = GroupInvite.parseInvite(link).getOrElse { e ->
            local.update { it.copy(message = e.message) }
            return
        }
        viewModelScope.launch {
            val current = groups.state.first().group
            if (current != null && current.groupId != parsed.group.groupId) {
                local.update {
                    it.copy(
                        message = "This phone is in “${current.name}”. Leave it first to join “${parsed.group.name}”.",
                    )
                }
            } else {
                join(link)
            }
        }
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

    /** Saves the group's hub address ([HubAddress]); automatic sync starts right away. */
    fun setHub(address: String) {
        val url = HubAddress.normalize(address)
        if (url == null) {
            local.update { it.copy(hubAddressError = HubText.NOT_AN_ADDRESS) }
            return
        }
        viewModelScope.launch {
            groups.setHubUrl(url)
            local.update { it.copy(hubAddressError = null, message = "Hub saved. Syncing with it now…") }
        }
    }

    fun removeHub() {
        viewModelScope.launch {
            groups.setHubUrl(null)
            local.update { it.copy(message = "Hub removed. This phone syncs over Nearby and sync files only.") }
        }
    }

    fun syncHub() = hubSync.syncNow()

    fun clearHubAddressError() = local.update { it.copy(hubAddressError = null) }

    fun clearJoinError() = local.update { it.copy(joinError = null) }

    fun messageShown() = local.update { it.copy(message = null) }
}
