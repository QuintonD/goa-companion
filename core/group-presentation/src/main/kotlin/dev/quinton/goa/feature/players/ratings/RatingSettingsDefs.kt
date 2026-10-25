package dev.quinton.goa.feature.players.ratings

import dev.quinton.goa.core.domain.rating.EloConfig
import dev.quinton.goa.feature.players.common.plural
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One Elo constant on Settings → Ratings: what it is called and means, its slider and its value's words. */
class RatingSetting(
    val title: String,
    val explanation: String?,
    val min: Double,
    val max: Double,
    val step: Double,
    val format: (Double) -> String,
    val get: (EloConfig) -> Double,
    val set: (EloConfig, Double) -> EloConfig,
) {
    /** [value] on the slider's grid (the phone's slider snaps the same way). */
    fun snap(value: Double): Double = min + ((value - min) / step).roundToInt() * step
}

/** A group of settings under a section header, with an optional note under it. */
class RatingSettingSection(val title: String, val note: String?, val settings: List<RatingSetting>)

/**
 * Settings → Ratings (ADR-011): every Elo constant with its plain-language explanation, the same on
 * the phone and in the web app (ADR-043).
 */
object RatingSettingsText {
    const val INTRO_TITLE = "How ratings work"
    const val INTRO =
        "Every hero seat counts. A team's strength is the average of its seats, each blending its pilot's and " +
            "hero's ratings, so double pilots and uneven teams are rated fairly. Players and heroes each get " +
            "their own Elo."
    const val RESET_TITLE = "Reset rating settings?"
    const val RESET_MESSAGE =
        "All constants go back to their defaults and every rating is recalculated from the match log."

    /** "Ratings are never stored: …(12 rated matches, 2 pending or without a result)…". */
    fun replayNote(rated: Int, skipped: Int): String =
        "Ratings are never stored: changing a value replays the whole match log " +
            "($rated rated ${if (rated == 1) "match" else "matches"}" +
            (if (skipped > 0) ", $skipped pending or without a result" else "") +
            "). Nothing is lost, and you can always reset."

    fun resetLabel(isDefault: Boolean) = if (isDefault) "Using default settings" else "Reset to defaults"

    val sections: List<RatingSettingSection> = listOf(
        RatingSettingSection(
            "Pilot and hero",
            null,
            listOf(
                RatingSetting(
                    "Hero influence",
                    "How much a hero's rating shifts its pilot's strength in a match. Winning with a strong " +
                        "hero earns the player a little less.",
                    0.0,
                    1.0,
                    0.05,
                    ::decimal2,
                    { it.heroInfluence },
                    { c, v -> c.copy(heroInfluence = v) },
                ),
                RatingSetting(
                    "Pilot influence",
                    "How much the pilot's rating shifts a hero's strength. A great player winning with a hero " +
                        "earns the hero a little less.",
                    0.0,
                    1.0,
                    0.05,
                    ::decimal2,
                    { it.pilotInfluence },
                    { c, v -> c.copy(pilotInfluence = v) },
                ),
            ),
        ),
        RatingSettingSection(
            "Uneven teams",
            null,
            listOf(
                RatingSetting(
                    "Seat advantage",
                    "Per hero of difference, the larger team's strength goes up by this much and the " +
                        "smaller team's goes down by it, so the gap widens by 2× this. 0 trusts the Handicap " +
                        "cards to balance it. Check the larger-team win rate on the Meta tab before changing it.",
                    -100.0,
                    100.0,
                    5.0,
                    { "${signed(it.roundToInt())} pts" },
                    { it.seatAdvantage },
                    { c, v -> c.copy(seatAdvantage = v) },
                ),
                RatingSetting(
                    "Double pilot weight",
                    "A player controlling two heroes carried more of the result. Their change is multiplied " +
                        "by 1 + weight for each extra hero (0.5 → ×1.5).",
                    0.0,
                    2.0,
                    0.1,
                    { "×${decimal2(1 + it)}" },
                    { it.doublePilotWeight },
                    { c, v -> c.copy(doublePilotWeight = v) },
                ),
            ),
        ),
        RatingSettingSection(
            "Speed of change",
            null,
            listOf(
                RatingSetting(
                    "K while provisional",
                    "Maximum points per match while a player or hero is still provisional, so new ratings " +
                        "settle quickly.",
                    0.0,
                    100.0,
                    5.0,
                    { it.roundToInt().toString() },
                    { it.kProvisional },
                    { c, v -> c.copy(kProvisional = v) },
                ),
                RatingSetting(
                    "K when established",
                    "Maximum points per match afterwards.",
                    0.0,
                    100.0,
                    5.0,
                    { it.roundToInt().toString() },
                    { it.kEstablished },
                    { c, v -> c.copy(kEstablished = v) },
                ),
                RatingSetting(
                    "Provisional games",
                    "Rated games before a rating stops being provisional.",
                    0.0,
                    30.0,
                    1.0,
                    { plural(it.roundToInt(), "game") },
                    { it.provisionalGames.toDouble() },
                    { c, v -> c.copy(provisionalGames = v.roundToInt()) },
                ),
            ),
        ),
        RatingSettingSection(
            "Game modes",
            "Scale every rating change by game length. 1.0 treats all modes alike.",
            listOf(
                multiplier("Quick", { it.quickMultiplier }) { c, v -> c.copy(quickMultiplier = v) },
                multiplier("Long", { it.longMultiplier }) { c, v -> c.copy(longMultiplier = v) },
                multiplier("Double lane", { it.doubleLaneMultiplier }) { c, v -> c.copy(doubleLaneMultiplier = v) },
            ),
        ),
        RatingSettingSection(
            "Starting point",
            null,
            listOf(
                RatingSetting(
                    "Starting rating",
                    "Where every player and hero begins. Only shifts the scale; rankings stay the same.",
                    1000.0,
                    2000.0,
                    50.0,
                    { it.roundToInt().toString() },
                    { it.initialRating },
                    { c, v -> c.copy(initialRating = v) },
                ),
            ),
        ),
    )

    private fun multiplier(title: String, get: (EloConfig) -> Double, set: (EloConfig, Double) -> EloConfig) =
        RatingSetting(title, null, 0.0, 3.0, 0.1, { "×${decimal2(it)}" }, get, set)

    /** At most two decimals, always one: 0.5 → "0.5", 1 → "1.0", 0.25 → "0.25" (as `%.2f` trimmed). */
    fun decimal2(value: Double): String {
        val hundredths = (abs(value) * HUNDRED).roundToLong()
        val whole = hundredths / 100
        val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
        val sign = if (value < 0 && hundredths != 0L) "-" else ""
        return "$sign$whole.${fraction.ifEmpty { "0" }}"
    }

    fun signed(value: Int): String = when {
        value > 0 -> "+$value"
        value < 0 -> "−${-value}"
        else -> "0"
    }

    private const val HUNDRED = 100.0
}
