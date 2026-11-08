package dev.quinton.goa.feature.board.play.puzzles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhyItWorksTest {
    @Test
    fun aPuzzlesParagraphSplitsSentenceBySentenceWordForWord() {
        val text = "Each Red hero is level 2, so each costs one Life counter (p.13): Red must lose both. Dangerous " +
            "Current reaches the 2 spaces behind its target (p.3, p.9). \"Before the attack\" comes first. 6 against " +
            "Arien's 6 + 1, and she falls too."
        val why = PuzzleText.whyItWorks(text)
        assertNull(why.lead)
        assertEquals(listOf("1", "2", "3", "4"), why.steps.map { it.label })
        assertEquals(
            "Each Red hero is level 2, so each costs one Life counter (p.13): Red must lose both.",
            why.steps.first().text,
        )
        // Nothing reworded or lost: the lines read back as the paragraph.
        assertEquals(text, why.steps.joinToString(" ") { it.text })
    }

    @Test
    fun aSetupReadsOneSentenceALineWordForWord() {
        val intro = "Round 3, turn 4. Brogan has played Mighty Punch (9) and acts first, then Arien with " +
            "Dangerous Current (8); Wasp and Xargatha act after them. Wasp holds Lift Up (p.14). Red has two Life " +
            "counters left."
        val lines = PuzzleText.sentences(intro)
        assertEquals(4, lines.size)
        assertEquals("Round 3, turn 4.", lines.first())
        assertEquals("Wasp holds Lift Up (p.14).", lines[2])
        assertEquals(intro, lines.joinToString(" "))
    }

    @Test
    fun aFullRoundSplitsByTurnWithWhatComesBeforeAsTheLead() {
        val text = "This is one way through, and other plans win too.\n\nTurn 1: Trinkets holds.\n\n" +
            "Turn 2: Whisper defeats a minion. Widget moves.\n\nTurn 4: The last push."
        val why = PuzzleText.whyItWorks(text)
        assertEquals("This is one way through, and other plans win too.", why.lead)
        assertEquals(listOf("Turn 1", "Turn 2", "Turn 4"), why.steps.map { it.label })
        assertEquals("Whisper defeats a minion. Widget moves.", why.steps[1].text)
    }

    @Test
    fun paragraphsThatAreNotTurnsAreNumbered() {
        val why = PuzzleText.whyItWorks("First part.\n\nSecond part.")
        assertEquals(listOf("1", "2"), why.steps.map { it.label })
    }
}
