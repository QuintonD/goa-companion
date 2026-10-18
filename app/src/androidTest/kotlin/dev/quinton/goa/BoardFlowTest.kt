package dev.quinton.goa

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Board sandbox from the Heroes tab: New board with two heroes a side, Set up (the opening), select a
 * hero and move it, then the board is in the Boards list. Every space on the map is a TalkBack node
 * whose click does what a tap on it does, so the test drives the map the way TalkBack would.
 */
@RunWith(AndroidJUnit4::class)
class BoardFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun setUpABoardMoveAHeroAndFindItInBoards() {
        rule.waitForText("32 heroes")
        rule.tapHeroesAction("Board")

        // Boards: nothing yet.
        rule.waitForText("No boards yet")
        rule.tapText("New board")

        // New board: Red is being filled first, in pick order.
        rule.waitForText("Adding here")
        rule.tap(hasContentDescription("Arien") and hasClickLabel("Add to Red"))
        rule.tap(hasContentDescription("Brogan") and hasClickLabel("Add to Red"))
        rule.tap(hasText("Blue") and hasRole(Role.RadioButton))
        rule.tap(hasContentDescription("Wasp") and hasClickLabel("Add to Blue"))
        rule.tap(hasContentDescription("Tigerclaw") and hasClickLabel("Add to Blue"))
        rule.waitForText("2 vs 2")
        rule.tapText("Set up")

        // The board opens on the opening: Arien stands on a Red hero Spawn point in the Red Throne Zone.
        val arien = hasContentDescription("Arien, Red, Red Throne Zone", substring = true)
        rule.waitFor(arien and hasClickLabel("Select"))
        rule.tap(arien and hasClickLabel("Select"))
        rule.waitFor(arien and isSelected())

        // Move: any empty space offers "Move here" while a piece is selected.
        rule.tap(hasClickLabel("Move here"))
        rule.waitFor(hasContentDescription("Undo") and isEnabled())

        // Back to Boards: the board is saved under its line-up.
        rule.tapDescription("Back")
        rule.waitForText("Arien, Brogan vs Wasp, Tigerclaw")
        rule.waitForText("Changed today", substring = true)
    }
}
