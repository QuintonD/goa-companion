package dev.quinton.goa.feature.board.play.puzzles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.BoardMapRepository
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleSession
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.core.navigation.PuzzleRoute
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.game.PlayAnnouncement
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.choiceFor
import dev.quinton.goa.feature.board.play.table.logLines
import javax.inject.Provider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long things take on the puzzle screen; tests shorten nothing, they run on virtual time. */
data class PuzzleTiming(
    /** A nudge comes by itself after this long without progress (nudges on). */
    val idleNudgeMs: Long = 90_000L,
    /** Each line of the other side's replies shows for this long… */
    val replyLineMs: Long = 700L,
    /** …but all of them together for no longer than this. */
    val replyMaxMs: Long = 3_500L,
    /** The last reply stays this long before the board catches up. */
    val replyHoldMs: Long = 600L,
)

/**
 * One puzzle (PRODUCT §10). The player answers their heroes' questions on the game table; the
 * runtime answers every other seat at once, and the screen narrates those replies line by line
 * before the board catches up. A move that loses the forced line is MISSED: a gentle reason from
 * what followed, Try again from before it ([PuzzleSession.undo]) or Restart. Nudges (on by default,
 * Settings → Puzzle nudges) come after a miss or a while without progress, each stronger; the last
 * one points at the move on the board ([PuzzleSession.bestChoice]); with nudges off they come only
 * when asked. Solved, or after Show solution: the explanation and the solution replayed step by
 * step on a fresh session. Progress (tries, nudges used, solution shown, solved) is saved locally.
 *
 * The session is not thread-safe: every call on it runs on [engine] (one thread in the app).
 */
@HiltViewModel(assistedFactory = PuzzleViewModel.Factory::class)
class PuzzleViewModel internal constructor(
    private val puzzleId: String,
    private val catalogRepository: CatalogRepository,
    private val mapRepository: BoardMapRepository,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: PuzzleProgressRepository,
    private val book: suspend () -> PuzzleBook,
    private val clock: EditClock,
    private val engine: CoroutineDispatcher,
    private val timing: PuzzleTiming = PuzzleTiming(),
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    @AssistedInject
    constructor(
        @Assisted route: PuzzleRoute,
        catalogRepository: CatalogRepository,
        mapRepository: BoardMapRepository,
        settingsRepository: SettingsRepository,
        progressRepository: PuzzleProgressRepository,
        book: Provider<PuzzleBook>,
        clock: EditClock,
    ) : this(
        route.puzzleId,
        catalogRepository,
        mapRepository,
        settingsRepository,
        progressRepository,
        { withContext(Dispatchers.Default) { book.get() } },
        clock,
        Dispatchers.Default.limitedParallelism(1),
    )

    @AssistedFactory
    interface Factory {
        fun create(route: PuzzleRoute): PuzzleViewModel
    }

    private val state = MutableStateFlow<PuzzleUiState>(PuzzleUiState.Loading)
    val uiState: StateFlow<PuzzleUiState> = state.asStateFlow()

    /** The puzzle being played; only touched on [engine]. */
    private var session: PuzzleSession? = null
    private var puzzles: PuzzleBook? = null
    private var info: PuzzleInfo? = null
    private var total = 0
    private var next: String? = null
    private var map: BoardMap? = null
    private var heroes: Map<String, BoardHero> = emptyMap()
    private var cards: Map<String, Card> = emptyMap()
    private var mine: Set<String> = emptySet()
    private var teams: Map<String, Team> = emptyMap()
    private var bottomTeam: Team = Team.RED

    private var phase = PuzzlePhase.SOLVING
    private var miss: String? = null
    private var replying: ReplyingUi? = null

    /** While the replies are narrated: the board just after the player's own move. */
    private var interim: Pair<GameView, List<String>>? = null
    private var selected: Choice? = null
    private var hint: Choice? = null
    private var nudgeLevel = 0
    private var nudgeVisible = false
    private var nudgesOn = true
    private var introOpen = true

    /** A move was made in this try, so the try is counted. */
    private var moved = false
    private var stored = PuzzleProgress()
    private var replay: Replay? = null
    private var busy = false
    private var announcementId = 0L
    private var announcement: PlayAnnouncement? = null

    /** The nudge that comes by itself after a while without progress. */
    private var idle: Job? = null

    private class Replay(val steps: List<ReplayStep>, val afterSolve: Boolean, var index: Int = 0)

    private class ReplayStep(val view: GameView, val decision: Decision?, val choice: Choice?, val lines: List<String>)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = try {
            book()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return unavailable(NoPuzzle.BROKEN)
        }
        val list = book.puzzles
        val index = list.indexOfFirst { it.id == puzzleId }
        if (index < 0) return unavailable(NoPuzzle.MISSING)
        val catalog = runCatching { catalogRepository.catalog.first() }.getOrDefault(Catalog.EMPTY)
        val maps = runCatching { mapRepository.maps.first() }.getOrDefault(emptyList())
        val started = withContext(engine) {
            try {
                book.start(puzzleId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return unavailable(NoPuzzle.BROKEN)
        val setup = withContext(engine) { started.game.setup }
        map = maps.firstOrNull { it.id == setup.mapId } ?: return unavailable(NoPuzzle.BROKEN)
        puzzles = book
        info = list[index]
        total = list.size
        next = list.getOrNull(index + 1)?.id
        heroes = catalog.boardHeroes()
        cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }
        mine = started.playerSeats
        teams = setup.seats.associate { it.heroId to it.team }
        bottomTeam = mine.firstNotNullOfOrNull(teams::get) ?: Team.RED
        session = started
        nudgesOn = settingsRepository.settings.catch { emit(AppSettings()) }.first().puzzleNudges
        publish()
        scheduleIdle()
        viewModelScope.launch {
            settingsRepository.settings.catch { emit(AppSettings()) }.collect { settings ->
                if (settings.puzzleNudges != nudgesOn) {
                    nudgesOn = settings.puzzleNudges
                    scheduleIdle()
                    publish()
                }
            }
        }
        viewModelScope.launch {
            progressRepository.progress.catch { emit(emptyMap()) }.collect { all ->
                stored = all[puzzleId] ?: PuzzleProgress()
                publish()
            }
        }
    }

    private fun unavailable(why: NoPuzzle) {
        state.value = PuzzleUiState.Unavailable(why)
    }

    // ---- Actions -------------------------------------------------------------------------

    fun onAction(action: PuzzleAction) {
        when (action) {
            is PuzzleAction.Tap -> tap(action.tap)

            is PuzzleAction.Select -> select(action.choice)

            is PuzzleAction.Answer -> answer(action.choice)

            PuzzleAction.TryAgain -> tryAgain()

            PuzzleAction.Restart -> restart()

            PuzzleAction.Nudge -> viewModelScope.launch { showNudge() }

            PuzzleAction.DismissNudge -> dismissNudge()

            is PuzzleAction.SetNudges -> viewModelScope.launch {
                withContext(NonCancellable) { settingsRepository.update { it.copy(puzzleNudges = action.on) } }
            }

            is PuzzleAction.SetIntroOpen -> {
                introOpen = action.open
                refresh()
            }

            PuzzleAction.ShowSolution -> showSolution()

            PuzzleAction.ReplayNext -> step(+1)

            PuzzleAction.ReplayBack -> step(-1)

            PuzzleAction.CloseReplay -> {
                replay = null
                scheduleIdle()
                refresh()
            }

            PuzzleAction.Flip -> {
                bottomTeam = bottomTeam.opponent
                refresh()
            }
        }
    }

    /** A tap on the board: the option it stands for is selected. Returns false when it was no option. */
    fun tap(tap: TableTap): Boolean {
        val puzzle = (uiState.value as? PuzzleUiState.Ready)?.puzzle ?: return false
        val choice = puzzle.table.choiceFor(tap) ?: return false
        select(choice)
        return true
    }

    private fun select(choice: Choice) {
        val puzzle = (uiState.value as? PuzzleUiState.Ready)?.puzzle ?: return
        if (!puzzle.table.asking || choice !in puzzle.table.decision?.options.orEmpty()) return
        selected = choice
        refresh()
    }

    private fun answer(choice: Choice) {
        val s = session ?: return
        if (busy || phase != PuzzlePhase.SOLVING || replay != null) return
        busy = true
        idle?.cancel()
        viewModelScope.launch {
            publish()
            val played = withContext(engine) { play(s, choice) }
            if (played == null) {
                busy = false
                publish()
                return@launch
            }
            selected = null
            hint = null
            nudgeVisible = false
            introOpen = false
            if (!moved) {
                moved = true
                record { it.copy(attempts = it.attempts + 1, lastPlayedAt = clock.now()) }
            } else {
                record { it.copy(lastPlayedAt = clock.now()) }
            }
            narrate(played)
            when (played.status) {
                PuzzleStatus.SOLVED -> solved()

                PuzzleStatus.MISSED -> missed(played.events)

                PuzzleStatus.PLAYING -> {
                    phase = PuzzlePhase.SOLVING
                    announce(played.events.joinToString(" ") { it.text })
                }
            }
            busy = false
            publish()
            scheduleIdle()
        }
    }

    /** What one answer did: every event, how many of them were the player's own move, the board after it. */
    private class Played(
        val events: List<GameEvent>,
        val own: Int,
        val ownView: GameView,
        val status: PuzzleStatus,
    )

    /** On [engine]: the answer, with the board just after the player's own move (a fork answered alone). */
    private fun play(s: PuzzleSession, choice: Choice): Played? {
        val decision = s.game.pending ?: return null
        if (decision.seat !in mine || choice !in decision.options) return null
        val before = s.game.events.size
        val fork = s.game.fork()
        val own = runCatching { fork.answer(decision.id, choice) }.getOrNull()
        val result = s.answer(decision.id, choice) as? AnswerResult.Accepted ?: return null
        val ownCount = if (own is AnswerResult.Accepted) fork.events.size - before else result.events.size
        val ownView = if (own is AnswerResult.Accepted) openView(fork) else openView(s.game)
        // A game over at the deadline is a miss when the goal wasn't met: pending null, status says.
        return Played(result.events, ownCount.coerceIn(0, result.events.size), ownView, s.status)
    }

    /** The other side's replies, one line at a time over the board as it was after the player's move. */
    private suspend fun narrate(played: Played) {
        val replies = played.events.drop(played.own).filterNot {
            it is GameEvent.RoundStarted || it is GameEvent.TurnStarted
        }
        if (replies.isEmpty()) return
        val lines = replies.map { it.text }
        interim = played.ownView to played.events.take(played.own).map { it.text }
        phase = PuzzlePhase.REPLYING
        replying = ReplyingUi(replyTitle(replies), emptyList())
        publish()
        val per = minOf(timing.replyLineMs, timing.replyMaxMs / lines.size)
        lines.indices.forEach { i ->
            delay(per)
            replying = ReplyingUi(replyTitle(replies), lines.take(i + 1))
            publish()
        }
        delay(timing.replyHoldMs)
        interim = null
        replying = null
    }

    /** "Brogan answers" when an enemy hero acted, else "What happens". */
    private fun replyTitle(replies: List<GameEvent>): String {
        val enemy = replies.firstNotNullOfOrNull { event ->
            when (event) {
                is GameEvent.ActionChosen -> event.heroId
                is GameEvent.Defended -> event.heroId
                is GameEvent.Moved -> event.unitId
                is GameEvent.Attacked -> event.attackerId
                else -> null
            }?.takeIf { id -> id in teams && teams[id] != team }
        }
        return enemy?.let { "${heroes.hero(it).name} answers" } ?: "What happens"
    }

    private val team: Team? get() = mine.firstNotNullOfOrNull(teams::get)

    private suspend fun solved() {
        phase = PuzzlePhase.SOLVED
        miss = null
        nudgeVisible = false
        hint = null
        val now = clock.now()
        record { it.copy(solved = true, solvedAt = it.solvedAt ?: now) }
        announce("Solved. ${info?.goal?.text.orEmpty()}")
    }

    private suspend fun missed(events: List<GameEvent>) {
        val puzzle = info ?: return
        phase = PuzzlePhase.MISSED
        miss = PuzzleText.missReason(
            goal = puzzle.goal,
            events = events,
            team = team,
            teamOf = teams::get,
            mine = mine,
            name = { heroes.hero(it).name },
            card = { cards[it]?.name },
        )
        announce(miss.orEmpty())
        // Struggling: with nudges on, the next one comes with the miss.
        if (nudgesOn && nudgeLevel < puzzle.nudges.size) showNudge(publish = false)
    }

    private fun tryAgain() {
        val s = session ?: return
        if (busy || phase != PuzzlePhase.MISSED) return
        busy = true
        viewModelScope.launch {
            withContext(engine) { s.undo() }
            phase = PuzzlePhase.SOLVING
            miss = null
            moved = false
            selected = null
            announce("Back to before that move.")
            refreshHint()
            busy = false
            publish()
            scheduleIdle()
        }
    }

    private fun restart() {
        val s = session ?: return
        if (busy) return
        busy = true
        idle?.cancel()
        viewModelScope.launch {
            val fresh = withContext(engine) { runCatching { s.restart() }.getOrNull() }
            if (fresh != null) session = fresh
            phase = PuzzlePhase.SOLVING
            miss = null
            moved = false
            selected = null
            hint = null
            nudgeVisible = false
            replay = null
            announce("Back to the starting position.")
            busy = false
            publish()
            scheduleIdle()
        }
    }

    // ---- Nudges ----------------------------------------------------------------------------

    /** The next nudge (or the last one again), gentle to strong; the last one points at the move. */
    private suspend fun showNudge(publish: Boolean = true) {
        val puzzle = info ?: return
        if (puzzle.nudges.isEmpty() || replay != null) return
        idle?.cancel()
        if (nudgeLevel < puzzle.nudges.size) nudgeLevel++
        nudgeVisible = true
        val level = nudgeLevel
        if (level > stored.nudgesUsed) record { it.copy(nudgesUsed = maxOf(it.nudgesUsed, level)) }
        refreshHint()
        announce("Nudge: ${puzzle.nudges[level - 1]}")
        if (publish) publish()
    }

    /** The last nudge's move on the board: the solver's choice for the question up now. */
    private suspend fun refreshHint() {
        val puzzle = info ?: return
        val s = session ?: return
        hint = if (nudgeVisible && nudgeLevel >= puzzle.nudges.size && phase == PuzzlePhase.SOLVING) {
            withContext(engine) {
                val job = currentCoroutineContext().job
                runCatching { s.bestChoice { !job.isActive } }.getOrNull()
            }
        } else {
            null
        }
    }

    private fun dismissNudge() {
        nudgeVisible = false
        hint = null
        refresh()
        scheduleIdle()
    }

    /** With nudges on, a nudge comes by itself after a while without progress on the current question. */
    private fun scheduleIdle() {
        idle?.cancel()
        val puzzle = info ?: return
        if (!nudgesOn || !waitingForNudge || nudgeLevel >= puzzle.nudges.size) return
        idle = viewModelScope.launch {
            delay(timing.idleNudgeMs)
            if (isActive && !busy && waitingForNudge) showNudge()
        }
    }

    /** The player is on a question with no nudge showing (and not watching the replay). */
    private val waitingForNudge: Boolean
        get() = phase == PuzzlePhase.SOLVING && replay == null && !nudgeVisible

    // ---- The solution, replayed ------------------------------------------------------------

    private fun showSolution() {
        val book = puzzles ?: return
        if (busy) return
        busy = true
        idle?.cancel()
        viewModelScope.launch {
            publish()
            val steps = withContext(engine) { runCatching { solution(book) }.getOrDefault(emptyList()) }
            if (steps.isNotEmpty()) {
                if (phase != PuzzlePhase.SOLVED) record { it.copy(solutionShown = true) }
                replay = Replay(steps, afterSolve = phase == PuzzlePhase.SOLVED)
                announce("The solution, step by step.")
            }
            busy = false
            publish()
        }
    }

    /** On [engine]: a fresh session played with the solver's choice at every player question. */
    private fun solution(book: PuzzleBook): List<ReplayStep> {
        val s = book.start(puzzleId)
        val steps = ArrayList<ReplayStep>()
        var seen = s.game.events.size
        fun since(): List<String> = s.game.events.drop(seen).also { seen = s.game.events.size }
            .filterNot { it is GameEvent.RoundStarted || it is GameEvent.TurnStarted }
            .map { it.text }
        var accepted = true
        while (accepted && steps.size < MAX_STEPS) {
            val (decision, choice) = bestMove(s) ?: break
            steps += ReplayStep(openView(s.game), decision, choice, since())
            accepted = s.answer(decision.id, choice) is AnswerResult.Accepted
        }
        steps += ReplayStep(openView(s.game), null, null, since())
        return steps
    }

    /** The player's question and the solver's answer to it, while the puzzle is still being played. */
    private fun bestMove(s: PuzzleSession): Pair<Decision, Choice>? {
        if (s.status != PuzzleStatus.PLAYING) return null
        val decision = s.game.pending ?: return null
        return s.bestChoice()?.let { decision to it }
    }

    private fun step(by: Int) {
        val r = replay ?: return
        r.index = (r.index + by).coerceIn(0, r.steps.lastIndex)
        refresh()
    }

    // ---- State ---------------------------------------------------------------------------

    private suspend fun record(transform: (PuzzleProgress) -> PuzzleProgress) {
        stored = transform(stored)
        withContext(NonCancellable) { progressRepository.update(puzzleId, transform) }
    }

    private fun announce(text: String) {
        if (text.isBlank()) return
        announcement = PlayAnnouncement(text, ++announcementId)
    }

    private fun refresh() {
        viewModelScope.launch { publish() }
    }

    /** Every hero as it sees itself: a puzzle's hands (and cards already played) are open. */
    private fun openView(game: GameSession): GameView {
        val first = mine.firstOrNull() ?: return game.view(null)
        val base = game.view(first)
        val own = game.setup.seats.associate { seat ->
            seat.heroId to game.view(seat.heroId).heroes.firstOrNull { it.heroId == seat.heroId }
        }
        return base.copy(heroes = base.heroes.map { hero -> own[hero.heroId] ?: hero })
    }

    private class Snapshot(val pending: Decision?, val view: GameView, val events: List<GameEvent>)

    /** Rebuilds what the screen shows from the session (on [engine]) and the screen's own state. */
    private suspend fun publish() {
        val s = session ?: return
        val puzzle = info ?: return
        val board = map ?: return
        val r = replay
        val snapshot = withContext(engine) { Snapshot(s.game.pending, openView(s.game), s.game.events.toList()) }
        val live = interim
        val step = r?.steps?.getOrNull(r.index)
        val table = if (step != null) replayTable(board, step) else liveTable(board, snapshot)
        val replayUi = if (r != null && step != null) replayUi(puzzle, r, step, table) else null
        val nudge = nudgeUi(puzzle, table)
        state.value = PuzzleUiState.Ready(
            PuzzleUi(
                info = puzzle,
                total = total,
                table = table,
                phase = phase,
                playing = puzzle.heroes.filter { it in mine }.ifEmpty { mine.toList() }.map { heroes.hero(it).name },
                miss = miss,
                replying = replying,
                nudge = nudge,
                nudgeLevel = nudgeLevel,
                nudgeCount = puzzle.nudges.size,
                nudgesOn = nudgesOn,
                introOpen = introOpen,
                attempts = stored.attempts,
                nudgesUsed = stored.nudgesUsed,
                replay = replayUi,
                recent = (live?.second ?: snapshot.events.map { it.text }).takeLast(RECENT),
                log = logLines(snapshot.events),
                next = next,
                busy = busy,
                announcement = announcement,
            ),
        )
    }

    private fun replayTable(board: BoardMap, step: ReplayStep) = TableUi(
        map = board,
        view = step.view,
        heroes = heroes,
        cards = cards,
        mine = mine,
        decision = step.decision,
        asking = false,
        selected = step.choice,
        bottomTeam = bottomTeam,
    )

    /** The live puzzle; while replies are narrated, the board just after the player's move and no question. */
    private fun liveTable(board: BoardMap, snapshot: Snapshot): TableUi {
        val live = interim
        val decision = snapshot.pending?.takeIf { live == null && phase == PuzzlePhase.SOLVING && it.seat in mine }
        val options = decision?.options.orEmpty()
        return TableUi(
            map = board,
            view = live?.first ?: snapshot.view,
            heroes = heroes,
            cards = cards,
            mine = mine,
            decision = decision,
            asking = decision != null && !busy,
            selected = selected?.takeIf { it in options },
            bottomTeam = bottomTeam,
            hint = hint?.takeIf { it in options },
        )
    }

    private fun replayUi(puzzle: PuzzleInfo, r: Replay, step: ReplayStep, table: TableUi): ReplayUi {
        val last = r.index == r.steps.lastIndex
        val solved = step.decision == null && r.steps.size > 1
        val title = when {
            step.decision != null && step.choice != null ->
                "${heroes.hero(step.decision.seat).name} · ${step.decision.prompt}: ${table.label(step.choice)}"

            solved -> "Goal met: ${puzzle.goal.text}"

            else -> "The solution can't be shown for this position."
        }
        return ReplayUi(r.index, r.steps.size, title, step.lines, last, solved, r.afterSolve)
    }

    private fun nudgeUi(puzzle: PuzzleInfo, table: TableUi): NudgeUi? {
        if (!nudgeVisible || nudgeLevel == 0) return null
        return NudgeUi(
            text = puzzle.nudges[nudgeLevel - 1],
            level = nudgeLevel,
            total = puzzle.nudges.size,
            hint = table.hint?.let(table::label),
        )
    }

    override fun onCleared() {
        idle?.cancel()
    }

    internal companion object {
        const val RECENT = 4

        /** A solution longer than this is not replayed (no puzzle comes close). */
        const val MAX_STEPS = 60
    }
}
