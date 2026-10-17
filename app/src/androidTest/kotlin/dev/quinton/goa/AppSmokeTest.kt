package dev.quinton.goa

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app (real Hilt graph, bundled catalog) and walks every top-level tab. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val tabs = listOf("Heroes", "Timer", "Draft", "Players")

    @Test
    fun everyTopLevelTabOpens() {
        tabs.forEach { label ->
            compose.onAllNodesWithText(label)[0].performClick()
            compose.waitForIdle()
        }
        compose.onAllNodesWithText("Heroes")[0].performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText("Heroes")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
