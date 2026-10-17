package dev.quinton.goa.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
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

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.onRoot().captureRoboImage()
    }

    @Composable private fun Settings() = SettingsScreen(PreviewSettings, onAction = {}, onBack = {}, onOpen = {})

    @Composable private fun About() = AboutScreen(PreviewAppInfo, onBack = {})

    @Test fun settingsDark() = capture(ThemeMode.DARK) { Settings() }

    @Test fun settingsLight() = capture(ThemeMode.LIGHT) { Settings() }

    @Test fun settingsDarkFontScale2() = capture(ThemeMode.DARK, 2f) { Settings() }

    @Test fun aboutDark() = capture(ThemeMode.DARK) { About() }

    @Test fun aboutLight() = capture(ThemeMode.LIGHT) { About() }

    @Test fun aboutLightFontScale2() = capture(ThemeMode.LIGHT, 2f) { About() }
}
