package dev.quinton.goa

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.model.settings.ThemeMode
import dev.quinton.goa.core.navigation.TopLevelTab
import dev.quinton.goa.core.testing.BundledAssetImages
import dev.quinton.goa.ui.GoaBottomBar
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bottom navigation on the slate: dark in both themes, the selected tab in bronze. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BottomBarScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Before fun art() = BundledAssetImages.install(ApplicationProvider.getApplicationContext())

    private fun shot(mode: ThemeMode) {
        compose.setContent {
            GoaTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column { GoaBottomBar(TopLevelTab.CATALOG, onSelect = {}) }
                }
            }
        }
        compose.waitUntil(ART_TIMEOUT_MS) { BundledAssetImages.idle }
        compose.onRoot().captureRoboImage()
    }

    @Test fun bottomBarDark() = shot(ThemeMode.DARK)

    @Test fun bottomBarLight() = shot(ThemeMode.LIGHT)

    @Test fun theTabsKeepTheirTagsAndSelection() {
        val chosen = mutableListOf<TopLevelTab>()
        compose.setContent { GoaTheme { GoaBottomBar(TopLevelTab.CATALOG, onSelect = { chosen += it }) } }
        compose.onNodeWithTag("nav_catalog").assertIsSelected()
        compose.onNodeWithTag("nav_players").assertIsNotSelected().performClick()
        assertThat(chosen).containsExactly(TopLevelTab.PLAYERS)
    }
}

private const val ART_TIMEOUT_MS = 15_000L
