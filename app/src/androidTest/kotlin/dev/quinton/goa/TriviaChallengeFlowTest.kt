package dev.quinton.goa

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The trivia leaderboard end to end (ADR-032): add a player, play the Challenge as them (all 20
 * questions), see their place on the summary, and find them on the leaderboard.
 */
@RunWith(AndroidJUnit4::class)
class TriviaChallengeFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** An answer button of a card-number question: its description is the number ("7", "4 plus"). */
    private val numberChoice = SemanticsMatcher("a number answer") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { Regex("""\d+( plus)?""").matches(it) }
    }

    @Test
    fun aChallengePlayedAsAPlayerPutsThemOnTheLeaderboard() {
        rule.createPlayerViaProfile("Sam")
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.tapDescription("Card trivia")

        // A fresh phone plays as Guest until someone is picked.
        rule.waitForDescription("Playing as Guest", substring = true)
        rule.tapDescription("Playing as Guest", substring = true)
        rule.waitForText("Who's playing?")
        rule.tap(hasContentDescription("Sam") and hasClickAction())
        rule.waitForDescription("Playing as Sam", substring = true)

        rule.tapText("Challenge")
        for (question in 1..ROUND) {
            rule.waitForText("Question $question of $ROUND")
            if (rule.exists(hasText("Same initiative") and hasClickAction())) {
                rule.tapText("Same initiative")
            } else {
                rule.tap(numberChoice)
            }
            rule.tapText(if (question == ROUND) "See results" else "Next")
        }

        // The summary: Sam's score, answer time and place (the only player on the board: 1st of 1).
        rule.waitForText("Round over")
        rule.waitForText("CHALLENGE · SAM")
        rule.waitForText("of $ROUND right", substring = true)
        rule.waitForDescription("Answer time", substring = true)
        rule.waitForText("1st of 1", substring = true)

        rule.tapText("Leaderboard")
        rule.waitFor(hasContentDescription("1st place, Sam", substring = true))
        rule.waitFor(hasText("/$ROUND", substring = true))

        // Sam's rounds open from the row, each removable.
        rule.tapDescription("1st place, Sam", substring = true)
        rule.waitForText("Remove from leaderboard")
    }

    private companion object {
        const val ROUND = 20
    }
}
