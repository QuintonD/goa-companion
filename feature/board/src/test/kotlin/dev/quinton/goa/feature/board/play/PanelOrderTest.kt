package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlayUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The panel at the end of a round (hands-on re-check, findings 1 and 2): the person's own question
 * comes before the round's summary for every kind (the Minion Battle, not only the level up), and a
 * new question starts at the top of the panel while the same one keeps where the person scrolled.
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

    private fun top(text: String): Float =
        compose.onAllNodes(hasText(text, substring = true)).onFirst().fetchSemanticsNode().positionInRoot.y

    private fun panel() = compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasText("END OF ROUND", substring = true)))
        .onFirst()

    private fun panelScroll(): Float =
        panel().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `minion battle - the question comes before the round's summary`() {
        val s = EndOfRoundGame.session()
        show(GydionLevelUpGame.play(s))
        assertThat(top(s.pending!!.prompt)).isLessThan(top("END OF ROUND"))
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
