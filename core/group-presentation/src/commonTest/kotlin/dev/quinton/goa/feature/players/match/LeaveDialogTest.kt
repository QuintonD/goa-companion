package dev.quinton.goa.feature.players.match

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Back on a match with unsaved edits asks first, with the phone's words on both platforms: a result
 * not logged yet says the timer keeps the finished match (the web said "Your edits…" for both).
 */
class LeaveDialogTest {
    @Test
    fun aResultNotLoggedYetSaysTheTimerKeepsIt() {
        val words = MatchEditing.leaveDialog(unsavedResult = true)
        assertEquals("Leave without saving?", words.title)
        assertEquals(
            "This result isn't logged yet. The timer keeps the finished match, so you can log it later " +
                "from the Timer tab.",
            words.body,
        )
        assertEquals("Leave", words.leave)
        assertEquals("Keep editing", words.keep)
    }

    @Test
    fun editsToASavedMatchAreDiscarded() {
        val words = MatchEditing.leaveDialog(unsavedResult = false)
        assertEquals("Discard changes?", words.title)
        assertEquals("Your edits to this match haven't been saved.", words.body)
        assertEquals("Discard", words.leave)
    }
}
