package dev.quinton.goa.core.domain.rules

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.rules.LevelRow

/** One printed line of a rulebook excerpt: a paragraph, a bullet item or a numbered item. */
sealed interface ExcerptLine {
    val text: String

    data class Paragraph(override val text: String) : ExcerptLine

    data class Bullet(override val text: String) : ExcerptLine

    data class Numbered(val number: String, override val text: String) : ExcerptLine
}

/**
 * Labels and spoken text for the rules screens (no rules content lives here), shared by the phone's
 * feature:rules and the web app (ADR-043) so both say exactly the same words.
 */
object RulesText {
    fun roman(tier: Int?): String = when (tier) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        else -> "–"
    }

    /** "p. 13" / "pp. 4, 7" — printed page numbers of the rulebook. */
    fun pages(pages: List<Int>): String = when (pages.size) {
        1 -> "p. ${pages.single()}"
        else -> "pp. ${pages.joinToString(", ")}"
    }

    fun spokenPages(pages: List<Int>): String = when (pages.size) {
        1 -> "rulebook page ${pages.single()}"
        else -> "rulebook pages ${pages.dropLast(1).joinToString(", ")} and ${pages.last()}"
    }

    /** "1 coin", "2 coins", "1 Life counter". */
    fun count(n: Int, one: String, many: String = "${one}s"): String = "$n ${if (n == 1) one else many}"

    /** The level & defeat ladder card's page tag (p.13 table, p.15 table, p.23 summary). */
    val LADDER_PAGES: List<Int> = listOf(13, 15, 23)

    /** Words that find the level & defeat ladder from the rules search box. */
    private val ladderWords =
        listOf("level", "defeat", "coin", "assist", "life", "counter", "spend", "tier", "cost", "upgrade", "ladder")

    /** Whether the rules list shows the ladder card for [query] (always for a blank query). */
    fun showsLadder(query: String): Boolean {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return words.isEmpty() || words.all { word -> ladderWords.any { it.startsWith(word) || word.startsWith(it) } }
    }

    /** The ladder table's column heads, as printed (line breaks where the sheet wraps them). */
    val LADDER_HEADERS: List<String> = listOf(
        "Level",
        "Lowest\nTier",
        "Coins\n(Defeat)",
        "Assist\nCoins",
        "Life\ncounters",
        "Next\nlevel\ncost",
        "Max\nTier",
    )

    /**
     * The same card label the Heroes search shows (catalog `Card.kindLabel`): "Red · Tier II",
     * "Gold · Basic", "Ultimate", "Handicap · Silver", "Spell · Red · Tier III".
     */
    fun cardLabel(hero: Hero, card: Card): String {
        val tier = card.tier
        val basic = card.color == CardColor.GOLD || card.color == CardColor.SILVER
        return when {
            card.id == hero.handicap?.id -> if (basic) "Handicap · ${colorLabel(card.color)}" else "Handicap"

            card.id.startsWith("${hero.id}-spell-") -> listOfNotNull(
                "Spell",
                colorLabel(card.color).takeIf { card.color != CardColor.PURPLE && card.color != CardColor.OTHER },
                tier?.let { if (it == ULTIMATE_TIER) "Ultimate" else "Tier ${roman(it)}" },
            ).joinToString(" · ")

            card.color == CardColor.PURPLE || tier == ULTIMATE_TIER -> "Ultimate"

            basic -> "${colorLabel(card.color)} · Basic"

            tier != null -> "${colorLabel(card.color)} · Tier ${roman(tier)}"

            else -> colorLabel(card.color)
        }
    }

    private fun colorLabel(color: CardColor): String = when (color) {
        CardColor.GOLD -> "Gold"
        CardColor.SILVER -> "Silver"
        CardColor.RED -> "Red"
        CardColor.GREEN -> "Green"
        CardColor.BLUE -> "Blue"
        CardColor.PURPLE -> "Purple"
        CardColor.OTHER -> "Special"
    }

    private val numbered = Regex("^(\\d+)\\.\\s+(.*)$")

    /** An excerpt's printed lines: `\n` separates them; "• " starts a bullet, "1. " a numbered item. */
    fun excerptLines(text: String): List<ExcerptLine> = text.split('\n').filter { it.isNotBlank() }.map { line ->
        val match = numbered.find(line)
        when {
            line.startsWith("• ") -> ExcerptLine.Bullet(line.removePrefix("• "))
            match != null -> ExcerptLine.Numbered(match.groupValues[1], match.groupValues[2])
            else -> ExcerptLine.Paragraph(line)
        }
    }

    /** A printed table's body row read with its column names: "6-8 Players, Wave Counters 2 x 7, …". */
    fun spokenTableRow(header: List<String>, row: List<String>): String =
        row.mapIndexed { c, cell -> if (c == 0) cell else "${header.getOrElse(c) { "" }} $cell" }.joinToString(", ")

    /** One ladder row for a screen reader, counts agreeing with their nouns. */
    fun spokenLadderRow(row: LevelRow, next: Int?): String = buildString {
        append("Level ${row.level}. Lowest card Tier ${row.lowestTier}. ")
        append("Defeat: ${count(row.defeatCoins, "coin")}, ")
        append("${count(row.assistCoins, "assist coin")}, ")
        append("team spends ${count(row.lifeCounters, "Life counter")}. ")
        append(if (next != null) "Next level costs ${count(next, "coin")}. " else "Top level. ")
        append(row.maxTier?.let { "Max card Tier $it." } ?: "")
    }

    /** The defeat result for a screen reader, with singular and plural counts. */
    fun spokenDefeat(row: LevelRow): String =
        "Level ${row.level} hero defeated, lowest card Tier ${row.lowestTier}. " +
            "Their team spends ${count(row.lifeCounters, "Life counter")}. " +
            "The hero who defeated them collects ${count(row.defeatCoins, "coin")}. " +
            "That hero's friendly heroes collect ${count(row.assistCoins, "assist coin")}."

    /**
     * Screen-reader text for an inline card icon: blank when the printed word right after it already
     * names it ("{life} Life counter" is read once).
     */
    fun spokenIconLabel(label: String, next: CardText.Segment?): String {
        val following = (next as? CardText.Segment.Text)?.value?.trimStart().orEmpty()
        return if (following.startsWith(label, ignoreCase = true)) " " else label
    }

    private const val ULTIMATE_TIER = 4
}
