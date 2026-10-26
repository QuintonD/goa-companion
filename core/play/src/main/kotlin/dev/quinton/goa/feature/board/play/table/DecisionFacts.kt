package dev.quinton.goa.feature.board.play.table

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.UnavailableAction
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.PrimaryAction
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardNote
import dev.quinton.goa.feature.board.ui.cards.CardVerdict
import dev.quinton.goa.feature.board.ui.cards.FocusCard

// The decision panel's words and choices (Android DecisionPanel, DecisionCards; the web app's table), shared.

/** How the panel lays out a question: the cards it offers, or the asking card beside the answers. */
enum class PanelLayout { CARDS, CARD_BESIDE, PLAIN }

fun panelLayout(kind: DecisionKind, hasSource: Boolean): PanelLayout = when (kind) {
    DecisionKind.PLAY_CARD, DecisionKind.DEFEND, DecisionKind.CHOOSE_CARD,
    DecisionKind.UPGRADE_CARD, DecisionKind.KEEP_UPGRADE,
    -> PanelLayout.CARDS

    else -> if (hasSource) PanelLayout.CARD_BESIDE else PanelLayout.PLAIN
}

/** One unavailable action's line: the engine's reason and where the rule is ("… (p.10, FAQ)."). */
fun unavailableLine(u: UnavailableAction): String = u.source?.let { "${u.text} ($it)." } ?: "${u.text}."

/**
 * The reasons a CHOOSE_ACTION question gives for the actions its card prints that aren't offered
 * (`DecisionContext.unavailable`, ADR-041 §5), so a lone Hold never looks like a broken app; none
 * for any other question.
 */
fun unavailableActions(decision: Decision): List<UnavailableAction> =
    if (decision.kind == DecisionKind.CHOOSE_ACTION) decision.context.unavailable else emptyList()

/** "Radius 2" / "Range 3" when the question speaks of the asking card's radius or range. */
fun sourceReach(prompt: String, card: Card): String? {
    val text = prompt.lowercase()
    return listOfNotNull(
        card.values.radius?.takeIf { "radius" in text }?.let { "Radius $it" },
        card.values.range?.takeIf { "range" in text }?.let { "Range $it" },
    ).joinToString(" · ").ifEmpty { null }
}

/** A short name for each kind of question, for the header. */
fun kindTitle(kind: DecisionKind): String = when (kind) {
    DecisionKind.PLAY_CARD -> "Play a card"
    DecisionKind.ACT_FIRST -> "Initiative tie"
    DecisionKind.RESPAWN -> "Respawn"
    DecisionKind.CHOOSE_ACTION -> "Action"
    DecisionKind.CHOOSE_SPACE -> "Space"
    DecisionKind.CHOOSE_UNIT -> "Unit"
    DecisionKind.CHOOSE_TOKEN -> "Token"
    DecisionKind.CHOOSE_CARD -> "Card"
    DecisionKind.DEFEND -> "Defend"
    DecisionKind.CHOOSE_OPTION -> "Choose one"
    DecisionKind.CONFIRM -> "Optional"
    DecisionKind.CHOOSE_NUMBER -> "Number"
    DecisionKind.REMOVE_MINION -> "Minion Battle"
    DecisionKind.MINION_RETURN -> "Minion back"
    DecisionKind.UPGRADE_CARD -> "Level up"
    DecisionKind.KEEP_UPGRADE -> "Level up"
}

/** An action chip's words: the action and the card's value for it ("Attack 6", "Movement 4", "Hold"). */
fun actionLabel(action: ActionType, card: Card?): String = when (action) {
    ActionType.PRIMARY -> {
        val value = when (card?.primary) {
            PrimaryAction.ATTACK -> card.values.attack
            PrimaryAction.MOVEMENT -> card.values.movement
            else -> null
        }
        listOfNotNull(PlayText.primary(card?.primary), value?.toString()).joinToString(" ")
    }

    else -> PlayText.action(action, card)
}

/** The rule an action follows that its card doesn't print, in a few words with the page; else null. */
fun actionRule(action: ActionType): String? = when (action) {
    ActionType.FAST_TRAVEL -> "To your Zone or an adjacent one, no enemy hero or minion in either (p.11)"
    ActionType.HOLD -> "Do nothing (p.12)"
    ActionType.CLEAR -> "Instead of attacking: remove adjacent tokens (p.13)"
    else -> null
}

/** The cards of the next Tier in [card]'s colour (p.15): what upgrading it offers. */
fun upgradesOf(table: TableUi, card: Card): List<Card> {
    val tier = card.tier ?: return emptyList()
    return table.cards.values
        .filter { it.heroId == card.heroId && it.color == card.color && it.tier == tier + 1 }
        .sortedBy { it.id }
}

/** Defense succeeds when its total is at least the Attack (p.14). */
fun holds(defense: Int?, attack: Int?): Boolean? = if (defense == null || attack == null) null else defense >= attack

/**
 * Whether a defense card stops the attack: a Block by its condition when the engine settles it
 * ([blocks], p.14 "regardless of the attack value"), else its Defense total against the Attack;
 * null when only resolving it tells.
 */
fun defenseVerdict(total: Int?, blocks: Boolean?, attack: Int?): Boolean? = blocks ?: holds(total, attack)

/** A card a question shows: the answer it is ([choice] null = shown but not an option). */
data class OfferedCard(val card: Card, val choice: Choice.Card?)

/**
 * The cards a card question shows, in order: its options; when they are all in the deciding hero's
 * hand, the whole hand, the cards it doesn't offer shown but marked (so "discard an attack card"
 * shows the rest of the hand too).
 */
fun offeredCards(table: TableUi): List<OfferedCard> {
    val decision = table.decision ?: return emptyList()
    val options = table.cardOptions
    val byId = options.associateBy { it.cardId }
    val hand = table.decider?.hand
    val wholeHand =
        hand != null && decision.kind != DecisionKind.KEEP_UPGRADE && decision.kind != DecisionKind.DEFEND &&
            options.isNotEmpty() && options.all { it.cardId in hand } && hand.size > options.size
    val ids = if (wholeHand) hand.orEmpty() else options.map { it.cardId }
    return ids.mapNotNull { id -> table.card(id)?.let { OfferedCard(it, byId[id]) } }
}

/** What confirming [card] does, as the button and the hint say it. */
fun cardAction(decision: Decision, card: Card): String = when (decision.kind) {
    DecisionKind.PLAY_CARD -> "Play ${card.name} facedown"
    DecisionKind.DEFEND -> "Defend with ${card.name}"
    DecisionKind.UPGRADE_CARD -> "Upgrade ${card.name}"
    DecisionKind.KEEP_UPGRADE -> "Keep ${card.name}"
    else -> "${cardVerb(decision.prompt)} ${card.name}"
}

/** The question's own verb when it is one ("You may discard an attack card" → Discard), else Choose. */
fun cardVerb(prompt: String): String {
    // Gydion's cards: "Choose and cast one spell in the spellbook" (the spell is cast once chosen).
    if (prompt.startsWith("Choose and cast")) return "Cast"
    val first = prompt.removePrefix("You may ").substringBefore(' ').lowercase()
    return CARD_VERBS[first] ?: "Choose"
}

val CARD_VERBS = mapOf(
    "discard" to "Discard",
    "retrieve" to "Retrieve",
    "swap" to "Swap",
    "take" to "Take",
    // Find Familiar: "You may remove up to three other spell cards from the spellbook faceup."
    "remove" to "Remove",
)

/** Why a card the question doesn't offer can't be chosen. */
fun notOffered(kind: DecisionKind): String = when (kind) {
    DecisionKind.PLAY_CARD -> "This card can't be played now."
    DecisionKind.UPGRADE_CARD -> "This card can't be upgraded now."
    else -> "This question doesn't offer this card."
}

/** The viewer's title for a question: "Wasp · Card". */
fun focusTitle(table: TableUi, decision: Decision): String =
    listOfNotNull(table.decider?.let { table.hero(it.heroId).name }, kindTitle(decision.kind)).joinToString(" · ")

/** A card of a play / discard / upgrade / keep question in the viewer, with what choosing it means. */
fun focusCard(table: TableUi, offered: OfferedCard): FocusCard {
    val decision = table.decision
    val card = offered.card
    val notes = mutableListOf<CardNote>()
    var related = emptyList<Card>()
    var relatedLabel: String? = null
    when (decision?.kind) {
        DecisionKind.KEEP_UPGRADE -> {
            // p.15: the card kept goes to hand, the other is tucked as an item.
            notes += CardNote("${card.name} goes to your hand.")
            table.cardOptions.firstOrNull { it.cardId != card.id }?.let { table.card(it.cardId) }?.let { other ->
                other.item?.let {
                    notes += CardNote("${other.name} becomes an item: +1 ${PlayText.stat(it)} (p.16)", it)
                }
            }
        }

        DecisionKind.UPGRADE_CARD -> {
            val next = upgradesOf(table, card)
            if (next.size == 2 && offered.choice != null) {
                related = next
                relatedLabel = "${card.name} is replaced by one of these: you keep one, the other becomes an item " +
                    "(p.15, p.16)."
            }
        }

        DecisionKind.PLAY_CARD -> if (table.decider?.at == null && offered.choice != null) {
            notes += CardNote("You are off the board: when this card resolves you may respawn, then act with it (p.7).")
        }

        else -> Unit
    }
    return FocusCard(
        card = card,
        enabled = offered.choice != null,
        reason = if (offered.choice == null) decision?.kind?.let(::notOffered) else null,
        notes = notes,
        related = related,
        relatedLabel = relatedLabel,
    )
}

/** A defense card's line: its Defense (or Block) and the verdict. */
data class DefenseLine(val detail: String, val verdict: CardVerdict, val total: Int? = null)

fun defenseLine(total: Int?, blocks: Boolean?, attack: Int?): DefenseLine {
    val verdict = defenseVerdict(total, blocks, attack)
    val label = when (verdict) {
        true -> if (blocks == true) "Blocks" else "Holds"
        false -> "Falls"
        null -> "Decided by its text"
    }
    val detail = when (blocks) {
        true -> "Block: stops this attack whatever its value"
        false -> "Block: not against this attack"
        null -> total?.let { "Defense $it" } ?: "Defense decided while it resolves"
    }
    val against = if (blocks == null && total != null && attack != null) "$detail against Attack $attack" else detail
    return DefenseLine(detail, CardVerdict(verdict, label, against), total.takeIf { blocks == null })
}
