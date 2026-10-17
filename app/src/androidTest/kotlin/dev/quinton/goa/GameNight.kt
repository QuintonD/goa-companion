package dev.quinton.goa

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The core game night, scripted through the real UI: House draft (2v2) → match setup →
 * table-rules timer for a full round → log the result → ratings and meta.
 *
 * [checkpoint] is called with a screen name on every main screen of the flow (the accessibility
 * test audits each one); [GameNightFlowTest] runs it without checkpoints.
 */
class GameNight(private val rule: AppRule, private val checkpoint: (String) -> Unit = {}) {
    private val players = listOf("Ana", "Ben", "Cal", "Dee")

    fun play() {
        grantNotifications()

        // ---- Draft setup: players, teams, method, Tie Breaker coin.
        rule.openTab("draft")
        rule.waitForText("Quick add a player")
        players.forEach { name ->
            rule.typeInto("Quick add a player", name)
            rule.tapDescription("Add player")
            rule.waitForText(name)
        }
        rule.waitForText("4 selected", substring = true)
        checkpoint("Draft setup · Players")
        rule.tapText("Next: Teams")
        rule.waitForText("2 players · 2 heroes", substring = true)
        checkpoint("Draft setup · Teams")
        rule.tapText("Next: Heroes")
        rule.tap(hasText("House draft") and hasClickAction())
        checkpoint("Draft setup · Heroes")
        rule.tapText("Next: Tie Breaker")
        rule.tapText("Flip the coin")
        rule.waitForText("is Team A", substring = true)
        checkpoint("Draft setup · Tie Breaker")
        val teamA = if (rule.exists(hasText("◆ Red is Team A"))) Side.RED else Side.BLUE
        val teamB = teamA.other
        rule.tap(hasText("Start draft", substring = true) and hasClickAction())

        // ---- Draft board: House draft 2v2 = Ban A · Pick A · Ban B · Pick B · Pick B · Pick A.
        val expected = listOf(
            Step(teamA, "Ban", "Team A · Step 1 of 6"),
            Step(teamA, "Pick", "Team A · Step 2 of 6"),
            Step(teamB, "Ban", "Team B · Step 3 of 6"),
            Step(teamB, "Pick 2", "Team B · Step 4 of 6 · 1 of 2"),
            Step(teamB, "Pick 2", "Team B · Step 5 of 6 · 2 of 2"),
            Step(teamA, "Pick", "Team A · Step 6 of 6"),
        )
        val picked = mutableListOf<String>()
        val pickedBy = linkedMapOf<String, Side>()
        expected.forEachIndexed { i, step ->
            rule.waitForText("${step.team.label} — ${step.verb}")
            rule.waitForText(step.subtitle, substring = true)
            if (i == 0) checkpoint("Draft board")
            val tile = hasClickLabelStartingWith("Choose ")
            rule.reveal(tile)
            val hero = rule.onAllNodes(tile).fetchSemanticsNodes().first().descriptions.first()
            rule.tap(hasContentDescription(hero) and tile)
            val action = if (step.verb.startsWith("Ban")) "Ban" else "Pick"
            rule.waitForText("$action $hero?")
            if (i == 0) checkpoint("Draft confirm dialog")
            rule.tap(hasAnyAncestor(isDialog()) and hasClickAction() and hasText("${step.team.glyph} $action"))
            rule.waitGone(hasText("$action $hero?"))
            if (action == "Pick") {
                picked += hero
                pickedBy[hero] = step.team
            }
        }
        assertEquals(4, picked.distinct().size)

        // ---- Draft complete: every pick has a pilot.
        rule.waitForText("Set up match")
        checkpoint("Draft complete")
        val seatPattern = Regex("""^(.+), played by (.+?)(,.*)?$""")
        picked.forEach { hero -> rule.reveal(hasContentDescription("$hero, played by", substring = true)) }
        val pilotOf = picked.associateWith { hero ->
            val desc = rule.onAllNodes(hasContentDescription("$hero, played by", substring = true))
                .fetchSemanticsNodes().first().descriptions.first()
            requireNotNull(seatPattern.find(desc)).groupValues[2]
        }
        assertEquals(players.toSet(), pilotOf.values.toSet())
        rule.tapText("Set up match")

        // ---- Match setup: Quick, 4 heroes → 3 Wave counters, 4 Life counters per team.
        rule.waitForText("Match setup")
        rule.tap(hasText("Quick") and hasClickAction())
        rule.waitForDescription("3 Wave counters")
        rule.waitForDescription("Red: 4 Life counters")
        rule.waitForDescription("Blue: 4 Life counters")
        checkpoint("Match setup")
        rule.tap(hasText("Start timer") and hasClickAction())

        // ---- Timer, round 1.
        // Every phase starts fresh and running (TimerEngine.next); the test pauses right away so
        // the full duration can be read off the dial.
        rule.waitForText("Round 1")
        assertPhase("Pre-round strategy", "ROUND START")
        pauseAndAssertClock(90)
        checkpoint("Timer · Strategy (paused)")

        rule.tapText("Next: Turn 1")
        assertPhase("Turn", "TURN 1 OF 4")
        rule.waitFor(hasStateDescription("Running"))
        checkpoint("Timer · Turn (running)")
        pauseAndAssertClock(30)
        // Pause / resume: the countdown moves only while running.
        val frozen = clock()
        Thread.sleep(1_500)
        rule.waitForIdle()
        assertEquals("Paused clock must not move", frozen, clock())
        rule.tapDescription("Resume")
        rule.waitFor(hasStateDescription("Running"))
        rule.waitUntil(5_000) { clock() != frozen }

        for (turn in 2..4) {
            rule.tapText("Next: Turn $turn")
            assertPhase("Turn", "TURN $turn OF 4")
            pauseAndAssertClock(30)
        }

        // ---- End of round: the six official steps in order, Upgrade timer on Level Up.
        val endSteps = listOf(
            "Minion Battle",
            "Remove Tokens, Return Markers",
            "Push the Lane",
            "Retrieve Cards",
            "Level Up",
            "Collect a Pity Coin",
        )
        endSteps.forEachIndexed { i, step ->
            val title = if (step == "Level Up") "Upgrade · Level Up" else step
            rule.tapText("Next: $title")
            assertPhase(title, "END OF ROUND · STEP ${i + 1} OF 6")
            assertEquals(endSteps, checklistOrder())
            rule.waitFor(hasText(step) and hasStateDescription("current step"))
            if (i == 0) checkpoint("Timer · End-of-round checklist")
            if (step == "Level Up") {
                pauseAndAssertClock(120)
                rule.waitForText("Upgrade 2:00")
                checkpoint("Timer · Upgrade on Level Up")
            }
        }
        rule.tapText("Start round 2")
        rule.waitForText("Round 2")
        assertPhase("Pre-round strategy", "ROUND START")

        // ---- End match (confirmed) → Log result.
        rule.tapDescription("End match")
        rule.waitForText("End the match?")
        rule.tap(hasAnyAncestor(isDialog()) and hasText("End match") and hasClickAction())
        rule.waitForText("Log result")
        checkpoint("Match summary")
        rule.tapText("Log result")

        // ---- Match editor: drafted seats and duration pre-filled.
        rule.waitForText("Duration (minutes, optional)")
        picked.forEach { hero -> rule.waitForDescription("Hero: $hero. Change hero") }
        players.forEach { name -> rule.waitForDescription("Pilot: $name. Change pilot") }
        val duration = rule.onNode(hasSetTextAction() and hasText("Duration (minutes, optional)"))
            .fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        assertTrue("Duration pre-filled from the timer, was '$duration'", duration.toIntOrNull() != null)
        checkpoint("Match editor")

        // Ground truth from the draft: each team's pilots are the pilots of the heroes it picked.
        val red = pickedBy.filterValues { it == Side.RED }.keys.map { pilotOf.getValue(it) }.toSet()
        val blue = pickedBy.filterValues { it == Side.BLUE }.keys.map { pilotOf.getValue(it) }.toSet()
        // The editor shows the same split: seats above the first "Team Blue" badge are Red.
        val blueTop = rule.onAllNodes(hasContentDescription("Team Blue"))
            .fetchSemanticsNodes().minOf { it.positionInRoot.y }
        val pilotRows = rule.onAllNodes(hasContentDescription("Pilot: ", substring = true)).fetchSemanticsNodes()
            .map {
                it.descriptions.first().removePrefix("Pilot: ").removeSuffix(". Change pilot") to it.positionInRoot.y
            }
        val editorRed = pilotRows.filter { it.second < blueTop }.map { it.first }.toSet()
        assertEquals("Editor Red seats match the Red draft picks ($pickedBy, $pilotOf)", red, editorRed)

        if (rule.exists(hasText("Completed") and hasClickAction())) rule.tapText("Completed")
        rule.tapDescription("Red won")
        rule.tapText("Throne")
        rule.tap(hasText("Save") and hasClickAction())

        // ---- Leaderboard: winners above 1200, losers below, all provisional.
        rule.openTab("players")
        rule.tap(hasText("Players") and hasClickAction() and hasRole(Role.Tab) and notBottomNav())
        rule.waitUntilOrDump("leaderboard rated") {
            rule.leaderboardRows().map(::parseRow).count { it.rating != null } == 4
        }
        checkpoint("Players · Leaderboard")
        val rows = rule.leaderboardRows().map(::parseRow).associateBy { it.name }
        red.forEach { name ->
            val row = rows.getValue(name)
            assertTrue("Winner $name above 1200: ${row.raw}", row.rating!! > 1200)
            assertTrue("Provisional: ${row.raw}", row.provisional)
        }
        blue.forEach { name ->
            val row = rows.getValue(name)
            assertTrue("Loser $name below 1200: ${row.raw}", row.rating!! < 1200)
            assertTrue("Provisional: ${row.raw}", row.provisional)
        }

        // ---- Meta: every picked hero is listed.
        rule.tap(hasText("Meta") and hasClickAction())
        picked.forEach { hero ->
            rule.reveal(
                hasContentDescription("$hero, 1 wins", substring = true) or
                    hasContentDescription("$hero, 0 wins", substring = true),
            )
        }
        checkpoint("Players · Meta")
    }

    private fun assertPhase(title: String, context: String) {
        rule.waitFor(hasText(title), unmerged = true)
        rule.waitFor(hasText(context), unmerged = true)
    }

    /** Pauses the running phase and checks the dial shows (almost) the full [seconds]. */
    private fun pauseAndAssertClock(seconds: Int) {
        rule.tapDescription("Pause")
        rule.waitFor(hasStateDescription("Paused"))
        val shown = clock()
        val (m, s) = shown.split(":").map { it.toInt() }
        val expected = "%d:%02d".format(seconds / 60, seconds % 60)
        assertTrue("Expected about $expected, dial shows $shown", m * 60 + s in (seconds - 3)..seconds)
    }

    /** The countdown digits inside the dial ("1:30", "0:30", "+0:03"). */
    private fun clock(): String {
        val pattern = Regex("""^\+?\d+:\d\d$""")
        return rule.onAllNodes(hasStateDescription("Paused") or hasStateDescription("Running"))
            .fetchSemanticsNodes()
            .flatMap { it.texts }
            .first { pattern.matches(it) }
    }

    /** End-of-round checklist titles, top to bottom (the number badge is skipped). */
    private fun checklistOrder(): List<String> {
        val states = setOf("done", "current step", "upcoming")
        return rule.onAllNodes(
            SemanticsMatcher("checklist row") { it.config.getOrNull(SemanticsProperties.StateDescription) in states },
        ).fetchSemanticsNodes()
            .sortedBy { it.positionInRoot.y }
            .map { row -> row.texts.first { it.toIntOrNull() == null } }
    }

    private enum class Side(val label: String, val glyph: String) {
        RED("◆ Red", "◆"),
        BLUE("● Blue", "●"),
        ;

        val other get() = if (this == RED) BLUE else RED
    }

    private data class Step(val team: Side, val verb: String, val subtitle: String)
}
