package dev.quinton.goa.core.game.ai

import org.junit.Test

/**
 * Opt-in long tournaments (not part of the normal test run): `AI_TOURNAMENT=<seeds>` plays
 * 12 × seeds mirrored games per pair over the first-game heroes; `AI_MIXED=<line-ups>` plays
 * 2 × line-ups mirrored games over line-ups drawn from all playable heroes (`AI_FIRST_SEED` is then
 * the first line-up). `AI_PAIRS=6:5,…` picks the pairs of strengths (default: every neighbouring
 * pair, 2:1 to 10:9; a variant as in [ExperimentTest] works too), `AI_THREADS` the threads.
 */
class TournamentTest {
    @Test
    fun `long tournaments between neighbouring strengths`() {
        val seeds = System.getenv("AI_TOURNAMENT")?.toIntOrNull()
        val mixed = System.getenv("AI_MIXED")?.toIntOrNull()
        if (seeds == null && mixed == null) return
        val pairs = (System.getenv("AI_PAIRS") ?: (2..10).joinToString(",") { "$it:${it - 1}" }).split(",").map {
            val (a, b) = it.split(":")
            side(a) to side(b)
        }
        val first = System.getenv("AI_FIRST_SEED")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        for ((a, b) in pairs) {
            val t0 = System.nanoTime()
            val score = if (mixed != null) {
                Tournament.runMixed(a, b, mixed, threads, firstLineup = first)
            } else {
                Tournament.run(a, b, seeds!!, threads, firstSeed = first)
            }
            println("TOURNAMENT $score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
            if (mixed != null) println("TOURNAMENT by hero ($a): ${score.byHero()}")
            // AI_GAMES=<hero>: every game with that hero seated (or "all").
            System.getenv("AI_GAMES")?.let { h ->
                score.lines.filter { h == "all" || h in it.substringBefore(":") }
                    .forEach { println("TOURNAMENT   $it") }
            }
        }
    }

    private fun side(spec: String): Tournament.Side =
        spec.toIntOrNull()?.let { Tournament.strength(it) } ?: ExperimentTest.side(spec)
}
