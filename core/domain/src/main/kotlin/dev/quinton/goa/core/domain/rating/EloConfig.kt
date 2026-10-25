// Doubles as the JVM writes them, so the browser's tie-break text is the phones' (ADR-043).
@file:UseSerializers(CanonicalDoubleSerializer::class)

package dev.quinton.goa.core.domain.rating

import dev.quinton.goa.core.domain.sync.CanonicalDoubleSerializer
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.rating.Rating
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * Every tunable constant of the seat-based Elo (docs/DOMAIN.md *Elo*, ADR-011). Editable under
 * Settings → Ratings; changing any value recomputes all ratings from the match log.
 *
 * Plain team Elo is recovered with `heroInfluence = pilotInfluence = seatAdvantage =
 * doublePilotWeight = 0`.
 */
@Serializable
data class EloConfig(
    /** Rating every player and hero starts at; also the neutral point of the influence terms. */
    val initialRating: Double = Rating.START,
    /** K-factor while a player/hero has fewer than [provisionalGames] rated games. */
    val kProvisional: Double = 40.0,
    /** K-factor once established. */
    val kEstablished: Double = 20.0,
    /** Rated games needed before a rating stops being provisional. */
    val provisionalGames: Int = 10,
    /** Player space: how much the hero's rating shifts a seat's strength. */
    val heroInfluence: Double = 0.5,
    /** Hero space: how much the pilot's rating shifts a seat's strength. */
    val pilotInfluence: Double = 0.5,
    /**
     * Uneven seat counts: each team's strength moves by this much per seat of difference (the
     * larger team up, the smaller team down), so the gap between them widens by 2× this per seat.
     * 0 = trust the Handicap card.
     */
    val seatAdvantage: Double = 0.0,
    /** Extra weight per additional seat a double pilot controls: `w = 1 + weight · (seats − 1)`. */
    val doublePilotWeight: Double = 0.5,
    val quickMultiplier: Double = 1.0,
    val longMultiplier: Double = 1.0,
    val doubleLaneMultiplier: Double = 1.0,
) {
    fun multiplier(mode: GameMode): Double = when (mode) {
        GameMode.QUICK -> quickMultiplier
        GameMode.LONG -> longMultiplier
        GameMode.DOUBLE_LANE -> doubleLaneMultiplier
    }

    fun k(ratedGames: Int): Double = if (ratedGames < provisionalGames) kProvisional else kEstablished

    fun isProvisional(ratedGames: Int): Boolean = ratedGames < provisionalGames

    /** Clamps every value into its allowed range (see [Limits]); used before saving user input. */
    fun sanitized(): EloConfig = EloConfig(
        initialRating = initialRating.clampOr(Limits.INITIAL_RATING, Rating.START),
        kProvisional = kProvisional.clampOr(Limits.K, DEFAULT.kProvisional),
        kEstablished = kEstablished.clampOr(Limits.K, DEFAULT.kEstablished),
        provisionalGames = provisionalGames.coerceIn(Limits.PROVISIONAL_GAMES),
        heroInfluence = heroInfluence.clampOr(Limits.INFLUENCE, DEFAULT.heroInfluence),
        pilotInfluence = pilotInfluence.clampOr(Limits.INFLUENCE, DEFAULT.pilotInfluence),
        seatAdvantage = seatAdvantage.clampOr(Limits.SEAT_ADVANTAGE, DEFAULT.seatAdvantage),
        doublePilotWeight = doublePilotWeight.clampOr(Limits.DOUBLE_PILOT_WEIGHT, DEFAULT.doublePilotWeight),
        quickMultiplier = quickMultiplier.clampOr(Limits.MODE_MULTIPLIER, 1.0),
        longMultiplier = longMultiplier.clampOr(Limits.MODE_MULTIPLIER, 1.0),
        doubleLaneMultiplier = doubleLaneMultiplier.clampOr(Limits.MODE_MULTIPLIER, 1.0),
    )

    /** Allowed ranges for user-edited values. */
    object Limits {
        val INITIAL_RATING = 0.0..3000.0
        val K = 0.0..100.0
        val PROVISIONAL_GAMES = 0..50
        val INFLUENCE = 0.0..1.0
        val SEAT_ADVANTAGE = -200.0..200.0
        val DOUBLE_PILOT_WEIGHT = 0.0..2.0
        val MODE_MULTIPLIER = 0.0..3.0
    }

    companion object {
        val DEFAULT = EloConfig()

        /** Elo's logistic scale: a 400-point gap means 10:1 expected odds. */
        const val SCALE = 400.0

        private fun Double.clampOr(range: ClosedFloatingPointRange<Double>, fallback: Double): Double =
            if (isNaN()) fallback else coerceIn(range)
    }
}
