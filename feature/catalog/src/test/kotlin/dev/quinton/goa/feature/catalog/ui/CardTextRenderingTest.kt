package dev.quinton.goa.feature.catalog.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText
import dev.quinton.goa.core.model.catalog.CardValues
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.presentation.catalog.printedValues
import dev.quinton.goa.core.presentation.catalog.spokenSummary
import dev.quinton.goa.core.testing.TestCatalog
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

    @Test
    fun boldKeywordsAreBoldWithoutMarkersAndUnmatchedMarkersStayLiteral() {
        val annotated = cardTextAnnotated("**This turn:** Gain 1 {coin}. Deal 2 ** damage.", Color.Gray)
        assertThat(annotated.text).isEqualTo("This turn: Gain 1 Coin. Deal 2 ** damage.")
        val bold = annotated.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertThat(bold.map { annotated.text.substring(it.start, it.end) }).containsExactly("This turn:")
    }

    @Test
    fun plusValuesPrintWithPlusAndReadAsPlus() {
        val card = TestCatalog.hero(1).cards.first().let {
            it.copy(values = CardValues(attack = 4, range = 1, plus = listOf(Stat.RANGE)))
        }
        val values = card.printedValues()
        assertThat(values.map { it.printed }).containsExactly("4", "1+").inOrder()
        assertThat(values.map { it.spoken }).containsExactly("Attack 4", "Range 1 plus").inOrder()
        assertThat(card.spokenSummary()).contains("Attack 4. Range 1 plus")
    }
}
