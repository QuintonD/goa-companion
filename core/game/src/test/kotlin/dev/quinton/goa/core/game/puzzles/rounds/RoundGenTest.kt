package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.StaticPolicy
import dev.quinton.goa.core.game.ai.learned.Learned
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.puzzles.GoalState
import dev.quinton.goa.core.game.puzzles.GoalTracker
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.game.puzzles.Value
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * Opt-in generator of full-round puzzles (ADR-052, ENCODING.md "Full rounds"):
 * `ROUND_GEN=<out dir>:<games>:<hours>`, `ROUND_DATA=<jsonl,…>`, `AI_THREADS`. Mines round starts of
 * self-play games where the game itself reached a goal late, and for each: proves it with the enemy's
 * actions known up to a turn (the most open level that can be proven), then applies the rejection rules
 * (really four turns, cards and turns that matter, `s7` fails). Writes one dossier per survivor: the
 * position, known plays, solution, certificate and every fact the text is written from. Resumable: a
 * case in `seen.txt` is not tried again.
 */
class RoundGenTest {
    @Test
    fun `mine full rounds`() {
        val spec = System.getenv("ROUND_GEN")?.split("|") ?: return
        val out = File(spec[0]).also { it.mkdirs() }
        val games = spec[1].toInt()
        val hours = spec[2].toDouble()
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 4
        val size = System.getenv("ROUND_SIZE")?.toInt() ?: 2
        val files = (System.getenv("ROUND_DATA") ?: "D:/.goa-qa/v09/a2/data/gen2/games-2.jsonl").split(",").map(::File)
        val seenFile = File(out, "seen.txt")
        val seen = ConcurrentHashMap.newKeySet<String>().apply { if (seenFile.exists()) addAll(seenFile.readLines()) }
        val deadline = System.nanoTime() + (hours * NANOS_PER_HOUR).toLong()
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "gen", STACK) }
        val found = ConcurrentHashMap<String, Int>()
        var read = 0
        val rng = kotlin.random.Random(SEED)
        val records = files.flatMap { f ->
            f.readLines().filter { it.isNotBlank() && "\"size\":$size," in it.take(HEAD) }
        }.shuffled(rng).take(games)
        println("GEN ${records.size} games to mine")
        val queue = java.util.concurrent.Semaphore(threads * 2)
        for (line in records) {
            if (System.nanoTime() > deadline) break
            val rec = Learned.JSON.decodeFromString(Learned.GameRecord.serializer(), line)
            if (rec.winner == null) continue
            read++
            // A few rounds of each game, so the time goes to many line-ups rather than one.
            for (m in RoundMine.rounds(rec, 2).shuffled(rng).take(ROUNDS_PER_GAME)) {
                if (m.played.values.any { p -> p.any { it == null } }) continue
                for (t in Team.entries) {
                    if (m.team(t).all { (found[it] ?: 0) >= ENOUGH_PER_HERO }) continue
                    for (goal in goals(m, t)) {
                        val id = "g${m.game}_r${m.round}_${t}_${slug(goal)}"
                        if (id in seen) continue
                        queue.acquire()
                        pool.submit {
                            try {
                                if (System.nanoTime() < deadline) {
                                    val made = runCatching { Case(m, t, goal, id, out).run() }
                                        .onFailure {
                                            println("GEN $id failed: $it")
                                            it.printStackTrace(System.out)
                                        }
                                        .getOrNull()
                                    if (made == true) for (h in m.team(t)) found.merge(h, 1, Int::plus)
                                    seen += id
                                    synchronized(seenFile) { seenFile.appendText("$id\n") }
                                }
                            } finally {
                                queue.release()
                            }
                        }
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(LONG_WAIT, TimeUnit.HOURS)
        println("GEN done: $read games read, survivors by player hero $found")
    }

    private fun goals(m: MinedRound, t: Team): List<PuzzleGoal> {
        val by = Deadline.END_OF_ROUND
        // Coins only where the game offers no goal a player would name first.
        val recognised = m.goalsFor(t).any { g ->
            (g is PuzzleGoal.DefeatHero || g is PuzzleGoal.PushLane || g is PuzzleGoal.WinGame) &&
                (m.metIn(g, t) ?: 0) >= LATE
        }
        val real = m.goalsFor(t).filter { g ->
            when (g) {
                is PuzzleGoal.GainCoins -> g.atLeast >= MIN_COINS && (m.metIn(g, t) ?: 0) >= LATE && !recognised

                // In the spike's numbers Survive only burned its budget: it stays out unless asked for.
                is PuzzleGoal.Survive -> !recognised && System.getenv("ROUND_SURVIVE") != null

                else -> (m.metIn(g, t) ?: 0) >= LATE
            }
        }
        return real.flatMap { g ->
            if (g is PuzzleGoal.GainCoins) {
                // A forced goal that ends too soon may need four turns one or two coins higher.
                (0..2).map {
                    PuzzleGoal.GainCoins(g.atLeast + it, by, "Gain ${g.atLeast + it} coins by the end of the round.")
                }
            } else {
                listOf(g)
            }
        }
    }

    private fun slug(g: PuzzleGoal): String = when (g) {
        is PuzzleGoal.DefeatHero -> "defeat-${g.heroId}"
        is PuzzleGoal.GainCoins -> "coins-${g.atLeast}"
        is PuzzleGoal.PushLane -> "push"
        is PuzzleGoal.Survive -> "survive"
        is PuzzleGoal.WinGame -> "win"
    }

    /** One position, team and goal through the whole filter. */
    private class Case(
        val m: MinedRound,
        val team: Team,
        val goal: PuzzleGoal,
        val id: String,
        val out: File,
    ) {
        val mine = m.team(team)
        val enemies = m.team(team.opponent)
        val spec = RoundSpec.of(m.start.checkpointState)
        val tracker = GoalTracker(goal, team, mine.toSet())
        val static = StaticPolicy(Evaluator(TestData.engine.data, Features.KNOBS))

        fun run(): Boolean {
            val sp = spec ?: return false.also {
                println("GEN $id: the builder can't express it: ${RoundSpec.whyNot(m.start.checkpointState)}")
            }
            val built = sp.build()
            if (!sp.same(m.start.checkpointState, built.state)) {
                println("GEN $id: the built position differs: ${sp.difference(m.start.checkpointState, built.state)}")
                return false
            }
            val heroIds = m.heroes.toSet()
            // The most known first, the cheap test of whether this can work at all; then harder for the grade.
            var survivor: (() -> Unit)? = null
            for (open in OPEN_LEVELS) {
                val actions = enemies.associateWith { h ->
                    m.actions[h].orEmpty().filterKeys { it < open }.mapValues { (_, l) ->
                        sp.mapChoices(l, heroIds) ?: return false
                    }
                }
                val known = KnownPlays(enemies.associateWith { h -> m.played.getValue(h).map { it!! } }, actions)
                val solver = PuzzleSolver(tracker, known = known)
                val root = solver.root(session(sp))
                val seconds = if (open == OPEN_LEVELS.first()) PROOF_SECONDS else OPEN_PROOF_SECONDS
                val prover = PlanProver(solver, PROOF_RUNS, System.nanoTime() + seconds * NANOS)
                val t0 = System.nanoTime()
                val v = prover.solve(root)
                val ms = (System.nanoTime() - t0) / MILLIS
                if (v != Value.WIN) {
                    println("GEN $id open$open: $v, ${prover.engineRuns} runs, $ms ms")
                    break
                }
                val proof = prover.verify(root) ?: error("$id: a proof that does not hold")
                val why = judge(sp, solver, known, root, proof, prover.engineRuns, ms, open) { survivor = it }
                println("GEN $id open$open: proven ${proof.positions} positions, $ms ms: ${why ?: "SURVIVES"}")
                if (why != null) break
            }
            survivor?.invoke()
            return survivor != null
        }

        fun session(sp: RoundSpec): EngineSession {
            val s = sp.build()
            s.quiet = true
            s.haltWhen = GoalTracker.haltAt(Deadline.END_OF_ROUND, sp.round, 1, beforeRetrieve = true)
            return s
        }

        /** A goal forced by the end of turn [last]? */
        fun forcedBy(sp: RoundSpec, known: KnownPlays, last: Int, seconds: Long): Value {
            val s = sp.build()
            s.quiet = true
            val round = sp.round
            s.haltWhen = { st -> st.round != round || st.turn > last || st.step.ordinal >= Step.EOR_EFFECTS.ordinal }
            val solver = PuzzleSolver(tracker, known = known)
            return PlanProver(solver, PROOF_RUNS, System.nanoTime() + seconds * NANOS).solve(solver.root(s))
        }

        private fun valueOf(solver: PuzzleSolver, n: Node, seconds: Long): Value =
            PlanProver(solver, PROOF_RUNS, System.nanoTime() + seconds * NANOS).solve(n)

        @Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "ReturnCount")
        fun judge(
            sp: RoundSpec,
            solver: PuzzleSolver,
            known: KnownPlays,
            root: Node,
            proof: PlanProver.Proof,
            runs: Long,
            ms: Long,
            open: Int,
            survives: (() -> Unit) -> Unit,
        ): String? {
            val line = PlanProver.mainLine(solver, root, proof.moves)
            val endTurn = PlanProver.turnOf(line.last().next)
            if (endTurn < 4) return "the solution ends in turn $endTurn"
            // Really four turns: not forced any sooner (a goal met only at the end by its nature can't be).
            val by2 = if (goal is PuzzleGoal.Survive) Value.LOSS else forcedBy(sp, known, 2, SHORTER_SECONDS)
            if (by2 == Value.WIN) return "forced by the end of turn 2"
            val by3 = if (goal is PuzzleGoal.Survive) Value.LOSS else forcedBy(sp, known, 3, SHORTER_SECONDS)
            if (by3 == Value.WIN) return "forced by the end of turn 3"
            // Every turn must have a choice that matters on the solution line.
            val matters = sortedMapOf<Int, String>()
            val firstCards = LinkedHashMap<String, String>()
            for (step in line) {
                val d = step.decision
                if (solver.sideOf(d) != Side.PLAYER || d.options.size < 2) continue
                val turn = PlanProver.turnOf(step.at)
                val isFirstCard = turn == 1 && d.kind.name == "PLAY_CARD"
                if (turn in matters && !isFirstCard) continue
                val verdicts = d.options.indices.filter { it != step.option }.map { i ->
                    i to valueOf(solver, solver.child(step.at, d.options[i])!!, OPTION_SECONDS)
                }
                val lost = verdicts.count { it.second == Value.LOSS }
                val won = verdicts.count { it.second == Value.WIN }
                if (lost + verdicts.count { it.second == Value.UNKNOWN } > 0 && turn !in matters) {
                    matters[turn] = "${d.seat} ${d.kind}: $lost proven lost, $won proven won, of ${d.options.size}"
                }
                if (isFirstCard) {
                    firstCards[d.seat] = "${won + 1} of ${d.options.size} proven to win, $lost proven lost: " +
                        (listOf(step.option) + verdicts.filter { it.second == Value.WIN }.map { it.first })
                            .joinToString { (d.options[it] as? Choice.Card)?.cardId ?: "?" }
                }
                if (isFirstCard && won + 1 == d.options.size && firstCards.size == mine.size &&
                    firstCards.values.all { it.startsWith("${d.options.size} of") }
                ) {
                    return "every first-turn card works"
                }
            }
            val free = (1..4).filter { it !in matters }
            if (free.isNotEmpty()) return "free turns $free"
            // Temptation: strength 7 on the player's seats must fail; who does solve it sets the grade.
            val s7 = play(sp, solver, S7)
            if (s7.solved) return "s7 solves it"
            val s10 = play(sp, solver, S10)
            val s3 = play(sp, solver, S3)
            if (s3.solved) return "s3 solves it"
            val base = when (open) {
                3 -> 3
                4 -> 2
                else -> 1
            }
            val grade = if (!s10.solved && base < 3) base + 1 else base
            survives {
                writeDossier(
                    sp, solver, known, root, proof, line, runs, ms, open, grade, by2, by3, matters, firstCards, s7, s10,
                )
            }
            return null
        }

        class Play(val solved: Boolean, val moves: List<String>, val events: List<String>)

        /** Strength [strength] plays the player's seats; enemies answer as the runtime does off the key. */
        fun play(sp: RoundSpec, base: PuzzleSolver, strength: Int): Play {
            val solver = PuzzleSolver(tracker, known = base.known)
            var n = solver.root(session(sp))
            val ai: AiPlayer = AiPlayers.deterministic(strength, SEED + strength)
            val moves = ArrayList<String>()
            var guard = 0
            while (n.progress.state == GoalState.OPEN && n.decision != null && guard++ < MAX_DECISIONS) {
                val d = n.decision!!
                val c = if (solver.sideOf(d) == Side.OPPONENT) {
                    d.options[reply(solver, n)]
                } else {
                    ai.choose(n.session, d).also { moves += moveCode(n, it) }
                }
                n = solver.child(n, c)!!
            }
            return Play(n.progress.state == GoalState.MET, moves, n.session.events.map { it.text })
        }

        fun reply(solver: PuzzleSolver, n: Node): Int {
            val choices = solver.choices(n)
            if (choices.size == 1) return choices.single()
            val s = solver.solve(n, budget = RUNTIME_BUDGET)
            if (s.value != Value.UNKNOWN) return s.best
            val scores = static.scores(n.session.state, n.decision!!)
            return choices.maxByOrNull { scores[it] }!!
        }

        fun moveCode(n: Node, c: Choice): String = when (c) {
            is Choice.Action -> "action(ActionType.${c.action.name})"

            is Choice.Card -> "card(\"${c.cardId}\")"

            is Choice.Space -> "space(${c.hex.q}, ${c.hex.r})"

            is Choice.Option -> "option(${c.index})"

            is Choice.Number -> "number(${c.value})"

            is Choice.Decline -> "decline()"

            is Choice.Token -> {
                val at = n.session.state.pieces.first { it.id == c.tokenId }.at!!
                "tokenAt(${at.q}, ${at.r})"
            }

            is Choice.Unit -> if (c.unitId in m.heroes) {
                "hero(\"${c.unitId}\")"
            } else {
                val st = n.session.state
                val at = st.minions.firstOrNull { it.id == c.unitId }?.at
                    ?: st.pieces.first { it.id == c.unitId }.at!!
                "unitAt(${at.q}, ${at.r})"
            }

            Choice.Yes -> "yes()"

            Choice.Pass -> "pass()"

            else -> error("No move for $c")
        }

        @Suppress("LongParameterList")
        fun writeDossier(
            sp: RoundSpec,
            solver: PuzzleSolver,
            known: KnownPlays,
            root: Node,
            proof: PlanProver.Proof,
            line: List<LineStep>,
            runs: Long,
            ms: Long,
            open: Int,
            grade: Int,
            by2: Value,
            by3: Value,
            matters: Map<Int, String>,
            firstCards: Map<String, String>,
            s7: Play,
            s10: Play,
        ) {
            val f = File(out, "$id.txt")
            f.writeText(
                buildString {
                    appendLine("id $id")
                    appendLine("goal ${goal.text} [$goal]")
                    appendLine("player $mine vs $enemies; round ${m.round} (game ${m.game} of ${m.rounds} rounds)")
                    appendLine("openFrom $open grade $grade")
                    appendLine(
                        "proof ${proof.positions} positions (${proof.playerPositions} player, " +
                            "${proof.enemyPositions} enemy), ${proof.edges} edges; search $runs runs, $ms ms",
                    )
                    appendLine(
                        "goal met in turns ${proof.earliest}..${proof.latest}; forced by turn 2: $by2; by turn 3: $by3",
                    )
                    appendLine("s7 solved ${s7.solved}, s10 solved ${s10.solved}")
                    for ((t, w) in matters) appendLine("turn $t matters: $w")
                    for ((h, w) in firstCards) appendLine("first card $h: $w")
                    appendLine("=== setUp")
                    append(sp.kotlin())
                    appendLine("=== known")
                    append(RoundSpec.knownKotlin(known))
                    appendLine("=== seats")
                    appendLine(mine.joinToString(",") + "|" + enemies.joinToString(","))
                    appendLine("=== solution")
                    val moves = line.filter { solver.sideOf(it.decision) == Side.PLAYER }
                    appendLine(moves.joinToString(", ") { moveCode(it.at, it.choice) })
                    appendLine("=== line")
                    var seen = root.session.events.size
                    for (step in line) {
                        val d = step.decision
                        val side = solver.sideOf(d)
                        val width = solver.choices(step.at).size
                        appendLine(
                            "[T${PlanProver.turnOf(
                                step.at,
                            )}] $side ${d.seat} ${d.kind} '${d.prompt}' of $width: ${moveCode(step.at, step.choice)}",
                        )
                        val ev = step.next.session.events
                        for (e in ev.drop(seen)) appendLine("    ${e.text}")
                        seen = ev.size
                    }
                    appendLine("=== s7 line (fails)")
                    appendLine(s7.moves.joinToString(", "))
                    for (e in s7.events.takeLast(EVENTS_SHOWN)) appendLine("    $e")
                    appendLine("=== s10 line (${if (s10.solved) "solves" else "fails"})")
                    appendLine(s10.moves.joinToString(", "))
                    appendLine("=== certificate")
                    for ((k, v) in proof.moves) appendLine("${k.format()} $v")
                },
            )
        }
    }

    private companion object {
        const val SEED = 52L
        const val LATE = 3
        const val MIN_COINS = 4
        const val HEAD = 40
        const val ENOUGH_PER_HERO = 6
        const val STACK = 512L shl 20
        const val LONG_WAIT = 8L
        const val NANOS = 1_000_000_000L
        const val MILLIS = 1_000_000L
        const val NANOS_PER_HOUR = 3.6e12
        const val PROOF_RUNS = 4_000_000L
        const val PROOF_SECONDS = 8L
        const val OPEN_PROOF_SECONDS = 15L
        const val ROUNDS_PER_GAME = 2
        const val SHORTER_SECONDS = 6L
        const val OPTION_SECONDS = 3L
        const val RUNTIME_BUDGET = 1_500L
        const val MAX_DECISIONS = 300
        const val EVENTS_SHOWN = 12
        const val S3 = 3
        const val S7 = 7
        const val S10 = 10

        /** The first turn the enemy's actions are free, most known first: 5 = only its reactions, all round. */
        val OPEN_LEVELS = listOf(5, 4, 3)
    }
}
