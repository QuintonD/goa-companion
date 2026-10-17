package dev.quinton.goa

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Game night, part 2: the group's player profiles. */
@RunWith(AndroidJUnit4::class)
class PlayersFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createFourPlayersAndRejectDuplicateName() {
        listOf("Ana", "Ben", "Cal", "Dee").forEach { rule.createPlayerViaProfile(it) }
        assertEquals(listOf("Ana", "Ben", "Cal", "Dee"), rule.leaderboardRows().map { parseRow(it).name }.sorted())

        // Names are unique, trimmed and case-insensitive (DOMAIN.md).
        rule.tapLabel("Add player")
        rule.waitForText("Name")
        rule.typeInto("Name", " ana ")
        rule.tapText("Save")
        rule.waitForText("is already in your group", substring = true)
        // Still on the editor; nothing was saved.
        rule.tapDescription("Back")
        if (rule.exists(hasText("Discard changes?"))) rule.tapText("Discard")
        rule.waitForDescription("Ana, no games yet", substring = true)
        val rows = rule.leaderboardRows().map { parseRow(it).name }
        assertEquals(4, rows.size)
        assertTrue(rows.count { it.equals("ana", ignoreCase = true) } == 1)
    }
}
