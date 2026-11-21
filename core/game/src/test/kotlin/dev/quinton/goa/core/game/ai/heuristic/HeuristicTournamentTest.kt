package dev.quinton.goa.core.game.ai.heuristic

import dev.quinton.goa.core.game.AiPlayer
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.GameSession
import dev.quinton.goa.core.game.ai.AiHarness
import dev.quinton.goa.core.game.ai.AiPlayers
import dev.quinton.goa.core.game.ai.Tournament
import java.util.Collections
import org.junit.Test

/**
 * Opt-in. `AI_H_TOURNAMENT=h10:s8,h10:h5` plays mirrored games of the heuristic book (`hN`)
 * against the old search (`sN`) or another book. `AI_H_GAMES` is the number of line-ups
 * (each is mirrored, so games are twice that; default 24). `AI_FORMAT` and `AI_THREADS` as elsewhere.
 * Prints the win rate, the Wilson interval and the mean, p95 and longest time to answer.
 * `AI_H_FIRST` is the first mixed line-up index, so a final table can use a held-out seed.
 */
class HeuristicTournamentTest {
    @Test
    fun `mirrored games`() {
        val spec = System.getenv("AI_H_TOURNAMENT") ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        for (pair in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (aName, bName) = pair.split(":")
            val aTimes = Collections.synchronizedList(ArrayList<Long>())
            val bTimes = Collections.synchronizedList(ArrayList<Long>())
            val a = side(aName, aTimes)
            val b = side(bName, bTimes)
            val t0 = System.nanoTime()
            val score = Tournament.runMixed(a, b, lineups, threads, firstLineup = first, format = format)
            val secs = (System.nanoTime() - t0) / 1e9
            val (lo, hi) = score.interval
            println(
                "H_TOURNAMENT $score Wilson ${"%.1f".format(lo * 100)}-${"%.1f".format(hi * 100)}% " +
                    "in ${"%.0f".format(secs)} s",
            )
            println("H_TOURNAMENT   ${score.details()}")
            println(
                "H_TOURNAMENT   answer ${a.label} ${ms(aTimes)} ms, ${b.label} ${ms(bTimes)} ms (mean / p95 / max)",
            )
        }
    }

    private fun ms(times: List<Long>): String {
        if (times.isEmpty()) return "n/a"
        val sorted = times.sorted()
        val p95 = sorted[((sorted.size - 1) * 95) / 100]
        val max = sorted.last() / 1_000_000.0
        return "%.2f / %.2f / %.1f".format(times.average() / 1_000_000.0, p95 / 1_000_000.0, max)
    }

    private fun side(spec: String, times: MutableList<Long>): Tournament.Side =
        Tournament.Side(spec) { seed -> timed(player(spec, seed), times) }

    private fun player(spec: String, seed: Long): AiPlayer {
        // h10+sim=0+spare=2: a strength and measurement knobs (HeuristicProfile.tune).
        val parts = spec.split("+")
        val n = parts[0].drop(1).toInt()
        val tune = parts.drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }
        return when (spec[0]) {
            'h', 'H' -> HeuristicAi(n, seed, tune = tune)
            's', 'S' -> AiPlayers.searchDeterministic(n, seed)
            else -> error("AI_H_TOURNAMENT wants h6 or s6, not $spec")
        }
    }

    private fun timed(inner: AiPlayer, times: MutableList<Long>): AiPlayer = object : AiPlayer {
        override fun choose(session: GameSession, decision: Decision, cancelled: () -> Boolean): Choice {
            val t0 = System.nanoTime()
            val choice = inner.choose(session, decision, cancelled)
            if (decision.options.size > 1) times.add(System.nanoTime() - t0)
            return choice
        }
    }
}
