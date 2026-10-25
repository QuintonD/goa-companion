package dev.quinton.goa.core.data.trivia

import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.LeaderboardPeriod
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaLeaderboard
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.PrimaryAction
import kotlinx.serialization.json.Json

/**
 * Labels and sentences of the card-trivia screens (ADR-028, ADR-032), in the rulebook's words, shared
 * by the phone and the web app so both say the same. No game data lives here.
 */
object TriviaText {
    fun stat(stat: TriviaStat): String = when (stat) {
        TriviaStat.INITIATIVE -> "Initiative"
        TriviaStat.ATTACK -> "Attack"
        TriviaStat.DEFENSE -> "Defense"
        TriviaStat.MOVEMENT -> "Movement"
        TriviaStat.RANGE -> "Range"
        TriviaStat.RADIUS -> "Radius"
    }

    fun group(group: CardGroup): String = when (group) {
        CardGroup.BASIC -> "Basic"
        CardGroup.TIER_1 -> "Tier I"
        CardGroup.TIER_2 -> "Tier II"
        CardGroup.TIER_3 -> "Tier III"
        CardGroup.ULTIMATE -> "Ultimate"
    }

    fun length(length: Int): String = if (length == TriviaConfig.ENDLESS) "Endless" else "$length"

    /** A printed number as on the card: "4", or "4+" for a `+` value. */
    fun value(value: Int, plus: Boolean): String = if (plus) "$value+" else "$value"

    /** How a screen reader reads [value]: "4 plus". */
    fun spokenValue(value: Int, plus: Boolean): String = if (plus) "$value plus" else "$value"

    /** The card colour's name, as the rest of the app names it ("Gold", "Purple"). */
    fun colorName(color: CardColor): String = when (color) {
        CardColor.GOLD -> "Gold"
        CardColor.SILVER -> "Silver"
        CardColor.RED -> "Red"
        CardColor.GREEN -> "Green"
        CardColor.BLUE -> "Blue"
        CardColor.PURPLE -> "Purple"
        CardColor.OTHER -> "Other"
    }

    /** "Gold · Basic", "Red · Tier II", "Purple · Ultimate". */
    fun cardLabel(card: Card): String {
        val color = if (card.color == CardColor.OTHER) "Special" else colorName(card.color)
        val tier = when (card.tier) {
            null -> "Basic"
            1 -> "Tier I"
            2 -> "Tier II"
            3 -> "Tier III"
            4 -> "Ultimate"
            else -> null
        }
        return listOfNotNull(color, tier).joinToString(" · ")
    }

    /** "Attack card", or null when the card's primary action is unknown. */
    fun primary(action: PrimaryAction?): String? = when (action) {
        PrimaryAction.ATTACK -> "Attack card"
        PrimaryAction.SKILL -> "Skill card"
        PrimaryAction.DEFENSE -> "Defense card"
        PrimaryAction.MOVEMENT -> "Movement card"
        PrimaryAction.DEFENSE_SKILL -> "Defense/Skill card"
        null -> null
    }

    /** "1 number", "3 numbers". */
    fun count(n: Int, one: String, many: String = "${one}s"): String = "${number(n)} ${if (n == 1) one else many}"

    /** "2,150": grouped digits in the device's locale. */
    fun number(n: Int): String = TriviaLocale.number(n)

    /** "1st", "2nd", "3rd", "11th", "22nd". */
    fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /** A Challenge answer time as the board ranks it, in tenths: "1:42.3". */
    fun answerTime(millis: Long): String {
        val tenths = TriviaLeaderboard.tenths(millis.coerceAtLeast(0))
        return "${tenths / TENTHS_PER_MINUTE}:${two(tenths / 10 % 60)}.${tenths % 10}"
    }

    /** How a screen reader reads [answerTime]: "1 minute 42.3 seconds". */
    fun spokenAnswerTime(millis: Long): String {
        val tenths = TriviaLeaderboard.tenths(millis.coerceAtLeast(0))
        val minutes = tenths / TENTHS_PER_MINUTE
        val seconds = "${tenths / 10 % 60}.${tenths % 10}"
        return listOfNotNull(
            minutes.takeIf { it > 0 }?.let { if (it == 1L) "1 minute" else "$it minutes" },
            "$seconds seconds",
        ).joinToString(" ")
    }

    /** A practice round's time to the second: "4:05", "1:02:03" (the app's duration format). */
    fun roundTime(millis: Long): String {
        val total = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
        val h = total / SECONDS_PER_HOUR
        val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val s = total % SECONDS_PER_MINUTE
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }

    /** How a screen reader reads [roundTime]: "4 minutes 5 seconds". */
    fun spokenRoundTime(millis: Long): String {
        val total = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
        val h = total / SECONDS_PER_HOUR
        val m = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val s = total % SECONDS_PER_MINUTE
        fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
        return buildList {
            if (h > 0) add(plural(h, "hour"))
            if (m > 0) add(plural(m, "minute"))
            if (s > 0 || isEmpty()) add(plural(s, "second"))
        }.joinToString(" ")
    }

    private fun two(n: Long): String = n.toString().padStart(2, '0')

    private const val TENTHS_PER_MINUTE = 600
    private const val MILLIS_PER_SECOND = 1000
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    /** "2nd", or "Joint 2nd" when another player shares it. */
    fun place(place: Int, tied: Boolean): String = if (tied) "Joint ${ordinal(place)}" else ordinal(place)

    /** Lenient: a config written by an older or newer build still opens (unknown keys, bad values). */
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    fun encode(config: TriviaConfig): String = json.encodeToString(TriviaConfig.serializer(), config)

    fun decode(raw: String): TriviaConfig =
        runCatching { json.decodeFromString(TriviaConfig.serializer(), raw) }.getOrDefault(TriviaConfig())

    // ---- Sentences the screens show -----------------------------------------------------------

    /** The Challenge explained in one line, beside its button. */
    const val CHALLENGE_LINE = "Challenge: 20 questions on every hero — scores go on the leaderboard."

    /** After a card or hero's name for a screen reader, when its data isn't verified. */
    const val UNVERIFIED_SPOKEN = "unverified data"

    /** ", unverified data" after a card or hero a screen reader names, when its data isn't verified. */
    fun spokenUnverified(verified: Boolean): String = if (verified) "" else ", $UNVERIFIED_SPOKEN"

    /** "Mad Dash, Arien's Red Tier II Attack card": how a screen reader names a card. */
    fun spokenCard(card: TriviaCardUi): String =
        "${card.name}, ${card.heroName}'s ${
            listOfNotNull(card.label.replace(" · ", " ").ifEmpty { null }, card.primary ?: "card").joinToString(" ")
        }${spokenUnverified(card.heroVerified)}"

    /** "Arien's Mad Dash", and ", unverified data" when the hero's data isn't verified. */
    fun spokenOwned(card: TriviaCardUi): String =
        "${card.heroName}'s ${card.name}${spokenUnverified(card.heroVerified)}"

    /**
     * When the player isn't this device's "You": whose learning progress this is. Null when they
     * are, or when no "You" is set (then anyone's rounds teach this device).
     */
    fun playingAsNote(playing: PlayingAsState): String? {
        val me = playing.me ?: return null
        return if (playing.player?.id == me.id) null else "Learning progress on this phone stays ${me.name}'s."
    }

    /** Who is playing, on the quiz and its summary: "as Sam", or "as Guest · not on the leaderboard". */
    fun playingAsLine(playerName: String?): String =
        if (playerName == null) "as Guest · not on the leaderboard" else "as $playerName"

    /**
     * Why Start is off ("No Ultimate card of the heroes you own prints Initiative."), or a warning
     * for a pool smaller than the round (questions repeat). The claim comes from the counted facts of
     * the quiz's own scope (chosen heroes, owned heroes or every hero) and names that scope.
     */
    fun startNote(state: TriviaHomeUiState.Ready): String? {
        val setup = state.setup
        val total = state.selection.total
        return when {
            total == 0 -> {
                val groups = or(CardGroup.entries.filter { it in setup.groups }.map(::group))
                val stats = or(TriviaStat.entries.filter { it in setup.stats }.map(::stat))
                val scope = when {
                    setup.heroIds.size == 1 -> " of the chosen hero"
                    setup.heroIds.isNotEmpty() -> " of the chosen heroes"
                    state.ownedOnly -> " of the heroes you own"
                    else -> ""
                }
                "No $groups card$scope prints $stats. Add a number or more cards."
            }

            setup.length != TriviaConfig.ENDLESS && total < setup.length ->
                "Only ${count(total, "number")} for ${setup.length} questions, so some come up more than once."

            else -> null
        }
    }

    /**
     * What a Challenge's summary says about the board: "2nd of 5 · your best 18/20", "New best! ·
     * 1st of 3", why it isn't on it, or null while the place is still being worked out.
     */
    fun challengeLine(state: TriviaQuizUiState.Summary): String? {
        val challenge = state.challenge ?: return null
        return when {
            !challenge.finished -> "Unfinished challenges don't go on the leaderboard."

            // Guest: the "as Guest · not on the leaderboard" line above says it.
            state.playerName == null -> null

            else -> challenge.standing?.let { standing ->
                val place = place(standing.place, standing.tied) + " of ${standing.of}"
                if (challenge.newBest) {
                    "New best! · $place"
                } else {
                    "$place · your best ${standing.best.correct}/${standing.best.questions}"
                }
            }
        }
    }

    fun tabLabel(tab: LeaderboardTab): String = when (tab) {
        LeaderboardTab.CHALLENGE -> "Challenge"
        LeaderboardTab.PRACTICE -> "Practice"
    }

    fun periodLabel(period: LeaderboardPeriod): String = when (period) {
        LeaderboardPeriod.ALL_TIME -> "All time"
        LeaderboardPeriod.LAST_30_DAYS -> "Last ${TriviaLeaderboard.RECENT_DAYS} days"
    }

    /** "Tier I, Tier II or Ultimate". */
    private fun or(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items.single()
        else -> items.dropLast(1).joinToString(", ") + " or " + items.last()
    }
}

/** A card as the quiz shows it: whose it is, its name, colour and tier, and its official image. */
data class TriviaCardUi(
    val heroId: String,
    val heroName: String,
    val portrait: String?,
    val cardId: String,
    val name: String,
    val color: CardColor,
    /** "Red · Tier II". */
    val label: String,
    /** "Attack card", or null. */
    val primary: String?,
    /** Bundled asset path of the official card image, or null. */
    val image: String?,
    /** False when the hero's data isn't verified yet: the quiz marks it (AGENTS.md). */
    val heroVerified: Boolean = true,
) {
    companion object {
        fun of(hero: Hero, card: Card) = TriviaCardUi(
            heroId = hero.id,
            heroName = hero.name,
            portrait = hero.portrait,
            cardId = card.id,
            name = card.name,
            color = card.color,
            label = TriviaText.cardLabel(card),
            primary = TriviaText.primary(card.primary),
            image = card.image,
            heroVerified = hero.verified,
        )
    }
}

/**
 * How numbers and dates read in the device's locale: the phone's `NumberFormat` / `java.time`, the
 * browser's `Intl`. Display only; nothing ranks or stores these strings.
 */
internal expect object TriviaLocale {
    /** "2,150". */
    fun number(n: Int): String

    /** "3 Oct" in [now]'s year, else "3 Oct 2025". */
    fun shortDate(at: Long, now: Long): String

    /** "3 October 2026". */
    fun spokenDate(at: Long): String
}
