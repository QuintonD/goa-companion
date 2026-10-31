package dev.quinton.goa.core.game.ai

/**
 * The team's chance of winning from the hand evaluation with the coach's knobs ([AiPlayers.TUNED]),
 * fitted by Platt scaling on 149 702 turn boundaries of 6 399 stored self-play games and checked on
 * held-out line-ups (AI.md "The coach", `CalibrationTest`). The old σ(score / 15) said 96% where 81% won.
 * A slope per stage of the game gained nothing held out. Self-play strength: a chance, not a promise.
 */
internal object WinChance {
    private const val SLOPE_2V2 = 0.0294
    private const val SLOPE_3V3 = 0.0258
    private const val SLOPE_4V4 = 0.0214
    private const val SLOPE_5V5 = 0.0204

    /** A finished game in a playout scores [Evaluator.WIN] less the round. */
    private const val DECIDED = 900.0

    /** Held-out reliability is within about 5 points, so nothing finer is shown. */
    const val ROUNDED_TO: Int = 5

    private fun slope(heroesPerSide: Int): Double = when {
        heroesPerSide <= 2 -> SLOPE_2V2
        heroesPerSide == 3 -> SLOPE_3V3
        heroesPerSide == 4 -> SLOPE_4V4
        else -> SLOPE_5V5
    }

    fun of(score: Double, heroesPerSide: Int): Double = when {
        score >= DECIDED -> 1.0
        score <= -DECIDED -> 0.0
        else -> Evaluator.sigmoid(slope(heroesPerSide) * score)
    }

    /** 0.63 → 65. Never 0 or 100 unless every sample finished the game that way. */
    fun percent(chance: Double): Int {
        if (chance >= 1.0) return 100
        if (chance <= 0.0) return 0
        return rounded(100 * chance).coerceIn(ROUNDED_TO, 100 - ROUNDED_TO)
    }

    private fun rounded(x: Double): Int = (x / ROUNDED_TO + 0.5).toInt() * ROUNDED_TO
}
