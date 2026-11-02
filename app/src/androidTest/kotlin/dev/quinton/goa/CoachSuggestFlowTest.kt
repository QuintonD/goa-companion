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
 * The coach's suggestions on the real engine (ADR-051, PRODUCT §9): a 3v3 against the computer at
 * strength 2. Suggest at the first card play opens the sheet with ranked options and the hero's tips,
 * closing it leaves the question unanswered, and the card can still be played.
 */
@RunWith(AndroidJUnit4::class)
class CoachSuggestFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun suggestAtTheFirstCardPlayAnswersNothing() {
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.openFromPlay("Board")
        rule.tap(hasText("Play against the computer") and hasClickAction())

        rule.waitForText("Tap heroes below")
        listOf("Wasp", "Brogan", "Arien", "Xargatha", "Tigerclaw", "Sabina").forEach { hero ->
            rule.tap(hasContentDescription(hero) and hasClickAction())
        }
        rule.waitForText("3 v 3")
        rule.tap(hasText("Next") and hasClickAction() and isEnabled())

        rule.waitForText("Game options")
        rule.tap(hasText("Wasp") and hasClickAction())
        rule.reveal(hasContentDescription("Computer strength"))
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(2f) }
        rule.waitForText("2 · Easy")
        rule.reveal(hasText("Forgotten Island"))
        rule.tapText("Flip the Tie Breaker coin")
        rule.waitForText("shows", substring = true)
        rule.tapText("Start the game")

        rule.waitFor(hasText("Play a card facedown"), timeout = GAME_TIMEOUT)
        rule.tap(hasText("Suggest") and hasClickAction() and isEnabled())
        rule.waitFor(hasContentDescription("to win", substring = true), timeout = GAME_TIMEOUT)
        rule.waitFor(hasText("TIPS FOR WASP"), timeout = GAME_TIMEOUT)
        rule.tapText("Done")

        // Nothing was answered: the question is still there, and the card can be played.
        rule.waitFor(hasText("Play a card facedown"), timeout = GAME_TIMEOUT)
        rule.tap(hasClickLabel("Look at the card"))
        rule.waitForText("Card 1 of", substring = true)
        rule.tap(
            hasText("facedown", substring = true) and hasText("Play ", substring = true) and
                hasClickAction() and isEnabled(),
        )
        rule.waitFor(hasContentDescription("1st to act", substring = true), timeout = GAME_TIMEOUT)
    }

    private companion object {
        const val GAME_TIMEOUT = 60_000L
    }
}
