package dev.quinton.goa.feature.trivia

import androidx.compose.runtime.Immutable
import dev.quinton.goa.core.designsystem.component.cardColorName
import dev.quinton.goa.core.domain.trivia.CardGroup
import dev.quinton.goa.core.domain.trivia.TriviaConfig
import dev.quinton.goa.core.domain.trivia.TriviaLeaderboard
import dev.quinton.goa.core.domain.trivia.TriviaStat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.PrimaryAction
import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.json.Json

/** Labels for the trivia screens, in the rulebook's words. No game data lives here. */
internal object TriviaText {
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

    /** How TalkBack reads [value]: "4 plus". */
    fun spokenValue(value: Int, plus: Boolean): String = if (plus) "$value plus" else "$value"

    /** "Gold · Basic", "Red · Tier II", "Purple · Ultimate". */
    fun cardLabel(card: Card): String {
        val color = if (card.color == CardColor.OTHER) "Special" else cardColorName(card.color)
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

    /** "2,150": grouped digits in the phone's locale. */
    fun number(n: Int): String = NumberFormat.getIntegerInstance().format(n)

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
        return String.format(Locale.ROOT, "%d:%02d.%d", tenths / TENTHS_PER_MINUTE, tenths / 10 % 60, tenths % 10)
    }

    /** How TalkBack reads [answerTime]: "1 minute 42.3 seconds". */
    fun spokenAnswerTime(millis: Long): String {
        val tenths = TriviaLeaderboard.tenths(millis.coerceAtLeast(0))
        val minutes = tenths / TENTHS_PER_MINUTE
        val seconds = String.format(Locale.ROOT, "%d.%d", tenths / 10 % 60, tenths % 10)
        return listOfNotNull(
            minutes.takeIf { it > 0 }?.let { if (it == 1L) "1 minute" else "$it minutes" },
            "$seconds seconds",
        ).joinToString(" ")
    }

    private const val TENTHS_PER_MINUTE = 600

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
}

/** A card as the quiz shows it: whose it is, its name, colour and tier, and its official image. */
@Immutable
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
