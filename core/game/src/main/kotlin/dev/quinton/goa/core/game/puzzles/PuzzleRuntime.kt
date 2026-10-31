package dev.quinton.goa.core.game.puzzles

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.GameEvent
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.PuzzleBook
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.PuzzleSession
import dev.quinton.goa.core.game.PuzzleStatus
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.StaticPolicy
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.book.Puzzles

/**
 * The bundled puzzles that can be played with [engine]: a puzzle is listed once every hero it seats
 * is playable. In the order of [Puzzles.all] (the book's learning progression, hard to hardest);
 * [PuzzleInfo.number] is the place in that order.
 * Each puzzle keeps one solver (and its transposition table) for all its sessions.
 */
class EnginePuzzleBook(
    private val engine: GameEngine,
    all: List<Puzzle> = Puzzles.all + Puzzles.fullRounds,
    /** Use the stored answer keys (tests switch them off to check the solver alone). */
    private val useKeys: Boolean = true,
) : PuzzleBook {
    private val available: List<Puzzle> =
        all.filter { p -> p.info.heroes.all { it in engine.playableHeroes } }

    /** Numbered within each part of the book, so the full rounds start again at 1. */
    override val puzzles: List<PuzzleInfo> = available.filter { it.known == null }.mapIndexed { i, p ->
        p.info.copy(number = i + 1)
    } + available.filter { it.known != null }.mapIndexed { i, p -> p.info.copy(number = i + 1) }

    private val prepared = HashMap<String, PreparedPuzzle>()

    /** The solver-ready puzzle (built once per book). */
    fun prepared(puzzleId: String): PreparedPuzzle {
        val puzzle = available.firstOrNull { it.info.id == puzzleId }
        requireNotNull(puzzle) { "No playable puzzle $puzzleId" }
        return prepared.getOrPut(puzzleId) {
            PreparedPuzzle(puzzle, engine, puzzles.first { it.id == puzzleId }, useKeys)
        }
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
    /** A round has too many positions for a key of every winning option: its key is the solution line. */
    val fullRound: Boolean = puzzle.known != null

    /** The starting game; sessions fork it. Never answered. */
    val root: EngineSession = puzzle.build(engine)

    val solver: PuzzleSolver

    val rootNode: Node

    /** The stored answer key, when it was made for this engine and this position. */
    val key: AnswerKey?

    init {
        val start = root.checkpointState
        root.haltWhen = GoalTracker.haltAt(puzzle.deadline, start.round, start.turn, beforeRetrieve = fullRound)
        val teams = puzzle.playerSeats.map { seat ->
            requireNotNull(root.state.hero(seat)) { "Player seat $seat is not in the position" }.team
        }.toSet()
        require(teams.size == 1) { "The player's seats must be on one team" }
        solver = PuzzleSolver(
            GoalTracker(info.goal, teams.single(), puzzle.playerSeats),
            puzzle.nodeBudget,
            if (fullRound) ROUND_MEMO else PuzzleSolver.DEFAULT_MAX_MEMO,
            puzzle.known,
        )
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

        /** A full round's transposition table on a phone (these searches never fill much of it). */
        const val ROUND_MEMO: Int = 100_000
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

    /** Full round: what is left of [PreparedPuzzle.RUNTIME_BUDGET] for the answer being worked on. */
    private var answerBudget = PreparedPuzzle.RUNTIME_BUDGET
    private val static by lazy(LazyThreadSafetyMode.NONE) {
        StaticPolicy(Evaluator(node.session.engineData, Features.KNOBS))
    }

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
    val onKey: Boolean get() = keyEntry() != null

    override fun answer(decisionId: Long, choice: Choice): AnswerResult {
        val d = node.session.pending ?: return AnswerResult.Rejected("The puzzle is over")
        if (solver.sideOf(d) != Side.PLAYER) return AnswerResult.Rejected("Not the player's decision")
        val index = d.options.indexOf(choice)
        if (decisionId != d.id || index < 0) return node.session.answer(decisionId, choice)
        val wins = keyEntry() as? AnswerKey.Wins
        val events = ArrayList<GameEvent>()
        val r = apply(decisionId, index, byPlayer = true, events)
        if (r !is AnswerResult.Accepted) return r
        answerBudget = PreparedPuzzle.RUNTIME_BUDGET
        // A full round's key is one line: off it nothing is known yet, a search or the end of the round decides.
        refresh(wins?.let { w -> (index in w.options).takeIf { it || !prepared.fullRound } })
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
        (keyEntry() as? AnswerKey.Wins)?.let { return d.options[it.options.first()] }
        val s = if (prepared.fullRound) budgeted() else solver.solve(node, cancelled)
        return if (s?.value == Value.WIN) d.options[s.best] else null
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
                prepared.fullRound -> if (budgeted()?.value == Value.LOSS) PuzzleStatus.MISSED else PuzzleStatus.PLAYING
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

                else -> (keyEntry() as? AnswerKey.Reply)?.option
                    ?: replyOffKey()
            }
            val r = apply(d.id, reply, byPlayer = false, events)
            check(r is AnswerResult.Accepted) { "The solver's reply was rejected: $r" }
            if (node.progress.state != GoalState.OPEN) refresh(null)
        }
    }

    /** A solve within what is left for this answer, or null when nothing is left. */
    private fun budgeted(): Solved? {
        if (answerBudget <= 0) return null
        val before = solver.nodesExpanded
        val s = solver.solve(node, budget = answerBudget)
        answerBudget -= solver.nodesExpanded - before
        return s
    }

    /** A full round off its key: a known play, else a budgeted solve, else the AI's static policy. */
    private fun roundReply(): Int {
        val choices = solver.choices(node)
        choices.singleOrNull()?.let { return it }
        val s = budgeted()
        if (s != null && s.value != Value.UNKNOWN) return s.best
        val d = checkNotNull(node.session.pending)
        val scores = static.scores(node.session.state, d)
        return choices.maxByOrNull { scores[it] } ?: choices.first()
    }

    /** Off the key: a full solve while the puzzle can still be won, a budgeted one after a miss. */
    private fun replyOffKey(): Int {
        if (prepared.fullRound) return roundReply()
        val budget = if (current == PuzzleStatus.MISSED) PreparedPuzzle.RUNTIME_BUDGET else solver.nodeBudget
        return solver.solve(node, budget = budget).best
    }

    /** The key's entry for the current position, when it fits the pending decision. */
    private fun keyEntry(): AnswerKey.Entry? {
        val options = node.session.pending?.options?.size ?: return null
        return when (val e = key?.entries?.get(AnswerKey.pathOf(path))) {
            is AnswerKey.Wins -> e.takeIf { w -> w.options.all { it < options } }
            is AnswerKey.Reply -> e.takeIf { it.option < options }
            null -> null
        }
    }
}
