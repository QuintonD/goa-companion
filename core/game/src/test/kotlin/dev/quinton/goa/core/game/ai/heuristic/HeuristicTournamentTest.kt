package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.SampleRunner
import dev.quinton.goa.core.game.ai.ThreadRunner
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.hybrid.HybridPlayers
import java.util.Collections
import org.junit.Test

/**
 * Opt-in. `AI_H_TOURNAMENT=h10:s8,h10:h5` plays mirrored games of the heuristic book (`hN`)
 * against the old search (`sN`) or another book. `AI_H_GAMES` is the number of line-ups
 * (each is mirrored, so games are twice that; default 24). `AI_FORMAT` and `AI_THREADS` as elsewhere.
 * Prints the win rate, the Wilson interval and the mean, p95 and longest time to answer.
 * `AI_H_FIRST` is the first mixed line-up index, so a final table can use a held-out seed.
 * `h10/action.move/s10` is h10 with those categories of question answered by s10 ([HybridAi]).
 */
class HeuristicTournamentTest {
    @Test
    fun `mirrored games`() {
        val spec = System.getenv("AI_H_TOURNAMENT") ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        // AI_SPRT=0.55: the pentanomial SPRT (p0 0.5, α β 0.05) on batches of AI_SPRT_BATCH line-ups, at most AI_H_GAMES.
        val sprt = System.getenv("AI_SPRT")?.toDoubleOrNull()?.let { Tournament.Sprt(it) }
        val sprtBatch = System.getenv("AI_SPRT_BATCH")?.toIntOrNull() ?: 16
        for (pair in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (aName, bName) = pair.split(":")
            val aTimes = Collections.synchronizedList(ArrayList<Long>())
            val bTimes = Collections.synchronizedList(ArrayList<Long>())
            val aPlays = Collections.synchronizedList(ArrayList<Long>())
            val bPlays = Collections.synchronizedList(ArrayList<Long>())
            val a = side(aName, aTimes, aPlays)
            val b = side(bName, bTimes, bPlays)
            val t0 = System.nanoTime()
            val score = Tournament.runMixed(
                a, b, lineups, threads, firstLineup = first, format = format, sprt = sprt, sprtBatch = sprtBatch,
            )
            val verdict = sprt?.let { " SPRT(${it.p1}) ${it.verdict(score)}" }.orEmpty()
            val secs = (System.nanoTime() - t0) / 1e9
            val (lo, hi) = score.interval
            println(
                "H_TOURNAMENT $score Wilson ${"%.1f".format(lo * 100)}-${"%.1f".format(hi * 100)}% " +
                    "in ${"%.0f".format(secs)} s$verdict",
            )
            println("H_TOURNAMENT   ${score.details()}")
            println(
                "H_TOURNAMENT   answer ${a.label} ${ms(aTimes)} ms, ${b.label} ${ms(bTimes)} ms (mean / p95 / max)",
            )
            println(
                "H_TOURNAMENT   card play ${a.label} ${ms(aPlays)} ms, ${b.label} ${ms(bPlays)} ms (mean / p95 / max)",
            )
            hybridStats()?.let { println("H_TOURNAMENT   $it") }
            if (System.getenv("AI_H_LINES") != null) score.lines.forEach { println("H_TOURNAMENT   game $it") }
            System.getenv("AI_H_OUT")?.let { out ->
                java.io.File(out).appendText(
                    "== $pair lineups $first+$lineups ${format.label}\n$score Wilson ${"%.1f".format(lo * 100)}-" +
                        "${"%.1f".format(hi * 100)}% in ${"%.0f".format(secs)} s$verdict\n${score.details()}\n" +
                        "answer ${a.label} ${ms(aTimes)} ms, ${b.label} ${ms(bTimes)} ms\n" +
                        "card play ${a.label} ${ms(aPlays)} ms, ${b.label} ${ms(bPlays)} ms\n",
                )
            }
        }
    }

    /** The hybrid players of the last pair: playouts, memo hits, samples drawn and distinct. */
    private val hybrids = Collections.synchronizedList(ArrayList<dev.quinton.goa.core.game.ai.hybrid.HybridAi>())

    /** Order-free fingerprint of every answer with two or more options (same answers, same hash). */
    private val answersHash = java.util.concurrent.atomic.AtomicLong()

    private fun hybridStats(): String? {
        val all = synchronized(hybrids) { hybrids.toList().also { hybrids.clear() } }
        if (all.isEmpty()) return null
        val t = LongArray(4)
        for (h in all) h.stats.forEachIndexed { i, v -> t[i] += v }
        return "answers hash ${answersHash.getAndSet(0)}; hybrid playouts ${t[0]}, memo hits ${t[1]}, samples ${t[2]} drawn / ${t[3]} distinct"
    }

    private fun ms(times: List<Long>): String {
        if (times.isEmpty()) return "n/a"
        val sorted = times.sorted()
        val p95 = sorted[((sorted.size - 1) * 95) / 100]
        val max = sorted.last() / 1_000_000.0
        return "%.2f / %.2f / %.1f".format(times.average() / 1_000_000.0, p95 / 1_000_000.0, max)
    }

    private fun side(spec: String, times: MutableList<Long>, plays: MutableList<Long>): Tournament.Side {
        val threads = spec.split("+").firstOrNull { it.startsWith("thr=") }?.substringAfter("=")?.toInt() ?: 1
        val runner = if (threads > 1) ThreadRunner(threads) else SampleRunner.SEQUENTIAL
        return Tournament.Side(spec) { seed -> timed(player(spec, seed, runner), times, plays) }
    }

    private fun player(spec: String, seed: Long, runner: SampleRunner): AiPlayer {
        // h10/action.move/s10: h10 with those question categories answered by s10 ([HybridAi], the category swaps).
        if (spec.count { it == '/' } == 2) {
            val (base, cats, delegate) = spec.split("/")
            return HybridAi(player(base, seed, runner), player(delegate, seed, runner), cats.split(".").toSet())
        }
        // h10+sim=0+spare=2: a strength and measurement knobs (HeuristicProfile.tune).
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() } - "thr"
        return when (spec[0]) {
            'h', 'H' -> HeuristicAi(n, seed, tune = tune)
            's', 'S' -> AiPlayers.searchDeterministic(n, seed)
            // The search prototypes of REVIEW-2-search.md (strength 10 only).
            'x', 'X' -> HybridPlayers.hybrid(seed, tune, runner).also {
                (it as? dev.quinton.goa.core.game.ai.hybrid.HybridAi)?.let(hybrids::add)
            }
            'y', 'Y' -> HybridPlayers.searchWithBook(seed, tune, runner)
            else -> error("AI_H_TOURNAMENT wants h6 or s6, not $spec")
        }
    }

    private fun timed(inner: AiPlayer, times: MutableList<Long>, plays: MutableList<Long>): AiPlayer = object : AiPlayer {
        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
            val t0 = System.nanoTime()
            val choice = inner.choose(session, decision, cancelled)
            val dt = System.nanoTime() - t0
            if (decision.options.size > 1) times.add(dt)
            if (decision.options.size > 1 && decision.kind == DecisionKind.PLAY_CARD) plays.add(dt)
            if (decision.options.size > 1) {
                answersHash.accumulateAndGet(decision.id * 31 + choice.hashCode()) { x, y -> x + y * 0x9E3779B1L }
            }
            return choice
        }
    }
}
