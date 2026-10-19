package dev.quinton.goa.core.presentation.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.rating.HeroUsage
import dev.quinton.goa.core.model.rating.Rating

/** Personal stats for the hero: derived Elo/usage plus who played it (shared meta stats). */
data class HeroPersonalStats(
    val rating: Rating?,
    val usage: HeroUsage?,
    val log: HeroLogSummary,
) {
    val hasGames: Boolean get() = log.games > 0 || (rating?.games ?: 0) > 0 || (usage?.matches ?: 0) > 0

    /** Games with the hero: the usage count, else the rated games, else the log's. */
    val games: Int get() = usage?.matches ?: rating?.games ?: log.games

    val wins: Int get() = usage?.wins ?: rating?.wins ?: log.wins

    /** The metrics of the "Your table" card, in order (Elo, Games, Win rate, then Pick and Ban rate). */
    val metrics: List<HeroMetric>
        get() = buildList {
            val games = games
            val wins = wins
            add(
                HeroMetric(
                    "Elo",
                    rating?.takeIf { it.games > 0 }?.let { elo(it.value) } ?: "—",
                    rating?.takeIf { it.provisional && it.games > 0 }?.let { "provisional" },
                ),
            )
            add(HeroMetric("Games", games.toString(), "${wins}W · ${games - wins}L"))
            add(HeroMetric("Win rate", if (games > 0) percent(wins.toDouble() / games) else "—", null))
            usage?.let {
                add(HeroMetric("Pick rate", percent(it.pickRate), null))
                add(HeroMetric("Ban rate", percent(it.banRate), plural(it.bans, "ban")))
            }
        }

    /** "Best with this hero" when someone qualifies, else "Played by" (everyone, most games first). */
    val pilotsTitle: String get() = if (log.bestPilots.isNotEmpty()) "Best with this hero" else "Played by"

    /** The pilots shown (at most [MAX_PILOTS]): the ranked ones, else everyone who played. */
    val pilots: List<HeroPilotStat> get() = log.bestPilots.ifEmpty { log.allPilots }.take(MAX_PILOTS)

    /** Under "Played by": when ranking starts. */
    val pilotsNote: String?
        get() = if (log.bestPilots.isEmpty()) "Rankings start at ${log.minGames} games per player." else null

    companion object {
        const val MAX_PILOTS = 3
        val EMPTY = HeroPersonalStats(null, null, HeroLogSummary.EMPTY)
        const val NO_GAMES_TITLE = "No games logged with this hero yet"
        const val NO_GAMES_MESSAGE =
            "Log a match in the Players tab to see Elo, win rate and who plays this hero best."
    }
}

/** One number on the "Your table" card: label, value, optional note ("provisional", "3W · 2L"). */
data class HeroMetric(val label: String, val value: String, val note: String?)

/** A pilot's record as shown: "57%" and "4–3". */
val HeroPilotStat.recordLine: String get() = "$wins–${games - wins}"

sealed interface HeroDetailUiState {
    data object Loading : HeroDetailUiState

    data class NotFound(val heroId: String) : HeroDetailUiState

    data class Ready(
        val hero: Hero,
        val productName: String,
        val deck: DeckLayout,
        val stats: HeroPersonalStats,
    ) : HeroDetailUiState

    companion object {
        /** The hero page for [heroId], or [NotFound]. */
        fun of(catalog: Catalog, heroId: String, stats: HeroPersonalStats): HeroDetailUiState {
            val hero = catalog.hero(heroId) ?: return NotFound(heroId)
            return Ready(hero, catalog.product(hero.product)?.name ?: hero.product, DeckLayout.of(hero), stats)
        }
    }
}

/** Order printed on the official Hero Profile card (rulebook p.21). */
val PROFILE_STATS: List<Stat> = listOf(Stat.ATTACK, Stat.INITIATIVE, Stat.DEFENSE, Stat.MOVEMENT)

/** The Hero Profile scale: 1 ("Very poor") to 8 ("Exceptional"). */
const val STAT_SCALE = 8

/** One Hero Profile bar: [base] solid, up to [max] conditional (level-ups, card text); null = unknown. */
data class StatBar(val stat: Stat, val base: Int?, val max: Int?) {
    /** "4", "3 → 5", or "—". */
    val rangeText: String
        get() = when {
            base == null || max == null -> "—"
            base == max -> "$base"
            else -> "$base → $max"
        }

    /** "Attack: 3 of 8, up to 5 conditionally". */
    val spoken: String
        get() = if (base == null || max == null) {
            "${stat.label}: unknown"
        } else {
            "${stat.label}: $base of $STAT_SCALE" + if (max > base) ", up to $max conditionally" else ""
        }

    /** Each of the [STAT_SCALE] segments: base, conditional, or empty. */
    val segments: List<StatSegment>
        get() = (1..STAT_SCALE).map { level ->
            when {
                base == null || max == null -> StatSegment.EMPTY
                level <= base -> StatSegment.BASE
                level <= max -> StatSegment.CONDITIONAL
                else -> StatSegment.EMPTY
            }
        }
}

enum class StatSegment { BASE, CONDITIONAL, EMPTY }

/** The Hero Profile bars in the printed order. */
fun HeroStats.bars(): List<StatBar> = PROFILE_STATS.map { stat ->
    val range = get(stat)
    StatBar(stat, range?.base, range?.max)
}

/** Rulebook p.20, under the Handicap card. */
const val HANDICAP_NOTE =
    "A weaker version of a basic card (usually Gold). With 5, 7 or 9 players, every hero on the " +
        "larger team replaces one of its basic cards with it. With 3 players, the player controlling " +
        "two heroes replaces a gold card of one of them with it. In even-count games, the most " +
        "experienced players may use it to even out skill."

/**
 * Rulebook p.20 has the 3-player Handicap card replace "a gold card of one of those heroes", but a
 * Handicap card without a gold frame (Takahide's is silver, and he has three gold cards) leaves
 * open which card it replaces. Shown as unresolved; the app never picks an answer.
 */
fun handicapConflict(hero: Hero, handicap: Card): String? {
    if (handicap.color == CardColor.GOLD) return null
    val golds = hero.cards.count { it.color == CardColor.GOLD }
    val frame = handicap.color.label.lowercase()
    val goldCards = if (golds > 1) " and ${hero.name} has $golds gold cards" else ""
    return "Unresolved: rulebook p.20 says that with 3 players the Handicap card replaces a gold card, but this " +
        "one has a $frame frame$goldCards. Neither the rulebook nor the card says which card it replaces."
}

/**
 * Takahide has three gold cards. Their own text says where each starts and how they swap
 * ("Swap this card with a different gold card in your deck."), so the note points there and
 * the tiles carry "Starts in hand" / "Starts in deck" from each card's reminder text.
 */
fun multipleGoldNote(cards: List<Card>): String? {
    val golds = cards.filter { it.color == CardColor.GOLD }
    val hand = golds.count { it.startsIn() == STARTS_IN_HAND }
    val deck = golds.count { it.startsIn() == STARTS_IN_DECK }
    if (golds.size < 2 || hand + deck == 0) return null
    return "${golds.size} gold cards: $hand ${if (hand == 1) "starts" else "start"} in your hand, $deck in your " +
        "deck, as their card text says. Tap a card for how it swaps."
}

/** Under the Spellbook heading. */
const val SPELLBOOK_NOTE = "Spells by tier. Deck cards name the spells they cast; tap a spell for its full card."

/** "Gold (no tier) · 3", "Tier II · 9", "Ultimate · 1". */
fun extraRowTitle(row: TierRow): String {
    val tier = row.tier
    val name = when {
        tier == null && row.cards.all { it.color == CardColor.GOLD } -> "Gold (no tier)"
        tier == null -> "No tier"
        tier == 4 -> "Ultimate"
        else -> "Tier ${tierNumeral(tier)}"
    }
    return "$name · ${row.cards.size}"
}

/** "Also known as …", or null without aliases. */
val Hero.aliasLine: String? get() = aliases.takeIf { it.isNotEmpty() }?.let { "Also known as ${it.joinToString()}" }

/** "Learn Arien's card numbers" — the hero page's way into card trivia. */
fun learnNumbersLabel(heroName: String): String = "Learn $heroName's card numbers"

/** Under the sources. */
const val SOURCES_FOOTER = "Game content and art © Wolff Designa. Private, non-commercial build."

/** No FAQ entries. */
const val NO_RULINGS = "No official rulings recorded for this hero."

/** One source line's meta: "https://… · retrieved 2026-10-17". */
fun sourceMeta(url: String?, retrieved: String?): String =
    listOfNotNull(url, retrieved?.let { "retrieved $it" }).joinToString(" · ")

sealed interface CardViewerUiState {
    data object Loading : CardViewerUiState

    data object NotFound : CardViewerUiState

    data class Ready(
        val hero: Hero,
        /** Every card of the hero in the same order as the detail screen. */
        val cards: List<Card>,
        val initialIndex: Int,
    ) : CardViewerUiState {
        fun isHandicap(card: Card): Boolean = hero.handicap?.id == card.id
    }

    companion object {
        /** [heroId]'s cards in deck order, opened at [cardId] (the first card if it isn't one of them). */
        fun of(catalog: Catalog, heroId: String, cardId: String?): CardViewerUiState {
            val hero = catalog.hero(heroId) ?: return NotFound
            val cards = DeckLayout.of(hero).orderedCards
            if (cards.isEmpty()) return NotFound
            val index = cards.indexOfFirst { it.id == cardId }
            return Ready(hero, cards, initialIndex = index.coerceAtLeast(0))
        }
    }
}
