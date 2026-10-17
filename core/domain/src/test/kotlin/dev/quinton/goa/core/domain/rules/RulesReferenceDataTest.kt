package dev.quinton.goa.core.domain.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.domain.timer.bundledAsset
import dev.quinton.goa.core.model.rules.RulesReference
import org.junit.Test

/**
 * Guards the shipped `rules-reference.json`. The numbers are the rulebook's tables (p.13, p.15,
 * p.23; the importer's `--rules-only` check matches them against the PDF); these tests make sure
 * the tables also agree with the rules they summarise.
 */
class RulesReferenceDataTest {
    private val reference = RulesReference.parse(bundledAsset("rules-reference.json"))
    private val levels = reference.ladder.levels

    @Test
    fun `levels 1 to 8, level up costs 1 to 7 summing to 28 coins for level 8`() {
        assertThat(levels.map { it.level }).containsExactly(1, 2, 3, 4, 5, 6, 7, 8).inOrder()
        assertThat(levels.first().levelUpCost).isNull()
        // p.15: "The first level up costs 1 coin. Each following level up costs 1 more coin than the previous one."
        assertThat(levels.drop(1).map { it.levelUpCost }).containsExactly(1, 2, 3, 4, 5, 6, 7).inOrder()
        assertThat(levels.sumOf { it.levelUpCost ?: 0 }).isEqualTo(28)
        // p.15: "A level 1 hero needs 3 coins to reach level 3"
        assertThat(levels.filter { it.level <= 3 }.sumOf { it.levelUpCost ?: 0 }).isEqualTo(3)
    }

    @Test
    fun `lowest and max card tiers follow the upgrade rule`() {
        // p.15-16: one upgrade per level, lowest non-basic card first, all three colours to Tier II
        // before Tier III, then the Ultimate (Tier IV) as the next level up. Starting hand: three Tier I.
        val hand = mutableListOf(1, 1, 1)
        var ultimate = false
        levels.forEach { row ->
            if (row.level > 1) {
                if (hand.all { it == 3 }) {
                    ultimate = true
                } else {
                    hand[hand.indexOf(hand.min())] += 1
                }
            }
            assertThat(row.lowestTier).isEqualTo(hand.min())
            val expectedMax = if (row.level == 1) {
                null
            } else if (ultimate) {
                4
            } else {
                hand.max()
            }
            assertThat(row.maxTier).isEqualTo(expectedMax)
        }
        assertThat(ultimate).isTrue()
    }

    @Test
    fun `a defeat pays the level, and assists and Life counters follow the lowest tier`() {
        levels.forEach { row ->
            // p.13: "collect coins equal to that hero's level"
            assertThat(row.defeatCoins).isEqualTo(row.level)
            // p.13: assist coins "based on the defeated hero's lowest card Tier" (table: 1/1/1/2/2/2/3/3)
            assertThat(row.assistCoins).isEqualTo(row.lowestTier)
            // p.13: "spends the number of Life counters, equal to that hero's lowest card Tier"
            assertThat(row.lifeCounters).isEqualTo(row.lowestTier)
        }
    }

    @Test
    fun `next level cost reads the following row`() {
        val ladder = reference.ladder
        assertThat(LadderLookup.nextLevelCost(ladder, 1)).isEqualTo(1)
        assertThat(LadderLookup.nextLevelCost(ladder, 4)).isEqualTo(4)
        assertThat(LadderLookup.nextLevelCost(ladder, 7)).isEqualTo(7)
        assertThat(LadderLookup.nextLevelCost(ladder, 8)).isNull()
    }

    @Test
    fun `every topic loads with verbatim excerpts and printed page refs`() {
        assertThat(reference.printing).isEqualTo(2)
        assertThat(reference.topics).isNotEmpty()
        assertThat(reference.topics.map { it.id }).containsNoDuplicates()
        val excerpts = reference.topics.flatMap { it.excerpts } + reference.ladder.allExcerpts
        excerpts.forEach { excerpt ->
            assertThat(excerpt.page).isIn(1..LAST_PRINTED_PAGE)
            assertThat(excerpt.text.isNotBlank()).isTrue()
        }
        reference.topics.forEach { topic ->
            assertThat(topic.excerpts).isNotEmpty()
            assertThat(topic.pages).isNotEmpty()
            assertThat(topic.pages.toSet()).isEqualTo(topic.excerpts.map { it.page }.toSet())
            topic.summary?.let { assertThat(it.isNotBlank()).isTrue() }
        }
        assertThat(reference.ladder.pages).containsExactly(13, 15, 23)
    }

    @Test
    fun `search matches titles, keywords and quoted text, ignoring typographic quotes`() {
        val topics = reference.topics
        assertThat(RulesSearch.filter(topics, "").size).isEqualTo(topics.size)
        assertThat(RulesSearch.filter(topics, "respawn").map { it.id }).contains("hero-respawn")
        assertThat(RulesSearch.filter(topics, "tie breaker").map { it.id }).contains("turn-sequence")
        assertThat(RulesSearch.filter(topics, "hero's lowest card tier").map { it.id }).contains("attack-defense")
        assertThat(RulesSearch.filter(topics, "zzzz nothing")).isEmpty()
    }

    private companion object {
        /** The rulebook's last printed page (the PDF has 24 pages: cover + printed pages 1-23). */
        const val LAST_PRINTED_PAGE = 23
    }
}
