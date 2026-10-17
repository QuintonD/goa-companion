package dev.quinton.goa

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings: theme switch and owned products scoping the catalog. */
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun themeSwitchesToLightAndBack() {
        rule.waitForText("32 heroes")
        assertTrue("Dark is the default theme", backgroundLuminance() < 0.3f)
        rule.tapDescription("Settings")
        rule.waitForText("Theme")
        rule.tap(hasText("Light") and hasClickAction())
        rule.waitUntil(TIMEOUT) { backgroundLuminance() > 0.7f }
        rule.tap(hasText("Dark") and hasClickAction())
        rule.waitUntil(TIMEOUT) { backgroundLuminance() < 0.3f }
    }

    @Test
    fun ownedProductsScopeTheCatalog() {
        rule.waitForText("32 heroes")
        rule.tapDescription("Settings")
        // Switching "everything" off starts from the base game only (Core, 7 heroes).
        rule.tap(hasText("I own every product") and hasClickAction())
        rule.waitForText("1 of 6 products owned")
        rule.tapDescription("Back")
        rule.waitForText("7 heroes")

        // Wayward (5 heroes) on, then off again.
        rule.tapDescription("Settings")
        rule.tap(hasText("Wayward") and hasClickAction())
        rule.waitForText("2 of 6 products owned")
        rule.tapDescription("Back")
        rule.waitForText("12 heroes")
        rule.tapDescription("Settings")
        rule.tap(hasText("Wayward") and hasClickAction())
        rule.waitForText("1 of 6 products owned")
        rule.tapDescription("Back")
        rule.waitForText("7 heroes")

        // Restore: owning everything brings all 32 back.
        rule.tapDescription("Settings")
        rule.tap(hasText("I own every product") and hasClickAction())
        rule.waitForText("All heroes are available in drafts and filters")
        rule.tapDescription("Back")
        rule.waitForText("32 heroes")
    }

    /** Relative luminance of the window background, sampled just under the status bar. */
    private fun backgroundLuminance(): Float {
        val map = rule.onRoot().captureToImage().toPixelMap()
        val c = map[8, map.height / 3]
        return 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    }
}
