package dev.quinton.goa

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Game night, the core flow: House draft (2v2) → match setup → table-rules timer for a full
 * round → log the result → ratings and meta. The script lives in [GameNight].
 */
@RunWith(AndroidJUnit4::class)
class GameNightFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun houseDraftThroughTimerToRatedMatch() {
        GameNight(rule).play()
    }
}
