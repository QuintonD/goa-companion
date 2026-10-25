package dev.quinton.goa.feature.board.ui.cards

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.bundledCatalog
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card focus viewer's gestures and accessibility (docs/DESIGN.md, "Card focus"), with real
 * catalog cards: a first tap focuses, a second tap confirms the card on show, back / an upward
 * swipe / the scrim / × close with nothing chosen, a sideways swipe moves to the next card, a card
 * that isn't an option can't be confirmed, and TalkBack has Select, Next card, Previous card, Close.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CardFocusTest {
    @get:Rule val compose = createComposeRule()

    private val cards: List<Card> = bundledCatalog.heroes.first { it.id == "wasp" }.allCards.take(4)
    private val confirmed = mutableListOf<Int>()
    private lateinit var state: CardFocusState

    /** A row of four thumbnails; tapping one opens the viewer on it (with a selection unless [viewOnly]). */
    private fun row(viewOnly: Boolean = false, disabled: Set<Int> = emptySet()) {
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                state = rememberCardFocusState()
                val focusCards = cards.mapIndexed { i, card ->
                    FocusCard(card, enabled = i !in disabled, reason = "Not one of the cards this question allows.")
                }
                Row {
                    cards.forEachIndexed { i, card ->
                        Box(
                            Modifier
                                .size(60.dp, 84.dp)
                                .cardFocusOrigin(state, card.id)
                                .testTag("thumb$i")
                                .clickable {
                                    state.open(
                                        focusCards,
                                        i,
                                        selection = if (viewOnly) {
                                            null
                                        } else {
                                            CardSelection(action = { "Discard ${it.name}" }) { confirmed += it }
                                        },
                                        title = "Wasp · Card",
                                    )
                                },
                        )
                    }
                }
                CardFocusHost(state)
            }
        }
    }

    private fun card(): SemanticsNodeInteraction = compose.onNodeWithTag(CARD_TAG)

    private fun shownName(): String = card().fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()

    private fun customAction(label: String) {
        val action = card().fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    private fun customActionLabels(): List<String> =
        card().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    @Test
    fun `the first tap focuses the card and chooses nothing`() {
        row()
        compose.onNodeWithTag("thumb1").performClick()
        compose.waitForIdle()
        assertThat(state.isOpen).isTrue()
        assertThat(shownName()).startsWith(cards[1].name)
        compose.onNodeWithText("Tap the card again to discard ${cards[1].name}").assertExists()
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `a second tap on the focused card confirms it, then the viewer closes`() {
        row()
        compose.onNodeWithTag("thumb2").performClick()
        card().performClick()
        compose.waitForIdle()
        assertThat(confirmed).containsExactly(2)
        assertThat(state.isOpen).isFalse()
    }

    @Test
    fun `the Confirm button does what the second tap does`() {
        row()
        compose.onNodeWithTag("thumb0").performClick()
        compose.onNodeWithTag(CONFIRM_TAG).performClick()
        compose.waitForIdle()
        assertThat(confirmed).containsExactly(0)
    }

    @Test
    fun `back closes with nothing chosen`() {
        row()
        compose.onNodeWithTag("thumb1").performClick()
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `predictive back follows the gesture, a cancelled one keeps the card, a finished one closes`() {
        row()
        compose.onNodeWithTag("thumb1").performClick()
        compose.waitForIdle()
        val dispatcher = dialogBackDispatcher()
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(80f, 400f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        assertThat(state.backProgress).isWithin(0.01f).of(0.5f)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertThat(state.isOpen).isTrue()
        assertThat(state.backProgress).isEqualTo(0f)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 400f, 0.7f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `an upward swipe on the card closes with nothing chosen`() {
        row()
        compose.onNodeWithTag("thumb1").performClick()
        card().performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `the scrim and the close button close with nothing chosen`() {
        row()
        compose.onNodeWithTag("thumb1").performClick()
        // Beside the card (its middle is the card itself, which a tap would choose).
        compose.onNodeWithTag(SCRIM_TAG).performTouchInput { click(Offset(width - 8f, 8f)) }
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        compose.onNodeWithTag("thumb1").performClick()
        compose.onNodeWithTag(CLOSE_TAG).performClick()
        compose.waitForIdle()
        assertThat(state.isOpen).isFalse()
        assertThat(confirmed).isEmpty()
    }

    @Test
    fun `a sideways swipe shows the next card, and the second tap confirms that one`() {
        row()
        compose.onNodeWithTag("thumb0").performClick()
        card().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertThat(state.index).isEqualTo(1)
        assertThat(shownName()).startsWith(cards[1].name)
        card().performClick()
        compose.waitForIdle()
        assertThat(confirmed).containsExactly(1)
    }

    @Test
    fun `a card that isn't an option is shown, says why, and can't be confirmed`() {
        row(disabled = setOf(1))
        compose.onNodeWithTag("thumb1").performClick()
        card().performClick()
        compose.waitForIdle()
        assertThat(confirmed).isEmpty()
        assertThat(state.isOpen).isTrue()
        compose.onNodeWithText("Not one of the cards this question allows.").assertExists()
        compose.onNodeWithTag(CONFIRM_TAG).assertIsNotEnabled()
        assertThat(customActionLabels()).doesNotContain("Select")
    }

    @Test
    fun `TalkBack has Select, Next card, Previous card and Close`() {
        row()
        compose.onNodeWithTag("thumb1").performClick()
        compose.waitForIdle()
        assertThat(customActionLabels()).containsExactly("Select", "Next card", "Previous card", "Close").inOrder()
        assertThat(card().fetchSemanticsNode().config[SemanticsActions.OnClick].label)
            .isEqualTo("Discard ${cards[1].name}")
        customAction("Next card")
        assertThat(state.index).isEqualTo(2)
        customAction("Previous card")
        customAction("Previous card")
        assertThat(state.index).isEqualTo(0)
        assertThat(customActionLabels()).doesNotContain("Previous card")
        customAction("Select")
        assertThat(confirmed).containsExactly(0)
        compose.onNodeWithTag("thumb3").performClick()
        compose.waitForIdle()
        customAction("Close")
        assertThat(state.isOpen).isFalse()
        assertThat(confirmed).containsExactly(0)
    }

    @Test
    fun `view only - a tap on the card chooses nothing, no Confirm, no Select`() {
        row(viewOnly = true)
        compose.onNodeWithTag("thumb2").performClick()
        card().performClick()
        compose.waitForIdle()
        assertThat(state.isOpen).isTrue()
        compose.onNodeWithTag(CONFIRM_TAG).assertDoesNotExist()
        assertThat(customActionLabels()).containsExactly("Next card", "Previous card", "Close").inOrder()
        card().performSemanticsActionIfAny()
        assertThat(confirmed).isEmpty()
    }

    /** View only, the card has no click action for TalkBack at all. */
    private fun SemanticsNodeInteraction.performSemanticsActionIfAny() {
        if (SemanticsActions.OnClick in fetchSemanticsNode().config) performSemanticsAction(SemanticsActions.OnClick)
    }

    /** The viewer window's own back dispatcher (it is a dialog window). */
    private fun dialogBackDispatcher(): OnBackPressedDispatcher {
        var dispatcher: OnBackPressedDispatcher? = null
        Espresso.onView(isRoot()).inRoot(isDialog()).perform(
            object : ViewAction {
                override fun getConstraints(): Matcher<android.view.View> = isRoot()

                override fun getDescription() = "find the back dispatcher"

                override fun perform(uiController: UiController, view: android.view.View) {
                    dispatcher = view.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher
                }
            },
        )
        return checkNotNull(dispatcher) { "No back dispatcher in the viewer's window" }
    }
}
