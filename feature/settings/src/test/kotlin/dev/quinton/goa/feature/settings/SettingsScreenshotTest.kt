package dev.quinton.goa.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.data.game.AiSpeed
import dev.quinton.goa.core.designsystem.component.GoaCard
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.BoardStyle
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.BundledAssetImages
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledAssetImages.idle }
        compose.onRoot().captureRoboImage()
    }

    @Composable private fun Settings() = SettingsScreen(PreviewSettings, onAction = {}, onBack = {}, onOpen = {})

    @Composable private fun About() = AboutScreen(PreviewAppInfo, onBack = {})

    @Test fun settingsDark() = capture(ThemeMode.DARK) { Settings() }

    @Test fun settingsLight() = capture(ThemeMode.LIGHT) { Settings() }

    @Test fun settingsDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Settings() }

    /** The row sits below the fold of the whole screen; a long result must stay readable at large text. */
    @Composable private fun Speed(test: SpeedTest) = GoaCard { SpeedRow(test) {} }

    @Test fun speedRunningDark() = capture(ThemeMode.DARK) { Speed(SpeedTest.Running(0.4f)) }

    @Test fun speedDoneDark() = capture(ThemeMode.DARK) {
        Speed(SpeedTest.Done(AiSpeed(2_130, 145, 410, "line")))
    }

    @Test fun speedDoneDarkFontScale2() = capture(ThemeMode.DARK, 2f) {
        Speed(SpeedTest.Done(AiSpeed(2_130, 145, 410, "line")))
    }

    @Composable private fun BoardStyleCard(style: BoardStyle) = GoaCard { BoardStyleSelector(style) {} }

    @Test fun boardStyleDark() = capture(ThemeMode.DARK) { BoardStyleCard(BoardStyle.MINIATURES) }

    @Test fun boardStyleLight() = capture(ThemeMode.LIGHT) { BoardStyleCard(BoardStyle.GRID_AND_DISCS) }

    @Test fun boardStyleDarkFontScale2() = capture(ThemeMode.DARK, 2f) { BoardStyleCard(BoardStyle.GRID_AND_DISCS) }

    @Test fun choosingABoardStyleSaysWhich() {
        val chosen = mutableListOf<BoardStyle>()
        compose.setContent { GoaTheme { BoardStyleSelector(BoardStyle.MINIATURES) { chosen += it } } }
        compose.onNodeWithText("Miniatures").assertIsSelected()
        compose.onNodeWithText("Grid and discs").assertIsNotSelected().performClick()
        assertThat(chosen).containsExactly(BoardStyle.GRID_AND_DISCS)
    }

    @Test fun aboutDark() = capture(ThemeMode.DARK) { About() }

    @Test fun aboutLight() = capture(ThemeMode.LIGHT) { About() }

    @Test fun aboutLightFontScale2() = capture(ThemeMode.LIGHT, 2f) { About() }
}

private const val IMAGE_TIMEOUT_MS = 15_000L
