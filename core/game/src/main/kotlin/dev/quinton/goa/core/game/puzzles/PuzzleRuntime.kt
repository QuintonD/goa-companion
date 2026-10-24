package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleSession
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.book.Puzzles

/**
 * The bundled puzzles that can be played with [engine]: a puzzle is listed once every hero it seats
 * is playable. Ordered by difficulty, then id; [PuzzleInfo.number] is the place in that order.
 * Each puzzle keeps one solver (and its transposition table) for all its sessions.
 */
class EnginePuzzleBook(
    private val engine: GameEngine,
    all: List<Puzzle> = Puzzles.all,
    /** Use the stored answer keys (tests switch them off to check the solver alone). */
    private val useKeys: Boolean = true,
) : PuzzleBook {
    private val available: List<Puzzle> =
        all.filter { p -> p.info.heroes.all { it in engine.playableHeroes } }
            .sortedWith(compareBy({ it.info.difficulty }, { it.info.id }))

    override val puzzles: List<PuzzleInfo> = available.mapIndexed { i, p -> p.info.copy(number = i + 1) }

    private val prepared = HashMap<String, PreparedPuzzle>()

    /** The solver-ready puzzle (built once per book). */
    fun prepared(puzzleId: String): PreparedPuzzle {
        val i = available.indexOfFirst { it.info.id == puzzleId }
        require(i >= 0) { "No playable puzzle $puzzleId" }
        return prepared.getOrPut(puzzleId) { PreparedPuzzle(available[i], engine, puzzles[i], useKeys) }
    }

    override fun start(puzzleId: String): PuzzleSession = prepared(puzzleId).newSession()
}

/** A puzzle's starting position halted at its deadline, with its solver and answer key. */
class PreparedPuzzle(
    val puzzle: Puzzle,
    engine: GameEngine,
    val info: PuzzleInfo = puzzle.info,
    useKey: Boolean = true,
) {
    /** The starting game; sessions fork it. Never answered. */
    val root: EngineSession = puzzle.build(engine)

    val solver: PuzzleSolver

    val rootNode: Node

    /** The stored answer key, when it was made for this engine and this position. */
    val key: AnswerKey?

    init {
        val start = root.checkpointState
        root.haltWhen = GoalTracker.haltAt(puzzle.deadline, start.round, start.turn)
        val teams = puzzle.playerSeats.map { seat ->
            requireNotNull(root.state.hero(seat)) { "Player seat $seat is not in the position" }.team
        }.toSet()
        require(teams.size == 1) { "The player's seats must be on one team" }
        solver = PuzzleSolver(GoalTracker(info.goal, teams.single(), puzzle.playerSeats), puzzle.nodeBudget)
        rootNode = solver.root(root)
        key = if (useKey) {
            AnswerKey.load(info.id)?.takeIf {
                it.engineVersion == GameEngine.ENGINE_VERSION && it.fingerprint == AnswerKey.fingerprint(rootNode)
            }
        } else {
            null
        }
    }

    fun newSession(): EnginePuzzleSession = EnginePuzzleSession(this)

    companion object {
        /** Solver expansions for a check off the answer key (≈ 0.1 s on a desktop, well under 1 s on a phone). */
        const val RUNTIME_BUDGET: Long = 1_500
    }
}

/**
 * A puzzle being played (contract: [PuzzleSession]). Every decision that is not the player's is
 * answered at once with the solver's reply; after each player answer the goal must still be forced
 * (any winning line counts), else the status is [PuzzleStatus.MISSED]. On the answer key's lines this
 * needs no search; off it (after a miss, or without a key) the solver searches. Answer through this
 * class, never through [game] directly.
 */
class EnginePuzzleSession internal constructor(private val prepared: PreparedPuzzle) : PuzzleSession {
    private val solver = prepared.solver
    private val key = prepared.key
    private var node: Node
    private val log = ArrayList<Logged>()
    private val path = ArrayList<Int>()
    private var current: PuzzleStatus = PuzzleStatus.PLAYING

    private class Logged(val decisionId: Long, val index: Int, val byPlayer: Boolean)

    init {
        node = solver.wrap(prepared.root.forkSession(), prepared.rootNode.progress, prepared.rootNode)
        refresh(null)
        autoAnswer()
    }

    override val info: PuzzleInfo get() = prepared.info
    override val game: GameSession get() = node.session
    override val playerSeats: Set<String> get() = prepared.puzzle.playerSeats
    override val status: PuzzleStatus get() = current

    /** The goal's progress on the line played so far. */
    val progress: Progress get() = node.progress

    /** The solver node of the current position (tests, the coach). */
    val position: Node get() = node

    /** True while the current position is on the answer key. */
    val onKey: Boolean get() = key?.entries?.containsKey(AnswerKey.pathOf(path)) == true

    override fun answer(decisionId: Long, choice: Choice): AnswerResult {
        val d = node.session.pending ?: return AnswerResult.Rejected("The puzzle is over")
        if (solver.sideOf(d) != Side.PLAYER) return AnswerResult.Rejected("Not the player's decision")
        val index = d.options.indexOf(choice)
        if (decisionId != d.id || index < 0) return node.session.answer(decisionId, choice)
        val wins = key?.entries?.get(AnswerKey.pathOf(path)) as? AnswerKey.Wins
        val events = ArrayList<GameEvent>()
        val r = apply(decisionId, index, byPlayer = true, events)
        if (r !is AnswerResult.Accepted) return r
        refresh(wins?.let { index in it.options })
        autoAnswer(events)
        return AnswerResult.Accepted(events)
    }

    override fun restart(): PuzzleSession = prepared.newSession()

    override fun undo(): Boolean {
        val last = log.indexOfLast { it.byPlayer }
        if (last < 0) return false
        val keep = log.subList(0, last).toList()
        log.clear()
        path.clear()
        node = solver.wrap(prepared.root.forkSession(), prepared.rootNode.progress, prepared.rootNode)
        for (a in keep) apply(a.decisionId, a.index, a.byPlayer, null)
        current = PuzzleStatus.PLAYING
        refresh(null)
        return true
    }

    override fun bestChoice(cancelled: () -> Boolean): Choice? {
        val d = node.session.pending ?: return null
        if (solver.sideOf(d) != Side.PLAYER || current != PuzzleStatus.PLAYING) return null
        (key?.entries?.get(AnswerKey.pathOf(path)) as? AnswerKey.Wins)?.let { return d.options[it.options.first()] }
        val s = solver.solve(node, cancelled)
        return if (s.value == Value.WIN) d.options[s.best] else null
    }

    private fun apply(decisionId: Long, index: Int, byPlayer: Boolean, events: MutableList<GameEvent>?): AnswerResult {
        val s = node.session
        val choice = s.pending?.options?.getOrNull(index) ?: return AnswerResult.Rejected("No option $index")
        val r = s.answer(decisionId, choice)
        if (r is AnswerResult.Accepted) {
            log += Logged(decisionId, index, byPlayer)
            path += index
            events?.addAll(r.events)
            node = solver.wrap(s, solver.tracker.after(node.progress, r.events, s), node)
        }
        return r
    }

    /**
     * SOLVED once the goal is met; MISSED once it failed or can no longer be forced. [keyed] is the
     * answer key's verdict on the player's last answer (null = not on the key: ask the solver).
     */
    private fun refresh(keyed: Boolean?) {
        current = when (node.progress.state) {
            GoalState.MET -> PuzzleStatus.SOLVED

            GoalState.FAILED -> PuzzleStatus.MISSED

            GoalState.OPEN -> when {
                current == PuzzleStatus.MISSED || keyed == false -> PuzzleStatus.MISSED
                keyed == true || onKey -> PuzzleStatus.PLAYING
                solver.solve(node).value == Value.LOSS -> PuzzleStatus.MISSED
                else -> PuzzleStatus.PLAYING
            }
        }
    }

    private fun autoAnswer(events: MutableList<GameEvent>? = null) {
        while (true) {
            val d = node.session.pending ?: return
            if (solver.sideOf(d) == Side.PLAYER) return
            val reply = when {
                node.progress.state != GoalState.OPEN -> 0

                else -> (key?.entries?.get(AnswerKey.pathOf(path)) as? AnswerKey.Reply)?.option
                    ?: replyOffKey()
            }
            val r = apply(d.id, reply, byPlayer = false, events)
            check(r is AnswerResult.Accepted) { "The solver's reply was rejected: $r" }
            if (node.progress.state != GoalState.OPEN) refresh(null)
        }
    }

    /** Off the key: a full solve while the puzzle can still be won, a budgeted one after a miss. */
    private fun replyOffKey(): Int {
        val budget = if (current == PuzzleStatus.MISSED &&
            key != null
        ) {
            PreparedPuzzle.RUNTIME_BUDGET
        } else {
            solver.nodeBudget
        }
        return solver.solve(node, budget = budget).best
    }
}
