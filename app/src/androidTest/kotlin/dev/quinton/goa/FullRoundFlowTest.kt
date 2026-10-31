package dev.quinton.goa

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A full-round puzzle on the real app, read from the screen (PRODUCT §10, ADR-052): the book's Full rounds
 * section, the first round's turn, the enemy's real cards and what each will do, a nudge per turn, the solution
 * step by step to the goal met. It saves what it sees under the app's files (`full-round-*.png`, for review).
 */
@RunWith(AndroidJUnit4::class)
class FullRoundFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null) ?: context.filesDir, "full-round-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, it)
        }
    }

    @Test
    fun openAFullRoundReadItsPlanAndReplayItsSolution() {
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.tapHeroesAction("Board")
        rule.tap(hasText("Puzzles", substring = true) and hasClickAction())

        rule.reveal(hasText("FULL ROUNDS"))
        rule.waitForText("FULL ROUNDS")
        shot("list")
        rule.tap(hasContentDescription("Full round 1: Where the Turret Goes", substring = true) and hasClickAction())

        rule.waitForText("Turn 1 of 4", timeout = PUZZLE_TIMEOUT)
        rule.waitFor(hasContentDescription("Push the lane", substring = true))
        shot("start")
        rule.reveal(hasText("WHAT THEY WILL PLAY"))
        rule.waitFor(hasContentDescription("turn 1:", substring = true))
        rule.waitFor(hasContentDescription("still to come", substring = true))
        shot("strip")

        rule.tap(hasContentDescription("More options"))
        rule.tapText("Nudge me")
        rule.waitForText("Nudge 1 of", substring = true)
        shot("nudge")

        rule.tap(hasContentDescription("More options"))
        rule.tapText("Show solution")
        rule.waitForText("step 1 of", substring = true, timeout = PUZZLE_TIMEOUT)
        shot("replay")
        var steps = 0
        while (!rule.exists(hasText("Goal met", substring = true)) && steps++ < MAX_STEPS) {
            rule.tap(hasContentDescription("Next step") and hasClickAction())
        }
        rule.waitForText("Goal met", substring = true)
        shot("goal-met")
    }

    private companion object {
        const val PUZZLE_TIMEOUT = 30_000L
        const val MAX_STEPS = 80
        const val FULL_QUALITY = 100
    }
}
