package dev.quinton.goa

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Play against the computer from the Play tab (PRODUCT §9): a 2v2 on Forgotten Island, the person plays
 * Wasp, the computer at strength 2 (Easy), Coach on. Play a card facedown, wait for the computer's three heroes to
 * play theirs, and see the reveal and the first question after it (the real engine and AI).
 */
@RunWith(AndroidJUnit4::class)
class PlayFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startAGameFromBoardsAndPlayThroughTheFirstReveal() {
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.openFromPlay("Play vs computer")

        // Alternate picks: Red, Blue, Red, Blue.
        rule.waitForText("Tap heroes below")
        listOf("Wasp", "Brogan", "Arien", "Xargatha").forEach { hero ->
            rule.tap(hasContentDescription(hero) and hasClickAction())
        }
        rule.waitForText("2 v 2")
        rule.tap(hasText("Next") and hasClickAction() and isEnabled())

        // You play Wasp; the computer is Easy (strength 2); Coach is on by default; Forgotten Island is the map.
        rule.waitForText("Game options")
        rule.tapText("One hero")
        rule.tap(hasText("Wasp") and hasClickAction())
        rule.reveal(hasContentDescription("Computer strength"))
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(2f) }
        rule.waitForText("2 · Easy")
        rule.reveal(hasText("Forgotten Island"))
        rule.tapText("Start game · flip the coin")
        rule.waitForText("shows", substring = true)
        rule.tapText("Start the game")

        // Turn 1: Wasp's hand as cards; a tap chooses one (it answers nothing), then the bronze
        // "Play … facedown" button under the hand plays it.
        rule.waitFor(hasText("Play a card facedown"), timeout = GAME_TIMEOUT)
        rule.tap(hasClickLabel("Choose"))
        rule.tap(
            hasText("facedown", substring = true) and hasText("Play ", substring = true) and
                hasClickAction() and isEnabled(),
        )

        // The computer plays its three cards, then all four are revealed together (p.7): the turn
        // track shows the played cards in acting order (ADR-041).
        rule.waitFor(hasContentDescription("1st to act", substring = true), timeout = GAME_TIMEOUT)
        // After the reveal someone acts: Wasp is asked, or a computer hero is choosing.
        rule.waitUntilOrDump("the first card resolves", GAME_TIMEOUT) {
            rule.exists(hasContentDescription("You choose · Wasp. Choose an action", substring = true)) ||
                rule.exists(hasContentDescription(" acting. ", substring = true)) ||
                rule.exists(hasText(" is choosing", substring = true)) ||
                rule.exists(hasText(" is thinking", substring = true)) ||
                rule.exists(hasContentDescription("Choose an action for", substring = true))
        }
    }

    private companion object {
        /** The computer answers off the main thread at the chosen pace; emulators are slow. */
        const val GAME_TIMEOUT = 60_000L
    }
}
