package dev.quinton.goa.feature.timer.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.domain.WallClock
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.di.TimerClock
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.feature.timer.session.TimerController
import dev.quinton.goa.feature.timer.system.CuePlayer
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Drives screen refreshes while the timer is visible. */
fun interface Ticker {
    fun ticks(): Flow<Unit>
}

class FrameTicker @Inject constructor() : Ticker {
    override fun ticks(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private companion object {
        const val TICK_MS = 100L
    }
}

sealed interface TimerEvent {
    data class OpenMatch(val matchId: String) : TimerEvent
}

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val controller: TimerController,
    private val repository: TimerRepository,
    private val matches: MatchRepository,
    catalogRepository: CatalogRepository,
    players: PlayerRepository,
    settingsRepository: SettingsRepository,
    private val cues: CuePlayer,
    private val ticker: Ticker,
    @param:TimerClock private val wall: WallClock,
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val seatsChoice = MutableStateFlow(DEFAULT_SEATS)
    private val events = Channel<TimerEvent>(Channel.BUFFERED)
    val navigation: Flow<TimerEvent> = events.receiveAsFlow()

    private val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val ticks: Flow<Unit> = visible.flatMapLatest { if (it) ticker.ticks() else emptyFlow() }
        .onStart { emit(Unit) }

    private val catalog: Flow<Catalog?> = catalogRepository.catalog.map<Catalog, Catalog?> { it }.onStart { emit(null) }

    private val linkedSeats: Flow<List<SeatInfo>?> = controller.snapshot
        .map { it?.matchId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(matches.observeMatch(id), catalog, players.observePlayers(includeArchived = true)) { m, c, p ->
                    m?.let { TimerUiMapper.seats(it, c, p) }
                }
            }
        }

    private val pending: Flow<List<PendingMatchItem>> = matches.observeMatches().map { list ->
        list.filter { it.status == MatchStatus.PENDING }
            .sortedByDescending { it.createdAt }
            .map(::pendingItem)
    }

    private val startState: Flow<StartUiState> = combine(
        repository.presets,
        repository.selectedPresetId,
        pending,
        seatsChoice,
    ) { presets, selected, pendingList, seats ->
        StartUiState(
            presets = presets.map { p ->
                PresetOption(
                    id = p.id,
                    name = p.name,
                    summary = TimerText.presetSummary(p),
                    usesActionTimer = p.phases.any { it.enabled && it.kind == PhaseKind.ACTION },
                )
            },
            selectedPresetId = selected,
            pendingMatches = pendingList,
            seats = seats,
        )
    }

    val uiState: StateFlow<TimerUiState> = combine(controller.snapshot, linkedSeats, startState, ticks) { s, seats, start, _ ->
        when {
            s == null -> TimerUiState(loading = false, start = start)
            s.status == TimerStatus.ENDED -> TimerUiState(
                loading = false,
                summary = TimerUiMapper.summary(s, controller.readout(s)),
            )
            else -> TimerUiState(loading = false, active = TimerUiMapper.active(s, controller.readout(s), seats))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerUiState())

    private var cueKey: String? = null
    private var cueElapsed: Long = 0

    init {
        viewModelScope.launch {
            combine(controller.snapshot, ticks) { s, _ -> s }.collect { s -> if (visible.value) onTick(s) }
        }
    }

    /** Screen lifecycle: cues and ticks only run while the timer is on screen. */
    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
        controller.uiVisible = isVisible
        if (!isVisible) cueKey = null
    }

    fun selectPreset(id: String) = launch { repository.select(id) }

    fun setSeats(seats: Int) {
        seatsChoice.value = seats.coerceIn(MIN_SEATS, MAX_SEATS)
    }

    fun start() = launch {
        controller.start(repository.selectedPreset(), actionSlots = seatsChoice.value)
    }

    fun togglePause() = command(TimerCommand.TogglePause).also { cues.tap(settings.value.hapticsEnabled) }

    fun next() = command(TimerCommand.Next)

    fun back() = command(TimerCommand.Back)

    fun restart() = command(TimerCommand.Restart)

    fun skipToEndOfRound() = command(TimerCommand.SkipToEndOfRound)

    fun flipTieBreaker() = command(TimerCommand.FlipTieBreaker)

    fun heroResolves(seat: Int) = command(TimerCommand.HeroResolves(seat))

    fun endMatch() = launch {
        controller.perform(TimerCommand.EndMatch)?.let { recordDuration(it) }
    }

    /** Writes the duration into the match (creating a pending one if needed) and opens it. */
    fun logResult() = launch {
        val s = controller.snapshot.first() ?: return@launch
        val id = recordDuration(s) ?: createMatch(s)
        controller.clear()
        events.send(TimerEvent.OpenMatch(id))
    }

    fun dismissSummary() = launch {
        controller.snapshot.first()?.let { recordDuration(it) }
        controller.clear()
    }

    private suspend fun recordDuration(s: TimerSnapshot): String? {
        val id = s.matchId ?: return null
        val match = matches.observeMatch(id).first() ?: return null
        val minutes = TimerUiMapper.durationMinutes(controller.readout(s).matchElapsedMillis)
        matches.upsert(match.copy(durationMin = minutes, mode = s.mode ?: match.mode))
        return id
    }

    private suspend fun createMatch(s: TimerSnapshot): String {
        val id = UUID.randomUUID().toString()
        matches.upsert(
            Match(
                id = id,
                createdAt = wall.nowMillis(),
                playedAt = s.matchStartWall,
                status = MatchStatus.PENDING,
                mode = s.mode ?: GameMode.QUICK,
                durationMin = TimerUiMapper.durationMinutes(controller.readout(s).matchElapsedMillis),
                participants = emptyList(),
            ),
        )
        return id
    }

    private suspend fun onTick(s: TimerSnapshot?) {
        if (s == null || s.status != TimerStatus.RUNNING) {
            cueKey = null
            return
        }
        val readout = controller.readout(s)
        val key = "${s.sessionId}:${s.round}:${s.stepIndex}:${(s.runningSince ?: 0) - s.accumulatedMillis}"
        val now = readout.elapsedInStepMillis
        if (key == cueKey && now - cueElapsed <= STALE_CUE_MS) {
            val crossed = controller.cuesCrossed(s, cueElapsed, now)
            val prefs = settings.value
            crossed.lastOrNull()?.let { cues.cue(it, prefs.hapticsEnabled, prefs.soundsEnabled) }
        }
        cueKey = key
        cueElapsed = now
        val remaining = readout.remainingMillis
        if (s.preset.autoAdvance && remaining != null && remaining <= 0) {
            controller.perform(TimerCommand.AdvanceDue)
        }
    }

    private fun command(command: TimerCommand) = launch { controller.perform(command) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun pendingItem(match: Match): PendingMatchItem {
        val seats = match.participants.size
        val players = match.playerIds.size
        val title = buildString {
            append(TimerText.modeLabel(match.mode))
            if (seats > 0) append(" · $seats heroes")
            if (players in 1 until seats) append(", $players players")
        }
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(match.createdAt))
        return PendingMatchItem(id = match.id, title = title, subtitle = "Pending since $date")
    }

    companion object {
        const val DEFAULT_SEATS = 4
        const val MIN_SEATS = 2
        const val MAX_SEATS = 10
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Gaps longer than this (app was hidden) don't replay old cues. */
        private const val STALE_CUE_MS = 2_000L
    }
}
