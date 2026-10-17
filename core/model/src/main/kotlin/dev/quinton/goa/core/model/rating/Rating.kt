package dev.quinton.goa.core.model.rating

/** A derived rating; always recomputable from the match log (ADR-003). */
data class Rating(
    val value: Double,
    val games: Int,
    val wins: Int,
    val provisional: Boolean,
) {
    val losses: Int get() = games - wins

    companion object {
        const val START = 1200.0
        val UNRATED = Rating(value = START, games = 0, wins = 0, provisional = true)
    }
}

/** One point on a rating history chart: the rating after [matchId] was replayed. */
data class RatingPoint(
    val matchId: String,
    val playedAt: Long,
    val value: Double,
    val delta: Double,
)

/** Usage and results for one hero across the match log (and drafts, for bans). */
data class HeroUsage(
    val heroId: String,
    val matches: Int,
    val wins: Int,
    val bans: Int,
    val pickRate: Double,
    val winRate: Double,
    val banRate: Double,
)
