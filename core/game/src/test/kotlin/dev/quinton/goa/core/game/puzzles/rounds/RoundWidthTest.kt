package dev.quinton.goa.core.game.puzzles.rounds

import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.Features
import dev.quinton.goa.core.game.ai.StaticPolicy
import dev.quinton.goa.core.game.ai.learned.Learned
import dev.quinton.goa.core.game.engine.Step
import dev.quinton.goa.core.game.puzzles.GoalTracker
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Node
import dev.quinton.goa.core.game.puzzles.Progress
import dev.quinton.goa.core.game.puzzles.PuzzleSolver
import dev.quinton.goa.core.game.puzzles.Side
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

/**
 * Opt-in (ADR-052): `ROUND_WIDTH=<positions>:<max positions per run>`. How big must any proof be? With
 * the player fixed to one static plan and every enemy answer open, counts the distinct positions in
 * each turn (a floor: a real proof answers every one of them) and the widths of the enemy's decisions.
 */
class RoundWidthTest {
    @Test
    fun `how many positions does one plan have to answer`() {
        val spec = System.getenv("ROUND_WIDTH")?.split(":") ?: return
        val n = spec[0].toInt()
        val cap = spec.getOrNull(1)?.toInt() ?: 3_000_000
        val games = System.getenv("ROUND_GAMES")?.toInt() ?: 200
        val threads = System.getenv("AI_THREADS")?.toInt() ?: 4
        val data = File(System.getenv("ROUND_DATA") ?: "D:/.goa-qa/v09/a2/data/gen2/games-2.jsonl")
        val mined = ArrayList<MinedRound>()
        var read = 0
        data.useLines { lines ->
            for (line in lines) {
                if (read >= games) break
                val rec = Learned.JSON.decodeFromString(Learned.GameRecord.serializer(), line)
                if (rec.size != 2 || rec.winner == null) continue
                read++
                mined += RoundMine.rounds(rec, 2).filter { m -> m.played.values.all { p -> p.all { it != null } } }
            }
        }
        val scored = mined.flatMap { m -> Team.entries.map { t -> Triple(m, t, freedom(m, t)) } }.sortedBy { it.third }
        val distinct = scored.distinctBy { it.first.game }
        val picks = distinct.take(n / 2) + distinct.drop(distinct.size / 2).take(n - n / 2)
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(null, r, "width", 512L shl 20) }
        for ((m, t, f) in picks) {
            pool.submit { runCatching { count(m, t, f, cap) }.onFailure { it.printStackTrace(System.out) } }
        }
        pool.shutdown()
        pool.awaitTermination(2, TimeUnit.HOURS)
    }

    private fun freedom(m: MinedRound, t: Team): Int = m.team(t.opponent).sumOf { h ->
        m.played.getValue(h).sumOf { TestData.engine.data.card(it!!).values.movement ?: 0 }
    }

    private data class Key(val a: Long, val b: Long, val seg: Int)

    private fun count(m: MinedRound, t: Team, f: Int, cap: Int) {
        val enemies = m.team(t.opponent)
        val known = KnownPlays(enemies.associateWith { h -> m.played.getValue(h).map { it!! } })
        // A goal that never closes the search early: Survive would end on a defeat, so count through it.
        val goal = PuzzleGoal.GainCoins(CLOSED, Deadline.END_OF_ROUND, "")
        val sv = PuzzleSolver(GoalTracker(goal, t, m.team(t).toSet()), known = known)
        val static = StaticPolicy(Evaluator(TestData.engine.data, Features.KNOBS))
        val seen = HashSet<Key>()
        val perTurn = IntArray(TURNS + 2)
        val enemyWidth = LongArray(TURNS + 2)
        val enemyNodes = LongArray(TURNS + 2)
        var full = false
        // A rigid plan: the first answer at each (turn, seat, kind, question in this turn) is kept when offered.
        val plan = HashMap<String, dev.quinton.goa.core.game.Choice>()

        fun go(n: Node) {
            val d = n.decision ?: return
            if (full) return
            val k = Key(n.checkpointHash, n.checkpointHash2, n.session.stepAnswers.hashCode())
            if (!seen.add(k)) return
            val st = n.session.state
            val turn = if (st.step.ordinal >= Step.EOR_EFFECTS.ordinal) TURNS + 1 else st.turn
            perTurn[turn]++
            if (seen.size >= cap) full = true
            if (sv.sideOf(d) == Side.OPPONENT) {
                val choices = sv.choices(n)
                if (d.options.size > 1 && choices.size > 1) {
                    enemyWidth[turn] += choices.size.toLong()
                    enemyNodes[turn]++
                }
                for (i in choices) go(sv.child(n, d.options[i])!!)
            } else {
                val slot = "${st.turn} ${d.seat} ${d.kind} ${d.prompt}"
                val kept = plan[slot]?.takeIf { it in d.options }
                val pick = kept ?: run {
                    val scores = static.scores(st, d)
                    d.options[d.options.indices.maxByOrNull { scores[it] } ?: 0].also { plan[slot] = it }
                }
                go(sv.child(n, pick)!!)
            }
        }
        val root = sv.wrap(m.halted().also { it.quiet = true }, Progress(), null)
        val t0 = System.nanoTime()
        go(root)
        val widths = (1..TURNS + 1).joinToString(" ") { i ->
            if (enemyNodes[i] == 0L) "-" else "%.1f".format(enemyWidth[i].toDouble() / enemyNodes[i])
        }
        println(
            "WIDTH g${m.game} r${m.round} $t freedom $f: positions by turn ${perTurn.drop(1)} total ${seen.size}" +
                "${if (full) " (CAPPED)" else ""} enemy width $widths, ${(System.nanoTime() - t0) / 1_000_000} ms",
        )
    }

    private companion object {
        const val TURNS = 4
        const val CLOSED = 1_000
    }
}
