package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.engine.GameEngine
import dev.quinton.goa.core.game.puzzles.AnswerKey
import dev.quinton.goa.core.game.puzzles.EnginePuzzleBook
import dev.quinton.goa.core.game.puzzles.GoalState
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.PreparedPuzzle
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.Side
import java.io.File

/**
 * A full round's proof, kept in test resources (ENCODING.md "Full rounds"): the player's option at every
 * position the plan reaches. [check] replays it through the engine against every enemy answer, which is
 * fast and needs no search, so a rules change that breaks a puzzle fails the build; `ROUND_PROVE`
 * (`FullRoundProveTest`) searches again when it does.
 */
class RoundCertificate(val moves: Map<String, Int>) {
    fun format(): String = moves.entries.joinToString("") { "${it.key} ${it.value}\n" }

    /** What the replay found: how much it walked, or the first thing wrong. */
    class Checked(val positions: Int, val edges: Long, val error: String?, val latest: Int)

    fun check(prep: PreparedPuzzle): Checked {
        val solver = prep.solver
        val done = HashSet<String>()
        var edges = 0L
        var latest = 0
        var error: String? = null

        fun go(n: Node) {
            if (error != null) return
            if (n.progress.state == GoalState.MET) {
                latest = maxOf(latest, PlanProver.turnOf(n))
                return
            }
            val d = n.decision
            if (n.progress.state == GoalState.FAILED || d == null) {
                error = "the goal failed in turn ${PlanProver.turnOf(n)}"
                return
            }
            val k = RoundKey.of(n).format()
            if (!done.add(k)) return
            if (solver.sideOf(d) == Side.OPPONENT) {
                for (i in solver.choices(n)) {
                    edges++
                    go(checkNotNull(solver.child(n, d.options[i])))
                }
            } else {
                val i = moves[k]
                if (i == null || i !in d.options.indices) {
                    error = "no plan at ${d.seat} ${d.kind} '${d.prompt}' in turn ${PlanProver.turnOf(n)}"
                    return
                }
                edges++
                go(checkNotNull(solver.child(n, d.options[i])))
            }
        }
        go(prep.rootNode)
        return Checked(done.size, edges, error, latest)
    }

    /** The key the book ships: the solution line against the answers that hold out longest. */
    fun key(prep: PreparedPuzzle): AnswerKey {
        val line = line(prep)
        val entries = LinkedHashMap<String, AnswerKey.Entry>()
        val path = ArrayList<Int>()
        for (step in line) {
            val at = AnswerKey.pathOf(path)
            entries[at] = if (prep.solver.sideOf(step.decision) == Side.PLAYER) {
                AnswerKey.Wins(listOf(step.option))
            } else {
                AnswerKey.Reply(step.option)
            }
            path += step.option
        }
        return AnswerKey(prep.info.id, GameEngine.ENGINE_VERSION, AnswerKey.fingerprint(prep.rootNode), entries)
    }

    /** The solution line through the engine. */
    fun line(prep: PreparedPuzzle): List<LineStep> = PlanProver.mainLine(prep.solver, prep.rootNode, plan())

    /** The stored plan, as the line walker reads it. */
    fun plan(): Map<RoundKey, Int> = PlanMoves(moves)

    /** The stored strings read as a plan. */
    private class PlanMoves(private val moves: Map<String, Int>) : AbstractMap<RoundKey, Int>() {
        override val entries: Set<Map.Entry<RoundKey, Int>> get() = emptySet()

        override fun get(key: RoundKey): Int? = moves[key.format()]

        override fun containsKey(key: RoundKey): Boolean = key.format() in moves
    }

    companion object {
        private const val DIR = "src/test/resources/dev/quinton/goa/core/game/puzzles/rounds"

        fun file(id: String) = File("$DIR/$id.cert")

        fun load(id: String): RoundCertificate? {
            val f = file(id)
            if (!f.exists()) return null
            return RoundCertificate(
                f.readLines().filter { it.isNotBlank() }.associate { line ->
                    val (k, v) = line.split(" ")
                    k to v.toInt()
                },
            )
        }

        fun of(proof: PlanProver.Proof) = RoundCertificate(
            proof.moves.entries.associate {
                it.key.format() to it.value
            },
        )

        fun prepared(p: Puzzle): PreparedPuzzle =
            EnginePuzzleBook(TestData.engine, listOf(p), useKeys = false).prepared(p.info.id)
    }
}
