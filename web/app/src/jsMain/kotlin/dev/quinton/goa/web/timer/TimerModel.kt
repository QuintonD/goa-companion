package dev.quinton.goa.web.timer

import dev.quinton.goa.core.domain.timer.TimerSnapshot
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.model.settings.TimerLayout
import dev.quinton.goa.feature.timer.data.TonightTurn
import dev.quinton.goa.feature.timer.session.TimerCommand
import dev.quinton.goa.feature.timer.ui.timer.PendingMatchItem
import dev.quinton.goa.feature.timer.ui.timer.SeatInfo
import dev.quinton.goa.feature.timer.ui.timer.StartUiState
import dev.quinton.goa.feature.timer.ui.timer.TimerUiMapper
import dev.quinton.goa.feature.timer.ui.timer.TimerUiState
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.resilient
import dev.quinton.goa.web.table.resilientStarting
import kotlin.js.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Timer tab's state and actions (the phone's `TimerViewModel`): the start card, the running
 * session and the match summary, from the shared [TimerUiMapper]. Lives while the screen does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerModel(private val services: AppServices, private val scope: CoroutineScope) {
    private val table = services.data.table
    private val controller = table.timer
    private val repository = table.timerStore
    private val seatsChoice = MutableStateFlow(DEFAULT_SEATS)

    /** Set by Log result / Done so a double tap can't act twice on the same summary. */
    private var finishing = false

    private val settingsFlow: Flow<AppSettings> =
        resilientStarting("Settings", AppSettings()) { services.data.content.settings.settings }

    private val settings: StateFlow<AppSettings> = settingsFlow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val catalog: Flow<Catalog?> =
        resilientStarting<Catalog?>("The catalog", null) { services.data.content.catalog.catalog.map { it } }

    private val ticks: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    private val linkedSeats: Flow<List<SeatInfo>?> = controller.snapshot
        .map { it?.matchId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(
                    resilient<Match?>("The match", null) { services.data.group.matches.observeMatch(id) },
                    catalog,
                    resilientStarting<List<Player>>("Players", emptyList()) {
                        services.data.group.players.observePlayers(includeArchived = true)
                    },
                ) { m, c, p -> m?.let { TimerUiMapper.seats(it, c, p) } }
            }
        }

    private val pending: Flow<List<PendingMatchItem>> = combine(
        resilientStarting<List<Match>>("The match log", emptyList()) { services.data.group.matches.observeMatches() },
        catalog,
    ) { list, c -> TimerUiMapper.pendingMatches(list, c, ::since) }

    private val startState: Flow<StartUiState> = combine(
        repository.presets,
        repository.selectedPresetId,
        pending,
        seatsChoice,
        repository.tonightTurn,
    ) { presets, selected, pendingList, seats, tonight ->
        TimerUiMapper.start(presets, selected, pendingList, seats, tonight)
    }

    // The layout comes straight from the settings, so a phone set to the round view never flashes the step view.
    private val layout: Flow<TimerLayout> = settingsFlow.map { it.timerLayout }.distinctUntilChanged()

    val uiState: StateFlow<TimerUiState> = combine(controller.snapshot, linkedSeats, startState, layout, ticks) {
            s,
            seats,
            start,
            layout,
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
                active = TimerUiMapper.active(s, controller.readout(s), seats, layout = layout),
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, TimerUiState())

    private var cueKey: String? = null
    private var cueElapsed: Long = 0
    private var shotKey: String? = null
    private var shotRemaining: Long = 0

    init {
        scope.launch { repository.load() }
        scope.launch { combine(controller.snapshot, ticks) { s, _ -> s }.collect { onTick(it) } }
        // The result logged from the summary was saved: the finished session is done.
        scope.launch {
            controller.snapshot
                .map { s -> s?.takeIf { it.status == TimerStatus.ENDED }?.loggingMatchId }
                .distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null)
                    } else {
                        resilient<String?>("The match", null) {
                            services.data.group.matches.observeMatch(id).map { m -> m?.id }
                        }
                    }
                }
                .collect { saved -> if (saved != null) controller.clearLogged(saved) }
        }
    }

    val loadError get() = repository.loadError

    fun retryLoad() = launch { repository.load() }

    fun selectPreset(id: String) = launch { repository.select(id) }

    fun setSeats(seats: Int) {
        seatsChoice.value = seats.coerceIn(MIN_SEATS, MAX_SEATS)
    }

    fun start() = launch {
        table.cues.unlock()
        controller.start(repository.selectedPreset(), actionSlots = seatsChoice.value)
    }

    fun togglePause() = command(TimerCommand.TogglePause)

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

    /** Round view: jump to a step of [round] (a stale tap from an earlier round does nothing). */
    fun jumpTo(round: Int, stepIndex: Int) = command(TimerCommand.JumpTo(round, stepIndex))

    /** The header's view switch: kept in Settings, so the next match agrees. */
    fun setTimerLayout(layout: TimerLayout) = launch {
        runCatching { services.data.content.settings.update { it.copy(timerLayout = layout) } }
            .onFailure { services.messages.show("Couldn't save the view. Try again.") }
    }

    /** Tonight's turn length for the selected preset (the preset itself is unchanged). */
    fun setTonightTurn(seconds: Int) = launch {
        val id = repository.selectedPresetId.first()
        repository.setTonightTurn(id, seconds.coerceIn(TonightTurn.MIN_SECONDS, TonightTurn.MAX_SECONDS))
    }

    fun saveTonightToPreset() = launch { repository.saveTonightToPreset() }

    /** The controller writes the duration to a linked match (single source). */
    fun endMatch() = command(TimerCommand.EndMatch)

    /**
     * Opens the linked match (its duration is already saved) and clears the session. Without a
     * linked match, opens a new match pre-filled with the duration under an id reserved in the
     * session (no stub match is saved); the session and its summary stay until that match is saved,
     * so backing out of the result screen loses nothing. Ignores repeated taps.
     */
    fun logResult() = finishOnce {
        val s = controller.snapshot.first()?.takeIf { it.status == TimerStatus.ENDED } ?: return@finishOnce
        val linked = s.matchId
        if (linked != null) {
            controller.clear()
            services.router.navigate(Route.MatchEdit(linked, result = true, prefillPlayedAt = s.matchStartWall))
            return@finishOnce
        }
        val id = controller.reserveLoggingMatchId() ?: return@finishOnce
        val mode = runCatching { TimerUiMapper.legalMode(s, repository.rules()) }.getOrNull()
        services.router.navigate(
            Route.MatchEdit(
                matchId = null,
                prefillDurationMin = TimerUiMapper.durationMinutes(controller.readout(s).matchElapsedMillis),
                prefillMode = mode?.let(TimerUiMapper::serialName),
                result = true,
                prefillPlayedAt = s.matchStartWall,
                newMatchId = id,
            ),
        )
    }

    fun dismissSummary() = finishOnce {
        if (controller.snapshot.first()?.status == TimerStatus.ENDED) controller.clear()
    }

    /** A pending match nobody is going to play leaves this list and the match log. */
    fun discardPending(matchId: String) = launch {
        runCatching {
            val matches = services.data.group.matches
            val match = matches.observeMatch(matchId).first() ?: return@runCatching
            if (match.status == MatchStatus.PENDING) matches.delete(matchId)
        }.onFailure { services.messages.show("Couldn't discard the match. Try again.") }
    }

    private fun finishOnce(block: suspend () -> Unit) {
        if (finishing) return
        finishing = true
        scope.launch {
            try {
                block()
            } finally {
                finishing = false
            }
        }
    }

    private suspend fun onTick(s: TimerSnapshot?) {
        if (s == null || s.status != TimerStatus.RUNNING) {
            cueKey = null
            shotKey = null
            return
        }
        val readout = controller.readout(s)
        val key = "${s.sessionId}:${s.round}:${s.stepIndex}:${(s.runningSince ?: 0) - s.accumulatedMillis}"
        val now = readout.elapsedInStepMillis
        if (key == cueKey && now - cueElapsed <= STALE_CUE_MS) {
            controller.cuesCrossed(s, cueElapsed, now).lastOrNull()?.let(::cue)
        }
        cueKey = key
        cueElapsed = now
        shotCues(s, readout.shotClockRemainingMillis)
        val remaining = readout.remainingMillis
        if (s.preset.autoAdvance && remaining != null && remaining <= 0) controller.perform(TimerCommand.AdvanceDue)
    }

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
            SHOT_CUES.filter { it * MILLIS in remaining until shotRemaining }.lastOrNull()?.let(::cue)
        }
        shotKey = key
        shotRemaining = remaining
    }

    private fun cue(secondsLeft: Int) {
        val prefs = settings.value
        table.cues.cue(secondsLeft, sound = prefs.soundsEnabled, flash = prefs.hapticsEnabled)
    }

    private fun command(command: TimerCommand) = launch { controller.perform(command) }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    companion object {
        const val DEFAULT_SEATS = 4
        const val MIN_SEATS = 2
        const val MAX_SEATS = 10
        private const val TICK_MS = 100L
        private const val MILLIS = 1_000L

        /** Gaps longer than this (the page was hidden) don't replay old cues. */
        private const val STALE_CUE_MS = 2_000L
        private val SHOT_CUES = listOf(10, 5, 0)

        /** "5:06 PM" today, else "Oct 16, 2026, 5:06 PM": pending matches from one evening differ by time. */
        fun since(millis: Long): String {
            val date = Date(millis.toDouble())
            val time = date.asDynamic().toLocaleTimeString(
                js("[]"),
                js("({ hour: 'numeric', minute: '2-digit' })"),
            ) as String
            val dayOptions = js("({ month: 'short', day: 'numeric', year: 'numeric' })")
            val day = date.asDynamic().toLocaleDateString(js("[]"), dayOptions) as String
            val today = Date().asDynamic().toLocaleDateString(js("[]"), dayOptions) as String
            return if (day == today) time else "$day, $time"
        }
    }
}
