package dev.quinton.goa

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Game night, part 1: look a hero up in the real bundled catalog. */
@RunWith(AndroidJUnit4::class)
class CatalogFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun catalogListsAllHeroesAndArienDetailAndCardsSwipe() {
        rule.waitForText("32 heroes")

        rule.onNode(hasSetTextAction()).performTextInput("Arien")
        rule.waitForText("1 of 32 heroes")
        rule.onNode(hasSetTextAction()).performImeAction()
        rule.tap(hasText("Arien") and hasClickAction() and !hasSetTextAction())

        // Hero detail header: complexity name and the four official role chips.
        rule.waitForText("the Tidemaster")
        rule.waitForText("Challenging")
        listOf("Tactician", "Disabler", "Durable", "Pusher").forEach { role ->
            rule.onAllNodes(hasText(role)).onFirst().assertExistsCompat(role)
        }

        // Open a card from the deck and swipe to the next one.
        rule.tap(hasClickLabel("View card"))
        rule.waitForText("Arien · ", substring = true)
        val (first, total) = pagerPosition()
        assertEquals(19, total)
        rule.reveal(hasCustomAction("Next card")).performTouchInput { swipeLeft() }
        rule.waitUntil(TIMEOUT) { pagerPosition().first == first + 1 }
        assertTrue(pagerPosition().first == first + 1)
    }

    private fun pagerPosition(): Pair<Int, Int> {
        val pattern = Regex("""Arien · (\d+) of (\d+)""")
        val text = rule.onAllNodes(hasText("Arien · ", substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.texts }
            .firstNotNullOf { pattern.find(it) }
        return text.groupValues[1].toInt() to text.groupValues[2].toInt()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat(what: String) {
    runCatching { assertExists() }.onFailure { throw AssertionError("Missing on hero detail: $what", it) }
}
