package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.GameEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogLinesTest {
    private fun heading(text: String) = LogLine(text, heading = true)
    private fun event(text: String) = LogLine(text, notice = true)

    @Test
    fun newestFirst_putsTheLatestTurnOnTopWithItsHeadingAboveItsEvents() {
        val lines = listOf(
            event("Setup."),
            heading("Round 1 begins."),
            heading("Turn 1 of round 1."),
            event("A"),
            event("B"),
            heading("Turn 2 of round 1."),
            event("C"),
            event("D"),
        )

        val ordered = newestFirst(lines)

        assertEquals(
            listOf(
                "Turn 2 of round 1.",
                "D",
                "C",
                "Round 1 begins.",
                "Turn 1 of round 1.",
                "B",
                "A",
                "Setup.",
            ),
            ordered.map { it.value.text },
        )
        // Each line keeps its place in the log, for the review's marker.
        assertEquals(listOf(5, 7, 6, 1, 2, 4, 3, 0), ordered.map { it.index })
    }

    @Test
    fun newestFirst_keepsAHeadingWithNothingAfterItYet() {
        val lines = listOf(heading("Turn 1 of round 1."), event("A"), heading("Turn 2 of round 1."))

        assertEquals(listOf("Turn 2 of round 1.", "Turn 1 of round 1.", "A"), newestFirst(lines).map { it.value.text })
    }

    @Test
    fun feedSince_isTheNoticesAddedSinceTheLastLook() {
        val lines = listOf(event("A"), heading("Turn 2 of round 1."), event("B"), LogLine("C"), event("D"))

        assertEquals(listOf("B", "D"), feedSince(lines, seen = 1).map { it.value.text })
        assertEquals(listOf(2, 4), feedSince(lines, seen = 1).map { it.index })
    }

    @Test
    fun feedSince_isEmptyOnTheFirstLookAndAfterATakeBack() {
        val lines = listOf(event("A"), event("B"))

        assertTrue(feedSince(lines, seen = null).isEmpty())
        assertTrue(feedSince(lines, seen = 3).isEmpty())
        assertTrue(feedSince(lines, seen = 2).isEmpty())
    }

    @Test
    fun logLines_noticesActionsAndMovesButNotHeadingsRevealsOrFacedownCards() {
        val lines = logLines(
            listOf(
                GameEvent.TurnStarted(1, 1, "Turn 1 of round 1."),
                GameEvent.Note("Brogan plays a card facedown."),
                GameEvent.Moved("brogan", emptyList(), "Brogan moves 2 spaces."),
                GameEvent.Defeated("wasp", "brogan", "Wasp is defeated by Brogan."),
                GameEvent.Defeated("red-melee-1", "brogan", "Red melee minion is defeated by Brogan."),
            ),
            heroIds = setOf("brogan", "wasp"),
        )

        assertEquals(listOf(false, false, true, true, true), lines.map { it.notice })
        assertEquals(listOf(false, false, false, true, false), lines.map { it.strong })
    }

    @Test
    fun logTurns_putsTheLatestTurnFirstWithItsEventsInPlayOrder() {
        val lines = listOf(
            event("Setup."),
            heading("Round 1 begins."),
            heading("Turn 1 of round 1."),
            event("A"),
            event("B"),
            heading("Turn 2 of round 1."),
            event("C"),
            event("D"),
        )

        val turns = logTurns(lines)

        assertEquals(3, turns.size)
        assertEquals(listOf("Turn 2 of round 1."), turns[0].headings.map { it.value.text })
        assertEquals(listOf("C", "D"), turns[0].events.map { it.value.text })
        assertEquals(listOf("Round 1 begins.", "Turn 1 of round 1."), turns[1].headings.map { it.value.text })
        assertEquals(listOf("A", "B"), turns[1].events.map { it.value.text })
        assertEquals(listOf(0), turns[2].events.map { it.index })
        assertTrue(turns[2].headings.isEmpty())
    }

    @Test
    fun logLines_namesWhoAnEventIsAboutItsKindAndFoldsTheBookkeeping() {
        val lines = logLines(
            listOf(
                GameEvent.ActionChosen(
                    "arien",
                    "c",
                    dev.quinton.goa.core.game.ActionType.PRIMARY,
                    "Arien chooses primary.",
                ),
                GameEvent.Attacked("arien", "tigerclaw", 6, "Arien attacks Tigerclaw with Attack 6."),
                GameEvent.Defeated("tigerclaw", "arien", "Tigerclaw is defeated by Arien."),
                GameEvent.Note("Brogan plays a card facedown."),
            ),
        )

        assertEquals(listOf("arien", "arien", "tigerclaw", null), lines.map { it.actor })
        assertEquals(listOf(LogKind.ACTION, LogKind.ATTACK, LogKind.DEFEAT, LogKind.OTHER), lines.map { it.kind })
        assertEquals(listOf(true, false, false, true), lines.map { it.detail })
    }

    @Test
    fun defenseSum_countsTheCardItemsMinionsAndTheRest() {
        assertEquals("Defense 3 = card 2 + minion 1", defenseSum(total = 3, printed = 2, minion = 1, items = 0))
        assertEquals(
            "Defense 4 = card 2 + items 1 + minion 1",
            defenseSum(total = 4, printed = 2, minion = 1, items = 1),
        )
        assertEquals("Defense 1 = card 2 − minion 1", defenseSum(total = 1, printed = 2, minion = -1, items = 0))
        assertEquals("Defense 5 = card 3 + effects 2", defenseSum(total = 5, printed = 3, minion = null, items = 0))
        assertEquals(null, defenseSum(total = 3, printed = 3, minion = 0, items = 0))
        assertEquals(null, defenseSum(total = null, printed = 3, minion = 1, items = 0))
    }
}
