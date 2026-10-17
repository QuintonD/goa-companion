package dev.quinton.goa.tools.catalogimport

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardValues
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.catalog.SecondaryAction
import dev.quinton.goa.core.model.catalog.Stat

/** A source card together with its permanent catalog ID and where it goes in the hero. */
data class MappedCard(val card: Card, val slot: Slot, val source: SoaCard, val artSlug: String?)

enum class Slot { DECK, HANDICAP, EXTRA }

/**
 * Maps Stats of Atlantis cards to catalog [Card]s. Card IDs are permanent:
 * `<hero>-gold`, `<hero>-silver`, `<hero>-<colour>-1`, `<hero>-<colour>-<2|3><a|b>`,
 * `<hero>-purple-4`, `<hero>-handicap`, extra deck cards `<hero>-<colour>-extra-<n>` and
 * spellbook pages `<hero>-spell-<name-slug>`.
 */
object CardMapping {
    fun slug(text: String): String = text.lowercase()
        .replace("'", "").replace("’", "")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun colourName(color: String): String = color.lowercase()

    fun cardId(heroId: String, card: SoaCard, extraIndex: Int? = null): String = when {
        card.handicapped -> "$heroId-handicap"

        card.extra -> "$heroId-${colourName(card.color)}-extra-${requireNotNull(extraIndex) { "extra index" }}"

        card.color == "GOLD" -> "$heroId-gold"

        card.color == "SILVER" -> "$heroId-silver"

        card.color == "PURPLE" -> "$heroId-purple-4"

        else -> {
            val level = requireNotNull(card.level) { "${card.name}: coloured card without level" }
            when (level) {
                1 -> "$heroId-${colourName(card.color)}-1"
                2, 3 -> "$heroId-${colourName(card.color)}-$level${variantLetter(card)}"
                else -> error("${card.name}: unexpected level $level")
            }
        }
    }

    private fun variantLetter(card: SoaCard): Char = when (card.variant?.first) {
        1 -> 'a'
        2 -> 'b'
        else -> error("${card.name}: level ${card.level} card without variant 1/2")
    }

    fun spellId(heroId: String, spell: SoaCard): String = "$heroId-spell-${slug(spell.name)}"

    /** File name (without `.webp`) of the card's artwork in Stats of Atlantis. */
    fun artSlug(card: SoaCard, extraIndex: Int? = null): String? {
        val prefix = when (card.color) {
            "RED" -> "Red"
            "BLUE" -> "Blue"
            "GREEN" -> "Green"
            else -> null
        }
        return when {
            card.school != null -> null
            card.extra -> "Extra$extraIndex"
            card.handicapped -> "Handicap"
            card.color == "GOLD" -> "Gold"
            card.color == "SILVER" -> "Silver"
            card.color == "PURPLE" -> "Ultimate"
            prefix != null && card.level == 1 -> "${prefix}IA"
            prefix != null && card.level == 2 -> "${prefix}II${variantLetter(card).uppercaseChar()}"
            prefix != null && card.level == 3 -> "${prefix}III${variantLetter(card).uppercaseChar()}"
            else -> null
        }
    }

    fun color(card: SoaCard): CardColor = when {
        card.handicapped || card.school != null -> CardColor.OTHER

        else -> when (card.color) {
            "GOLD" -> CardColor.GOLD
            "SILVER" -> CardColor.SILVER
            "RED" -> CardColor.RED
            "GREEN" -> CardColor.GREEN
            "BLUE" -> CardColor.BLUE
            "PURPLE" -> CardColor.PURPLE
            else -> error("Unknown colour ${card.color}")
        }
    }

    fun tier(card: SoaCard): Int? = when {
        card.school != null -> card.level
        card.handicapped || card.extra -> null
        card.color == "GOLD" || card.color == "SILVER" -> null
        card.color == "PURPLE" -> 4
        else -> card.level
    }

    fun primary(action: String?): PrimaryAction? = when (action) {
        null -> null
        "ATTACK" -> PrimaryAction.ATTACK
        "SKILL" -> PrimaryAction.SKILL
        "DEFENSE" -> PrimaryAction.DEFENSE
        "MOVEMENT" -> PrimaryAction.MOVEMENT
        "DEFENSE_SKILL" -> PrimaryAction.DEFENSE_SKILL
        else -> error("Unknown primary action $action")
    }

    fun item(item: String?): Stat? = when (item) {
        null -> null
        "ATTACK" -> Stat.ATTACK
        "DEFENSE" -> Stat.DEFENSE
        "INITIATIVE" -> Stat.INITIATIVE
        "MOVEMENT" -> Stat.MOVEMENT
        "RANGE" -> Stat.RANGE
        "AREA" -> Stat.RADIUS
        else -> error("Unknown item $item")
    }

    fun role(trait: String): Role = Role.valueOf(trait.uppercase())

    fun values(card: SoaCard): CardValues {
        val primary = primary(card.primaryAction)
        return CardValues(
            attack = if (primary == PrimaryAction.ATTACK) card.primaryValue else card.secondaryAttack,
            defense = if (primary == PrimaryAction.DEFENSE || primary == PrimaryAction.DEFENSE_SKILL) {
                card.primaryValue
            } else {
                card.secondaryDefense
            },
            movement = if (primary == PrimaryAction.MOVEMENT) card.primaryValue else card.secondaryMovement,
            range = card.modifierValue.takeIf { card.modifier == "RANGE" },
            radius = card.modifierValue.takeIf { card.modifier == "AREA" },
            plus = plus(card),
        )
    }

    /**
     * Stats whose printed value carries a `+` (source `primaryValueSign` / `modifierValueSign`
     * `"PLUS"`), in [Stat] order. `"EXCLAMATION"` is the Block marker, not a sign of the value.
     */
    fun plus(card: SoaCard): List<Stat> {
        val signs = setOf(null, "PLUS", "EXCLAMATION")
        require(card.primaryValueSign in signs && card.modifierValueSign in setOf(null, "PLUS")) {
            "${card.name}: unknown value sign ${card.primaryValueSign} / ${card.modifierValueSign}"
        }
        return buildList {
            if (card.primaryValueSign == "PLUS") {
                add(
                    when (primary(card.primaryAction)) {
                        PrimaryAction.ATTACK -> Stat.ATTACK
                        PrimaryAction.DEFENSE, PrimaryAction.DEFENSE_SKILL -> Stat.DEFENSE
                        PrimaryAction.MOVEMENT -> Stat.MOVEMENT
                        else -> error("${card.name}: '+' on a ${card.primaryAction} value")
                    },
                )
            }
            if (card.modifierValueSign == "PLUS") {
                add(
                    when (card.modifier) {
                        "RANGE" -> Stat.RANGE
                        "AREA" -> Stat.RADIUS
                        else -> error("${card.name}: '+' on modifier ${card.modifier}")
                    },
                )
            }
        }.sortedBy { it.ordinal }
    }

    fun secondary(card: SoaCard): List<SecondaryAction> = buildList {
        if (card.secondaryMovement != null) add(SecondaryAction.MOVEMENT)
        if (card.secondaryDefense != null) add(SecondaryAction.DEFENSE)
        if (card.secondaryAttack != null) add(SecondaryAction.ATTACK)
    }

    fun toCard(heroId: String, id: String, card: SoaCard, unknownIcons: MutableSet<String>): Card = Card(
        id = id,
        heroId = heroId,
        name = card.name.trim(),
        color = color(card),
        tier = tier(card),
        initiative = card.initiative,
        primary = primary(card.primaryAction),
        secondary = secondary(card),
        values = values(card),
        ranged = card.primaryAction == "ATTACK" && card.modifier == "RANGE",
        block = card.primaryValueSign == "EXCLAMATION",
        item = item(card.item),
        text = TextMarkup.fromSoa(card.description, unknownIcons),
        image = null,
    )

    /** All of a hero's cards with IDs and slots; extras are numbered in source order. */
    fun heroCards(heroId: String, cards: List<SoaCard>, unknownIcons: MutableSet<String>): List<MappedCard> {
        var extra = 0
        return cards.map { source ->
            val extraIndex = if (source.extra) ++extra else null
            val id = cardId(heroId, source, extraIndex)
            val slot = when {
                source.handicapped -> Slot.HANDICAP
                else -> Slot.DECK
            }
            MappedCard(toCard(heroId, id, source, unknownIcons), slot, source, artSlug(source, extraIndex))
        }
    }

    fun spellCards(heroId: String, spells: List<SoaCard>, unknownIcons: MutableSet<String>): List<MappedCard> =
        spells.map { MappedCard(toCard(heroId, spellId(heroId, it), it, unknownIcons), Slot.EXTRA, it, null) }

    private val colourOrder =
        listOf(CardColor.GOLD, CardColor.SILVER, CardColor.RED, CardColor.GREEN, CardColor.BLUE, CardColor.PURPLE)

    /** Deck order: basic cards (gold, extra golds, silver), then tiers I–III red/green/blue, a before b, Ultimate. */
    val deckOrder: Comparator<Card> = compareBy<Card>(
        { if (it.color == CardColor.GOLD || it.color == CardColor.SILVER) 0 else it.tier ?: 9 },
        { colourOrder.indexOf(it.color) },
        { it.id },
    )
}
