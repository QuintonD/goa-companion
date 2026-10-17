package dev.quinton.goa.feature.timer

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.timer.ui.presets.PresetEditActions
import dev.quinton.goa.feature.timer.ui.presets.PresetEditScreen
import dev.quinton.goa.feature.timer.ui.presets.PresetsActions
import dev.quinton.goa.feature.timer.ui.presets.PresetsScreen
import dev.quinton.goa.feature.timer.ui.preview.TimerPreviewData
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupScreen
import dev.quinton.goa.feature.timer.ui.setup.SetupActions
import dev.quinton.goa.feature.timer.ui.timer.ActiveUiState
import dev.quinton.goa.feature.timer.ui.timer.TimerActions
import dev.quinton.goa.feature.timer.ui.timer.TimerScreen
import dev.quinton.goa.feature.timer.ui.timer.TimerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TimerScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
        compose.setContent { GoaTheme(themeMode = mode, content = content) }
        compose.onRoot().captureRoboImage()
    }

    private fun active(state: ActiveUiState, mode: ThemeMode = ThemeMode.DARK) =
        capture(mode) { TimerScreen(TimerUiState(loading = false, active = state), TimerActions()) }

    @Test fun startDark() = capture { TimerScreen(TimerPreviewData.start, TimerActions()) }

    @Test fun startLight() = capture(ThemeMode.LIGHT) { TimerScreen(TimerPreviewData.start, TimerActions()) }

    @Test fun turnDark() = active(TimerPreviewData.turn)

    @Test fun turnLight() = active(TimerPreviewData.turn, ThemeMode.LIGHT)

    @Test
    @Config(fontScale = 2f)
    fun turnFontScale2() = active(TimerPreviewData.turn)

    @Test fun alertsOffBannerDark() = active(TimerPreviewData.turn.copy(alertsOff = true))

    @Test fun pausedWarningDark() = active(TimerPreviewData.paused)

    @Test fun overtimeDark() = active(TimerPreviewData.overtime)

    @Test fun upgradeOnLevelUpDark() = active(TimerPreviewData.upgrade)

    @Test fun upgradeOnLevelUpLight() = active(TimerPreviewData.upgrade, ThemeMode.LIGHT)

    @Test fun checklistStepDark() = active(TimerPreviewData.checklistStep)

    @Test fun actionTimerDark() = active(TimerPreviewData.action)

    @Test fun summaryDark() = capture { TimerScreen(TimerPreviewData.summary, TimerActions()) }

    @Test fun setupDark() = capture { MatchSetupScreen(TimerPreviewData.setup, SetupActions()) }

    @Test fun setupLight() = capture(ThemeMode.LIGHT) { MatchSetupScreen(TimerPreviewData.setup, SetupActions()) }

    @Test
    @Config(fontScale = 2f)
    fun setupFontScale2() = capture { MatchSetupScreen(TimerPreviewData.setup, SetupActions()) }

    @Test fun setupLinkedDoubleLaneDark() = capture { MatchSetupScreen(TimerPreviewData.setupLinked, SetupActions()) }

    @Test fun presetsDark() = capture { PresetsScreen(TimerPreviewData.presetsList, PresetsActions()) }

    @Test fun presetsLight() = capture(ThemeMode.LIGHT) {
        PresetsScreen(TimerPreviewData.presetsList, PresetsActions())
    }

    @Test fun editPresetDark() = capture { PresetEditScreen(TimerPreviewData.editPreset, PresetEditActions()) }

    @Test
    @Config(fontScale = 2f)
    fun editPresetFontScale2() = capture { PresetEditScreen(TimerPreviewData.editPreset, PresetEditActions()) }
}
