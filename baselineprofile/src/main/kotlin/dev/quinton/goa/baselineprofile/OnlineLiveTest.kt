package dev.quinton.goa.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The minified build plays online (ADR-056): every frame of the protocol is a serialized type, and R8 could break
 * one that no JVM test would notice. Joins a real room on the real server, picks a hero, plays a card and sees the
 * reveal, then deletes its account. `online-host.mjs` hosts the room and plays the other seat, and this runs only
 * when it is given that room's code:
 *   :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *     -Pandroid.testInstrumentationRunnerArguments.class=dev.quinton.goa.baselineprofile.OnlineLiveTest
 *     -Pandroid.testInstrumentationRunnerArguments.onlineRoom=<code>
 * With `onlineTarget=dev.quinton.goa` it plays on the signed release installed on the device, not the bench build.
 * It clears that app's data first.
 */
@RunWith(AndroidJUnit4::class)
class OnlineLiveTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun theMinifiedBuildPlaysARealGame() {
        val room = InstrumentationRegistry.getArguments().getString("onlineRoom")
        assumeTrue("needs a room from online-host.mjs", room != null)
        val target = InstrumentationRegistry.getArguments().getString("onlineTarget") ?: TARGET_APP

        device.executeShellCommand("pm clear $target")
        device.executeShellCommand("am start -W -n $target/dev.quinton.goa.MainActivity")
        tap(By.text("Play"))
        tap(By.text("Online"))
        wait(By.text("Your name"))
        field(By.text("Your name")).text = "Check Android"
        tap(By.text("Create account"))
        tap(By.text("I've saved it"))

        field(reveal(By.text("Room code or invite link"))).text = requireNotNull(room)
        tap(reveal(By.text("Join")))
        wait(By.textStartsWith("Room "))

        // The button waits for the room's socket, which takes a moment on a real connection: as a person would,
        // tap again until the sheet opens. If it never does the socket never came up, and that is the failure.
        val sheet = By.text("Heroes someone else has picked aren't here.")
        val until = System.currentTimeMillis() + GAME_TIMEOUT_MS
        while (!device.hasObject(sheet) && System.currentTimeMillis() < until) {
            device.findObject(By.text("Pick your hero"))?.click()
            device.wait(Until.hasObject(sheet), SHORT_MS)
        }
        wait(sheet)
        shot("lobby")
        tap(By.text("Arien"))
        // Back to the lobby, and the seat shows the hero: the server's Lobby frame after our own pick.
        wait(By.descContains("Arien"))
        tap(reveal(By.text("I'm ready")))

        // The host starts as soon as everyone is ready: the table is GameStart and Table frames decoded.
        wait(By.text("Play a card facedown"), GAME_TIMEOUT_MS)
        shot("table")
        tap(By.descContains("Initiative"))
        wait(By.textContains("Card 1 of"))
        tap(By.textContains("facedown"))

        // Both cards are in, so the server's reveal comes back: more of the game's events and views.
        wait(By.descContains("1st to act"), GAME_TIMEOUT_MS)
        shot("reveal")
        assertEquals("the app is still running", target, device.currentPackageName)

        // Leaves no trace: deleting the account gives up the seat, and the host's game ends unrated.
        device.pressBack()
        device.waitForIdle()
        tap(reveal(By.text("Delete account")))
        wait(By.text("Delete your account?"))
        requireNotNull(device.wait(Until.findObjects(By.text("Delete account")), TIMEOUT_MS)).last().click()
        wait(By.text("Create account"))
        shot("deleted")
    }

    private fun field(label: BySelector): UiObject2 = wait(By.clazz(EDIT_TEXT).hasDescendant(label))

    /** Scrolls down to what may be below the fold. */
    private fun reveal(selector: BySelector): BySelector {
        if (device.wait(Until.hasObject(selector), SHORT_MS) != true) {
            wait(By.scrollable(true)).scrollUntil(Direction.DOWN, Until.findObject(selector))
        }
        return selector
    }

    private fun wait(selector: BySelector, timeout: Long = TIMEOUT_MS): UiObject2 =
        requireNotNull(device.wait(Until.findObject(selector), timeout)) {
            val screen = ByteArrayOutputStream().also(device::dumpWindowHierarchy).toString()
            val seen = Regex("""(?:text|content-desc)="([^"]+)"""").findAll(screen).map { it.groupValues[1] }.toList()
            "Nothing on screen matches $selector. It shows: $seen"
        }

    private fun tap(selector: BySelector) {
        wait(selector).click()
        device.waitForIdle()
    }

    private fun shot(name: String) {
        device.executeShellCommand("screencap -p /data/local/tmp/goa-online-$name.png")
    }

    private companion object {
        const val EDIT_TEXT = "android.widget.EditText"
        const val TIMEOUT_MS = 20_000L
        const val SHORT_MS = 2_000L
        const val GAME_TIMEOUT_MS = 60_000L
    }
}
