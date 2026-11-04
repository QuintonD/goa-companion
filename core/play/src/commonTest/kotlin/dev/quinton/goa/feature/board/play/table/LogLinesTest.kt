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
}
