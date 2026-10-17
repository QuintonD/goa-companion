package dev.quinton.goa.core.model.catalog

import com.google.common.truth.Truth.assertThat
import dev.quinton.goa.core.model.catalog.CardText.Segment.Break
import dev.quinton.goa.core.model.catalog.CardText.Segment.Icon
import dev.quinton.goa.core.model.catalog.CardText.Segment.Reminder
import dev.quinton.goa.core.model.catalog.CardText.Segment.Strong
import dev.quinton.goa.core.model.catalog.CardText.Segment.Text
import org.junit.Test

class CardTextTest {
    @Test
    fun `icons, reminders and breaks are parsed`() {
        assertThat(CardText.parse("Gain 1 {coin}.\n~(Not an action (really).)")).containsExactly(
            Text("Gain 1 "),
            Icon("coin"),
            Text("."),
            Break,
            Reminder("Not an action (really)."),
        ).inOrder()
    }

    @Test
    fun `bold keywords become strong segments`() {
        assertThat(CardText.parse("**This turn:** Enemy heroes cannot move.")).containsExactly(
            Strong(listOf(Text("This turn:"))),
            Text(" Enemy heroes cannot move."),
        ).inOrder()
        assertThat(CardText.parse("An enemy hero **chooses one —**\n• You gain 2 coins.")).containsExactly(
            Text("An enemy hero "),
            Strong(listOf(Text("chooses one —"))),
            Break,
            Text("• You gain 2 coins."),
        ).inOrder()
    }

    @Test
    fun `strong spans may contain icons and reminders`() {
        assertThat(CardText.parse("**+2 {attack} Attack ~(once)**.")).containsExactly(
            Strong(listOf(Text("+2 "), Icon("attack"), Text(" Attack "), Reminder("once"))),
            Text("."),
        ).inOrder()
        // Markers inside a reminder or icon do not close the span.
        assertThat(CardText.parse("**A ~(x ** y) b**")).containsExactly(
            Strong(listOf(Text("A "), Reminder("x ** y"), Text(" b"))),
        )
    }

    @Test
    fun `unmatched or empty markers are literal text`() {
        assertThat(CardText.parse("Deal 2 ** damage.")).containsExactly(Text("Deal 2 ** damage."))
        assertThat(CardText.parse("****")).containsExactly(Text("****"))
        // A span never crosses a paragraph break.
        assertThat(CardText.parse("**Open\nclose**")).containsExactly(Text("**Open"), Break, Text("close**")).inOrder()
        assertThat(CardText.parse("**a** b **c")).containsExactly(
            Strong(listOf(Text("a"))),
            Text(" b **c"),
        ).inOrder()
    }

    @Test
    fun `plain text drops markers and spells icons out`() {
        assertThat(CardText.plain("**This round:** +1 {fast_travel}.\n~(Reminder.)"))
            .isEqualTo("This round: +1 fast travel.\n(Reminder.)")
        assertThat(CardText.plain("Deal 2 ** damage.")).isEqualTo("Deal 2 ** damage.")
    }

    @Test
    fun `flatten expands strong spans`() {
        assertThat(CardText.flatten(CardText.parse("**{rune_axe} Axe:** go"))).containsExactly(
            Icon("rune_axe"),
            Text(" Axe:"),
            Text(" go"),
        ).inOrder()
    }
}
