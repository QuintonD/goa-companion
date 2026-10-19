package dev.quinton.goa.core.presentation.catalog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The snippet under a card search result (synthetic card text). */
class CardSnippetTest {
    private val choose = "**Choose one —**\n• Perform a synthetic action.\n• Defeat a placeholder."

    @Test
    fun `the paragraph that matched is shown`() {
        assertThat(matchingParagraph(choose, "placeholder")).isEqualTo("• Defeat a placeholder.")
        assertThat(matchingParagraph("First line.\nSecond line.", "second")).isEqualTo("Second line.")
    }

    @Test
    fun `a lead-in comes with the options after it`() {
        // Matched by name or hero: the first paragraph is just "Choose one —".
        assertThat(matchingParagraph(choose, "reload")).isEqualTo(choose)
        assertThat(matchingParagraph(choose, "")).isEqualTo(choose)
        assertThat(matchingParagraph("If able:\nMove 1 space.", "able")).isEqualTo("If able:\nMove 1 space.")
    }

    @Test
    fun `an ordinary first paragraph stands alone`() {
        assertThat(matchingParagraph("Move 2 spaces.\nThen attack.", "zzz")).isEqualTo("Move 2 spaces.")
        assertThat(matchingParagraph("", "zzz")).isEmpty()
    }
}
