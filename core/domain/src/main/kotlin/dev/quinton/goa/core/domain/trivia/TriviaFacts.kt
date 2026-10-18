package dev.quinton.goa.core.domain.trivia

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.Stat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Card-number trivia (ADR-028). Every question is built from a number the catalog stores for a
 * card; nothing is computed or guessed, and a null value is never asked about.
 */

/** A number printed on an Action card that the quiz can ask about. */
@Serializable
enum class TriviaStat(val key: String) {
    @SerialName("initiative")
    INITIATIVE("initiative"),

    @SerialName("attack")
    ATTACK("attack"),

    @SerialName("defense")
    DEFENSE("defense"),

    @SerialName("movement")
    MOVEMENT("movement"),

    @SerialName("range")
    RANGE("range"),

    @SerialName("radius")
    RADIUS("radius"),
    ;

    /** The card's printed value for this stat, or null when the card prints none. */
    fun valueOf(card: Card): Int? = when (this) {
        INITIATIVE -> card.initiative
        ATTACK -> card.values.attack
        DEFENSE -> card.values.defense
        MOVEMENT -> card.values.movement
        RANGE -> card.values.range
        RADIUS -> card.values.radius
    }

    /** True when the card prints this value with a `+` (Attack `4+`). Initiative never has one. */
    fun isPlus(card: Card): Boolean = when (this) {
        INITIATIVE -> false
        ATTACK -> Stat.ATTACK in card.values.plus
        DEFENSE -> Stat.DEFENSE in card.values.plus
        MOVEMENT -> Stat.MOVEMENT in card.values.plus
        RANGE -> Stat.RANGE in card.values.plus
        RADIUS -> Stat.RADIUS in card.values.plus
    }

    companion object {
        fun fromKey(key: String): TriviaStat? = entries.firstOrNull { it.key == key }
    }
}

/** Which of a hero's deck cards the quiz covers, by tier. */
@Serializable
enum class CardGroup(val key: String) {
    /** Gold and Silver (and any extra basic cards, e.g. Takahide's). */
    @SerialName("basic")
    BASIC("basic"),

    @SerialName("tier1")
    TIER_1("tier1"),

    @SerialName("tier2")
    TIER_2("tier2"),

    @SerialName("tier3")
    TIER_3("tier3"),

    @SerialName("ultimate")
    ULTIMATE("ultimate"),
    ;

    companion object {
        fun of(card: Card): CardGroup? = when (card.tier) {
            null -> BASIC
            1 -> TIER_1
            2 -> TIER_2
            3 -> TIER_3
            4 -> ULTIMATE
            else -> null
        }

        fun fromKey(key: String): CardGroup? = entries.firstOrNull { it.key == key }
    }
}

/** One printed number: [stat] on card [cardId] of hero [heroId] is [value] (`value+` when [plus]). */
data class TriviaFact(
    val heroId: String,
    val cardId: String,
    val stat: TriviaStat,
    val value: Int,
    val plus: Boolean = false,
) {
    /** Stable key for progress: survives app updates as long as the card id does. */
    val key: String get() = keyOf(cardId, stat)

    companion object {
        fun keyOf(cardId: String, stat: TriviaStat): String = "$cardId/${stat.key}"
    }
}

/** Builds the facts the quiz can ask from the catalog. */
object TriviaFacts {
    /**
     * Every printed number on [hero]'s deck cards (not the Handicap card or extras such as Gydion's
     * spellbook) for [stats] on cards in [groups], in deck order.
     */
    fun of(
        hero: Hero,
        stats: Set<TriviaStat> = TriviaStat.entries.toSet(),
        groups: Set<CardGroup> = CardGroup.entries.toSet(),
    ): List<TriviaFact> =
        hero.cards.flatMap { card ->
            if (CardGroup.of(card) !in groups) return@flatMap emptyList()
            TriviaStat.entries.filter { it in stats }.mapNotNull { stat ->
                stat.valueOf(card)?.let { TriviaFact(hero.id, card.id, stat, it, stat.isPlus(card)) }
            }
        }

    fun of(
        catalog: Catalog,
        heroIds: Collection<String>,
        stats: Set<TriviaStat> = TriviaStat.entries.toSet(),
        groups: Set<CardGroup> = CardGroup.entries.toSet(),
    ): List<TriviaFact> = heroIds.distinct().mapNotNull(catalog::hero).flatMap { of(it, stats, groups) }

    /**
     * Every value each stat takes anywhere in [catalog]'s deck cards, ascending: the answer buttons
     * of a Recall question and the pool distractors are drawn from.
     */
    fun valueRange(catalog: Catalog): Map<TriviaStat, List<Int>> {
        val all = catalog.heroes.flatMap { of(it) }
        return TriviaStat.entries.associateWith { stat ->
            all.filter { it.stat == stat }.map { it.value }.distinct().sorted()
        }
    }
}
