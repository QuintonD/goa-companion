package dev.quinton.goa.feature.draft

import dev.quinton.goa.core.domain.draft.TeamSlot
import dev.quinton.goa.feature.draft.ui.coinTeamWords
import dev.quinton.goa.feature.draft.ui.slotName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CoinTeamWordsTest {
    @Test fun namesTheMethodsTeamsAsTheTableKnowsThem() {
        assertEquals(
            "Pool of 15. The coin team bans 1 and picks 1, the other team bans 1 and picks 2, then teams alternate " +
                "2 picks; the last pick is single.",
            coinTeamWords(
                "Pool of 15. Team A bans 1 and picks 1, Team B bans 1 and picks 2, then teams alternate 2 picks; " +
                    "the last pick is single.",
            ),
        )
        assertEquals(
            "Pick from every available hero, the coin team first, alternating teams.",
            coinTeamWords("Pick from every available hero, Team A first, alternating teams."),
        )
    }

    @Test fun everyShippedSummaryLosesTheLetters() {
        bundledMethods.methods.forEach { method ->
            val words = coinTeamWords(method.summary)
            assertFalse(words, words.contains("Team A") || words.contains("Team B"))
        }
    }

    @Test fun slotNames() {
        assertEquals("Coin team", slotName(TeamSlot.A))
        assertEquals("Other team", slotName(TeamSlot.B))
    }
}
