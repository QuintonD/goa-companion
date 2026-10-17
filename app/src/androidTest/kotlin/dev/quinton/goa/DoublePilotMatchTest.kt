package dev.quinton.goa

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Game night, odd count: 5 players as 3v3, Ana pilots two Red heroes (house rule). */
@RunWith(AndroidJUnit4::class)
class DoublePilotMatchTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun manualThreeVsThreeWithDoublePilotIsRated() {
        listOf("Ana", "Ben", "Cal", "Dee", "Eve").forEach { rule.createPlayerViaProfile(it) }

        rule.tap(hasText("Matches") and hasClickAction())
        rule.tapLabel("Log match")
        rule.waitForText("Duration (minutes, optional)")

        // New matches start 2v2; add the third seat on each team (Red's button comes first).
        rule.reveal(hasText("Add seat") and hasClickAction())
        rule.onAllNodes(hasText("Add seat") and hasClickAction())[0].performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasText("Add seat") and hasClickAction())[1].performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntilOrDump("six empty seats") {
            rule.onAllNodes(hasContentDescription("Hero: Unknown hero. Change hero")).fetchSemanticsNodes().size == 6
        }

        // Red: Arien (Ana), Brogan (Ana), Tigerclaw (Ben). Blue: Wasp (Cal), Sabina (Dee), Xargatha (Eve).
        val seats = listOf(
            "Arien" to "Ana",
            "Brogan" to "Ana",
            "Tigerclaw" to "Ben",
            "Wasp" to "Cal",
            "Sabina" to "Dee",
            "Xargatha" to "Eve",
        )
        seats.forEach { (hero, pilot) ->
            rule.tap(hasContentDescription("Hero: Unknown hero. Change hero") and hasClickAction())
            rule.waitForText("Search heroes")
            rule.onNode(hasSetTextAction() and hasText("Search heroes")).performTextInput(hero)
            rule.tap(hasText(hero) and hasRole(Role.RadioButton))
            rule.waitForDescription("Hero: $hero. Change hero")

            rule.tap(hasContentDescription("Pilot: none. Change pilot") and hasClickAction())
            rule.waitForText("Pilot for", substring = true)
            rule.tap(hasText(pilot) and hasRole(Role.RadioButton))
            rule.waitGone(hasText("Pilot for", substring = true))
        }
        rule.waitForText("Double pilot")

        if (rule.exists(hasText("Completed") and hasClickAction())) rule.tapText("Completed")
        rule.tapDescription("Red won")
        rule.tapText("Throne")
        rule.tap(hasText("Save") and hasClickAction())

        rule.tap(hasText("Players") and hasClickAction() and hasRole(Role.Tab) and notBottomNav())
        rule.waitUntilOrDump("leaderboard rated") {
            rule.leaderboardRows().count { parseRow(it).rating != null } == 5
        }
        val rows = rule.leaderboardRows().map(::parseRow)
        assertEquals("Ana is listed once", 1, rows.count { it.name == "Ana" })
        assertEquals(5, rows.size)
        val byName = rows.associateBy { it.name }
        listOf("Ana", "Ben").forEach { assertTrue(byName.getValue(it).raw, byName.getValue(it).rating!! > 1200) }
        listOf("Cal", "Dee", "Eve").forEach { assertTrue(byName.getValue(it).raw, byName.getValue(it).rating!! < 1200) }
        // Ana carried two seats: doublePilotWeight 0.5 → her gain is 1.5× Ben's.
        assertTrue(byName.getValue("Ana").raw, byName.getValue("Ana").rating!! > byName.getValue("Ben").rating!!)
    }
}
