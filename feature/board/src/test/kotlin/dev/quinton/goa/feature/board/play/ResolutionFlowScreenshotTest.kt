package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * The resolution flow (ADR-046) after a 3v3 turn on the real engine ([ResolutionFlowGame]): Brogan's
 * Onslaught defended by Wasp's Lift Up is open with the card shown, the four other cards collapsed;
 * one expanded by a tap; the defense card tapped into the viewer. Portrait, landscape, light, W384 at
 * font scale 2.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ResolutionFlowScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, before: () -> Unit = { scrollToFlow() }) {
        val play = ResolutionFlowGame.play()
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
        before()
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    private fun entry(text: String) = compose.onAllNodes(hasContentDescription(text, substring = true) and hasClickAction()).onFirst()

    /** The panel scrolled down to the flow's last entry, so the flow is in view below the question. */
    private fun scrollToFlow() {
        entry("Tigerclaw, computer").performScrollTo()
    }

    @Test fun flowDark() = capture(ThemeMode.DARK)

    @Test fun flowLight() = capture(ThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun flowLandscapeDark() = capture(ThemeMode.DARK)

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun flowW384DarkFontScale2() = capture(ThemeMode.DARK, 2f)

    /** Xargatha's entry tapped open: her action, the attack on the minion, its defeat, the coins. */
    @Test
    fun flowExpandedDark() = capture(ThemeMode.DARK) {
        entry("Xargatha, computer").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(ANIMATION_MS)
        // The entry below it in view: Xargatha's steps sit just above.
        entry("Gydion, computer").performScrollTo()
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun flowExpandedW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) {
        entry("Xargatha, computer").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(ANIMATION_MS)
        // The entry below it in view: Xargatha's steps sit just above.
        entry("Gydion, computer").performScrollTo()
    }

    /** Wasp's Lift Up tapped in the open entry: the card large, its Defense total under it. */
    @Test
    fun flowDefenseCardFocusDark() = capture(ThemeMode.DARK) {
        entry("Wasp's defense card").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(ANIMATION_MS)
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
        const val ANIMATION_MS = 1_000L
    }
}
