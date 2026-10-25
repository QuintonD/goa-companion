package dev.quinton.goa

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Play against the computer from Boards (PRODUCT §9): a 2v2 on Forgotten Island, the person plays
 * Wasp, the computer Easy, Coach on. Play a card facedown, wait for the computer's three heroes to
 * play theirs, and see the reveal and the first question after it (the real engine and AI).
 */
@RunWith(AndroidJUnit4::class)
class PlayFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startAGameFromBoardsAndPlayThroughTheFirstReveal() {
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.tapHeroesAction("Board")
        rule.tap(hasText("Play against the computer") and hasClickAction())

        // Alternate picks: Red, Blue, Red, Blue.
        rule.waitForText("Tap heroes below")
        listOf("Wasp", "Brogan", "Arien", "Xargatha").forEach { hero ->
            rule.tap(hasContentDescription(hero) and hasClickAction())
        }
        rule.waitForText("2 v 2")
        rule.tap(hasText("Next") and hasClickAction() and isEnabled())

        // You play Wasp; the computer is Easy; Coach is on by default; Forgotten Island is the map.
        rule.waitForText("Game options")
        rule.tap(hasText("Wasp") and hasClickAction())
        rule.tapText("Easy")
        rule.reveal(hasText("Forgotten Island"))
        rule.tapText("Flip the Tie Breaker coin")
        rule.waitForText("shows", substring = true)
        rule.tapText("Start the game")

        // Turn 1: Wasp's hand as cards; the first tap opens the card focus viewer (it answers
        // nothing), then its "Play … facedown" button plays the card.
        rule.waitFor(hasText("Play a card facedown"), timeout = GAME_TIMEOUT)
        rule.tap(hasClickLabel("Look at the card"))
        rule.waitForText("Card 1 of", substring = true)
        rule.tap(
            hasText("facedown", substring = true) and hasText("Play ", substring = true) and
                hasClickAction() and isEnabled(),
        )

        // The computer plays its three cards, then all four are revealed together (p.7).
        rule.waitFor(hasText("Cards revealed:", substring = true), timeout = GAME_TIMEOUT)
        // After the reveal someone acts: Wasp is asked, or a computer hero is choosing.
        rule.waitUntilOrDump("the first card resolves", GAME_TIMEOUT) {
            rule.exists(hasText("WASP · ACTION", substring = true)) ||
                rule.exists(hasText(" is choosing", substring = true)) ||
                rule.exists(hasText(" is thinking", substring = true)) ||
                rule.exists(hasText("Choose an action for", substring = true))
        }
    }

    private companion object {
        /** The computer answers off the main thread at the chosen pace; emulators are slow. */
        const val GAME_TIMEOUT = 60_000L
    }
}
