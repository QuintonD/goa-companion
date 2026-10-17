package dev.quinton.goa.core.model.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The quick rules reference, `assets/catalog/rules-reference.json` (docs/DOMAIN.md, "Rules
 * reference"). Hand-curated, but every rules sentence is quoted verbatim from the pinned official
 * rulebook and `tools/catalog-import --rules-only` checks each excerpt against the PDF's text on
 * its page, so a reworded rulebook (e.g. a 3rd printing) fails loudly instead of drifting.
 * Page numbers are the rulebook's PRINTED page numbers (PDF page = printed page + 1).
 */

@Serializable
data class RulesReference(
    val schemaVersion: Int,
    /** Rulebook printing the excerpts were taken from (2 = 2nd printing). */
    val printing: Int,
    /** Id of the pinned rulebook in `tools/catalog-import/sources.lock.json` (`documents`). */
    val rulebook: String,
    val ladder: LevelLadder,
    val topics: List<RulesTopic>,
) {
    companion object {
        const val PATH = "catalog/rules-reference.json"

        private val json = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

        fun parse(text: String): RulesReference = json.decodeFromString(serializer(), text)
    }
}

/**
 * The level and defeat tables: p.13 (lowest card Tier, coins for a defeat, assist coins), p.15
 * (level up cost, max card Tier) and the p.23 summary (assist coins / Life counters). Numbers are
 * data, never computed. The excerpts are the verbatim rules shown with the table.
 */
@Serializable
data class LevelLadder(
    val pages: List<Int>,
    val levels: List<LevelRow>,
    /** What a defeat costs and pays, incl. minion coins. */
    val defeat: List<RulesExcerpt>,
    /** Levelling up at the end of the round. */
    val levelUp: List<RulesExcerpt>,
    /** Card text beats the rules (p.3): shown above the card exceptions. */
    val cardText: RulesExcerpt,
) {
    val allExcerpts: List<RulesExcerpt> get() = defeat + levelUp + cardText
}

@Serializable
data class LevelRow(
    val level: Int,
    /** "Lowest card Tier" (p.13): 1 = I, 2 = II, 3 = III. */
    val lowestTier: Int,
    /** "Coins (Defeat)" (p.13): coins the attacker collects for defeating a hero of this level. */
    val defeatCoins: Int,
    /** "Assist Coins" (p.13): collected by the defeated hero's enemies' friendly heroes. */
    val assistCoins: Int,
    /** "Life counters" (p.23): spent by the defeated hero's team (= lowest card Tier, p.13). */
    val lifeCounters: Int,
    /** "Level Up cost" (p.15): coins to reach THIS level from the one below; null at level 1 ("-"). */
    val levelUpCost: Int?,
    /** "Max card Tier" (p.15): 4 = IV (the Ultimate); null at level 1 ("-"). */
    val maxTier: Int?,
)

@Serializable
data class RulesTopic(
    val id: String,
    val title: String,
    /** Printed pages the excerpts come from, for the topic list. */
    val pages: List<Int>,
    /** Extra search words (not shown). */
    val keywords: List<String> = emptyList(),
    /** Optional one-line orientation in our own words; always shown labelled "Summary". */
    val summary: String? = null,
    val excerpts: List<RulesExcerpt>,
)

/**
 * A verbatim rulebook quote. [text] keeps the printed list markers: `\n` separates paragraphs and
 * list items, and a line starting with "• " is a bullet item ("1. " a numbered one).
 */
@Serializable
data class RulesExcerpt(
    val page: Int,
    /** The rulebook's own heading for the passage, if any (also verbatim). */
    val heading: String? = null,
    val text: String,
)
