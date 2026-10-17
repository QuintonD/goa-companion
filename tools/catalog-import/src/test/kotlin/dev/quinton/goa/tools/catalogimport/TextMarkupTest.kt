package dev.quinton.goa.tools.catalogimport

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText
import org.junit.Test

class TextMarkupTest {
    @Test
    fun `printed line wraps are joined into one paragraph`() {
        val soa = "Target a unit adjacent to you.\nBefore the attack: You may move another\n" +
            "unit that is adjacent to the target 1 space."
        assertThat(TextMarkup.fromSoa(soa)).isEqualTo(
            "Target a unit adjacent to you.\n" +
                "Before the attack: You may move another unit that is adjacent to the target 1 space.",
        )
    }

    @Test
    fun `a colon at the end of a line does not start a paragraph`() {
        val soa = "Target a unit in range. After the attack:\nIf the target is adjacent to you, push it 1 space."
        assertThat(TextMarkup.fromSoa(soa))
            .isEqualTo("Target a unit in range. After the attack: If the target is adjacent to you, push it 1 space.")
    }

    @Test
    fun `reminder lines become reminder paragraphs`() {
        val soa = "Swap with an enemy minion in range.\n~(Swap places with the target. This is not movement.)"
        val out = TextMarkup.fromSoa(soa)
        assertThat(
            out,
        ).isEqualTo("Swap with an enemy minion in range.\n~(Swap places with the target. This is not movement.)")
        assertThat(
            CardText.parse(out).last(),
        ).isEqualTo(CardText.Segment.Reminder("Swap places with the target. This is not movement."))
    }

    @Test
    fun `choose-one options become bullets and continuation lines are joined`() {
        val soa = "**Choose one —**\n>>Target a hero in range with\n" +
            ">a ::marker_bounty:: Bounty marker.\n>>Target a unit adjacent to you."
        assertThat(TextMarkup.fromSoa(soa)).isEqualTo(
            "**Choose one —**\n• Target a hero in range with a {marker_bounty} Bounty marker.\n" +
                "• Target a unit adjacent to you.",
        )
    }

    @Test
    fun `bold keywords are kept as markup`() {
        val out = TextMarkup.fromSoa("**This turn:** Enemy heroes in radius\ncannot fast travel.")
        assertThat(out).isEqualTo("**This turn:** Enemy heroes in radius cannot fast travel.")
        assertThat(
            CardText.parse(out).first(),
        ).isEqualTo(CardText.Segment.Strong(listOf(CardText.Segment.Text("This turn:"))))
        // A bold line still starts its own paragraph after a sentence.
        assertThat(TextMarkup.fromSoa("Gain 1 coin.\n**End of turn:** May repeat once."))
            .isEqualTo("Gain 1 coin.\n**End of turn:** May repeat once.")
        assertThat(TextMarkup.tidyBold("** This turn: ** go")).isEqualTo("**This turn:** go")
    }

    @Test
    fun `aligned text keeps source-1 bold on the reference paragraphs`() {
        val soa = TextMarkup.fromSoa(
            "**Choose one, or both,** on different targets **—**\n>>Target a hero.\n>>Target a\n>unit.",
        )
        val faq = TextMarkup.fromFaq(
            "**Choose one or both**, on different targets —\n- Target a hero.\n- Target a unit.",
        )
        assertThat(TextMarkup.agrees(soa, faq)).isFalse()
        val sameWords = TextMarkup.fromFaq(
            "Choose one, or both, on different targets —\n- Target a hero.\n- Target a unit.",
        )
        assertThat(TextMarkup.aligned(soa, sameWords))
            .isEqualTo("**Choose one, or both,** on different targets **—**\n• Target a hero.\n• Target a unit.")
        // A bold span the reference would split across paragraphs keeps the source-1 text.
        assertThat(TextMarkup.aligned("**This turn: you** win.", "This turn:\nyou win.")).isNull()
        assertThat(TextMarkup.aligned("This turn: you win.", "This turn:\nyou win.")).isEqualTo("This turn:\nyou win.")
    }

    @Test
    fun `icon-keyed alternatives each get a paragraph and dividers are dropped`() {
        val soa = "::tiebreaker_blue::: Target a minion adjacent to you.\n---\n" +
            "::tiebreaker_orange::: Target a hero in range."
        assertThat(TextMarkup.fromSoa(soa))
            .isEqualTo(
                "{tiebreaker_blue}: Target a minion adjacent to you.\n{tiebreaker_orange}: Target a hero in range.",
            )
        val runes = "if a rune is active:\n::rune_axe::: +3 ::attack_gold:: Attack.\n::rune_anvil::: Gain 2 coins."
        assertThat(TextMarkup.fromSoa(runes))
            .isEqualTo("if a rune is active:\n{rune_axe}: +3 {attack} Attack.\n{rune_anvil}: Gain 2 coins.")
    }

    @Test
    fun `a line after an inline icon is a continuation`() {
        val soa = "if you would place any tokens this way, place ::token_illusion::\nIllusion tokens instead."
        assertThat(
            TextMarkup.fromSoa(soa),
        ).isEqualTo("if you would place any tokens this way, place {token_illusion} Illusion tokens instead.")
    }

    @Test
    fun `source icon tokens map onto the app icon names`() {
        assertThat(TextMarkup.iconName("attack_red")).isEqualTo("attack")
        assertThat(TextMarkup.iconName("defense_silver")).isEqualTo("defense")
        assertThat(TextMarkup.iconName("movement_blue")).isEqualTo("movement")
        assertThat(TextMarkup.iconName("range_purple")).isEqualTo("range")
        assertThat(TextMarkup.iconName("radius_silver")).isEqualTo("radius")
        assertThat(TextMarkup.iconName("skill_silver")).isEqualTo("skill")
        assertThat(TextMarkup.iconName("life_counters")).isEqualTo("life")
        assertThat(TextMarkup.iconName("tiebreaker_blue")).isEqualTo("tiebreaker_blue")
        assertThat(TextMarkup.iconName("token_zombie")).isEqualTo("token_zombie")
        assertThat(TextMarkup.iconName("not_an_icon")).isNull()
    }

    @Test
    fun `unknown icons are kept as text and reported`() {
        val unknown = mutableSetOf<String>()
        assertThat(TextMarkup.fromSoa("Place a ::mystery:: token.", unknown)).isEqualTo("Place a ::mystery:: token.")
        assertThat(unknown).containsExactly("mystery")
    }

    @Test
    fun `goa2-faq markdown converts to the same markup`() {
        val faq = "**Choose one —**\n- Target a unit adjacent to you.\n" +
            "- Defeat a minion adjacent to you.\n_(This is a reminder.)_"
        assertThat(TextMarkup.fromFaq(faq)).isEqualTo(
            "Choose one —\n• Target a unit adjacent to you.\n" +
                "• Defeat a minion adjacent to you.\n~(This is a reminder.)",
        )
        assertThat(TextMarkup.fromFaq("::tiebreaker_blue::: A.\n--- \n::tiebreaker_orange:: B."))
            .isEqualTo("{tiebreaker_blue}: A.\n{tiebreaker_orange} B.")
    }

    @Test
    fun `padding inside parentheses and repeated spaces are tidied`() {
        assertThat(TextMarkup.tidy("~( You  are defeated. )")).isEqualTo("~(You are defeated.)")
    }

    @Test
    fun `agreement ignores typography and paragraphing but not words`() {
        assertThat(
            TextMarkup.agrees("If \"Guardian Spirit\" is in the discard.", "If “Guardian Spirit” is in the\ndiscard."),
        ).isTrue()
        assertThat(TextMarkup.agrees("Choose one —\n• A.", "Choose one -\nA.")).isTrue()
        assertThat(TextMarkup.agrees("**Choose one —**\n• A.", "Choose one —\n• A.")).isTrue()
        assertThat(TextMarkup.agrees("Push it 1 space.", "Push the target 1 space.")).isFalse()
        assertThat(TextMarkup.agrees("Gain 2 coins.", "gain 2 coins.")).isFalse()
    }

    @Test
    fun `aligned prefers the reference wording only when the words agree`() {
        assertThat(TextMarkup.aligned("A b\nc.", "A b c.")).isEqualTo("A b c.")
        assertThat(TextMarkup.aligned("A b c.", "A x c.")).isNull()
    }

    @Test
    fun `difference description points at the first differing word`() {
        assertThat(TextMarkup.describeDifference("one two three", "one two four")).startsWith("word 3:")
    }

    @Test
    fun `rulings become plain paragraphs`() {
        assertThat(TextMarkup.plainRuling("**No.** It does.\n --- \n- First\n- Uses ::attack:: here"))
            .isEqualTo("No. It does.\n• First\n• Uses attack here")
    }
}
