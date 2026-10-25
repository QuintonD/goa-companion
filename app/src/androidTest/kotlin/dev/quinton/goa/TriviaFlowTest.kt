package dev.quinton.goa

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Card trivia from the Heroes tab: set up a quiz on one hero, play a 10-question round, see the summary. */
@RunWith(AndroidJUnit4::class)
class TriviaFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /** An answer button of a card-number question: its description is the number ("7", "4 plus"). */
    private val numberChoice = SemanticsMatcher("a number answer") { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null &&
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                .any { Regex("""\d+( plus)?""").matches(it) }
    }

    @Test
    fun quizOneHeroThroughToTheSummary() {
        rule.waitForText("32 heroes")
        rule.tapHeroesAction("Card trivia")

        // Home: overall progress and the setup.
        rule.waitForText("numbers learned", substring = true)
        rule.waitForText("New quiz")

        // Heroes → only Arien.
        rule.tapDescription("Heroes: ", substring = true)
        rule.waitForText("Heroes to quiz")
        rule.onNode(hasSetTextAction()).performTextInput("Arien")
        rule.tap(hasText("Arien") and hasRole(Role.Checkbox))
        rule.waitForText("1 hero chosen")
        rule.tapText("Done")
        rule.waitForDescription("Heroes: Arien", substring = true)
        rule.reveal(hasText("in this quiz", substring = true))

        rule.tapText("Start quiz")
        for (question in 1..ROUND) {
            rule.waitForText("Question $question of $ROUND")
            if (rule.exists(hasText("Same initiative") and hasClickAction())) {
                // An initiative duel: any pick will do here.
                rule.tapText("Same initiative")
            } else {
                rule.tap(numberChoice)
            }
            // The answer is revealed: right or wrong, the feedback names the printed number.
            val last = question == ROUND
            rule.tapText(if (last) "See results" else "Next")
        }

        rule.waitForText("Round over")
        rule.waitForText("of $ROUND right", substring = true)
        rule.waitForDescription("Best streak", substring = true)
        rule.tapText("Done")

        // Back on the home (where it was scrolled to), the setup still quizzes Arien and the
        // round's card-number answers were saved: some numbers are now being learned.
        rule.reveal(hasText("New quiz"))
        rule.reveal(hasContentDescription("Heroes: Arien", substring = true))
        val learningSome =
            !hasContentDescription("Learning: 0") and hasContentDescription("Learning: ", substring = true)
        rule.reveal(learningSome)
    }

    private companion object {
        const val ROUND = 10
    }
}
