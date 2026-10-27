package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The end of a round with a Minion Battle the person's team loses (hands-on re-check, finding 1): the
 * question and its Confirm first, the round's summary after it — at font scale 2 too, and in landscape.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class EndOfRoundScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun capture(mode: ThemeMode, fontScale: Float = 1f) {
        val play = GydionLevelUpGame.play(EndOfRoundGame.session())
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) {
                    GameScreen(
                        state = GameUiState.Playing(play),
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
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    @Test fun minionBattleDark() = capture(ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun minionBattleW384DarkFontScale2() = capture(ThemeMode.DARK, 2f)

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun minionBattleLandscapeLight() = capture(ThemeMode.LIGHT)

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
