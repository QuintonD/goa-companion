package dev.quinton.goa

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Play against the computer (PRODUCT §9) on the real Hilt graph, engine and catalog: Boards › New
 * game, the rulebook's first line-up (Wasp and Xargatha against Arien and Brogan, picks alternating),
 * Start with the defaults (you play Wasp), play a card facedown, see the reveal, answer the next
 * question, leave and find the game in Boards to resume.
 */
@RunWith(AndroidJUnit4::class)
class PlayFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startAGameAgainstTheComputerPlayACardAndResumeItFromBoards() {
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.tapHeroesAction("Board")
        rule.tapText("New game")

        // Picks alternate like a draft: Wasp (Red), Arien (Blue), Xargatha (Red), Brogan (Blue).
        listOf("Wasp" to "Red", "Arien" to "Blue", "Xargatha" to "Red", "Brogan" to "Blue").forEach { (hero, team) ->
            rule.tap(hasContentDescription(hero) and hasClickLabel("Add to $team"))
        }
        rule.tapText("Next")
        rule.waitForText("Who plays")
        rule.tapText("Start")

        // The first question is the player's: play a card facedown (p.7).
        rule.waitForText("Play a card facedown", timeout = GAME_TIMEOUT)
        rule.waitFor(hasClickLabel("Pick"))
        rule.onAllNodes(hasClickLabel("Pick"))[0].performClick()
        rule.tap(hasText("facedown", substring = true) and hasClickAction())

        // The computer plays its cards; the reveal waits for Continue.
        rule.waitForText("CARDS REVEALED · p.7", timeout = GAME_TIMEOUT)
        rule.tapText("Continue")
        rule.waitFor(hasText("· YOU ·", substring = true), timeout = GAME_TIMEOUT)

        // Saved as it goes: back in Boards it can be resumed.
        rule.tapDescription("Back")
        rule.waitForText("Resume")
        rule.tapText("Resume")
        rule.waitFor(hasText("· YOU ·", substring = true), timeout = GAME_TIMEOUT)
    }

    private companion object {
        const val GAME_TIMEOUT = 30_000L
    }
}
