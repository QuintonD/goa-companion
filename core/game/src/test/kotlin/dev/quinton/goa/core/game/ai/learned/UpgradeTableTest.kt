package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.ai.Evaluator
import dev.quinton.goa.core.game.ai.LearnedNet
import dev.quinton.goa.core.game.ai.NetEvaluator
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.model.match.Team
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.sqrt
import org.junit.Test

/**
 * Opt-in: the per-hero upgrade table (AI.md "Per-hero upgrade tables", ADR-049). `AI_UPGRADES=<games>,…`
 * replays self-play games played with ε-exploration at the level-up questions (`AI_EPS_UPGRADE` of
 * [LearnedSelfPlayTest]) and, for every answer to [DecisionKind.UPGRADE_CARD] and
 * [DecisionKind.KEEP_UPGRADE] with a real choice, takes the game's result minus the net's chance just
 * before it (a control variate: it removes most of the game's noise). Monte Carlo control: the mean per
 * (question, card) is the choice's value, and the output (`AI_UPGRADES_OUT`) keeps the choices whose
 * lead over the other option is at least `AI_UPGRADES_Z` (2) standard errors.
 */
class UpgradeTableTest {
    private class Tally {
        var n = 0
        var sum = 0.0
        var sq = 0.0

        fun add(x: Double) {
            n++
            sum += x
            sq += x * x
        }

        val mean: Double get() = sum / n
        val se: Double get() = if (n < 2) 1.0 else sqrt(((sq - sum * sum / n) / (n - 1)).coerceAtLeast(0.0) / n)
    }

    private class Answer(val key: String, val rivals: List<String>, val x: Double)

    @Test
    fun `value of each upgrade choice`() {
        val spec = System.getenv("AI_UPGRADES") ?: return
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val net = System.getenv("AI_UPGRADES_NET")?.let(Learned::net) ?: LearnedNet.CURRENT
        val z = System.getenv("AI_UPGRADES_Z")?.toDouble() ?: 2.0
        val games = Learned.readGames(spec.split(",").map(::File)).filter { it.winner != null }
        val pool = Executors.newFixedThreadPool(threads)
        val answers = try {
            pool.invokeAll(games.map { r -> Callable { answers(r, net) } }).flatMap { it.get() }
        } finally {
            pool.shutdown()
        }
        val tally = LinkedHashMap<String, Tally>()
        val rivals = LinkedHashMap<String, MutableSet<String>>()
        for (a in answers) {
            tally.getOrPut(a.key) { Tally() }.add(a.x)
            rivals.getOrPut(a.key) { LinkedHashSet() } += a.rivals
        }
        println("UPGRADES ${answers.size} answers from ${games.size} games, ${tally.size} (question, card) cells")
        val keep = StringBuilder()
        for ((key, t) in tally.entries.sortedBy { it.key }) {
            val others = rivals.getValue(key).mapNotNull { tally[it] }.filter { it.n >= 2 }
            val lead = if (others.isEmpty()) 0.0 else t.mean - others.maxOf { it.mean }
            val se = sqrt(t.se * t.se + (others.maxOfOrNull { it.se * it.se } ?: 0.0))
            println(
                "UPGRADES %-34s n %5d mean %+.3f se %.3f lead %+.3f z %+.1f".format(key, t.n, t.mean, t.se, lead, lead / se),
            )
            if (others.isNotEmpty() && lead >= z * se) keep.append(key).append(' ').append("%.3f".format(lead)).append('\n')
        }
        System.getenv("AI_UPGRADES_OUT")?.let { File(it).writeText(keep.toString()) }
        println("UPGRADES kept\n$keep")
    }

    /** One game's level-up answers with a real choice. */
    private fun answers(r: Learned.GameRecord, net: LearnedNet): List<Answer> {
        val winner = Team.valueOf(r.winner!!)
        val ev = NetEvaluator(TestData.engine.data, net)
        val s = TestData.engine.newGame(r.setup) as EngineSession
        val out = ArrayList<Answer>()
        for (c in r.answers) {
            val d = s.pending ?: break
            if (d.kind in KINDS && d.options.size > 1 && c is Choice.Card) {
                val logit = ev.redLogit(s.state)
                val red = Evaluator.sigmoid(logit)
                val before = if (d.team == Team.RED) red else 1 - red
                val result = if (winner == d.team) 1.0 else 0.0
                val rivals = d.options.filterIsInstance<Choice.Card>().filter { it != c }.map { key(d.kind, it.cardId) }
                out += Answer(key(d.kind, c.cardId), rivals, result - before)
            }
            if (s.answer(d.id, c) !is AnswerResult.Accepted) break
        }
        return out
    }

    private fun key(kind: DecisionKind, card: String) = "${if (kind == DecisionKind.UPGRADE_CARD) "up" else "keep"}:$card"

    companion object {
        private val KINDS = setOf(DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE)
    }
}
