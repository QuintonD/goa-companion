package dev.quinton.goa.feature.board.play.review

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.play.game.GameAction
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlaySheet
import dev.quinton.goa.feature.board.play.game.PlayUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * System back through the game-state review, one level per press (hands-on 2026-10-25: back from
 * a turn's board closed the whole sheet, so the third press left Boards for the Heroes tab):
 * the rewound board → the Turns list → the sheet closes → the screen's own back (the app's
 * NavDisplay, here a BackHandler around the screen) leaves the game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ReviewBackTest {
    @get:Rule val compose = createComposeRule()

    /** Round 2, turn 2 with the board of round 1's turn 3 open. */
    private fun rewound(): PlayUi = reviewViewModel(reviewGameAt(round = 2, turn = 2)) { vm, settle ->
        vm.onAction(GameAction.Review(ReviewAction.Open(true)))
        settle()
        vm.onAction(GameAction.Review(ReviewAction.Rewind(1, 3)))
        settle()
    }

    @Test
    fun `back steps out of the rewind, then the sheet, then the game, one level each`() {
        val start = rewound()
        assertThat(start.review?.rewind).isNotNull()
        var play by mutableStateOf(start)
        var leftGame = 0
        compose.setContent {
            GoaTheme(themeMode = ThemeMode.DARK) {
                // The app's back (NavDisplay pops the game): registered first, so anything above wins.
                BackHandler { leftGame++ }
                GameScreen(
                    state = GameUiState.Playing(play),
                    onAction = { action ->
                        if (action is GameAction.Review && action.action == ReviewAction.CloseRewind) {
                            play = play.copy(review = play.review?.copy(rewind = null))
                        }
                    },
                    onTap = { false },
                    onDelete = {},
                    onNewGame = {},
                    onDone = {},
                    onBack = { leftGame++ },
                    initialSheet = PlaySheet.Review(ReviewTab.TURNS),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Start of round 1 · turn 3").assertExists()

        // 1: the rewound board → the Turns list, the sheet still open.
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Start of round 1 · turn 3").assertDoesNotExist()
        compose.onNodeWithText("Game state").assertExists()
        compose.onNodeWithText("Turns").assertExists()
        assertThat(leftGame).isEqualTo(0)

        // 2: the sheet closes; still in the game.
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Game state").assertDoesNotExist()
        assertThat(leftGame).isEqualTo(0)

        // 3: leaves the game (to Boards, where it was opened from).
        Espresso.pressBack()
        compose.waitForIdle()
        assertThat(leftGame).isEqualTo(1)
    }
}
