package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import dev.quinton.goa.feature.board.play.table.promptParts
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The panel at the end of a round (hands-on re-check, findings 1 and 2; UI hill climb round 3): the round's summary
 * is one folded row over the question, so the question is in view and the summary never cut under it, and a new
 * question starts at the top of the panel while the same one keeps where the person scrolled.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w384dp-h832dp-xxhdpi")
class PanelOrderTest {
    @get:Rule val compose = createComposeRule()

    private var play by mutableStateOf<PlayUi?>(null)

    private fun show(first: PlayUi, fontScale: Float = 2f) {
        play = first
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = ThemeMode.DARK) {
                    GameScreen(
                        state = GameUiState.Playing(play!!),
                        onAction = {},
                        onTap = { false },
                        onDelete = {},
                        onNewGame = {},
                        onDone = {},
                        onBack = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun panel() = compose
        .onAllNodes(hasScrollAction() and hasAnyDescendant(hasContentDescription("End of round", substring = true)))
        .onFirst()

    private fun panelScroll(): Float =
        panel().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `minion battle - the round's summary is one folded row, the question and its list in view`() {
        val s = EndOfRoundGame.session()
        val play = GydionLevelUpGame.play(s)
        show(play)
        val screen = compose.onRoot().fetchSemanticsNode().size.height
        // A question about pieces never peeks: its numbered list is the decision (round 4 gate).
        compose.onAllNodes(hasContentDescription("Show the whole panel")).assertCountEquals(0)
        val moment = play.table.moment!!
        compose.onNodeWithContentDescription(moment.title).assertExists()
        compose.onAllNodes(hasText(moment.text, substring = true)).assertCountEquals(0)
        // The question is said once, in the panel's head (one TalkBack node).
        val head = compose.onAllNodes(hasContentDescription(promptParts(s.pending!!.prompt).text!!, substring = true))
        assertThat(head.onFirst().fetchSemanticsNode().positionInRoot.y).isLessThan(screen.toFloat())
        val row = compose.onAllNodes(hasContentDescription("spaces from", substring = true)).onFirst()
        assertThat(row.fetchSemanticsNode().positionInRoot.y).isLessThan(screen.toFloat())
        // A tap opens the steps.
        compose.onNodeWithContentDescription(moment.title).performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText(moment.text, substring = true)).onFirst().assertExists()
    }

    @Test
    fun `a new question starts at the top of the panel, the same question keeps its scroll`() {
        val s = EndOfRoundGame.session()
        val battle = GydionLevelUpGame.play(s)
        show(battle)
        panel().performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, SCROLL) }
        compose.waitForIdle()
        val scrolled = panelScroll()
        assertThat(scrolled).isGreaterThan(0f)

        // The same question composed again (something new in the recent lines): the scroll stays.
        play = battle.copy(recent = listOf("Minion Battle: Red 3, Blue 4. Red removes 1."))
        compose.waitForIdle()
        assertThat(panelScroll()).isEqualTo(scrolled)

        // The next question (the level up): back to the top, its header in view.
        EndOfRoundGame.next(s)
        assertThat(s.pending!!.kind).isEqualTo(DecisionKind.UPGRADE_CARD)
        play = GydionLevelUpGame.play(s)
        compose.waitForIdle()
        assertThat(panelScroll()).isEqualTo(0f)
    }

    private companion object {
        const val SCROLL = 400f
    }
}
