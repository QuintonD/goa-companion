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
import dev.quinton.goa.feature.board.play.HeroStateGame.play
import dev.quinton.goa.feature.board.play.HeroStateGame.session
import dev.quinton.goa.feature.board.play.game.GameScreen
import dev.quinton.goa.feature.board.play.game.GameUiState
import dev.quinton.goa.feature.board.play.game.PlaySheet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero sheet as a game state (ADR-044), on the real rules engine ([HeroStateGame]): an enemy before
 * the reveal (a card back, the item icons) and after it (the card faceup, NOW, initiative as it counts),
 * the person's own hero (items with the tucked cards, the hand), a hero off the board. Portrait,
 * landscape, light, 384 dp at font scale 2.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HeroStateScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun sheet(heroId: String, mode: ThemeMode, revealed: Boolean = false, fontScale: Float = 1f) {
        val play = play(session(revealed))
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
                        initialSheet = PlaySheet.Hero(heroId),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    // ---- An enemy before the reveal: the card back, the item icons ---------------------------

    @Test fun heroStateEnemyBeforeRevealDark() = sheet("wasp", ThemeMode.DARK)

    @Test fun heroStateEnemyBeforeRevealLight() = sheet("wasp", ThemeMode.LIGHT)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun heroStateEnemyBeforeRevealW384DarkFontScale2() = sheet("wasp", ThemeMode.DARK, fontScale = 2f)

    // ---- An enemy after the reveal: faceup, NOW, initiative 14 (printed 13) ------------------

    @Test fun heroStateEnemyAfterRevealDark() = sheet("wasp", ThemeMode.DARK, revealed = true)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun heroStateEnemyAfterRevealLandscapeDark() = sheet("wasp", ThemeMode.DARK, revealed = true)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun heroStateEnemyAfterRevealW384LightFontScale2() = sheet("wasp", ThemeMode.LIGHT, revealed = true, fontScale = 2f)

    // ---- The person's own hero: items with the tucked cards, the hand ------------------------

    @Test fun heroStateOwnItemsDark() = sheet("arien", ThemeMode.DARK)

    @Test fun heroStateOwnItemsLight() = sheet("arien", ThemeMode.LIGHT, revealed = true)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun heroStateOwnItemsLandscapeLight() = sheet("arien", ThemeMode.LIGHT)

    // ---- Off the board ------------------------------------------------------------------------

    @Test fun heroStateOffBoardDark() = sheet("tigerclaw", ThemeMode.DARK, revealed = true)

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
