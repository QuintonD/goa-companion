package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.game.ai.Tournament
import dev.quinton.goa.core.game.ai.hybrid.HybridPlayers
import org.junit.Test

/**
 * Opt-in (wave 3 Track D). `AI_PB_SEARCH=x10+pol=1+scope=2` plays the hybrid search with that spec against the
 * same search with `pbooks=0` (no hero playbooks anywhere inside the search: candidate order, opponent model,
 * playouts, the HEval leaf's hero terms), mirrored 2v2 line-ups from `AI_H_FIRST`, in batches of
 * `AI_PB_BATCH` line-ups (default 16), at most `AI_H_GAMES` line-ups (default 128, 256 games).
 *
 * It stops early on two pentanomial SPRTs ([Tournament.Sprt], α = β = 0.05): with-playbooks 55% against 50%,
 * and 45% against 50%. Both decided: a gain (the first accepts 55%), a loss (the second accepts 45%), or
 * neither within ±5 points. `AI_H_OUT` appends each running score and the verdict to that file.
 */
class SearchPlaybookAblationTest {
    @Test
    fun `the search with and without hero playbooks`() {
        val spec = System.getenv("AI_PB_SEARCH") ?: return
        val lineups = System.getenv("AI_H_GAMES")?.toIntOrNull() ?: 128
        val first = System.getenv("AI_H_FIRST")?.toIntOrNull() ?: 1000
        val batch = System.getenv("AI_PB_BATCH")?.toIntOrNull() ?: 16
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 2
        val knobs = knobs(spec)
        val with = Tournament.Side(spec) { seed -> HybridPlayers.hybrid(seed, knobs) }
        val without = Tournament.Side("$spec+pbooks=0") { seed ->
            HybridPlayers.hybrid(seed, knobs + ("pbooks" to 0.0))
        }
        val up = Tournament.Sprt(0.55)
        val down = Tournament.Sprt(0.45)
        val total = Tournament.Score(with, without)
        var done = 0
        var verdict = "undecided after the cap"
        while (done < lineups) {
            val n = minOf(batch, lineups - done)
            val s = Tournament.runMixed(with, without, n, threads, firstLineup = first + done)
            done += n
            total.aWins += s.aWins
            total.bWins += s.bWins
            total.draws += s.draws
            for (k in total.pairs.indices) total.pairs[k] += s.pairs[k]
            val vUp = up.verdict(total)
            val vDown = down.verdict(total)
            val line = "PB_SEARCH lineups $first+$done: $total; SPRT 55% $vUp, 45% $vDown"
            println(line)
            println("PB_SEARCH   batch ${s.details()}")
            out(line)
            if (vUp != Tournament.Sprt.Verdict.GOING_ON && vDown != Tournament.Sprt.Verdict.GOING_ON) {
                verdict = when {
                    vUp == Tournament.Sprt.Verdict.H1 -> "playbooks gain (accepts 55%)"
                    vDown == Tournament.Sprt.Verdict.H1 -> "playbooks lose (accepts 45%)"
                    else -> "within ±5 points (both accept 50%)"
                }
                break
            }
        }
        val (lo, hi) = total.interval
        val end = "PB_SEARCH final $spec: ${total.aWins}-${total.bWins} of ${total.games} = " +
            "${"%.1f".format(total.rate * 100)}% Wilson ${"%.1f".format(lo * 100)}-" +
            "${"%.1f".format(hi * 100)}%; $verdict"
        println(end)
        out(end)
        assertThat(total.games).isGreaterThan(0)
    }

    private fun knobs(spec: String): Map<String, Double> =
        spec.split("+").drop(1).associate { kv -> kv.substringBefore("=") to kv.substringAfter("=").toDouble() }

    private fun out(line: String) {
        System.getenv("AI_H_OUT")?.let { java.io.File(it).appendText(line + "\n") }
    }
}
