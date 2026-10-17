package dev.quinton.goa

import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The second game of the night: a logged match → "Result saved" → Rematch "Same seats" → Match
 * setup → timer (rules sheet, rules screen, notification tap back to the Timer tab) → End match →
 * "Who won?" with the same teams → a second rated result.
 */
@RunWith(AndroidJUnit4::class)
class RematchTimerFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val red = listOf("Arien" to "Ana", "Brogan" to "Ben")
    private val blue = listOf("Wasp" to "Cal", "Sabina" to "Dee")

    @Test
    fun sameSeatsRematchRunsTheTimerAndLogsTheSecondResult() {
        grantNotifications()
        listOf("Ana", "Ben", "Cal", "Dee").forEach { rule.createPlayerViaProfile(it) }

        // The Timer tab is left on Timer presets: Start timer must still land on the timer (game night [88]).
        rule.openTab("timer")
        rule.tapDescription("Manage timer presets")
        rule.waitForText("Timer presets")
        rule.openTab("players")

        // ---- Game 1, logged by hand: Red wins.
        rule.tap(hasText("Matches") and hasClickAction())
        rule.tapText("Log a match")
        rule.waitForText("Duration (minutes, optional)")
        (red + blue).forEach { (hero, pilot) -> rule.fillNextSeat(hero, pilot) }
        rule.tapDescription("Red won", substring = true)
        rule.tap(hasText("Save") and hasClickAction())
        rule.waitForText("Result saved")
        rule.ratingChanges(4)

        // ---- Rematch, same seats → Match setup for a new pending match with the same four heroes.
        rule.tapText("Rematch")
        rule.waitForText("Same players, heroes and teams.")
        rule.tap(hasText("Same seats") and hasClickAction())
        rule.waitForText("Match setup")
        rule.waitForText("Rematch · 4 heroes")
        rule.waitForText("Rematch: same seats")
        rule.waitFor(hasTestTag("nav_timer") and isSelected())
        rule.tap(hasText("Quick") and hasClickAction())
        rule.tap(hasText("Start timer") and hasClickAction())
        rule.waitForText("Round 1")

        // ---- Rules from the running timer: the Levels & defeats sheet knows this match's heroes
        // (it lists only their cards), so the timer is linked to the rematch.
        rule.tapDescription("Levels and defeats")
        rule.waitForText("Levels & defeats")
        rule.reveal(hasText("Cards in this match that change this", ignoreCase = true))
        pressBack()
        rule.waitGone(hasText("Levels & defeats"))
        rule.waitForText("Round 1")
        // ... and the full rules reference from the overflow menu, then back to the same timer.
        rule.tapDescription("More options")
        rule.tap(hasText("Rules") and hasClickAction())
        rule.waitFor(hasText("Rules") and !hasClickAction())
        rule.tapDescription("Back")
        rule.waitForText("Round 1")

        // ---- Play on into turn 1's Resolve step, so the notification has a later step to return to.
        settle()
        rule.tapText("Next: Turn 1")
        settle()
        rule.tapText("Cards revealed")
        rule.waitForText("Resolve")

        // ---- Away from the timer, its bar shows on other tabs; the notification brings the Timer back.
        // The running timer has no tab bar (no mis-taps next to Next): system Back leaves it.
        pressBack()
        rule.openTab("catalog")
        rule.waitForText("32 heroes")
        rule.waitFor(hasClickLabel("Open the timer"))
        tapTimerNotification()
        // Straight onto the running timer, same step (full screen: no tab bar, no timer bar).
        rule.waitForText("Round 1")
        rule.waitForText("Resolve")
        rule.waitGone(hasText("32 heroes"))
        rule.waitGone(hasClickLabel("Open the timer"))

        // ---- End match → "Who won?" with the rematch's teams; Blue takes game 2.
        rule.tapDescription("End match")
        rule.waitForText("End the match?")
        rule.tap(hasAnyAncestor(isDialog()) and hasText("End match") and hasClickAction())
        rule.tapText("Log result")
        rule.waitForText("Which team won?")
        assertEquals(red.map { it.second }.toSet(), rule.winnerPilots("Red"))
        assertEquals(blue.map { it.second }.toSet(), rule.winnerPilots("Blue"))
        (red + blue).forEach { (hero, _) -> rule.reveal(hasContentDescription("Hero: $hero. Change hero")) }
        rule.tapDescription("Blue won", substring = true)
        rule.tap(hasText("Save") and hasClickAction())
        rule.waitForText("Result saved")
        val changes = rule.ratingChanges(4)
        blue.forEach { (_, pilot) -> assertTrue(changes.getValue(pilot).raw, changes.getValue(pilot).delta > 0) }
        red.forEach { (_, pilot) -> assertTrue(changes.getValue(pilot).raw, changes.getValue(pilot).delta < 0) }
        rule.tapText("Done")

        // ---- Both games are in the log; one win each, so everyone has played two.
        rule.openTab("players")
        rule.tap(hasText("Players") and hasClickAction() and hasRole(Role.Tab) and notBottomNav())
        rule.waitUntilOrDump("both games rated") {
            rule.leaderboardRows().count { it.contains("1 wins 1 losses") } == 4
        }
    }

    /** The timer ignores a Next within 0.5 s of a step starting (double-tap guard): wait it out. */
    private fun settle() {
        Thread.sleep(600)
        rule.waitForIdle()
    }

    private fun pressBack() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        rule.waitForIdle()
    }

    /** What a tap on the ongoing timer notification does: send its content intent. */
    private fun tapTimerNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var intent: android.app.PendingIntent? = null
        rule.waitUntilOrDump("the ongoing timer notification") {
            intent = manager.activeNotifications
                .firstOrNull { it.isOngoing && it.notification.contentIntent != null }
                ?.notification?.contentIntent
            intent != null
        }
        requireNotNull(intent).send()
        rule.waitForIdle()
    }
}
