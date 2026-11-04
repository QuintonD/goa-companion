package dev.quinton.goa.core.game.ai.learned

import dev.quinton.goa.core.game.ai.Tournament
import java.io.File
import org.junit.Test

/**
 * Opt-in: `AI_TEAMS=a:b,c:d` plays variant a against b (ExperimentTest's variant syntax plus
 * [Learned.side]'s keys), mirrored, on `AI_N` line-ups of `AI_SIZE` against `AI_SIZE` (default 3)
 * from the line-up family `AI_FAMILY` (train / tune / test, default test) starting at `AI_FIRST`.
 * `AI_SPRT=0.55` stops as soon as the games separate a win rate of 0.55 from 0.5 (`AI_N` is then the
 * most it plays); `AI_SCORECARD=1` prints each hero's uplift, worst first. `AI_SEAT=<hero>,…` seats one
 * of those heroes in every line-up (the per-hero bench's line-ups, [Teams.seating]); `AI_STORE=<file>`
 * appends every game in [LearnedSelfPlayTest]'s format.
 */
class TeamTournamentTest {
    @Test
    fun `variants against each other at any team size`() {
        val pairs = System.getenv("AI_TEAMS")?.split(",") ?: return
        val size = System.getenv("AI_SIZE")?.toIntOrNull() ?: 3
        val n = System.getenv("AI_N")?.toIntOrNull() ?: 8
        val first = System.getenv("AI_FIRST")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val sprt = System.getenv("AI_SPRT")?.toDoubleOrNull()?.let { Tournament.Sprt(it) }
        val family = when (System.getenv("AI_FAMILY") ?: "test") {
            "train" -> Teams.TRAIN
            "tune" -> Teams.TUNE
            else -> Teams.TEST
        }
        for (pair in pairs) {
            val (a, b) = pair.split(":")
            val t0 = System.nanoTime()
            val seat = System.getenv("AI_SEAT")?.split(",")
            val specs =
                if (seat == null) Teams.specs(n, size, family, first) else Teams.seating(n, size, family, seat, first)
            val store = System.getenv("AI_STORE")?.let(::File)
            // AI_KILLS=1: each side's team kills open and taken, the kill extension's rate, work per card play
            // (ADR-061).
            Oracle.predicted.set(0)
            Oracle.right.set(0)
            val kills = if (System.getenv("AI_KILLS") != null) listOf(KillCount(), KillCount()) else null

            // oracle/<variant>: the oracle exploiter on that variant, predicting the other side's card plays.
            fun side(x: String, other: String) =
                if (x.startsWith("oracle/")) Oracle.side(x.removePrefix("oracle/"), other) else Learned.side(x)
            val score = Teams.mirrored(
                side(a, b),
                side(b, a),
                specs,
                threads,
                sprt,
                onGame = store?.let { f ->
                    { p: Teams.Played -> Learned.store(f, p, a, b, family) }
                },
                kills = kills,
            )
            println("TEAMS ${size}v$size $score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
            println("TEAMS   ${score.details()}")
            println("TEAMS by hero ($a): ${score.byHero()}")
            sprt?.let { println("TEAMS SPRT ${it.verdict(score)} after ${score.games} games (pairs)") }
            if (System.getenv("AI_SCORECARD") != null) println("TEAMS scorecard\n${Tournament.scorecard(score)}")
            if (Oracle.predicted.get() > 0) {
                println(
                    "TEAMS oracle predicted ${Oracle.right.get()} of ${Oracle.predicted.get()} enemy card plays right",
                )
            }
            kills?.let { (ka, kb) ->
                println("TEAMS kills ${ka.line(a)}")
                println("TEAMS kills ${kb.line(b)}")
            }
            if (System.getenv("AI_LINES") != null) score.lines.forEach { println("TEAMS   $it") }
        }
    }
}
