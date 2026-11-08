package dev.quinton.goa.feature.rules

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.feature.rules.ui.DIAGRAMS
import dev.quinton.goa.feature.rules.ui.bannerTitle
import dev.quinton.goa.feature.rules.ui.openingWords
import dev.quinton.goa.feature.rules.ui.topicAnchors
import dev.quinton.goa.feature.rules.ui.topicSections
import org.junit.Test

class TopicSectionsTest {
    private fun topic(id: String) = bundledReference.topics.first { it.id == id }

    @Test fun `a long topic is navigated by the rulebook's own heads`() {
        val turn = topic("turn-sequence")
        val sections = topicSections(turn)
        val labels = sections.map { it.label }
        assertThat(labels).containsAtLeast("Round Structure", "Tied Initiative", "Turn Ends").inOrder()
        // Every label but the app's own word is printed in the passage it opens.
        sections.forEach { assertThat(turn.excerpts[it.excerpt].heading?.trimEnd(':')).isEqualTo(it.label) }
    }

    @Test fun `pushing the lane finds its diagrams and its boxes by their own words`() {
        val push = topic("pushing-the-lane")
        val sections = topicSections(push)
        assertThat(sections.map { it.label }).contains(DIAGRAMS)
        val box = sections.first { it.label.startsWith("A Lane can be Pushed") }
        assertThat(push.excerpts[box.excerpt].text).startsWith(box.label.removeSuffix("…"))
        assertThat(sections.map { it.excerpt }).isInOrder()
    }

    @Test fun `pushing the lane points at its steps and both diagrams near its top`() {
        val push = topic("pushing-the-lane")
        val anchors = topicAnchors(push)!!
        assertThat(anchors.diagrams.map { it.first }).hasSize(2)
        // Steps opens the passage that prints the numbered steps, its own words untouched.
        val steps = push.excerpts[anchors.steps!!]
        assertThat(steps.heading).isEqualTo("Push the Lane")
        assertThat(steps.text).contains("1. Flip one Wave counter")
        // Only a topic with diagrams gets the anchors.
        assertThat(topicAnchors(topic("turn-sequence"))).isNull()
    }

    @Test fun `every label that quotes the book is verbatim`() {
        bundledReference.topics.forEach { topic ->
            topicSections(topic).filter { it.label != DIAGRAMS }.forEach { section ->
                val excerpt = topic.excerpts[section.excerpt]
                val quoted = section.label.removeSuffix("…")
                val source = excerpt.heading ?: excerpt.text.removePrefix("• ")
                assertThat(source).startsWith(quoted)
            }
        }
    }

    @Test fun `opening words end at a word with an ellipsis`() {
        assertThat(openingWords("A Lane can be Pushed both at the end of round.")).isEqualTo("A Lane can be Pushed…")
        assertThat(openingWords("Short one.")).isEqualTo("Short one")
    }

    @Test fun `a long title gives the banner its head`() {
        assertThat(bannerTitle("Pushing the Lane", large = false)).isEqualTo("Pushing the Lane")
        assertThat(bannerTitle("Turn sequence & tied initiative", large = false)).isEqualTo("Turn sequence")
        assertThat(bannerTitle("End of round: the 6 steps", large = false)).isEqualTo("End of round")
        assertThat(bannerTitle("Attack, defense & defeats", large = false)).isEqualTo("Attack, defense")
        assertThat(bannerTitle("Minions, minion coins & the Heavy minion", large = false)).isEqualTo("Minions")
    }
}
