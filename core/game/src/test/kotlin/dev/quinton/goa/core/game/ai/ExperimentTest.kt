package dev.quinton.goa.core.game.ai

import dev.quinton.goa.core.game.AiLevel
import org.junit.Test

/**
 * Opt-in tuning experiments: `AI_EXPERIMENT=a:b,c:d` plays profile variant a against b, mirrored, on
 * `AI_MIXED` mixed line-ups from `AI_FIRST_SEED` (or `AI_TOURNAMENT` first-game seeds). A variant is
 * a strength (`s1`…`s10`) or a level (`hard`), optionally followed by overrides:
 * `s8+samples=24+next=0.3+xEXPOSURE=0.8` (see [override] for the keys). `AI_FORMAT=3v3` (or `4v4`,
 * `4v4river`, `5v5river`) plays bigger teams ([AiHarness.Format]).
 */
class ExperimentTest {
    @Test
    fun `variants against each other`() {
        val pairs = System.getenv("AI_EXPERIMENT")?.split(",") ?: return
        val seeds = System.getenv("AI_TOURNAMENT")?.toIntOrNull() ?: 2
        val first = System.getenv("AI_FIRST_SEED")?.toIntOrNull() ?: 0
        val threads = System.getenv("AI_THREADS")?.toIntOrNull() ?: 4
        val mixed = System.getenv("AI_MIXED")?.toIntOrNull()
        val format = AiHarness.Format.parse(System.getenv("AI_FORMAT"))
        for (pair in pairs) {
            val (a, b) = pair.split(":")
            val sa = side(a)
            val sb = side(b)
            val t0 = System.nanoTime()
            val score = if (mixed != null) {
                Tournament.runMixed(sa, sb, mixed, threads, first, format)
            } else {
                Tournament.run(sa, sb, seeds, threads, first)
            }
            println("EXPERIMENT [$format] $score in ${"%.0f".format((System.nanoTime() - t0) / 1e9)} s")
            println("EXPERIMENT   ${score.details()}")
            if (mixed != null) println("EXPERIMENT by hero ($a): ${score.byHero()}")
        }
    }

    companion object {
        fun side(spec: String): Tournament.Side {
            val p = variant(spec)
            val level = AiLevel.entries.firstOrNull { it.name.equals(spec.substringBefore("+"), true) }
            val strength = spec.substringBefore("+").removePrefix("s").toIntOrNull() ?: level!!.strength
            return Tournament.Side(spec) { seed -> SearchAi(AiLevel.of(strength), p, seed, timeCap = false) }
        }

        internal fun variant(spec: String): LevelProfile {
            val parts = spec.split("+")
            val base = parts[0]
            val level = AiLevel.entries.firstOrNull { it.name.equals(base, true) }
            var p = if (level != null) AiPlayers.profile(level) else AiPlayers.profile(base.removePrefix("s").toInt())
            for (o in parts.drop(1)) {
                val (k, v) = o.split("=")
                p = override(p, k, v)
            }
            return p
        }

        @Suppress("CyclomaticComplexMethod")
        private fun override(p: LevelProfile, k: String, v: String): LevelProfile {
            val kn = p.knobs
            // x<TERM>=<scale>, e.g. xEXPOSURE=0.8 (an [EvalKnobs.scales] entry).
            if (k.startsWith("x")) {
                return p.copy(knobs = kn.copy(scales = kn.scales + (Term.valueOf(k.drop(1)) to v.toDouble())))
            }
            return when (k) {
                "samples" -> p.copy(playSamples = v.toInt())

                "other" -> p.copy(otherSamples = v.toInt())

                "cand" -> p.copy(candidates = v.toInt())

                "temp" -> p.copy(temperature = v.toDouble())

                "gap" -> p.copy(maxGap = v.toDouble())

                "slip" -> p.copy(slipRate = v.toDouble())

                "blind" -> p.copy(blindRate = v.toDouble())

                "risk" -> p.copy(risk = v.toDouble())

                "memory" -> p.copy(memory = v.toBoolean())

                "model" -> p.copy(modelPlays = v.toBoolean())

                "static" -> p.copy(staticOnly = v.toBoolean())

                "greedySelf" -> p.copy(greedySelf = v.toBoolean())

                "greedyTeam" -> p.copy(greedyTeam = v.toBoolean())

                "greedyEnemies" -> p.copy(greedyEnemies = v.toBoolean())

                "shallow" -> p.copy(shallow = v.toBoolean())

                "horizon" -> p.copy(playHorizon = Horizon.valueOf(v))

                "otherHorizon" -> p.copy(otherHorizon = Horizon.valueOf(v))

                "nearest" -> p.copy(knobs = kn.copy(nearestThreatOnly = v.toBoolean()))

                "oneCard" -> p.copy(knobs = kn.copy(oneCardPerTurn = v.toBoolean()))

                "focus" -> p.copy(knobs = kn.copy(focusFire = v.toBoolean()))

                "tau" -> p.copy(opponentTau = v.toDouble())

                "upgrade" -> p.copy(upgradeHorizon = Horizon.valueOf(v))

                "greedyMax" -> p.copy(greedyMax = v.toInt())

                "next" -> p.copy(knobs = kn.copy(nextTurnThreat = v.toDouble()))

                "pieces" -> p.copy(knobs = kn.copy(pieces = v.toDouble()))

                "presence" -> p.copy(knobs = kn.copy(presencePerSpace = v.toDouble()))

                "inZone" -> p.copy(knobs = kn.copy(inBattleZone = v.toDouble()))

                "now" -> p.copy(knobs = kn.copy(nowWeight = v.toDouble()))

                "lane" -> p.copy(knobs = kn.copy(laneWeight = v.toDouble()))

                "margin" -> p.copy(knobs = kn.copy(minionMargin = v.toDouble()))

                "pp" -> p.copy(knobs = kn.copy(pushPerAttack = v.toDouble()))

                "work" -> p.copy(workBudget = v.toLong())

                "race" -> p.copy(race = v.toDouble())

                "tempo" -> p.copy(knobs = kn.copy(killTempo = v.toDouble()))

                "hand" -> p.copy(knobs = kn.copy(handRating = v.toDouble()))

                // The exploiter (AI.md "Team sizes"): hunt=selfExposure/enemyExposure/enemyLife, e.g. hunt=0/2/1.5.
                "hunt" -> v.split("/").map { it.toDouble() }.let { (se, ee, el) ->
                    p.copy(hunt = Hunt(selfExposure = se, enemyExposure = ee, enemyLife = el))
                }

                else -> error("Unknown override $k")
            }
        }
    }
}
