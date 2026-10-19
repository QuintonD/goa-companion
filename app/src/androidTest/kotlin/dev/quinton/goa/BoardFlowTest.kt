package dev.quinton.goa

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Board sandbox from a finished draft (PRODUCT §8): a House draft 2v2, "Try on the board" (one
 * tap), the opening with the first Red pick on a Red hero Spawn point; select that hero and move it
 * through the TalkBack action of a named space right next to it, then find the board in Boards.
 * Every space in view is a node whose click does what a tap on it does, so the test drives the map
 * the way TalkBack would.
 */
@RunWith(AndroidJUnit4::class)
class BoardFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tryADraftOnTheBoardMoveAHeroAndFindItInBoards() {
        val redPicks = draftTwoVersusTwo()

        // Draft complete: "Try on the board" sets up the opening in one tap.
        rule.tap(hasText("Try on the board") and hasClickAction())

        // The first Red pick stands on a Red hero Spawn point in the Red Throne Zone.
        val hero = redPicks.first()
        val onBoard = hasContentDescription("$hero, Red, Red Throne Zone", substring = true)
        rule.waitFor(onBoard and hasClickLabel("Select"))
        rule.tap(onBoard and hasClickLabel("Select"))
        rule.waitFor(onBoard and isSelected())

        // Move it one space: an empty space named as 1 space from the hero, offering "Move here".
        val nextTo = hasContentDescription("Empty space", substring = true) and
            hasContentDescription("1 space from $hero", substring = true) and
            hasClickLabel("Move here")
        rule.tap(nextTo)
        rule.waitFor(hasContentDescription("$hero moved 1 space", substring = true))
        rule.waitFor(hasContentDescription("Undo") and isEnabled())

        // Back to the draft, then Heroes › Board: the board is saved under its line-up.
        rule.tapDescription("Back")
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.tapHeroesAction("Board")
        rule.waitForText("Changed today", substring = true)
        rule.waitFor(hasText(hero, substring = true) and hasText(" vs ", substring = true))
    }

    /** A House draft with four players, 2v2, picks taken from the top of the pool; returns Red's picks in order. */
    private fun draftTwoVersusTwo(): List<String> {
        grantNotifications()
        rule.openTab("draft")
        rule.waitForText("Quick add a player")
        listOf("Ana", "Ben", "Cal", "Dee").forEach { name ->
            rule.typeInto("Quick add a player", name)
            rule.tapDescription("Add player")
            rule.reveal(hasText(name))
        }
        rule.reveal(hasText("4 selected", substring = true))
        rule.tapText("Next: Teams")
        rule.waitForText("2 players · 2 heroes", substring = true)
        rule.tapText("Next: Heroes")
        rule.tap(hasText("House draft") and hasClickAction())
        rule.tapText("Next: Tie Breaker")
        rule.tapText("Flip the coin")
        rule.waitForText("is Team A", substring = true)
        val teamA = if (rule.exists(hasText("◆ Red is Team A"))) "◆" else "●"
        rule.tap(hasText("Start draft", substring = true) and hasClickAction())

        // House draft 2v2: Ban A · Pick A · Ban B · Pick B · Pick B · Pick A.
        val steps = listOf(
            "Ban" to true,
            "Pick" to true,
            "Ban" to false,
            "Pick" to false,
            "Pick" to false,
            "Pick" to true,
        )
        val redPicks = mutableListOf<String>()
        steps.forEach { (action, byTeamA) ->
            val glyph = if (byTeamA) {
                teamA
            } else if (teamA == "◆") {
                "●"
            } else {
                "◆"
            }
            val tile = hasClickLabelStartingWith("Choose ")
            rule.reveal(tile)
            val chosen = rule.onAllNodes(tile).fetchSemanticsNodes().first().descriptions.first()
            rule.tap(hasContentDescription(chosen) and tile)
            rule.waitForText("$action $chosen?")
            rule.tap(hasAnyAncestor(isDialog()) and hasClickAction() and hasText("$glyph $action"))
            rule.waitGone(hasText("$action $chosen?"))
            if (action == "Pick" && glyph == "◆") redPicks += chosen
        }
        assertEquals(2, redPicks.size)
        rule.reveal(hasText("Try on the board"))
        return redPicks
    }
}
