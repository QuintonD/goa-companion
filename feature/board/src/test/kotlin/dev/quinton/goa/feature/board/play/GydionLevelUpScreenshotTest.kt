package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.HAND_CARD
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.OTHER_UPGRADE
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.UPGRADE
import dev.quinton.goa.feature.board.play.GydionLevelUpGame.name
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
 * Gydion's level up (ADR-045 extended after the hands-on pass): the rows with the spells each card
 * casts under it, the official item icons in words, the question before the round's summary (at large
 * text it filled the panel), and the card focus viewer whose card shrinks before a line under it is
 * squeezed (short screens, font scale 2) — on an upgrade card with its spells, on a spell, at Keep.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GydionLevelUpScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun capture(mode: ThemeMode, fontScale: Float = 1f, keep: Boolean = false, before: () -> Unit = {}) {
        val play = GydionLevelUpGame.play(GydionLevelUpGame.session(keep = keep))
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

    private fun tap(description: String) {
        compose.onAllNodes(hasContentDescription(description, substring = true) and hasClickAction())
            .onFirst()
            .performClick()
    }

    private val upgradeCard = { tap("Upgrade of ${name(HAND_CARD)}: ${name(UPGRADE)}") }
    private val spell = { tap("A spell ${name(UPGRADE)} casts: ${name("gydion-spell-fireball")}") }
    private val keepCard = { tap(name(OTHER_UPGRADE)) }

    // ---- The rows ----------------------------------------------------------------------------

    @Test fun gydionUpgradeRowsDark() = capture(ThemeMode.DARK)

    /** Large text: the rows come first, the round's summary after them. */
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun gydionUpgradeRowsW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun gydionUpgradeRowsLandscapeLight() = capture(ThemeMode.LIGHT)

    @Test fun gydionKeepRowsDark() = capture(ThemeMode.DARK, keep = true)

    // ---- The viewer --------------------------------------------------------------------------

    /** Lesser Evocation: "Upgrade of …", its item in words, its spells to tap, Go to. */
    @Test fun gydionFocusUpgradeDark() = capture(ThemeMode.DARK, before = upgradeCard)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun gydionFocusUpgradeW384DarkFontScale2() = capture(ThemeMode.DARK, 2f, before = upgradeCard)

    /** A short phone: the card gives way, every line under it is whole. */
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    @Test
    fun gydionFocusUpgradeShortLight() = capture(ThemeMode.LIGHT, before = upgradeCard)

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun gydionFocusUpgradeLandscapeDark() = capture(ThemeMode.DARK, before = upgradeCard)

    /** Fireball, a spell Lesser Evocation casts: read, not chosen, Go to Elementary Evocation. */
    @Test fun gydionFocusSpellDark() = capture(ThemeMode.DARK, before = spell)

    /** Keep: what goes to the hand and the other's item, without a second item line squeezed in. */
    @Test fun gydionFocusKeepDark() = capture(ThemeMode.DARK, keep = true, before = keepCard)

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun gydionFocusKeepW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f, keep = true, before = keepCard)

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
