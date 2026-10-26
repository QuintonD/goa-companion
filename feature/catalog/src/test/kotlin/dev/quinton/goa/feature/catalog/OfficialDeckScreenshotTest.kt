package dev.quinton.goa.feature.catalog

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.HeroLogSummary
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.testing.BundledAssetImages
import dev.quinton.goa.core.testing.bundledHero
import dev.quinton.goa.feature.catalog.detail.ExtrasSection
import dev.quinton.goa.feature.catalog.detail.HeroDetailScreen
import dev.quinton.goa.feature.catalog.detail.HeroDetailUiState
import dev.quinton.goa.feature.catalog.detail.HeroPersonalStats
import dev.quinton.goa.feature.catalog.viewer.CardViewerScreen
import dev.quinton.goa.feature.catalog.viewer.CardViewerUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The deck as the official cards (ADR-042): bundled heroes with their card scans, deck symbol and
 * portrait, so the screenshots show what a player sees.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OfficialDeckScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private fun detail(id: String): HeroDetailUiState.Ready {
        val hero = bundledHero(id)
        return HeroDetailUiState.Ready(
            hero = hero,
            productName = "Core",
            deck = DeckLayout.of(hero),
            stats = HeroPersonalStats(null, null, HeroLogSummary(0, 0, emptyList(), emptyList())),
        )
    }

    private fun viewer(id: String, cardId: String): CardViewerUiState.Ready {
        val hero = bundledHero(id)
        val cards = DeckLayout.of(hero).orderedCards
        return CardViewerUiState.Ready(hero, cards, cards.indexOfFirst { it.id == cardId })
    }

    private fun shot(
        mode: ThemeMode = ThemeMode.DARK,
        fontScale: Float = 1f,
        scrollTo: String? = null,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GoaTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        if (scrollTo != null) compose.onNode(hasScrollAction()).performScrollToKey(scrollTo)
        compose.waitUntil(IMAGE_TIMEOUT_MS) { BundledAssetImages.idle }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage()
    }

    @Composable
    private fun Detail(id: String) = HeroDetailScreen(detail(id), onBack = {}, onCardClick = { _, _ -> })

    @Test
    @Config(qualifiers = "w411dp-h3000dp-xxhdpi")
    fun deckDark() = shot { Detail("arien") }

    @Test
    @Config(qualifiers = "w411dp-h3000dp-xxhdpi")
    fun deckLight() = shot(ThemeMode.LIGHT) { Detail("arien") }

    /** S25 Ultra width at the largest font: three columns still, names wrap under each card. */
    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun deckW384DarkFontScale2() = shot(fontScale = 2f, scrollTo = "deck-RED") { Detail("arien") }

    /** Held sideways: a colour's five cards in one row. */
    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun deckLandscapeDark() = shot(scrollTo = "deck-RED") { Detail("arien") }

    /** Takahide's three gold cards say under each where it starts. */
    @Test fun deckBasicsTakahideDark() = shot(scrollTo = "deck-BASIC") { Detail("takahide") }

    @Test
    @Config(qualifiers = "w411dp-h2000dp-xxhdpi")
    fun spellbookDark() = shot {
        ExtrasSection(
            DeckLayout.of(bundledHero("gydion")),
            onCardClick = {},
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        )
    }

    @Test fun cardViewerDark() = shot { CardViewerScreen(viewer("arien", "arien-red-2a"), onBack = {}) }

    @Test fun cardViewerLight() = shot(ThemeMode.LIGHT) {
        CardViewerScreen(viewer("arien", "arien-red-2a"), onBack = {})
    }

    @Test fun cardViewerFontScale2() = shot(fontScale = 2f) {
        CardViewerScreen(viewer("arien", "arien-red-2a"), onBack = {})
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 15_000L
    }
}
