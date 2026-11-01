package dev.quinton.goa.feature.rules

import androidx.compose.ui.graphics.luminance
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.feature.rules.ui.RulesBox
import dev.quinton.goa.feature.rules.ui.RulesBoxes
import dev.quinton.goa.feature.rules.ui.RulesDiagrams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The boxes are read off the printed pages by opening words: a reworded reference must not lose them silently. */
class RulesBoxesTest {
    private val excerpts = bundledReference.topics.flatMap { it.excerpts } + bundledReference.ladder.allExcerpts

    @Test
    fun `every boxed passage is in the reference`() {
        RulesBoxes.entries.forEach { entry ->
            assertTrue(
                "p.${entry.page} \"${entry.opening}\" is not in the reference",
                excerpts.any {
                    RulesBoxes.of(it) == entry.box && it.page == entry.page && it.text.contains(entry.opening)
                },
            )
        }
    }

    @Test
    fun `each diagram has its printed caption, under the push's steps`() {
        val lane = bundledReference.topics.first { it.id == "pushing-the-lane" }.excerpts
        assertEquals(
            listOf(LookArt.PUSH_DIAGRAM_1, LookArt.PUSH_DIAGRAM_2),
            lane.mapNotNull { RulesDiagrams.of(it)?.art },
        )
        assertEquals("Push the Lane", lane[lane.indexOfFirst { RulesDiagrams.of(it) != null } - 1].heading)
    }

    @Test
    fun `the book's boxes come out as printed`() {
        val lane = bundledReference.topics.first { it.id == "pushing-the-lane" }.excerpts
        assertEquals(listOf(null, null, null, null, RulesBox.BLUE, RulesBox.BLUE), lane.map(RulesBoxes::of))
        val endOfRound = bundledReference.topics.first { it.id == "end-of-round" }.excerpts
        assertEquals(RulesBox.GREEN, RulesBoxes.of(endOfRound[1]))
        assertNull(RulesBoxes.of(endOfRound[0]))
    }

    /** WCAG contrast of each box's text on its ground: at least 4.5:1. */
    @Test
    fun `box text is readable`() {
        RulesBox.entries.forEach { box ->
            val ratio = contrast(box.ground.luminance(), box.onGround.luminance())
            assertTrue("$box is only $ratio:1", ratio >= 4.5)
        }
    }

    private fun contrast(a: Float, b: Float): Float = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}
