package dev.quinton.goa.feature.timer.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.domain.setup.MatchSetup
import dev.quinton.goa.core.domain.timer.PhaseKind
import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.timer.TimerText
import dev.quinton.goa.feature.timer.data.TimerRepository
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.feature.timer.session.TimerController
import dev.quinton.goa.feature.timer.system.CuePlayer
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
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
    /** Open the linked match to log its result (its duration is already saved); [startedAt]: session start. */
    data class OpenMatch(val matchId: String, val startedAt: Long? = null) : TimerEvent

    /**
     * No linked match: open a NEW match pre-filled with the duration and, when legal for the
     * session's hero count, its mode (a `GameMode` serial name). Nothing is saved until the
     * user saves that match.
     */
    data class NewMatch(
        val durationMin: Int,
        val mode: String?,
        val startedAt: Long? = null,
        /** The id the new match is saved under; saving it clears the ended session. */
        val matchId: String? = null,
    ) : TimerEvent
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
) : ViewModel() {
    private val visible = MutableStateFlow(false)
    private val seatsChoice = MutableStateFlow(DEFAULT_SEATS)
    private val events = Channel<TimerEvent>(Channel.BUFFERED)
    val navigation: Flow<TimerEvent> = events.receiveAsFlow()

    /** Notifications denied/blocked (re-checked whenever the screen starts). */
    private val alertsOff = MutableStateFlow(false)

    /** Session whose alerts-off banner was dismissed. */
    private val bannerDismissedFor = MutableStateFlow<String?>(null)

    /** Set by Log result / Done so a double tap can't act twice on the same summary. */
    private val finishing = AtomicBoolean(false)

    /** Log result opened a new match's result screen; set until the timer screen shows again. */
    @Volatile private var resultOpened = false

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
        repository.tonightTurn,
    ) { presets, selected, pendingList, seats, tonight ->
        val own = presets.firstOrNull { it.id == selected }
            ?.phases?.firstOrNull { it.enabled && it.kind == PhaseKind.TURN }?.seconds
        StartUiState(
            tonight = own?.let { TonightUi(tonight?.takeIf { t -> t.presetId == selected }?.seconds ?: it, it) },
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

    private val banner: Flow<Pair<Boolean, String?>> = combine(alertsOff, bannerDismissedFor) { off, id -> off to id }

    val uiState: StateFlow<TimerUiState> = combine(controller.snapshot, linkedSeats, startState, banner, ticks) {
            s,
            seats,
            start,
            (off, dismissedFor),
            _,
        ->
        when {
            s == null -> TimerUiState(loading = false, start = start)

            s.status == TimerStatus.ENDED -> TimerUiState(
                loading = false,
                summary = TimerUiMapper.summary(s, controller.readout(s)),
            )

            else -> TimerUiState(
                loading = false,
                active = TimerUiMapper.active(
                    s,
                    controller.readout(s),
                    seats,
                    alertsOff = off && dismissedFor != s.sessionId,
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TimerUiState())

    private var cueKey: String? = null
    private var cueElapsed: Long = 0

    init {
        viewModelScope.launch {
            combine(controller.snapshot, ticks) { s, _ -> s }.collect { s -> if (visible.value) onTick(s) }
        }
        // The result logged from the summary was saved: the finished session is done.
        viewModelScope.launch {
            controller.snapshot
                .map { s -> s?.takeIf { it.status == TimerStatus.ENDED }?.loggingMatchId }
                .distinctUntilChanged()
                .flatMapLatest { id -> id?.let { matches.observeMatch(it).map { m -> m?.id } } ?: flowOf(null) }
                .collect { saved -> if (saved != null) controller.clearLogged(saved) }
        }
    }

    /** Screen lifecycle: cues and ticks only run while the timer is on screen. */
    fun setVisible(isVisible: Boolean) {
        if (isVisible) resultOpened = false // Back on the timer: Log result works again.
        visible.value = isVisible
        controller.uiVisible = isVisible
        if (isVisible) refreshNotificationAccess() else cueKey = null
    }

    /** Re-checks notification access (screen start, after the permission prompt). */
    fun refreshNotificationAccess() {
        alertsOff.value = !controller.notificationsEnabled()
    }

    fun dismissAlertsBanner() = launch {
        bannerDismissedFor.value = controller.snapshot.first()?.sessionId
    }

    fun selectPreset(id: String) = launch { repository.select(id) }

    fun setSeats(seats: Int) {
        seatsChoice.value = seats.coerceIn(MIN_SEATS, MAX_SEATS)
    }

    fun start() = launch {
        refreshNotificationAccess()
        controller.start(repository.selectedPreset(), actionSlots = seatsChoice.value)
    }

    fun togglePause() = command(TimerCommand.TogglePause).also { cues.tap(settings.value.hapticsEnabled) }

    fun next() = command(TimerCommand.Next)

    fun back() = command(TimerCommand.Back)

    fun restart() = command(TimerCommand.Restart)

    fun skipToEndOfRound() = command(TimerCommand.SkipToEndOfRound)

    fun flipTieBreaker() = command(TimerCommand.FlipTieBreaker)

    fun setTieBreaker(team: Team?) = command(TimerCommand.SetTieBreaker(team))

    fun heroResolves(seat: Int) = command(TimerCommand.HeroResolves(seat))

    fun startShotClock() = command(TimerCommand.StartShotClock)

    fun cancelShotClock() = command(TimerCommand.CancelShotClock)

    fun timeOut(team: Team) = command(TimerCommand.TimeOut(team))

    /** Tonight's turn length for the selected preset (the preset itself is unchanged). */
    fun setTonightTurn(seconds: Int) = launch {
        val id = repository.selectedPresetId.first()
        repository.setTonightTurn(id, seconds.coerceIn(TONIGHT_MIN_SECONDS, TONIGHT_MAX_SECONDS))
    }

    fun saveTonightToPreset() = launch { repository.saveTonightToPreset() }

    /** The controller writes the duration to a linked match (single source). */
    fun endMatch() = command(TimerCommand.EndMatch)

    /**
     * Opens the linked match (its duration is already saved) and clears the session. Without a
     * linked match, opens a new match pre-filled with the duration under an id reserved in the
     * session (no stub match is saved); the session and its summary stay until that match is
     * saved, so backing out of the result screen loses nothing. Ignores repeated taps.
     */
    fun logResult() = finishOnce {
        val s = controller.snapshot.first()?.takeIf { it.status == TimerStatus.ENDED } ?: return@finishOnce
        val linked = s.matchId
        if (linked != null) {
            controller.clear()
            events.send(TimerEvent.OpenMatch(linked, s.matchStartWall))
            return@finishOnce
        }
        // The summary stays on screen while the result screen opens: a second tap must not open it twice.
        if (resultOpened) return@finishOnce
        val id = controller.reserveLoggingMatchId() ?: return@finishOnce
        resultOpened = true
        events.send(
            TimerEvent.NewMatch(
                durationMin = TimerUiMapper.durationMinutes(controller.readout(s).matchElapsedMillis),
                mode = legalMode(s)?.let(::serialName),
                startedAt = s.matchStartWall,
                matchId = id,
            ),
        )
    }

    fun dismissSummary() = finishOnce {
        if (controller.snapshot.first()?.status == TimerStatus.ENDED) controller.clear()
    }

    /**
     * Runs [block] unless a Log result / Done is already in flight. Once it completes the session
     * is cleared, so a late second tap finds no ended session and does nothing.
     */
    private fun finishOnce(block: suspend () -> Unit) {
        if (!finishing.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                finishing.set(false)
            }
        }
    }

    /** The session's mode if the rules allow it for its number of heroes (Action slots = seats). */
    private suspend fun legalMode(s: TimerSnapshot): GameMode? {
        val mode = s.mode ?: return null
        val option = MatchSetup(repository.rules()).modeOptions(s.actionSlots).firstOrNull { it.mode == mode }
        return mode.takeIf { option?.available == true }
    }

    private fun serialName(mode: GameMode): String = GameMode.serializer().descriptor.getElementName(mode.ordinal)

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
        shotCues(s, readout.shotClockRemainingMillis)
        val remaining = readout.remainingMillis
        if (s.preset.autoAdvance && remaining != null && remaining <= 0) {
            controller.perform(TimerCommand.AdvanceDue)
        }
    }

    private var shotKey: String? = null
    private var shotRemaining: Long = 0

    /** "Hurry up" cues at 10 s, 5 s and zero, played once each like phase cues. */
    private fun shotCues(s: TimerSnapshot, remaining: Long?) {
        val clock = s.shotClock
        if (clock == null || remaining == null) {
            shotKey = null
            return
        }
        val key = "${s.sessionId}:${s.round}:${s.stepIndex}:${clock.durationMillis}:" +
            "${(clock.runningSince ?: 0) - clock.accumulatedMillis}"
        if (key == shotKey && shotRemaining - remaining <= STALE_CUE_MS) {
            val crossed = SHOT_CUES.filter { it * 1000L in remaining until shotRemaining }
            val prefs = settings.value
            crossed.lastOrNull()?.let { cues.cue(it, prefs.hapticsEnabled, prefs.soundsEnabled) }
        }
        shotKey = key
        shotRemaining = remaining
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

        private val SHOT_CUES = listOf(10, 5, 0)
        const val TONIGHT_STEP_SECONDS = 15
        const val TONIGHT_MIN_SECONDS = 15
        const val TONIGHT_MAX_SECONDS = 300
    }
}
