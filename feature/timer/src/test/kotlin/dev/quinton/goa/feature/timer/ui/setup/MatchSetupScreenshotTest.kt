package dev.quinton.goa.feature.timer.ui.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.timer.ui.preview.TimerPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The whole Match setup page on a tall screen, so the house-rule labels next to the counters,
 * the Double lane rules note and the Tie Breaker coin card (draft coin, correction) are covered.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2400dp-xhdpi")
class MatchSetupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(state: MatchSetupUiState) {
        compose.setContent { GoaTheme(themeMode = ThemeMode.DARK) { MatchSetupScreen(state, SetupActions()) } }
        compose.onRoot().captureRoboImage()
    }

    @Test fun doublePilotFullPage() = capture(TimerPreviewData.setup)

    @Test fun draftCoinDoubleLaneFullPage() = capture(TimerPreviewData.setupLinked)

    @Test
    fun correctingTheDraftCoinFullPage() = capture(
        TimerPreviewData.setupLinked.copy(correctingCoin = true, tieBreaker = Team.RED, coinSource = CoinSource.HERE),
    )
}
