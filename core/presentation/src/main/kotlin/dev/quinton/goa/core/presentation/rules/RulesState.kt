package dev.quinton.goa.core.presentation.rules

import dev.quinton.goa.core.domain.rules.DefeatExceptions
import dev.quinton.goa.core.domain.rules.LadderLookup
import dev.quinton.goa.core.domain.rules.RulesSearch
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.catalog.Hero
import dev.quinton.goa.core.model.rules.LevelLadder
import dev.quinton.goa.core.model.rules.LevelRow
import dev.quinton.goa.core.model.rules.RulesReference
import dev.quinton.goa.core.model.rules.RulesTopic
import dev.quinton.goa.core.presentation.catalog.kindLabel

// ---- Topic list -------------------------------------------------------------------------------

data class TopicItem(val id: String, val title: String, val pages: List<Int>, val summary: String?)

sealed interface RulesUiState {
    data object Loading : RulesUiState

    data object Unavailable : RulesUiState

    data class Ready(val query: String, val topics: List<TopicItem>, val totalTopics: Int) : RulesUiState {
        /** The Levels & defeats card shows for a blank query or one made of ladder words. */
        val showLadder: Boolean get() = RulesText.showsLadder(query)

        /** "Quick reference", or "3 of 11 topics" while searching. */
        val topicsHeader: String
            get() = if (query.isBlank()) "Quick reference" else "${topics.size} of $totalTopics topics"

        /** Nothing at all matches (no topic, and the ladder card hidden). */
        val isEmpty: Boolean get() = topics.isEmpty() && !showLadder
    }

    companion object {
        /** The Rules screen for [query]; [reference] null = the bundled reference couldn't be read. */
        fun of(reference: RulesReference?, query: String): RulesUiState = if (reference == null) {
            Unavailable
        } else {
            Ready(
                query = query,
                topics = RulesSearch.filter(reference.topics, query).map(::item),
                totalTopics = reference.topics.size,
            )
        }

        private fun item(topic: RulesTopic) = TopicItem(topic.id, topic.title, topic.pages.sorted(), topic.summary)
    }
}

// ---- Topic detail -----------------------------------------------------------------------------

sealed interface RulesTopicUiState {
    data object Loading : RulesTopicUiState

    data object NotFound : RulesTopicUiState

    data class Ready(val topic: RulesTopic) : RulesTopicUiState

    companion object {
        fun of(reference: RulesReference?, topicId: String): RulesTopicUiState =
            reference?.topics?.firstOrNull { it.id == topicId }?.let { Ready(it) } ?: NotFound
    }
}

// ---- Level & defeat ladder --------------------------------------------------------------------

/** A catalog card whose printed text changes a defeat's cost or reward. [text] is the card's markup. */
data class ExceptionCardUi(
    val heroName: String,
    val cardName: String,
    /** "Tier III · Red", "Handicap", "Spellbook" … */
    val label: String,
    val text: String,
)

sealed interface LadderUiState {
    data object Loading : LadderUiState

    data object Unavailable : LadderUiState

    data class Ready(
        val ladder: LevelLadder,
        /** Level picked under "Hero defeated", or null. */
        val selectedLevel: Int?,
        val exceptions: List<ExceptionCardUi>,
        /** True when [exceptions] cover the heroes of the current match, false = every hero. */
        val matchHeroesOnly: Boolean,
    ) : LadderUiState {
        /** The picked level's row, or null. */
        val selected: LevelRow? get() = selectedLevel?.let { LadderLookup.row(ladder, it) }

        /** The selection after picking [level] (see [LadderSelection]). */
        fun selectionAfterPicking(level: Int): Int? = LadderSelection.afterPicking(selectedLevel, level)

        val cardsHeader: String
            get() = if (matchHeroesOnly) "Cards in this match that change this" else "Cards that change this"

        /** Shown when no card changes the defeat. */
        val noCardsText: String
            get() = if (matchHeroesOnly) {
                "No card of the heroes in this match changes Life counters or assist coins."
            } else {
                "No card in the catalog changes Life counters or assist coins."
            }
    }

    companion object {
        /**
         * The ladder with [selectedLevel] picked. Cards that change a defeat come from the heroes of
         * the match ([heroIds]) when it has any known hero, else from every hero in [catalog].
         */
        fun of(
            reference: RulesReference?,
            catalog: Catalog,
            heroIds: List<String>,
            selectedLevel: Int?,
        ): LadderUiState {
            if (reference == null) return Unavailable
            val matchHeroes = heroIds.distinct().mapNotNull(catalog::hero)
            val heroes = matchHeroes.ifEmpty { catalog.heroes }
            return Ready(
                ladder = reference.ladder,
                selectedLevel = selectedLevel,
                exceptions = DefeatExceptions.find(heroes).map { (hero, card) ->
                    ExceptionCardUi(hero.name, card.name, RulesText.cardLabel(hero, card), card.text)
                },
                matchHeroesOnly = matchHeroes.isNotEmpty(),
            )
        }
    }
}

/** Picking a level selects it; picking the selected one again clears the selection. */
object LadderSelection {
    fun afterPicking(selected: Int?, level: Int): Int? = if (selected == level) null else level
}

/** The three numbers for a picked level, in the rulebook's table terms. */
data class DefeatNumber(val value: Int, val label: String, val detail: String)

/** "Life counters", "Coins (Defeat)", "Assist Coins" for [row]. */
fun defeatNumbers(row: LevelRow): List<DefeatNumber> = listOf(
    DefeatNumber(row.lifeCounters, "Life counters", "their team spends"),
    DefeatNumber(row.defeatCoins, "Coins (Defeat)", "the hero who defeated them collects"),
    DefeatNumber(row.assistCoins, "Assist Coins", "that hero's friendly heroes collect"),
)

/** "Level 3 hero defeated · lowest card Tier I". */
fun defeatTitle(row: LevelRow): String =
    "Level ${row.level} hero defeated · lowest card Tier ${RulesText.roman(row.lowestTier)}"

/** The ladder table's column headers (line breaks where the phone breaks them). */
val LADDER_HEADERS: List<String> = listOf(
    "Level",
    "Lowest\nTier",
    "Coins\n(Defeat)",
    "Assist\nCoins",
    "Life\ncounters",
    "Next\nlevel\ncost",
    "Max\nTier",
)

/** One ladder table row's cells, in [LADDER_HEADERS] order. */
fun ladderCells(ladder: LevelLadder, row: LevelRow): List<String> {
    val next = LadderLookup.nextLevelCost(ladder, row.level)
    return listOf(
        "${row.level}",
        RulesText.roman(row.lowestTier),
        "${row.defeatCoins}",
        "${row.assistCoins}",
        "${row.lifeCounters}",
        next?.toString() ?: "–",
        RulesText.roman(row.maxTier),
    )
}

/** A ladder row for screen readers, with singular and plural counts. */
fun spokenRow(row: LevelRow, next: Int?): String = buildString {
    append("Level ${row.level}. Lowest card Tier ${row.lowestTier}. ")
    append("Defeat: ${RulesText.count(row.defeatCoins, "coin")}, ")
    append("${RulesText.count(row.assistCoins, "assist coin")}, ")
    append("team spends ${RulesText.count(row.lifeCounters, "Life counter")}. ")
    append(if (next != null) "Next level costs ${RulesText.count(next, "coin")}. " else "Top level. ")
    append(row.maxTier?.let { "Max card Tier $it." } ?: "")
}

/** The defeat result for screen readers, with singular and plural counts. */
fun spokenDefeat(row: LevelRow): String =
    "Level ${row.level} hero defeated, lowest card Tier ${row.lowestTier}. " +
        "Their team spends ${RulesText.count(row.lifeCounters, "Life counter")}. " +
        "The hero who defeated them collects ${RulesText.count(row.defeatCoins, "coin")}. " +
        "That hero's friendly heroes collect ${RulesText.count(row.assistCoins, "assist coin")}."

// ---- Excerpts ---------------------------------------------------------------------------------

/** One printed line of an excerpt: a paragraph, a bullet item or a numbered item. */
sealed interface ExcerptLine {
    val text: String

    data class Paragraph(override val text: String) : ExcerptLine

    data class Bullet(override val text: String) : ExcerptLine

    data class Numbered(val number: String, override val text: String) : ExcerptLine
}

private val numbered = Regex("^(\\d+)\\.\\s+(.*)$")

/** An excerpt's text split into its printed lines: "• " bullets, "1. " steps, paragraphs. */
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

/** Labels for the rules screens (no rules content lives here). */
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

    /**
     * The same card label the Heroes search shows ([kindLabel]): "Red · Tier II", "Gold · Basic",
     * "Ultimate", "Handicap · Silver", "Spell · Red · Tier III".
     */
    fun cardLabel(hero: Hero, card: Card): String = card.kindLabel(isHandicap = card.id == hero.handicap?.id)

    /** Words that find the level & defeat ladder from the search box. */
    val ladderWords: List<String> =
        listOf("level", "defeat", "coin", "assist", "life", "counter", "spend", "tier", "cost", "upgrade", "ladder")

    /** True for a blank query or one whose every word is (a prefix of) a ladder word. */
    fun showsLadder(query: String): Boolean {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        return words.isEmpty() || words.all { word -> ladderWords.any { it.startsWith(word) || word.startsWith(it) } }
    }

    /** The printed pages of the Levels & defeats tables. */
    val LADDER_PAGES: List<Int> = listOf(13, 15, 23)

    const val LADDER_TITLE = "Levels & defeats"
    const val LADDER_SUBTITLE = "Life counters, coins and assist coins per level, level costs and card Tiers"
    const val EMPTY_TITLE = "No rules match"
    const val EMPTY_MESSAGE = "Try another word, such as “respawn”, “tie” or “push”."
    const val LIST_SOURCE = "Quoted word for word from the official Guards of Atlantis II rulebook (2nd printing). " +
        "Page numbers are the rulebook's printed pages."
    const val TOPIC_SOURCE =
        "Source: the official Guards of Atlantis II rulebook, 2nd printing (printed page numbers)."
    const val UNAVAILABLE = "The bundled rules reference could not be read."

    /** "Rulebook pp. 4, 7 · quoted word for word". */
    fun topicPagesLine(pages: List<Int>): String = "Rulebook ${pages(pages)} · quoted word for word"
}
