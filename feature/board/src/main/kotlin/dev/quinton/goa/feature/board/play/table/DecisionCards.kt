package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Decision
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.CardNote
import dev.quinton.goa.feature.board.ui.cards.CardSelection
import dev.quinton.goa.feature.board.ui.cards.CardVerdict
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardAspect
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin
import dev.quinton.goa.feature.board.ui.cards.isSpell

/*
 * Card questions (play, defend, discard, retrieve, upgrade, keep) answered with the card focus
 * viewer (docs/DESIGN.md, "Card focus"): the first tap on a card zooms it, a second tap on it (or
 * its Confirm button) answers; back or an upward swipe returns with nothing chosen.
 */

/** A card a question shows: the answer it is ([choice] null = shown but not an option). */
internal data class OfferedCard(val card: Card, val choice: Choice.Card?)

/**
 * The cards a card question shows, in order: its options; when they are all in the deciding hero's
 * hand, the whole hand, the cards it doesn't offer shown but marked (so "discard an attack card"
 * shows the rest of the hand too).
 */
internal fun offeredCards(table: TableUi): List<OfferedCard> {
    val decision = table.decision ?: return emptyList()
    val options = table.cardOptions
    val byId = options.associateBy { it.cardId }
    val hand = table.decider?.hand
    val wholeHand =
        hand != null && decision.kind != DecisionKind.KEEP_UPGRADE && decision.kind != DecisionKind.DEFEND &&
            options.isNotEmpty() && options.all { it.cardId in hand } && hand.size > options.size
    val ids = if (wholeHand) hand.orEmpty() else options.map { it.cardId }
    // Gydion's cast question: the card's other spells, shown but marked with the engine's reason.
    val named = decision.context.unavailableCards.map { it.cardId }.filter { it !in ids }
    return (ids + named).mapNotNull { id -> table.card(id)?.let { OfferedCard(it, byId[id]) } }
}

/** Why the question leaves out a card the asking card names (DecisionContext.unavailableCards), in words. */
internal fun unavailableCardLine(table: TableUi, cardId: String): String? =
    table.decision?.context?.unavailableCards?.firstOrNull { it.cardId == cardId }?.let { u ->
        u.source?.let { "${u.text} ($it)." } ?: "${u.text}."
    }

/** What confirming [card] does, as the button and the hint say it. */
internal fun cardAction(decision: Decision, card: Card): String = when (decision.kind) {
    DecisionKind.PLAY_CARD -> "Play ${card.name} facedown"
    DecisionKind.DEFEND -> "Defend with ${card.name}"
    DecisionKind.UPGRADE_CARD -> "Upgrade ${card.name}"
    DecisionKind.KEEP_UPGRADE -> "Keep ${card.name}"
    else -> "${cardVerb(decision.prompt)} ${card.name}"
}

/** The question's own verb when it is one ("You may discard an attack card" → Discard), else Choose. */
internal fun cardVerb(prompt: String): String {
    // Gydion's cards: "Choose and cast one spell in the spellbook" (the spell is cast once chosen).
    if (prompt.startsWith("Choose and cast")) return "Cast"
    val first = prompt.removePrefix("You may ").substringBefore(' ').lowercase()
    return VERBS[first] ?: "Choose"
}

private val VERBS = mapOf(
    "discard" to "Discard",
    "retrieve" to "Retrieve",
    "swap" to "Swap",
    "take" to "Take",
    // Find Familiar: "You may remove up to three other spell cards from the spellbook faceup."
    "remove" to "Remove",
)

/** Why a card the question doesn't offer can't be chosen. */
private fun notOffered(kind: DecisionKind): String = when (kind) {
    DecisionKind.PLAY_CARD -> "This card can't be played now."
    DecisionKind.UPGRADE_CARD -> "This card can't be upgraded now."
    else -> "This question doesn't offer this card."
}

/** The viewer's title for a question: "Wasp · Card". */
internal fun focusTitle(table: TableUi, decision: Decision): String =
    listOfNotNull(table.decider?.let { table.hero(it.heroId).name }, kindTitle(decision.kind)).joinToString(" · ")

/** Opens [offered] in the viewer at [index], confirming answers [onAnswer]. */
private fun openOffered(
    focus: CardFocusState,
    table: TableUi,
    offered: List<OfferedCard>,
    index: Int,
    onAnswer: (Choice) -> Unit,
    context: String? = null,
    cards: List<FocusCard> = offered.map { focusCard(table, it) },
) {
    val decision = table.decision ?: return
    focus.open(
        cards,
        index,
        selection = CardSelection(
            action = { cardAction(decision, it) },
            context = context ?: decision.prompt,
            onConfirm = { i -> offered.getOrNull(i)?.choice?.let(onAnswer) },
        ),
        title = focusTitle(table, decision),
        owner = decision.id,
    )
}

/** A card of a play / discard / upgrade / keep question in the viewer, with what choosing it means. */
private fun focusCard(table: TableUi, offered: OfferedCard): FocusCard {
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
        reason = if (offered.choice == null) {
            unavailableCardLine(table, card.id) ?: decision?.kind?.let(::notOffered)
        } else {
            null
        },
        notes = notes,
        related = related,
        relatedLabel = relatedLabel,
    )
}

/** Closes the viewer when its question is gone; Confirm waits while the game is busy. */
@Composable
private fun FocusLifecycle(focus: CardFocusState, decision: Decision, busy: Boolean) {
    DisposableEffect(focus, decision.id) { onDispose { focus.closeFor(decision.id) } }
    LaunchedEffect(focus, decision.id, busy) { focus.setCanConfirm(decision.id, !busy) }
}

// ---- Play a card, discard, retrieve, upgrade, keep ------------------------------------------

/**
 * Turn step 1 (p.7): the hand as card images. Tap one to see it large, tap it again to play it
 * facedown. With no cards, Pass.
 */
@Composable
internal fun HandPicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    table.decider?.takeIf { it.at == null }?.let { hero ->
        Text(
            "${table.hero(hero.heroId).name} is off the board. When this card resolves you may respawn in " +
                "your Throne Zone, then act with it (p.7).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val offered = offeredCards(table)
    OfferedRow(table, offered, focus) { i -> openOffered(focus, table, offered, i, onAnswer) }
    if (offered.any { it.choice != null }) TapHint("Tap a card to see it large; tap it again to play it facedown.")
    if (table.buttonOptions.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            table.buttonOptions.forEach { choice ->
                OutlinedButton(onClick = {
                    onAnswer(choice)
                }, enabled = !busy, modifier = Modifier.heightIn(min = 52.dp)) {
                    Text(table.label(choice))
                }
            }
        }
    }
}

/** Card choices other than playing: discard, retrieve, which card to upgrade, which to keep. */
@Composable
internal fun CardPicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val offered = offeredCards(table)
    OfferedRow(table, offered, focus, keep = decision.kind == DecisionKind.KEEP_UPGRADE) { i ->
        openOffered(focus, table, offered, i, onAnswer)
    }
    if (offered.any { it.choice != null }) {
        val verb = when (decision.kind) {
            DecisionKind.UPGRADE_CARD -> "upgrade it"
            DecisionKind.KEEP_UPGRADE -> "keep it"
            else -> "${cardVerb(decision.prompt).lowercase()} it"
        }
        TapHint("Tap a card to see it large; tap it again to $verb.")
    }
    // The asking card's spells that can't be cast now, one line each with the engine's reason (ADR-040).
    decision.context.unavailableCards.forEach { u ->
        val name = table.card(u.cardId)?.name ?: return@forEach
        Text(
            "$name: ${unavailableCardLine(table, u.cardId)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (decision.kind == DecisionKind.KEEP_UPGRADE) {
        Text(
            "One goes to your hand; the other is tucked under your hero as an item (p.15, p.16).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TapHint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.TouchApp,
            null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The cards in a row: the whole hand in view (five cards after a level up) shrinking to fit, down to
 * a size still easy to tap; only beyond that does the row scroll. Few cards are shown larger (two
 * cards to keep side by side, up to 140 dp), so the card itself is what is read (ADR-041). Each one
 * is a TalkBack node that opens the viewer; one the question doesn't offer is dimmed and marked.
 */
@Composable
private fun OfferedRow(
    table: TableUi,
    offered: List<OfferedCard>,
    focus: CardFocusState,
    keep: Boolean = false,
    onOpen: (Int) -> Unit,
) {
    val reason = table.decision?.kind?.let(::notOffered)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = offered.size.coerceAtLeast(1)
        // A landscape spell card (Gydion) is as tall as the others and about twice as wide (CardShape.kt).
        val units = offered.sumOf { cardAspect(it.card) / CARD_ASPECT.toDouble() }.toFloat().coerceAtLeast(1f)
        val min = if (offered.any { it.card.isSpell }) MIN_SPELL_CARD else MIN_HAND_CARD
        val width = ((maxWidth - Spacing.sm * (n - 1)) / units).coerceIn(min, largestCard(n))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            offered.forEachIndexed { i, item ->
                OfferedThumb(item, width, keep, unavailableCardLine(table, item.card.id) ?: reason, focus) { onOpen(i) }
            }
        }
    }
}

@Composable
private fun OfferedThumb(
    item: OfferedCard,
    width: Dp,
    keep: Boolean,
    reason: String?,
    focus: CardFocusState,
    onOpen: () -> Unit,
) {
    val card = item.card
    val offered = item.choice != null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = spokenCard(card)
            stateDescription = listOfNotNull(
                if (keep) card.item?.let { "As an item: +1 ${PlayText.stat(it)}" } else null,
                if (!offered) reason else null,
            ).joinToString(", ")
            role = Role.Button
            onClick(label = "Look at the card") {
                onOpen()
                true
            }
        },
    ) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier.cardFocusOrigin(focus, card.id).alpha(if (offered) 1f else DIMMED),
        ) {
            TableCard(card, width = width)
        }
        if (keep) card.item?.let { ItemIcon(it, 14.dp) }
        if (!offered) {
            Icon(
                Icons.Outlined.Block,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(scaledIconSize(14.dp, 1.3f)),
            )
        }
    }
}

// ---- Defense (p.14) ---------------------------------------------------------------------

/**
 * Attacked: the attacker's card with the Attack total, then the hand's cards themselves (their
 * Defense is printed on them), each with the verdict under it: "Holds" or "Falls" in words and an
 * icon (never colour alone) and the Defense total when items or minions change it; "Don't defend".
 * Tap a card to see it large with its verdict, tap it again to defend with it; "Don't defend" is
 * chosen, then confirmed.
 */
@Composable
internal fun DefensePanel(
    table: TableUi,
    onSelect: (Choice) -> Unit,
    onAnswer: (Choice) -> Unit,
    focus: CardFocusState,
    busy: Boolean,
) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val context = decision.context
    val attack = context.attackTotal
    val attacker = context.attackerId?.let(table::pieceName)
    val heading = listOfNotNull(
        attacker?.let { "$it attacks" },
        context.rangedAttack?.let { if (it) "ranged" else "melee" },
    ).joinToString(" · ").ifEmpty { "Attacked" }
    AttackHeader(heading, attack, context.minionModifier, table.sourceCard, decision.prompt, focus)
    val offered = table.cardOptions.mapNotNull { choice -> table.card(choice.cardId)?.let { OfferedCard(it, choice) } }
    val rows = offered.map { item ->
        val id = item.card.id
        defenseLine(context.defensePreview[id], context.blockPreview[id], attack)
    }
    if (attack != null && rows.isNotEmpty() && rows.all { it.verdict.holds == false }) {
        // p.14: a failed defense still discards the card, and the hero is defeated all the same.
        Text(
            "None of your cards stops Attack $attack. Not defending keeps them in your hand (p.14).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (offered.isNotEmpty()) TapHint("Tap a card to see it large; tap it again to defend with it.")
    val focusCards = offered.mapIndexed { i, item -> FocusCard(item.card, verdict = rows[i].verdict) }
    val summary = listOfNotNull(heading, attack?.let { "Attack $it" }).joinToString(" · ")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val n = offered.size.coerceAtLeast(1)
        val width = ((maxWidth - Spacing.sm * (n - 1)) / n).coerceIn(MIN_HAND_CARD, HAND_CARD)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            offered.forEachIndexed { i, item ->
                DefenseCard(item.card, rows[i], width, focus) {
                    openOffered(focus, table, offered, i, onAnswer, context = summary, cards = focusCards)
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        table.buttonOptions.forEach { choice ->
            DeclineRow(table.label(choice), choice == table.selected) { onSelect(choice) }
        }
    }
    val declined = table.selected?.takeIf { it !is Choice.Card && it in table.buttonOptions }
    if (declined != null) {
        // The button is under every row: bring it into view once "Don't defend" is chosen.
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(declined) { requester.bringIntoView() }
        Button(
            onClick = { onAnswer(declined) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).bringIntoViewRequester(requester),
        ) {
            Text(table.label(declined))
        }
    }
}

/**
 * The attack: the attacker's card (tap to read it), who attacks and how, the Attack total, and the
 * minion defense modifier counted. It carries the question for TalkBack (a polite live region).
 */
@Composable
private fun AttackHeader(
    heading: String,
    attack: Int?,
    minionModifier: Int?,
    card: Card?,
    prompt: String,
    focus: CardFocusState,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (card != null) {
                Surface(
                    onClick = { focus.show(listOf(card), title = "Attacking card") },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .cardFocusOrigin(focus, card.id)
                        .clearAndSetSemantics {
                            contentDescription = "Attacking card: ${spokenCard(card)}"
                            role = Role.Button
                            onClick(label = "Read the card") {
                                focus.show(listOf(card), title = "Attacking card")
                                true
                            }
                        },
                ) { TableCard(card, width = ATTACK_CARD) }
                Spacer(Modifier.width(Spacing.md))
            }
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = prompt
                },
            ) {
                Text(
                    heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    attack?.let { "Attack $it" } ?: "Attack decided by the card",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                )
                minionModifier?.takeIf { it != 0 }?.let { m ->
                    Text(
                        "Minion defense modifier ${if (m > 0) "+$m" else "$m"} is counted below (p.14).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    "Discard a card to defend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** A defense card's line: its Defense (or Block) and the verdict. */
internal data class DefenseLine(val detail: String, val verdict: CardVerdict, val total: Int? = null)

internal fun defenseLine(total: Int?, blocks: Boolean?, attack: Int?): DefenseLine {
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

/** One hand card to defend with: the official card, the verdict under it, the total when it differs. */
@Composable
private fun DefenseCard(card: Card, line: DefenseLine, width: Dp, focus: CardFocusState, onOpen: () -> Unit) {
    val printed = card.values.defense
    val changed = line.total != null && line.total != printed
    val spoken = listOf(card.name, line.detail, line.verdict.label).joinToString(", ")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(width)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Look at the card") {
                    onOpen()
                    true
                }
            },
    ) {
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            modifier = Modifier.cardFocusOrigin(focus, card.id),
        ) { TableCard(card, width = width) }
        Verdict(line.verdict.holds, line.verdict.label)
        if (changed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    GameIcons.defense.vector,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(scaledIconSize(12.dp, 1.3f)),
                )
                Text(
                    " ${line.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "Holds" with a tick or "Falls" with a warning sign: words and shape, not colour alone. */
@Composable
private fun Verdict(verdict: Boolean?, text: String) {
    val color = when (verdict) {
        true -> MaterialTheme.colorScheme.secondary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (verdict != null) {
            Icon(
                if (verdict) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                null,
                tint = color,
                modifier = Modifier.size(scaledIconSize(16.dp, 1.3f)),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text,
            style = if (verdict == null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeclineRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else GoaTheme.colors.engravedBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected },
    ) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                "Keep your cards; the attack succeeds (p.14).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The largest a card in a row of [n] grows: few cards are shown large enough to read. */
private fun largestCard(n: Int): Dp = when {
    n <= 2 -> 140.dp
    n == 3 -> 110.dp
    else -> HAND_CARD
}

private val HAND_CARD = 84.dp
private val MIN_HAND_CARD = 60.dp
private val ATTACK_CARD = 56.dp

/** A row with spells: a landscape card this tall (≈ 90 × 62 dp) is still easy to tap and read. */
private val MIN_SPELL_CARD = 44.dp
private const val DIMMED = 0.45f
