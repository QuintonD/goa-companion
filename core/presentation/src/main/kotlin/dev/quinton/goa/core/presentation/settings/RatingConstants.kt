package dev.quinton.goa.core.presentation.settings

import dev.quinton.goa.core.domain.rating.EloConfig
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One Elo constant on Settings → Ratings (ADR-011): what it's called, what it does, the range and
 * step the control offers, how its value reads, and how it is read from and written to an
 * [EloConfig]. The phone's sliders and the hub's range inputs are both built from
 * [RatingConstants.sections].
 */
data class RatingConstant(
    /** Stable form-field name. */
    val key: String,
    val title: String,
    val explanation: String?,
    val range: ClosedFloatingPointRange<Double>,
    val step: Double,
    val format: (Double) -> String,
    val get: (EloConfig) -> Double,
    val set: (EloConfig, Double) -> EloConfig,
) {
    /** [value] on this constant's grid (start + n × [step]) and inside its [range]. */
    fun snap(value: Double): Double {
        val clamped = value.coerceIn(range)
        return range.start + ((clamped - range.start) / step).roundToInt() * step
    }
}

/** A titled group of constants, with an optional line under the title. */
data class RatingSection(val title: String, val note: String?, val constants: List<RatingConstant>)

/** Settings → Ratings: every Elo constant with a plain-language explanation, as the app shows them. */
object RatingConstants {
    const val INTRO_TITLE = "How ratings work"

    const val INTRO = "Every hero seat counts. A team's strength is the average of its seats, each blending " +
        "its pilot's and " +
        "hero's ratings, so double pilots and uneven teams are rated fairly. " +
        "Players and heroes each get their own Elo."

    /** "Ratings are never stored: …" with the match counts of the current replay. */
    fun replayNote(rated: Int, skipped: Int): String =
        "Ratings are never stored: changing a value replays the whole match log " +
            "($rated rated ${if (rated == 1) "match" else "matches"}" +
            (if (skipped > 0) ", $skipped pending or without a result" else "") +
            "). Nothing is lost, and you can always reset."

    const val RESET_TITLE = "Reset rating settings?"
    const val RESET_TEXT =
        "All constants go back to their defaults and every rating is recalculated from the match log."

    private val heroInfluence = RatingConstant(
        key = "heroInfluence",
        title = "Hero influence",
        explanation = "How much a hero's rating shifts its pilot's strength in a match. Winning with a strong " +
            "hero earns the player a little less.",
        range = 0.0..1.0,
        step = 0.05,
        format = ::decimal2,
        get = { it.heroInfluence },
        set = { c, v -> c.copy(heroInfluence = v) },
    )

    private val pilotInfluence = RatingConstant(
        key = "pilotInfluence",
        title = "Pilot influence",
        explanation = "How much the pilot's rating shifts a hero's strength. A great player winning with a hero " +
            "earns the hero a little less.",
        range = 0.0..1.0,
        step = 0.05,
        format = ::decimal2,
        get = { it.pilotInfluence },
        set = { c, v -> c.copy(pilotInfluence = v) },
    )

    private val seatAdvantage = RatingConstant(
        key = "seatAdvantage",
        title = "Seat advantage",
        explanation = "Per hero of difference, the larger team's strength goes up by this much and the " +
            "smaller team's goes down by it, so the gap widens by 2× this. 0 trusts the Handicap cards to " +
            "balance it. Check the larger-team win rate on the Meta tab before changing it.",
        range = -100.0..100.0,
        step = 5.0,
        format = { "${signed(it.roundToInt())} pts" },
        get = { it.seatAdvantage },
        set = { c, v -> c.copy(seatAdvantage = v) },
    )

    private val doublePilotWeight = RatingConstant(
        key = "doublePilotWeight",
        title = "Double pilot weight",
        explanation = "A player controlling two heroes carried more of the result. Their change is multiplied " +
            "by 1 + weight for each extra hero (0.5 → ×1.5).",
        range = 0.0..2.0,
        step = 0.1,
        format = { "×${decimal2(1 + it)}" },
        get = { it.doublePilotWeight },
        set = { c, v -> c.copy(doublePilotWeight = v) },
    )

    private val kProvisional = RatingConstant(
        key = "kProvisional",
        title = "K while provisional",
        explanation = "Maximum points per match while a player or hero is still provisional, so new ratings " +
            "settle quickly.",
        range = 0.0..100.0,
        step = 5.0,
        format = ::whole,
        get = { it.kProvisional },
        set = { c, v -> c.copy(kProvisional = v) },
    )

    private val kEstablished = RatingConstant(
        key = "kEstablished",
        title = "K when established",
        explanation = "Maximum points per match afterwards.",
        range = 0.0..100.0,
        step = 5.0,
        format = ::whole,
        get = { it.kEstablished },
        set = { c, v -> c.copy(kEstablished = v) },
    )

    private val provisionalGames = RatingConstant(
        key = "provisionalGames",
        title = "Provisional games",
        explanation = "Rated games before a rating stops being provisional.",
        range = 0.0..30.0,
        step = 1.0,
        format = { games(it.roundToInt()) },
        get = { it.provisionalGames.toDouble() },
        set = { c, v -> c.copy(provisionalGames = v.roundToInt()) },
    )

    private fun multiplier(
        key: String,
        title: String,
        get: (EloConfig) -> Double,
        set: (EloConfig, Double) -> EloConfig,
    ) = RatingConstant(key, title, null, 0.0..3.0, 0.1, { "×${decimal2(it)}" }, get, set)

    private val initialRating = RatingConstant(
        key = "initialRating",
        title = "Starting rating",
        explanation = "Where every player and hero begins. Only shifts the scale; rankings stay the same.",
        range = 1000.0..2000.0,
        step = 50.0,
        format = ::whole,
        get = { it.initialRating },
        set = { c, v -> c.copy(initialRating = v) },
    )

    val sections: List<RatingSection> = listOf(
        RatingSection("Pilot and hero", null, listOf(heroInfluence, pilotInfluence)),
        RatingSection("Uneven teams", null, listOf(seatAdvantage, doublePilotWeight)),
        RatingSection("Speed of change", null, listOf(kProvisional, kEstablished, provisionalGames)),
        RatingSection(
            "Game modes",
            "Scale every rating change by game length. 1.0 treats all modes alike.",
            listOf(
                multiplier("quickMultiplier", "Quick", { it.quickMultiplier }) { c, v -> c.copy(quickMultiplier = v) },
                multiplier("longMultiplier", "Long", { it.longMultiplier }) { c, v -> c.copy(longMultiplier = v) },
                multiplier("doubleLaneMultiplier", "Double lane", { it.doubleLaneMultiplier }) { c, v ->
                    c.copy(doubleLaneMultiplier = v)
                },
            ),
        ),
        RatingSection("Starting point", null, listOf(initialRating)),
    )

    val all: List<RatingConstant> get() = sections.flatMap { it.constants }

    /**
     * The config a submitted form stands for: each value in [values] (by [RatingConstant.key])
     * snapped to its control's grid and range, as the app's sliders snap; a missing or unreadable
     * one keeps [current]'s. The store then sanitizes it, as the app's repository does.
     */
    fun apply(current: EloConfig, values: Map<String, String?>): EloConfig = all.fold(current) { config, c ->
        val value = values[c.key]?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@fold config
        c.set(config, c.snap(value))
    }

    /**
     * Like [apply], but only for the constants the person moved: a value in [submitted] counts as
     * changed when it snaps to another value than the one the form was shown with ([shown], same
     * keys). Every other constant keeps [current]'s, so a form opened before another device changed
     * a constant never writes that constant back (the app's sliders change one constant at a time).
     * A missing or unreadable shown value counts as changed.
     */
    fun applyChanged(current: EloConfig, shown: Map<String, String?>, submitted: Map<String, String?>): EloConfig =
        all.fold(current) { config, c ->
            val value = number(submitted[c.key]) ?: return@fold config
            val before = number(shown[c.key])
            if (before != null && c.snap(before) == c.snap(value)) config else c.set(config, c.snap(value))
        }

    private fun number(text: String?): Double? = text?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** "0.5", "1.0", "0.05": at most two decimals, at least one. */
    fun decimal2(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.').let { if ('.' !in it) "$it.0" else it }

    private fun whole(value: Double): String = value.roundToInt().toString()

    private fun signed(value: Int): String = when {
        value > 0 -> "+$value"
        value < 0 -> "−${-value}"
        else -> "0"
    }

    private fun games(n: Int): String = if (n == 1) "1 game" else "$n games"
}
