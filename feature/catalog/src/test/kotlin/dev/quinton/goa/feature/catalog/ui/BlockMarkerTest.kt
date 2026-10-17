package dev.quinton.goa.feature.catalog.ui

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.PrimaryAction
import org.junit.Test

/**
 * Rulebook p.14: a "!" Defense card blocks "by fulfilling a Block condition in the card text …
 * If not, you are defeated." The marker says Block only when the text has a Block condition.
 */
class BlockMarkerTest {
    private fun card(text: String, block: Boolean = true) = Card(
        id = "x-green-1",
        heroId = "x",
        name = "Synthetic",
        color = CardColor.GREEN,
        primary = PrimaryAction.DEFENSE,
        block = block,
        text = text,
    )

    @Test
    fun `a printed exclamation mark with a Block condition is labelled Block`() {
        assertThat(card("Block a ranged attack.").blockMarkerLabel()).isEqualTo("Block")
        assertThat(card("If you do, block the attack.").blockMarkerLabel()).isEqualTo("Block")
        assertThat(card("{rune_horn}: Block a basic attack.").blockMarkerLabel()).isEqualTo("Block")
    }

    @Test
    fun `a printed exclamation mark without a Block condition is not labelled Block`() {
        assertThat(card("~(You are defeated.)").blockMarkerLabel()).isEqualTo("No Block condition")
        // "Blocked" or "blocking" is not the word Block.
        assertThat(card("Ignore blocking terrain.").blockMarkerLabel()).isEqualTo("No Block condition")
    }

    @Test
    fun `a card without the exclamation mark has no marker`() {
        assertThat(card("Block a ranged attack.", block = false).blockMarkerLabel()).isNull()
    }
}
