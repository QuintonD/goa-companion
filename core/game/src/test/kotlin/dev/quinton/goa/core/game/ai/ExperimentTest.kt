package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import org.junit.Test

/**
 * Opt-in tuning experiments: `AI_EXPERIMENT=a:b,c:d` plays profile variant a against b (mirrored,
 * `AI_TOURNAMENT` seeds). Names: the four levels in lower case, or a variant below.
 */
class ExperimentTest {
    private val hard = AiPlayers.profile(AiLevel.HARD)
    private val expert = AiPlayers.profile(AiLevel.EXPERT)

    private val variants: Map<String, LevelProfile> =
        AiLevel.entries.associate { it.name.lowercase() to AiPlayers.profile(it) } +
            mapOf(
                "expertNextTurn" to expert.copy(playHorizon = Horizon.NEXT_TURN),
                "expertLight" to expert.copy(playSamples = 10, otherSamples = 4),
                "expert24" to expert.copy(playSamples = 24, otherSamples = 8),
                "expertRisk" to expert.copy(risk = 0.3),
                "expertWide" to expert.copy(candidates = 14),
                "hard8" to hard.copy(playSamples = 8, otherSamples = 3, greedySelf = true),
                "greedyAll" to hard.copy(greedyTeam = true, greedyEnemies = true),
            ) +
            AiLevel.entries.flatMap { level ->
                val p = AiPlayers.profile(level)
                val n = level.name.lowercase()
                listOf(
                    "${n}NoShallow" to p.copy(shallow = false),
                    "${n}NoPieces" to p.copy(knobs = p.knobs.copy(pieces = 0.0)),
                    "${n}Old" to p.copy(shallow = false, knobs = p.knobs.copy(pieces = 0.0)),
                )
            }

    @Test
    fun `variants against each other`() {
        val pairs = System.getenv("AI_EXPERIMENT")?.split(",") ?: return
        val seeds = System.getenv("AI_TOURNAMENT")?.toIntOrNull() ?: 2
        val first = System.getenv("AI_FIRST_SEED")?.toIntOrNull() ?: 0
        for (pair in pairs) {
            val (a, b) = pair.split(":")
            val pa = variants.getValue(a)
            val pb = variants.getValue(b)
            val t0 = System.nanoTime()
            // The levels only label the two sides; the profiles decide how they play.
            val factory = { level: AiLevel, seed: Long ->
                SearchAi(level, if (level == AiLevel.EXPERT) pa else pb, seed, timeCap = false)
            }
            val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 6
            val mixed = System.getenv("AI_MIXED")?.toIntOrNull()
            val score = if (mixed != null) {
                Tournament.runMixed(AiLevel.EXPERT, AiLevel.HARD, mixed, threads, first, factory)
            } else {
                Tournament.run(AiLevel.EXPERT, AiLevel.HARD, seeds, threads, first, factory)
            }
            println("EXPERIMENT $a vs $b: $score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
            if (mixed != null) println("EXPERIMENT by hero ($a): ${score.byHero()}")
        }
    }
}
