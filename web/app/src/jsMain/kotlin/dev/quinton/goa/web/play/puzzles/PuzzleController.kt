package dev.quinton.goa.web.play.puzzles

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.data.PuzzleProgressRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.puzzle.PuzzleProgress
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameView
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.play.game.PlayAnnouncement
import dev.quinton.goa.feature.board.play.puzzles.MAX_SOLUTION_STEPS
import dev.quinton.goa.feature.board.play.puzzles.NoPuzzle
import dev.quinton.goa.feature.board.play.puzzles.NudgeUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleAction
import dev.quinton.goa.feature.board.play.puzzles.PuzzleMove
import dev.quinton.goa.feature.board.play.puzzles.PuzzlePhase
import dev.quinton.goa.feature.board.play.puzzles.PuzzleText
import dev.quinton.goa.feature.board.play.puzzles.PuzzleTiming
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUi
import dev.quinton.goa.feature.board.play.puzzles.PuzzleUiState
import dev.quinton.goa.feature.board.play.puzzles.ReplayStep
import dev.quinton.goa.feature.board.play.puzzles.ReplayUi
import dev.quinton.goa.feature.board.play.puzzles.ReplyingUi
import dev.quinton.goa.feature.board.play.puzzles.replayTitle
import dev.quinton.goa.feature.board.play.puzzles.replies
import dev.quinton.goa.feature.board.play.puzzles.replyTitle
import dev.quinton.goa.feature.board.play.review.ReviewKeeper
import dev.quinton.goa.feature.board.play.review.ReviewUi
import dev.quinton.goa.feature.board.play.review.reviewUi
import dev.quinton.goa.feature.board.play.table.TableTap
import dev.quinton.goa.feature.board.play.table.TableUi
import dev.quinton.goa.feature.board.play.table.choiceFor
import dev.quinton.goa.feature.board.play.table.logLines
import dev.quinton.goa.feature.board.play.table.turnLog
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.engine.PuzzleSnapshot
import dev.quinton.goa.web.engine.Turn
import dev.quinton.goa.web.play.data.EngineClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One puzzle on the web (PRODUCT §10), as the phone's PuzzleViewModel plays it: the player answers
 * their heroes' questions on the game table; the puzzle runtime (in the engine worker) answers every
 * other seat at once, and the screen narrates those replies line by line before the board catches
 * up. A move that loses the forced line is MISSED (a gentle reason from what followed; Try again from
 * before it, Restart). Nudges come after a miss or [PuzzleTiming.idleNudgeMs] without
 * progress (with nudges on); the last one outlines the solver's move. Solved, or after Show solution:
 * the explanation and the solution replayed step by step on a fresh copy. Progress is stored here.
 *
 * The session lives in the engine worker; when the worker was started afresh since (a cancelled
 * search elsewhere), the puzzle is set up again and the player's moves replayed (the engine is
 * deterministic, so it is the same position).
 */
class PuzzleController(
    private val puzzleId: String,
    private val scope: CoroutineScope,
    private val engine: EngineClient,
    private val catalog: suspend () -> Catalog,
    private val maps: suspend () -> List<BoardMap>,
    private val settings: SettingsRepository,
    private val progressRepository: PuzzleProgressRepository,
    private val clock: EditClock,
    private val timing: PuzzleTiming = PuzzleTiming(),
) {
    var state: PuzzleUiState by mutableStateOf(PuzzleUiState.Loading)
        private set

    private var snapshot: PuzzleSnapshot? = null
    private var generation = -1
    private var info: PuzzleInfo? = null
    private var total = 0
    private var next: String? = null
    private var map: BoardMap? = null
    private var heroes: Map<String, BoardHero> = emptyMap()
    private var cards: Map<String, Card> = emptyMap()
    private var mine: Set<String> = emptySet()
    private var teams: Map<String, Team> = emptyMap()
    private var bottomTeam: Team = Team.RED

    /** The player's accepted answers since the start: replayed into a fresh worker. */
    private val moves = ArrayList<Choice>()

    private var phase = PuzzlePhase.SOLVING
    private var miss: String? = null
    private var replying: ReplyingUi? = null
    private var interim: Pair<GameView, List<String>>? = null
    private var selected: Choice? = null
    private var hint: Choice? = null
    private var nudgeLevel = 0
    private var nudgeVisible = false
    private var nudgesOn = true

    /** Nudges switched here while the settings store can't be written (it then lives for this visit). */
    private var nudgesOverride: Boolean? = null
    private var introOpen = true
    private var moved = false
    private var stored = PuzzleProgress()
    private var replay: Replay? = null
    private var busy = false
    private var announcementId = 0L
    private var announcement: PlayAnnouncement? = null
    private var idle: Job? = null
    private val review = ReviewKeeper()
    private var reviewData: EngineResponse.Review? = null
    private var reviewAsked: Turn? = null

    private class Replay(val steps: List<ReplayStep>, val afterSolve: Boolean, var index: Int = 0)

    fun start() {
        scope.launch { load() }
    }

    @Suppress("TooGenericExceptionCaught") // the worker's failures arrive as plain errors
    private suspend fun load() {
        val list = try {
            (engine.call(EngineRequest.PuzzleList) as? EngineResponse.Puzzles)?.puzzles
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return unavailable(NoPuzzle.BROKEN)
        val index = list.indexOfFirst { it.id == puzzleId }
        if (index < 0) return unavailable(NoPuzzle.MISSING)
        val started = startSession() ?: return unavailable(NoPuzzle.BROKEN)
        val catalog = runCatching { catalog() }.getOrDefault(Catalog.EMPTY)
        val maps = runCatching { maps() }.getOrDefault(emptyList())
        map = maps.firstOrNull { it.id == started.setup.mapId } ?: return unavailable(NoPuzzle.BROKEN)
        info = list[index]
        total = list.size
        next = list.getOrNull(index + 1)?.id
        heroes = catalog.boardHeroes()
        cards = catalog.heroes.flatMap { it.allCards }.associateBy { it.id }
        mine = started.playerSeats
        teams = started.setup.seats.associate { it.heroId to it.team }
        bottomTeam = mine.firstNotNullOfOrNull(teams::get) ?: Team.RED
        nudgesOn = runCatching { settings.settings.first().puzzleNudges }.getOrDefault(true)
        publish()
        scheduleIdle()
        scope.launch {
            runCatching {
                settings.settings.catch { }.collect { s ->
                    if (nudgesOverride == null && s.puzzleNudges != nudgesOn) {
                        nudgesOn = s.puzzleNudges
                        scheduleIdle()
                        publish()
                    }
                }
            }
        }
        scope.launch {
            progressRepository.progress.catch { emit(emptyMap()) }.collect { all ->
                stored = all[puzzleId] ?: PuzzleProgress()
                publish()
            }
        }
    }

    /** Sets the puzzle up in the worker (and replays the player's moves when the worker is new). */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun startSession(): PuzzleSnapshot? = try {
        val response = engine.call(EngineRequest.PuzzleStart(puzzleId)) as? EngineResponse.Puzzle
        var current = response?.puzzle
        for (choice in moves) {
            val pending = current?.pending ?: break
            current =
                (engine.call(EngineRequest.PuzzleAnswer(pending.id, choice)) as? EngineResponse.PuzzlePlayed)?.puzzle
        }
        generation = engine.generation
        snapshot = current
        current
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    /** The worker still has this puzzle (else it is set up again). */
    private suspend fun ensureSession(): Boolean = generation == engine.generation || startSession() != null

    private fun unavailable(why: NoPuzzle) {
        state = PuzzleUiState.Unavailable(why)
    }

    // ---- Actions ---------------------------------------------------------------------------

    fun onAction(action: PuzzleAction) {
        when (action) {
            is PuzzleAction.Tap -> tap(action.tap)

            is PuzzleAction.Select -> select(action.choice)

            is PuzzleAction.Answer -> answer(action.choice)

            PuzzleAction.TryAgain -> tryAgain()

            PuzzleAction.Restart -> restart()

            PuzzleAction.Nudge -> scope.launch { showNudge() }

            PuzzleAction.DismissNudge -> dismissNudge()

            is PuzzleAction.SetNudges -> setNudges(action.on)

            is PuzzleAction.SetIntroOpen -> {
                introOpen = action.open
                publish()
            }

            PuzzleAction.ShowSolution -> showSolution()

            PuzzleAction.ReplayNext -> step(+1)

            PuzzleAction.ReplayBack -> step(-1)

            PuzzleAction.CloseReplay -> {
                replay = null
                scheduleIdle()
                publish()
            }

            PuzzleAction.Flip -> {
                bottomTeam = bottomTeam.opponent
                publish()
            }

            is PuzzleAction.Review -> {
                val turns = (state as? PuzzleUiState.Ready)?.puzzle?.review?.turns
                if (review.handle(action.action, turns)) refreshReview()
            }
        }
    }

    /** A tap on the board: the option it stands for is selected. False when it was no option. */
    fun tap(tap: TableTap): Boolean {
        val puzzle = (state as? PuzzleUiState.Ready)?.puzzle ?: return false
        val choice = puzzle.table.choiceFor(tap) ?: return false
        select(choice)
        return true
    }

    private fun select(choice: Choice) {
        val puzzle = (state as? PuzzleUiState.Ready)?.puzzle ?: return
        if (!puzzle.table.asking || choice !in puzzle.table.decision?.options.orEmpty()) return
        selected = choice
        publish()
    }

    private fun setNudges(on: Boolean) {
        nudgesOn = on
        scope.launch {
            val saved = runCatching { settings.update { it.copy(puzzleNudges = on) } }.isSuccess
            nudgesOverride = if (saved) null else on
        }
        scheduleIdle()
        publish()
    }

    private fun answer(choice: Choice) {
        if (busy || phase != PuzzlePhase.SOLVING || replay != null) return
        val decision = snapshot?.pending ?: return
        if (decision.seat !in mine || choice !in decision.options) return
        busy = true
        idle?.cancel()
        publish()
        scope.launch {
            val played = runCatching {
                if (!ensureSession()) return@runCatching null
                engine.call(EngineRequest.PuzzleAnswer(decision.id, choice)) as? EngineResponse.PuzzlePlayed
            }.getOrNull()
            val move = played?.move
            if (played == null || move == null) {
                busy = false
                publish()
                return@launch
            }
            moves += choice
            selected = null
            hint = null
            nudgeVisible = false
            introOpen = false
            val now = clock.now()
            if (!moved) {
                moved = true
                record { it.copy(attempts = it.attempts + 1, lastPlayedAt = now) }
            } else {
                record { it.copy(lastPlayedAt = now) }
            }
            narrate(move)
            snapshot = played.puzzle
            reviewData = null
            when (move.status) {
                PuzzleStatus.SOLVED -> solved()

                PuzzleStatus.MISSED -> missed(move.events)

                PuzzleStatus.PLAYING -> {
                    phase = PuzzlePhase.SOLVING
                    announce(move.events.joinToString(" ") { it.text })
                }
            }
            busy = false
            publish()
            scheduleIdle()
            if (review.open) refreshReview()
        }
    }

    /** The other side's replies, one line at a time over the board as it was after the player's move. */
    private suspend fun narrate(move: PuzzleMove) {
        val replies = replies(move)
        if (replies.isEmpty()) return
        val lines = replies.map { it.text }
        val title = replyTitle(replies, teams, team) { heroes.hero(it).name }
        interim = move.ownView to move.events.take(move.own).map { it.text }
        phase = PuzzlePhase.REPLYING
        replying = ReplyingUi(title, emptyList())
        publish()
        val per = minOf(timing.replyLineMs, timing.replyMaxMs / lines.size)
        lines.indices.forEach { i ->
            delay(per)
            replying = ReplyingUi(title, lines.take(i + 1))
            publish()
        }
        delay(timing.replyHoldMs)
        interim = null
        replying = null
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
        if (busy || phase != PuzzlePhase.MISSED) return
        busy = true
        publish()
        scope.launch {
            val back = runCatching {
                if (!ensureSession()) return@runCatching null
                engine.call(EngineRequest.PuzzleUndo) as? EngineResponse.Puzzle
            }.getOrNull()
            if (back != null) {
                snapshot = back.puzzle
                moves.removeLastOrNull()
            }
            review.reset()
            reviewData = null
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
        if (busy) return
        busy = true
        idle?.cancel()
        publish()
        scope.launch {
            val fresh = runCatching {
                if (!ensureSession()) return@runCatching null
                engine.call(EngineRequest.PuzzleRestart) as? EngineResponse.Puzzle
            }.getOrNull()
            if (fresh != null) {
                snapshot = fresh.puzzle
                moves.clear()
            }
            review.reset()
            reviewData = null
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
        hint = if (nudgeVisible && nudgeLevel >= puzzle.nudges.size && phase == PuzzlePhase.SOLVING) {
            runCatching {
                if (!ensureSession()) return@runCatching null
                (engine.call(EngineRequest.PuzzleBest) as? EngineResponse.Best)?.choice
            }.getOrNull()
        } else {
            null
        }
    }

    private fun dismissNudge() {
        nudgeVisible = false
        hint = null
        publish()
        scheduleIdle()
    }

    /** With nudges on, a nudge comes by itself after a while without progress on the current question. */
    private fun scheduleIdle() {
        idle?.cancel()
        val puzzle = info ?: return
        if (!nudgesOn || !waitingForNudge || nudgeLevel >= puzzle.nudges.size) return
        idle = scope.launch {
            delay(timing.idleNudgeMs)
            if (isActive && !busy && waitingForNudge) showNudge()
        }
    }

    private val waitingForNudge: Boolean
        get() = phase == PuzzlePhase.SOLVING && replay == null && !nudgeVisible

    // ---- The solution, replayed ------------------------------------------------------------

    private fun showSolution() {
        if (busy) return
        busy = true
        idle?.cancel()
        publish()
        scope.launch {
            val steps = runCatching {
                (engine.call(EngineRequest.PuzzleSolution(puzzleId)) as? EngineResponse.Solution)?.steps
            }.getOrNull().orEmpty().take(MAX_SOLUTION_STEPS + 1)
            if (steps.isNotEmpty()) {
                if (phase != PuzzlePhase.SOLVED) record { it.copy(solutionShown = true) }
                replay = Replay(steps, afterSolve = phase == PuzzlePhase.SOLVED)
                announce("The solution, step by step.")
            }
            busy = false
            publish()
        }
    }

    private fun step(by: Int) {
        val r = replay ?: return
        r.index = (r.index + by).coerceIn(0, r.steps.lastIndex)
        publish()
    }

    // ---- The review (Game state) -----------------------------------------------------------

    private fun refreshReview() {
        if (!review.open) {
            reviewData = null
            publish()
            return
        }
        val rewind = review.rewindAt?.let { Turn(it.first, it.second) }
        scope.launch {
            val data = runCatching {
                if (!ensureSession()) return@runCatching null
                engine.call(EngineRequest.PuzzleReview(rewind)) as? EngineResponse.Review
            }.getOrNull()
            reviewData = data
            reviewAsked = rewind
            publish()
        }
    }

    // ---- State -----------------------------------------------------------------------------

    private suspend fun record(transform: (PuzzleProgress) -> PuzzleProgress) {
        stored = transform(stored)
        runCatching { progressRepository.update(puzzleId, transform) }
    }

    private fun announce(text: String) {
        if (text.isBlank()) return
        announcement = PlayAnnouncement(text, ++announcementId)
    }

    /** Rebuilds what the screen shows from the latest snapshot and the screen's own state. */
    private fun publish() {
        val s = snapshot ?: return
        val puzzle = info ?: return
        val board = map ?: return
        val r = replay
        val step = r?.steps?.getOrNull(r.index)
        val table = if (step != null) replayTable(board, step) else liveTable(board, s)
        val replayUi = if (r != null && step != null) {
            val title = replayTitle(puzzle, step, r.steps.size, { heroes.hero(it).name }, table::label)
            val last = r.index == r.steps.lastIndex
            ReplayUi(
                r.index,
                r.steps.size,
                title,
                step.lines,
                last,
                step.decision == null && r.steps.size > 1,
                r.afterSolve,
            )
        } else {
            null
        }
        val live = interim
        state = PuzzleUiState.Ready(
            PuzzleUi(
                info = puzzle,
                total = total,
                table = table,
                phase = phase,
                playing = puzzle.heroes.filter { it in mine }.ifEmpty { mine.toList() }.map { heroes.hero(it).name },
                miss = miss,
                replying = replying,
                nudge = nudgeUi(puzzle, table),
                nudgeLevel = nudgeLevel,
                nudgeCount = puzzle.nudges.size,
                nudgesOn = nudgesOn,
                introOpen = introOpen,
                attempts = stored.attempts,
                nudgesUsed = stored.nudgesUsed,
                replay = replayUi,
                recent = (live?.second ?: s.events.map { it.text }).takeLast(RECENT),
                log = logLines(s.events),
                next = next,
                busy = busy,
                announcement = announcement,
                review = buildReview(s),
            ),
        )
    }

    private fun buildReview(s: PuzzleSnapshot): ReviewUi? {
        val data = reviewData ?: return null
        if (!review.open) return null
        return reviewUi(
            heroIds = s.setup.seats.map { it.heroId }.toSet(),
            reviews = data.reviews,
            pushes = data.pushes,
            rewind = review.rewindAt,
        ) { round, turn -> data.rewind?.takeIf { reviewAsked == Turn(round, turn) } }
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
    private fun liveTable(board: BoardMap, s: PuzzleSnapshot): TableUi {
        val live = interim
        val decision = s.pending?.takeIf { live == null && phase == PuzzlePhase.SOLVING && it.seat in mine }
        val options = decision?.options.orEmpty()
        return TableUi(
            map = board,
            view = live?.first ?: s.view,
            heroes = heroes,
            cards = cards,
            mine = mine,
            decision = decision,
            asking = decision != null && !busy,
            selected = selected?.takeIf { it in options },
            bottomTeam = bottomTeam,
            hint = hint?.takeIf { it in options },
            // While the replies are narrated the board is the one after the player's move: no later events.
            turnLog = if (live == null) turnLog(s.events) else emptyList(),
        )
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

    fun dispose() {
        idle?.cancel()
    }

    private companion object {
        const val RECENT = 4
    }
}
