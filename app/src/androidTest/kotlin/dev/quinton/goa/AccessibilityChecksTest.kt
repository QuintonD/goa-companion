package dev.quinton.goa

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.disableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility Test Framework checks (touch target size, labels, contrast, ...) on the main
 * screens of a game night. Every screen is checked and all findings are reported together.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityChecksTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val findings = mutableListOf<String>()

    /** Checks are switched on only while a screen is audited, so navigation never aborts the run. */
    private fun check(screen: String) {
        rule.waitForIdle()
        android.util.Log.i("A11yAudit", "Auditing screen: $screen")
        rule.enableAccessibilityChecks()
        try {
            runCatching { rule.onRoot().tryPerformAccessibilityChecks() }
                .onFailure { findings += "[$screen] ${it.message}" }
        } finally {
            rule.disableAccessibilityChecks()
        }
    }

    @Test
    fun mainScreensPassAccessibilityChecks() {
        grantNotifications()
        rule.waitForText("32 heroes")
        check("Heroes")

        rule.tap(hasText("Arien") and hasClickAction() and !hasSetTextAction())
        rule.waitForText("the Tidemaster")
        check("Hero detail")
        rule.tap(hasClickLabel("View card"))
        rule.waitForText("Arien · ", substring = true)
        check("Card viewer")
        rule.tapDescription("Back")
        rule.tapDescription("Back")

        rule.openTab("timer")
        check("Timer")

        rule.openTab("draft")
        rule.waitForText("Quick add a player")
        check("Draft setup")

        rule.openTab("players")
        check("Players")
        rule.tap(hasText("Matches") and hasClickAction())
        check("Matches")
        rule.tap(hasText("Meta") and hasClickAction())
        check("Meta")

        rule.tapDescription("Settings")
        rule.waitForText("Theme")
        check("Settings")

        assertTrue(findings.joinToString("\n\n"), findings.isEmpty())
    }

    /** Draft board, match setup, running timer, checklist, editor, leaderboard and meta. */
    @Test
    fun gameNightScreensPassAccessibilityChecks() {
        GameNight(rule, checkpoint = ::check).play()
        assertTrue(findings.joinToString("\n\n"), findings.isEmpty())
    }
}
