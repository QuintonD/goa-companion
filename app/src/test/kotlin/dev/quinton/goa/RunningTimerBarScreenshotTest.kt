package dev.quinton.goa

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.timer.ui.timer.RunningTimerUi
import dev.quinton.goa.ui.RunningTimerBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "timer running" bar shown above the tabs: running, paused and overtime. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RunningTimerBarScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun bar(clock: String, paused: Boolean = false, overtime: Boolean = false) = RunningTimerUi(
        phase = "Turn",
        context = "Round 2 · Turn 3 of 4",
        clock = clock,
        paused = paused,
        overtime = overtime,
        description = "",
    )

    private fun shot(mode: ThemeMode) {
        compose.setContent {
            GoaTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        RunningTimerBar(bar("0:17"), onClick = {})
                        RunningTimerBar(bar("0:17", paused = true), onClick = {})
                        RunningTimerBar(bar("+0:05", overtime = true), onClick = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Test fun runningTimerBarDark() = shot(ThemeMode.DARK)

    @Test fun runningTimerBarLight() = shot(ThemeMode.LIGHT)
}
