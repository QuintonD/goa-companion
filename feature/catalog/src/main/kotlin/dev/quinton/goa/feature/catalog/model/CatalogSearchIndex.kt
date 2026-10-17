package dev.quinton.goa.feature.catalog.model

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Hero
import java.text.Normalizer

/** Where a search token matched, strongest first. */
enum class MatchField(val weight: Int) {
    NAME(100),
    ALIAS(80),
    TITLE(60),
    CARD_NAME(30),
    CARD_TEXT(10),
}

/**
 * One hero matched by a query. [field] and [card] explain the *least direct* reason the hero
 * matched (e.g. a card name), so the UI can show "Card: Tidal Blast" under the tile.
 * For a blank query every hero is a hit with [score] 0 and no [field].
 */
data class SearchHit(
    val heroId: String,
    val score: Int,
    val field: MatchField?,
    val card: Card?,
)

/**
 * In-memory full-text index over hero names, aliases, titles, card names and card text
 * ([CardText.plain], so inline icons are searchable as words such as "attack").
 *
 * Matching is case- and accent-insensitive; every whitespace-separated token must match
 * somewhere in the hero (AND). Hero and alias names are also matched without spaces or
 * punctuation, so "neb kher" finds "NebKher". Pure Kotlin, no Android dependencies.
 */
class CatalogSearchIndex(heroes: List<Hero>) {
    private class CardDoc(val card: Card, val name: String, val text: String)

    private class HeroDoc(
        val heroId: String,
        val names: List<String>,
        val aliases: List<String>,
        val title: String,
        val cards: List<CardDoc>,
    )

    private val docs: List<HeroDoc> = heroes.map { hero ->
        HeroDoc(
            heroId = hero.id,
            names = listOf(normalize(hero.name)).withCompact(),
            aliases = hero.aliases.map(::normalize).withCompact(),
            title = normalize(hero.title),
            cards = hero.allCards.map { CardDoc(it, normalize(it.name), normalize(CardText.plain(it.text))) },
        )
    }

    val size: Int get() = docs.size

    /** Hits in catalog order (callers sort); heroes that don't match are omitted. */
    fun search(query: String): List<SearchHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return docs.map { SearchHit(it.heroId, score = 0, field = null, card = null) }
        return docs.mapNotNull { doc -> match(doc, tokens) }
    }

    private fun match(doc: HeroDoc, tokens: List<String>): SearchHit? {
        var score = 0
        var weakest: TokenMatch? = null
        for (token in tokens) {
            val best = bestMatch(doc, token) ?: return null
            score += best.score
            if (weakest == null || best.field.weight < weakest.field.weight) weakest = best
        }
        return SearchHit(doc.heroId, score, weakest?.field, weakest?.card)
    }

    private class TokenMatch(val field: MatchField, val score: Int, val card: Card? = null)

    private fun bestMatch(doc: HeroDoc, token: String): TokenMatch? {
        val nameScore = doc.names.bestScore(token)
        val aliasScore = doc.aliases.bestScore(token)
        val titleScore = positionBonus(doc.title, token)
        return when {
            nameScore != null -> TokenMatch(MatchField.NAME, MatchField.NAME.weight + nameScore)
            aliasScore != null -> TokenMatch(MatchField.ALIAS, MatchField.ALIAS.weight + aliasScore)
            titleScore != null -> TokenMatch(MatchField.TITLE, MatchField.TITLE.weight + titleScore)
            else -> bestCardMatch(doc, token)
        }
    }

    private fun bestCardMatch(doc: HeroDoc, token: String): TokenMatch? {
        for (card in doc.cards) {
            val bonus = positionBonus(card.name, token) ?: continue
            return TokenMatch(MatchField.CARD_NAME, MatchField.CARD_NAME.weight + bonus, card.card)
        }
        return doc.cards.firstOrNull { it.text.contains(token) }
            ?.let { TokenMatch(MatchField.CARD_TEXT, MatchField.CARD_TEXT.weight, it.card) }
    }

    private fun List<String>.bestScore(token: String): Int? = mapNotNull { positionBonus(it, token) }.maxOrNull()

    companion object {
        private val Diacritics = Regex("\\p{Mn}+")
        private val NonAlphanumeric = Regex("[^\\p{L}\\p{N}]+")
        private val Whitespace = Regex("\\s+")

        /** Lower-case, accent-free, with typographic quotes folded to ASCII. */
        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Diacritics, "")
            .replace('’', '\'')
            .replace('‘', '\'')
            .lowercase()

        fun tokenize(query: String): List<String> =
            normalize(query).trim().split(Whitespace).map { it.trim() }.filter { it.isNotEmpty() }

        private fun List<String>.withCompact(): List<String> =
            flatMap { listOf(it, it.replace(NonAlphanumeric, "")) }.distinct()

        /** Null if [token] isn't in [field]; otherwise a bonus for prefix (20) or word-start (10) matches. */
        private fun positionBonus(field: String, token: String): Int? {
            val at = field.indexOf(token)
            return when {
                at < 0 -> null
                at == 0 -> 20
                startsWord(field, at) -> 10
                field.indexOf(token, at + 1).let { it > 0 && startsWord(field, it) } -> 10
                else -> 0
            }
        }

        private fun startsWord(field: String, at: Int) = at == 0 || !field[at - 1].isLetterOrDigit()
    }
}
