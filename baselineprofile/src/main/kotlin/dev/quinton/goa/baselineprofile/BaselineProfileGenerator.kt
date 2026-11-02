package dev.quinton.goa.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collects what a player's first minutes run (ADR-057), so ART compiles it ahead of time: the start and
 * the Heroes tab (also the startup profile, which orders the dex), the hero page, every tab, and a game
 * against the computer on the painted board. A test of the project's own, run by Gradle like the
 * end-to-end tests; what it produces is a file in the repo, not a measure.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(TARGET_APP, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        wait(By.textContains(" heroes"))
    }

    @Test
    fun journeys() = rule.collect(TARGET_APP, maxIterations = 3, stableIterations = 2) {
        pressHome()
        startActivityAndWait()
        wait(By.textContains(" heroes"))
        catalog()
        listOf("Timer", "Draft", "Players").forEach { tab ->
            tap(By.text(tab))
            device.waitForIdle()
        }
        tap(By.text("Play"))
        tap(By.text("Board"))
        puzzles()
        game()
    }

    private fun MacrobenchmarkScope.catalog() {
        val grid = wait(By.scrollable(true))
        repeat(2) { grid.fling(Direction.DOWN) }
        repeat(2) { grid.fling(Direction.UP) }
        tap(By.descContains("Complexity"))
        val page = wait(By.scrollable(true))
        repeat(2) { page.fling(Direction.DOWN) }
        device.pressBack()
        wait(By.textContains(" heroes"))
    }

    private fun MacrobenchmarkScope.puzzles() {
        tap(By.text("Puzzles"))
        wait(By.scrollable(true)).fling(Direction.DOWN)
        device.pressBack()
        device.waitForIdle()
    }

    /** A 2v2 from New game, as PlayFlowTest plays it: the setup, the table, the map moved. */
    private fun MacrobenchmarkScope.game() {
        tap(By.text("Play against the computer"))
        wait(By.textContains("Tap heroes below"))
        listOf("Arien", "Brogan", "Bain", "Brynn").forEach { tap(By.desc(it)) }
        tap(By.text("Next"))
        wait(By.text("Game options"))
        tap(By.text("Arien"))
        wait(By.scrollable(true)).scrollUntil(Direction.DOWN, Until.findObject(By.text("Flip the Tie Breaker coin")))
        tap(By.text("Flip the Tie Breaker coin"))
        tap(By.text("Start the game"))
        wait(By.text("Play a card facedown"), GAME_TIMEOUT_MS)
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(2) {
            device.swipe(w / 2, h / 3, w / 2, h / 2, SWIPE_STEPS)
            device.swipe(w / 2, h / 2, w / 2, h / 3, SWIPE_STEPS)
        }
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.wait(selector: BySelector, timeout: Long = TIMEOUT_MS): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), timeout)) {
            val screen = ByteArrayOutputStream().also(device::dumpWindowHierarchy).toString()
            val seen = Regex("""(?:text|content-desc)="([^"]+)"""").findAll(screen).map { it.groupValues[1] }.toList()
            "Nothing on screen matches $selector. It shows: $seen"
        }

    private fun MacrobenchmarkScope.tap(selector: BySelector) {
        wait(selector).click()
        device.waitForIdle()
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val GAME_TIMEOUT_MS = 60_000L
        const val SWIPE_STEPS = 20
    }
}
