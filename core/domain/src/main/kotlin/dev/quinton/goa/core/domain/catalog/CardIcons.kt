package dev.quinton.goa.core.domain.catalog

import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardText

/**
 * What a screen reader says for each inline icon in card text ([CardText.ICONS]), the words of the
 * phone's `GameIcons`, and the card text as it is read out. Shared so the web app reads a card the
 * way TalkBack does (ADR-043).
 */
object CardIcons {
    val LABELS: Map<String, String> = mapOf(
        "attack" to "Attack",
        "defense" to "Defense",
        "initiative" to "Initiative",
        "movement" to "Movement",
        "range" to "Range",
        "radius" to "Radius",
        "skill" to "Skill",
        "coin" to "Coin",
        "hold" to "Hold",
        "fast_travel" to "Fast Travel",
        "clear" to "Clear",
        "ranged" to "Ranged",
        "block" to "Block",
        "item" to "Item",
        "ultimate" to "Ultimate",
        "minion" to "Minion",
        "heavy_minion" to "Heavy minion",
        "spawn" to "Spawn point",
        "life" to "Life counter",
        "wave" to "Wave counter",
        "tiebreaker_blue" to "Tie Breaker, blue side",
        "tiebreaker_orange" to "Tie Breaker, orange side",
        "marker_bounty" to "Bounty marker",
        "marker_poison" to "Poison marker",
        "rune_anvil" to "Anvil rune",
        "rune_axe" to "Axe rune",
        "rune_bird" to "Bird rune",
        "rune_horn" to "Horn rune",
        "rune_anvil_marker" to "Anvil rune marker",
        "rune_axe_marker" to "Axe rune marker",
        "rune_bird_marker" to "Bird rune marker",
        "rune_horn_marker" to "Horn rune marker",
        "token_barrier" to "Barrier token",
        "token_blast" to "Blast token",
        "token_dud" to "Dud token",
        "token_familiar" to "Familiar token",
        "token_glitch" to "Glitch token",
        "token_grenade" to "Grenade token",
        "token_ice" to "Ice token",
        "token_illusion" to "Illusion token",
        "token_magma" to "Magma token",
        "token_rock" to "Rock token",
        "token_smoke_bomb" to "Smoke bomb token",
        "token_totem" to "Totem token",
        "token_tree" to "Tree token",
        "token_zombie" to "Zombie token",
    )

    /** The spoken word for icon [token]; null for a name the app has no icon for (shown as text). */
    fun label(token: String): String? = LABELS[token]

    /**
     * What is read for an inline icon: its label, or a blank when the printed text right after it
     * already names it ("{life} Life counter" reads "Life counter" once, not twice).
     */
    fun spokenLabel(label: String, next: CardText.Segment?): String {
        val following = (next as? CardText.Segment.Text)?.value?.trimStart().orEmpty()
        return if (following.startsWith(label, ignoreCase = true)) " " else label
    }

    /** [text] (card markup) as it is read: icons as their words, reminders in brackets, breaks kept. */
    fun spoken(text: String): String = buildString { appendSpoken(CardText.parse(text)) }

    private fun StringBuilder.appendSpoken(segments: List<CardText.Segment>) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is CardText.Segment.Text -> append(segment.value)

                is CardText.Segment.Icon -> {
                    val label = label(segment.name)
                    val next = segments.getOrNull(index + 1)
                    append(if (label == null) segment.name.replace('_', ' ') else spokenLabel(label, next))
                }

                is CardText.Segment.Reminder -> {
                    append("(")
                    appendSpoken(CardText.parse(segment.value))
                    append(")")
                }

                is CardText.Segment.Strong -> appendSpoken(segment.segments)

                CardText.Segment.Break -> append("\n")
            }
        }
    }
}

/** The whole card for a screen reader: its summary, then its text as read ("Tidal Blast. Red · Tier II. …"). */
fun cardDescription(card: Card, isHandicap: Boolean): String =
    card.spokenSummary(isHandicap) + card.text.takeIf { it.isNotBlank() }?.let { ". " + CardIcons.spoken(it) }.orEmpty()
