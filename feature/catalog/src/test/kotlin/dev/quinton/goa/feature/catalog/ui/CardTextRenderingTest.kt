package dev.quinton.goa.feature.catalog.ui

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText
import org.junit.Test

class CardTextRenderingTest {
    @Test
    fun everyMarkupIconHasAGlyphAndSpokenLabel() {
        CardText.ICONS.forEach { name ->
            val icon = GameIcons.forToken(name)
            assertThat(icon).isNotNull()
            assertThat(icon?.label).isNotEmpty()
        }
        assertThat(GameIcons.forToken("not_an_icon")).isNull()
    }

    @Test
    fun iconsReadAsWordsAndUnknownIconsFallBackToText() {
        val annotated = cardTextAnnotated("Gain 1 {coin}. Use {mystery_thing}.\n~(Reminder.)", Color.Gray)
        // Inline icons carry their spoken label as alternate text, so TalkBack reads it.
        assertThat(annotated.text).isEqualTo("Gain 1 Coin. Use mystery thing.\n(Reminder.)")
    }
}
