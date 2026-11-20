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
 * Prints the win rate, the Wilson interval and the mean time to answer.
 */
class HeuristicTournamentTest {
    @Test
    fun `mirrored games`() {
        val spec = System.getenv("AI_H_TOURNAMENT") ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 24
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        for (pair in spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val (aName, bName) = pair.split(":")
            val aTimes = Collections.synchronizedList(ArrayList<Long>())
            val bTimes = Collections.synchronizedList(ArrayList<Long>())
            val a = side(aName, aTimes)
            val b = side(bName, bTimes)
            val t0 = System.nanoTime()
            val score = Tournament.runMixed(a, b, lineups, threads, format = format)
            val secs = (System.nanoTime() - t0) / 1e9
            val (lo, hi) = score.interval
            println(
                "H_TOURNAMENT $score Wilson ${"%.1f".format(lo * 100)}-${"%.1f".format(hi * 100)}% " +
                    "in ${"%.0f".format(secs)} s",
            )
            println("H_TOURNAMENT   ${score.details()}")
            println("H_TOURNAMENT   answer ${a.label} ${ms(aTimes)} ms, ${b.label} ${ms(bTimes)} ms (mean)")
        }
    }

    private fun ms(times: List<Long>): String =
        if (times.isEmpty()) "n/a" else "%.2f".format(times.average() / 1_000_000.0)

    private fun side(spec: String, times: MutableList<Long>): Tournament.Side =
        Tournament.Side(spec) { seed -> timed(player(spec, seed), times) }

    private fun player(spec: String, seed: Long): AiPlayer {
        val n = spec.drop(1).toInt()
        return when (spec[0]) {
            'h', 'H' -> HeuristicAi(n, seed)
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
