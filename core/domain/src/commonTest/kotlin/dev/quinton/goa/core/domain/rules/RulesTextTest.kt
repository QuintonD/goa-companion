package dev.quinton.goa.core.domain.rules

import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.rules.LevelRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules screens' words, the same on the phone and in the browser. */
class RulesTextTest {
    private val level1 = LevelRow(1, lowestTier = 1, defeatCoins = 1, assistCoins = 1, lifeCounters = 1, null, null)
    private val level2 = LevelRow(2, lowestTier = 1, defeatCoins = 2, assistCoins = 1, lifeCounters = 1, 2, 2)

    @Test
    fun pagesAreThePrintedPages() {
        assertEquals("p. 13", RulesText.pages(listOf(13)))
        assertEquals("pp. 4, 7", RulesText.pages(listOf(4, 7)))
        assertEquals("rulebook pages 13, 15 and 23", RulesText.spokenPages(listOf(13, 15, 23)))
    }

    @Test
    fun countsAgreeWithTheirNouns() {
        assertTrue(
            RulesText.spokenLadderRow(level1, 2).contains("Defeat: 1 coin, 1 assist coin, team spends 1 Life counter."),
        )
        assertTrue(RulesText.spokenLadderRow(level2, 3).contains("Next level costs 3 coins."))
        assertTrue(RulesText.spokenLadderRow(level2, null).contains("Top level. Max card Tier 2."))
        assertTrue(RulesText.spokenDefeat(level1).contains("collects 1 coin."))
    }

    @Test
    fun excerptLinesKeepThePrintedListMarkers() {
        val lines = RulesText.excerptLines("Intro:\n• one\n2. two\n\nend")

        assertEquals(
            listOf(
                ExcerptLine.Paragraph("Intro:"),
                ExcerptLine.Bullet("one"),
                ExcerptLine.Numbered("2", "two"),
                ExcerptLine.Paragraph("end"),
            ),
            lines,
        )
    }

    @Test
    fun aTableRowIsReadWithItsColumnNames() {
        assertEquals(
            "6-8 Players, Wave Counters 2 x 7",
            RulesText.spokenTableRow(listOf("Players", "Wave Counters"), listOf("6-8 Players", "2 x 7")),
        )
    }

    @Test
    fun ladderWordsFindTheLadder() {
        assertTrue(RulesText.showsLadder(""))
        assertTrue(RulesText.showsLadder("Assist coins"))
        assertFalse(RulesText.showsLadder("respawn"))
    }

    @Test
    fun anIconFollowedByItsOwnNameIsReadOnce() {
        val segments = CardText.parse("spends 1 additional {life} Life counter.")
        val icon = segments.indexOfFirst { it is CardText.Segment.Icon }

        assertEquals(" ", RulesText.spokenIconLabel("Life counter", segments.getOrNull(icon + 1)))
        assertEquals("Coin", RulesText.spokenIconLabel("Coin", CardText.Segment.Text(" to the bank")))
    }
}
