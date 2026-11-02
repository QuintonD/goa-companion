package dev.quinton.goa.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.designsystem.component.OfficialBitmaps
import dev.quinton.goa.core.designsystem.component.PlateColor
import dev.quinton.goa.core.designsystem.component.rememberOfficialBitmap
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.testing.BundledAssetImages
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The look's images are decoded once, however many parts show them (ADR-057). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OfficialBitmapsTest {
    @get:Rule val compose = createComposeRule()

    @Before fun images() {
        OfficialBitmaps.clear()
        BundledAssetImages.install(ApplicationProvider.getApplicationContext())
    }

    private fun settle() {
        compose.waitUntil(TIMEOUT_MS) { BundledAssetImages.idle }
        compose.waitForIdle()
    }

    @Test
    fun aScreenOfPlatesDecodesTheSlateAndItsCornersOnce() {
        compose.setContent {
            Column { repeat(PLATES) { Box(Modifier.size(40.dp).slatePlate()) } }
        }
        settle()
        assertThat(BundledAssetImages.requests).isEqualTo(1 + LookArt.corners().size)
    }

    @Test
    fun aPartThatComesLaterHasItsImageFromItsFirstComposition() {
        var late by mutableStateOf(false)
        val seen = mutableListOf<ImageBitmap?>()
        compose.setContent {
            rememberOfficialBitmap(LookArt.COIN)
            if (late) seen += rememberOfficialBitmap(LookArt.COIN)
        }
        settle()
        compose.runOnIdle { late = true }
        compose.waitForIdle()
        assertThat(seen.first()).isNotNull()
    }

    @Test
    fun aNewPathNeverKeepsTheOldImage() {
        var color by mutableStateOf(PlateColor.GOLD)
        var shown: ImageBitmap? = null
        compose.setContent { shown = rememberOfficialBitmap(LookArt.textBox(color)) }
        settle()
        val gold = shown
        assertThat(gold).isNotNull()
        compose.runOnIdle { color = PlateColor.BLUE }
        compose.waitForIdle()
        assertThat(shown).isNotSameInstanceAs(gold)
        compose.waitUntil(TIMEOUT_MS) { shown != null }
        assertThat(shown).isNotSameInstanceAs(gold)
    }

    private companion object {
        const val PLATES = 40
        const val TIMEOUT_MS = 60_000L
    }
}
