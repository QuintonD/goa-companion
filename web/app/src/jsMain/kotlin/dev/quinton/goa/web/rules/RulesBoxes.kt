package dev.quinton.goa.web.rules

import dev.quinton.goa.core.model.rules.RulesExcerpt

/** The coloured boxes the rulebook prints some passages in; goa-look-catalog.css has their colours (≥ 4.5:1). */
internal enum class RulesBox(val cssClass: String) {
    BLUE("box-blue"),
    GREEN("box-green"),
    ORANGE("box-orange"),
}

/**
 * Which passages of `rules-reference.json` the rulebook prints in a box (feature:rules `RulesBoxes`,
 * the same table): read off the pinned PDF (the colour behind each passage's text on its page), by page
 * and opening words. The data doesn't record it; `RulesWebTest` fails if a passage is no longer there.
 */
internal object RulesBoxes {
    class Entry(val page: Int, val opening: String, val box: RulesBox)

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
