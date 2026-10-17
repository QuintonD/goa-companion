package dev.quinton.goa.core.domain.rules

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.core.model.rules.RulesTopic

/** Lookups over the bundled level ladder (`rules-reference.json`). Reads the tables; never derives rules. */
object LadderLookup {
    fun row(ladder: LevelLadder, level: Int): LevelRow? = ladder.levels.firstOrNull { it.level == level }

    /**
     * Coins to go from [level] to the next level: the p.15 "Level Up cost" of level + 1. Null at
     * the top level (level 8 has no next level).
     */
    fun nextLevelCost(ladder: LevelLadder, level: Int): Int? = row(ladder, level + 1)?.levelUpCost
}

/**
 * A card among the given heroes whose printed text changes what a defeat costs or pays (Life
 * counters, assist coins). Its text is quoted verbatim from the catalog; the app never applies it.
 */
data class DefeatException(val hero: Hero, val card: Card)

object DefeatExceptions {
    private val phrases = listOf("life counter", "assist coin")

    /**
     * Catalog text query: cards (incl. Handicap cards and extras such as spellbooks) whose text
     * has the `{life}` icon or names Life counters or assist coins. Ordered by hero, then deck order.
     */
    fun find(heroes: List<Hero>): List<DefeatException> = heroes.flatMap { hero ->
        hero.allCards.filter(::matches).map { DefeatException(hero, it) }
    }

    fun matches(card: Card): Boolean {
        val segments = CardText.flatten(CardText.parse(card.text))
        if (segments.any { it is CardText.Segment.Icon && it.name == "life" }) return true
        val plain = CardText.plain(card.text).lowercase()
        return phrases.any { it in plain }
    }
}

/** Topic search for the Rules screen: title, keywords, headings and excerpt text, all words must match. */
object RulesSearch {
    fun filter(topics: List<RulesTopic>, query: String): List<RulesTopic> {
        val words = fold(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return topics
        return topics.filter { topic ->
            val haystack = haystack(topic)
            words.all { it in haystack }
        }
    }

    /** Lower case with typographic quotes and dashes folded, so "hero's" finds "hero’s". */
    private fun fold(text: String): String = text.lowercase()
        .replace('’', '\'').replace('‘', '\'')
        .replace('“', '"').replace('”', '"')
        .replace('—', '-').replace('–', '-')

    private fun haystack(topic: RulesTopic): String = fold(
        buildString {
            append(topic.title).append(' ')
            topic.keywords.forEach { append(it).append(' ') }
            topic.summary?.let { append(it).append(' ') }
            topic.excerpts.forEach { excerpt ->
                excerpt.heading?.let { append(it).append(' ') }
                append(excerpt.text).append(' ')
            }
        },
    )
}
