package dev.quinton.goa.feature.sync.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.GroupRepository
import dev.quinton.goa.core.data.GroupState
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SyncRepository
import dev.quinton.goa.core.data.backup.AppInfo
import dev.quinton.goa.core.domain.sync.SyncValidation
import dev.quinton.goa.core.domain.sync.protocol.ClientPhase
import dev.quinton.goa.core.domain.sync.protocol.ClientState
import dev.quinton.goa.core.domain.sync.protocol.HostPhase
import dev.quinton.goa.core.domain.sync.protocol.HostState
import dev.quinton.goa.core.domain.sync.protocol.SyncClient
import dev.quinton.goa.core.domain.sync.protocol.SyncDevice
import dev.quinton.goa.core.domain.sync.protocol.SyncHost
import dev.quinton.goa.core.domain.sync.protocol.SyncPeer
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.sync.PhoneName
import dev.quinton.goa.feature.sync.SyncText
import dev.quinton.goa.feature.sync.di.SessionTransport
import dev.quinton.goa.feature.sync.di.SyncTransportFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SessionRole { HOST, JOIN }

/** Where the sync sheet is. */
sealed interface SessionStage {
    /** Host or Join? */
    data object Choose : SessionStage

    data object NotInGroup : SessionStage

    /** This phone's own data breaks a rule, so it can't be shared until fixed. */
    data class LocalProblem(val message: String) : SessionStage

    data class Hosting(val state: HostState) : SessionStage

    data class Joining(val state: ClientState) : SessionStage
}

data class SyncSessionUiState(
    val stage: SessionStage = SessionStage.Choose,
    val groupName: String? = null,
    /** What the other phones see this one as. */
    val deviceName: String = "",
    /** Right after joining: "Which player are you?" comes next. */
    val firstSync: Boolean = false,
    /** This phone has picked its player ("Who are you?"); if not, a finished sync offers it. */
    val meChosen: Boolean = true,
    /** Asked for by the route; the sheet starts it once the permissions are granted. */
    val requestedRole: SessionRole? = null,
    /** The phones run different app versions (hero ratings can differ). */
    val versionHint: String? = null,
) {
    /** Merged data is being written or sent: the sheet holds open (Cancel is still offered). */
    val busy: Boolean get() = when (val s = stage) {
        is SessionStage.Hosting -> s.state.phase == HostPhase.MERGING || s.state.phase == HostPhase.SENDING
        is SessionStage.Joining -> s.state.phase == ClientPhase.RECEIVING || s.state.phase == ClientPhase.APPLYING
        else -> false
    }

    val finished: Boolean get() = when (val s = stage) {
        is SessionStage.Hosting -> s.state.phase == HostPhase.DONE || s.state.phase == HostPhase.FAILED
        is SessionStage.Joining -> s.state.phase == ClientPhase.DONE || s.state.phase == ClientPhase.FAILED
        else -> false
    }

    val succeeded: Boolean get() = when (val s = stage) {
        is SessionStage.Hosting -> s.state.phase == HostPhase.DONE
        is SessionStage.Joining -> s.state.phase == ClientPhase.DONE
        else -> false
    }
}

/**
 * One sync session at the table (ADR-025): Host collects every phone's data, merges and sends the
 * result back; Join sends this phone's data and takes the result. The session lives as long as the
 * sheet: closing it (or Cancel) stops the radios, and a phone that hadn't saved the merged data
 * keeps its own untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SyncSessionViewModel.Factory::class)
class SyncSessionViewModel @AssistedInject constructor(
    @Assisted role: String?,
    @Assisted private val firstSync: Boolean,
    private val groups: GroupRepository,
    private val sync: SyncRepository,
    private val players: PlayerRepository,
    private val transports: SyncTransportFactory,
    private val clock: EditClock,
    private val appInfo: AppInfo,
    private val phoneName: PhoneName,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(role: String?, firstSync: Boolean): SyncSessionViewModel
    }

    private sealed interface Session {
        class Host(val host: SyncHost) : Session

        class Client(val client: SyncClient) : Session

        data class Problem(val stage: SessionStage) : Session
    }

    private val session = MutableStateFlow<Session?>(null)
    private var job: Job? = null

    /** The last cancelled session, until it has released the radios. */
    private var stopping: Job? = null
    private var transport: SessionTransport? = null
    private val requested =
        MutableStateFlow(SessionRole.entries.firstOrNull { it.name.equals(role, ignoreCase = true) })

    private val stage: Flow<SessionStage?> = session.flatMapLatest { s ->
        when (s) {
            null -> flowOf(null)
            is Session.Host -> s.host.state.map { SessionStage.Hosting(it) }
            is Session.Client -> s.client.state.map { SessionStage.Joining(it) }
            is Session.Problem -> flowOf(s.stage)
        }
    }

    val uiState: StateFlow<SyncSessionUiState> = combine(
        stage,
        groups.state,
        players.observePlayers(includeArchived = true),
        requested,
    ) { stage, group, people, role ->
        val others = when (stage) {
            is SessionStage.Hosting -> stage.state.peers.mapNotNull { it.device }
            is SessionStage.Joining -> listOfNotNull(stage.state.host)
            else -> emptyList()
        }
        SyncSessionUiState(
            stage = stage ?: if (group.group == null) SessionStage.NotInGroup else SessionStage.Choose,
            groupName = group.group?.name,
            deviceName = deviceName(group, people),
            firstSync = firstSync,
            meChosen = people.any { it.id == group.mePlayerId },
            requestedRole = role,
            versionHint = SyncText.versionHint(appInfo.versionName(), others),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSessionUiState(firstSync = firstSync))

    /** Starts hosting or joining; call once the Nearby permissions are granted. */
    fun start(role: SessionRole) {
        if (job?.isActive == true) return
        requested.value = null
        val previous = stopping
        job = viewModelScope.launch {
            // A cancelled session releases the shared Nearby client in its `finally`; let it finish
            // first, or it would stop this session's advertising.
            previous?.join()
            val state = groups.state.first()
            val group = state.group ?: return@launch
            // Never share data this phone's own checks would refuse from another phone.
            SyncValidation.problem(sync.export())?.let { problem ->
                session.value = Session.Problem(SessionStage.LocalProblem(problem))
                return@launch
            }
            val name = deviceName(state, players.observePlayers(includeArchived = true).first())
            val me = SyncDevice(state.deviceId, name, appInfo.versionName())
            val opened = transports.create().also { transport = it }
            try {
                when (role) {
                    SessionRole.HOST -> {
                        val host = SyncHost(opened, group, me, sync)
                        session.value = Session.Host(host)
                        host.run()
                        host.state.value.takeIf { it.phase == HostPhase.DONE }
                            ?.let { groups.recordSync(it.synced, clock.now()) }
                    }

                    SessionRole.JOIN -> {
                        val client = SyncClient(opened, group, me, sync)
                        session.value = Session.Client(client)
                        client.run()
                        val done = client.state.value
                        val host = done.host
                        if (done.phase == ClientPhase.DONE && host != null) groups.recordSync(listOf(host), clock.now())
                    }
                }
            } finally {
                opened.shutdown()
                if (transport === opened) transport = null
            }
        }
    }

    /** Host: stop waiting for more phones and sync the ones in. */
    fun syncNow() {
        (session.value as? Session.Host)?.host?.syncNow()
    }

    /** Join: several hosts in range; join this one. */
    fun chooseHost(peer: SyncPeer) {
        (session.value as? Session.Client)?.client?.choose(peer)
    }

    /** Cancel, or "Try again" after a failure: back to Host / Join. */
    fun reset() {
        stop()
        session.value = null
    }

    private fun stop() {
        job?.cancel()
        stopping = job
        job = null
        transport?.shutdown()
        transport = null
    }

    override fun onCleared() {
        stop()
    }

    private fun deviceName(state: GroupState, people: List<Player>): String =
        people.firstOrNull { it.id == state.mePlayerId }?.name ?: state.deviceName ?: phoneName.get()
}
