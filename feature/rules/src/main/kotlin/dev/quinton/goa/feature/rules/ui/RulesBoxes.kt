package dev.quinton.goa.feature.rules.ui

import androidx.compose.ui.graphics.Color
import dev.quinton.goa.core.designsystem.component.LookArt
import dev.quinton.goa.core.model.rules.RulesExcerpt

/**
 * The coloured boxes the rulebook prints some passages in, with text colours that pass 4.5:1:
 * blue #0F6F99 is the book's own (5.3:1); its green (#40A848, 3.0:1) and orange (#D07028, 3.5:1) carry
 * white too faintly, so they are darkened to 5.3:1 and 5.2:1. The book sets the green ones in italics.
 * On the dark theme's page each box is a shade deeper ([darkGround]), the same hue, so it sits in the dim page
 * without glowing.
 */
internal enum class RulesBox(
    val ground: Color,
    val darkGround: Color,
    val onGround: Color,
    val italic: Boolean = false,
) {
    BLUE(Color(0xFF0F6F99), Color(0xFF0C5778), Color(0xFFF4FAFD)),
    GREEN(Color(0xFF2B7A33), Color(0xFF225F28), Color(0xFFFFFFFF), italic = true),
    ORANGE(Color(0xFFA8560F), Color(0xFF86440C), Color(0xFFFFFFFF)),
    ;

    /** The box's ground on the reader's page of the theme. */
    fun ground(dark: Boolean): Color = if (dark) darkGround else ground
}

/**
 * Which passages of `rules-reference.json` the rulebook prints in a box: read off the pinned PDF (the
 * colour behind each passage's text on its page), by page and opening words. The data doesn't record
 * it; `RulesBoxesTest` fails if a passage named here is no longer in the reference.
 */
internal object RulesBoxes {
    internal class Entry(val page: Int, val opening: String, val box: RulesBox)

    val entries = listOf(
        Entry(7, "If two (or more) players on different teams", RulesBox.BLUE),
        Entry(7, "If your hero is not on the board", RulesBox.BLUE),
        Entry(8, "A Lane can be Pushed both", RulesBox.BLUE),
        Entry(8, "If a Spawn point is occupied by a non-token", RulesBox.BLUE),
        Entry(13, "As long as there are any friendly non-heavy", RulesBox.BLUE),
        Entry(14, "+1 for every adjacent friendly melee", RulesBox.BLUE),
        Entry(14, "Cards with this icon can be used", RulesBox.BLUE),
        Entry(14, "Defense cards with an exclamation mark", RulesBox.BLUE),
        Entry(15, "If you forget to level up", RulesBox.BLUE),
        Entry(16, "Each item increases the value", RulesBox.BLUE),
        Entry(18, "Displacing a minion miniature", RulesBox.BLUE),
        Entry(20, "After the last Wave counter on either Lane", RulesBox.BLUE),
        Entry(20, "The double-lane map has two individual Lanes", RulesBox.BLUE),
        Entry(20, "During setup, place the minions", RulesBox.BLUE),
        Entry(20, "Minions are bound to the Battle Zone", RulesBox.BLUE),
        Entry(4, "For example, if two players", RulesBox.GREEN),
        Entry(4, "Use your best judgement", RulesBox.GREEN),
        Entry(4, "Exceptions can be made for first-time", RulesBox.GREEN),
        Entry(7, "The Defense action is an exception", RulesBox.GREEN),
        Entry(8, "If you need to remove more minions", RulesBox.GREEN),
        Entry(8, "This cancels all your Active effects", RulesBox.GREEN),
        Entry(15, "Your Basic cards (Gold and Silver)", RulesBox.GREEN),
        Entry(15, "You must first upgrade all your non-basic", RulesBox.GREEN),
        Entry(15, "You should now have exactly 5 cards", RulesBox.GREEN),
        Entry(17, "After you are done performing the action", RulesBox.GREEN),
        Entry(20, "When using the double-lane map", RulesBox.GREEN),
        Entry(20, "Note: The double-lane map", RulesBox.ORANGE),
    )

    fun of(excerpt: RulesExcerpt): RulesBox? {
        val text = excerpt.text.removePrefix(BULLET)
        return entries.firstOrNull { it.page == excerpt.page && text.startsWith(it.opening) }?.box
    }

    private const val BULLET = "• "
}

/**
 * The diagrams the rulebook prints, and the caption it prints under each. A caption is a verbatim passage
 * of `rules-reference.json` (so the importer checks it against the PDF); the picture goes above it, and
 * `RulesBoxesTest` fails if a caption named here is no longer in the reference.
 */
internal object RulesDiagrams {
    internal class Entry(val page: Int, val opening: String, val art: String, val aspect: Float)

    val entries = listOf(
        Entry(8, "The Red team has 4 minions", LookArt.PUSH_DIAGRAM_1, 1461f / 1126f),
        Entry(8, "All minions remaining in the old", LookArt.PUSH_DIAGRAM_2, 1437f / 1126f),
    )

    fun of(excerpt: RulesExcerpt): Entry? =
        entries.firstOrNull { it.page == excerpt.page && excerpt.text.startsWith(it.opening) }
}
