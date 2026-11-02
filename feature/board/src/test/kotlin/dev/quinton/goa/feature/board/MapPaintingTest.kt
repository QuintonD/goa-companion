package dev.quinton.goa.feature.board

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.feature.board.map.MapPainting
import dev.quinton.goa.feature.board.map.PreloadMapPainting
import dev.quinton.goa.feature.board.map.rememberMapPainting
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A board opens on its painting when it is at hand, not on a frame of the vector map (ADR-057). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapPaintingTest {
    @get:Rule val compose = createComposeRule()

    private val map = bundledMaps.first { it.art != null }

    @Before fun images() = BundledImages.install(ApplicationProvider.getApplicationContext(), memoryCache = true)

    @Test
    fun comingBackToABoardHasItsPaintingFromTheFirstComposition() {
        var shown by mutableStateOf(true)
        val seen = mutableListOf<MapPainting?>()
        compose.setContent { if (shown) seen += rememberMapPainting(map) }
        compose.waitUntil(TIMEOUT_MS) { seen.last() != null }
        compose.runOnIdle { shown = false }
        compose.waitForIdle()
        seen.clear()
        compose.runOnIdle { shown = true }
        compose.waitForIdle()
        assertThat(seen.first()).isNotNull()
    }

    @Test
    fun aPreloadedPaintingIsThereFromTheFirstComposition() {
        var board by mutableStateOf(false)
        var preloaded by mutableStateOf(false)
        val seen = mutableListOf<MapPainting?>()
        compose.setContent {
            PreloadMapPainting(map.art!!.image)
            preloaded = true
            if (board) seen += rememberMapPainting(map)
        }
        compose.waitUntil(TIMEOUT_MS) { preloaded && BundledImages.idle }
        compose.runOnIdle { board = true }
        compose.waitForIdle()
        assertThat(seen.first()).isNotNull()
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
