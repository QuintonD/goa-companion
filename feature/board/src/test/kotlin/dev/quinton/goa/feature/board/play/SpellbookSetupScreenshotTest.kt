package dev.quinton.goa.feature.board.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.quinton.goa.core.data.EditClock
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.NewGameRoute
import dev.quinton.goa.core.testing.FakeBoardMapRepository
import dev.quinton.goa.core.testing.FakeCatalogRepository
import dev.quinton.goa.core.testing.FakeDraftRecordRepository
import dev.quinton.goa.core.testing.FakeGameSaveRepository
import dev.quinton.goa.core.testing.FakeMatchRepository
import dev.quinton.goa.core.testing.FakeSettingsRepository
import dev.quinton.goa.feature.board.BundledImages
import dev.quinton.goa.feature.board.bundledCatalog
import dev.quinton.goa.feature.board.bundledMaps
import dev.quinton.goa.feature.board.play.setup.Control
import dev.quinton.goa.feature.board.play.setup.NewGameAction
import dev.quinton.goa.feature.board.play.setup.NewGameScreen
import dev.quinton.goa.feature.board.play.setup.NewGameStep
import dev.quinton.goa.feature.board.play.setup.NewGameViewModel
import kotlin.random.Random
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Choosing Gydion (extends ADR-040): New game's "You play" offers his spellbook beside his name, the
 * line-up's menu on his chip too, and one tap shows the 22 spell cards from the catalog, by tier.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SpellbookSetupScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = false)

    private fun newGame(options: Boolean) = NewGameViewModel(
        NewGameRoute(red = listOf("gydion", "brogan"), blue = listOf("wasp", "arien")),
        SavedStateHandle(),
        FakeCatalogRepository(bundledCatalog),
        FakeBoardMapRepository(bundledMaps),
        FakeDraftRecordRepository(),
        FakeMatchRepository(),
        FakeSettingsRepository(),
        FakeGameSaveRepository(),
        { FakeGameFactory(bundledMaps, playableHeroes = setOf("gydion", "brogan", "wasp", "arien")) },
        EditClock { 0L },
        Random(2),
    ).also { vm ->
        if (options) {
            vm.onAction(NewGameAction.Step(NewGameStep.OPTIONS))
            vm.onAction(NewGameAction.SetControl(Control.Hero("gydion")))
        }
    }

    @Composable
    private fun NewGame(options: Boolean, spellbook: String? = null) {
        val vm = remember { newGame(options) }
        val state by vm.uiState.collectAsState()
        NewGameScreen(state, onAction = vm::onAction, onStart = {}, onBack = {}, initialSpellbook = spellbook)
    }

    private fun capture(
        mode: ThemeMode,
        fontScale: Float = 1f,
        before: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                GoaTheme(themeMode = mode) { content() }
            }
        }
        compose.waitForIdle()
        before()
        compose.waitForIdle()
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledImages.idle }
        compose.waitForIdle()
        captureScreenRoboImage()
    }

    /** "You play": Gydion, with "Gydion's spellbook · 22 spell cards" under his row. */
    @Test fun spellbookSetupOptionsDark() = capture(ThemeMode.DARK) { NewGame(options = true) }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookSetupOptionsW384LightFontScale2() = capture(ThemeMode.LIGHT, 2f) { NewGame(options = true) }

    /** The line-up: Gydion's chip menu offers his spellbook. */
    @Test
    fun spellbookSetupLineupMenuDark() = capture(ThemeMode.DARK, before = {
        compose.onAllNodes(hasContentDescription("Pick 1: Gydion", substring = true) and hasClickAction()).onFirst()
            .performClick()
    }) { NewGame(options = false) }

    /** The 22 spell cards by tier, before the game. */
    @Test fun spellbookSetupSheetDark() = capture(ThemeMode.DARK) { NewGame(options = true, spellbook = "gydion") }

    @Test fun spellbookSetupSheetLight() = capture(ThemeMode.LIGHT) { NewGame(options = false, spellbook = "gydion") }

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test
    fun spellbookSetupSheetLandscapeDark() = capture(ThemeMode.DARK) { NewGame(options = true, spellbook = "gydion") }

    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    @Test
    fun spellbookSetupSheetW384DarkFontScale2() =
        capture(ThemeMode.DARK, 2f) { NewGame(options = true, spellbook = "gydion") }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
    }
}
