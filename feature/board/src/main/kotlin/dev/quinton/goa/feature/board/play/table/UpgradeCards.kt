package dev.quinton.goa.feature.board.play.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.ui.cards.CardFocusState
import dev.quinton.goa.feature.board.ui.cards.CardNote
import dev.quinton.goa.feature.board.ui.cards.CardSelection
import dev.quinton.goa.feature.board.ui.cards.FocusCard
import dev.quinton.goa.feature.board.ui.cards.cardAspect
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * Level up, the first question (p.15, ADR-045): which card to upgrade, with the two cards each one
 * would become shown beside it before the colour is chosen. One row per card: the hand card (the
 * answer), an arrow, then the next Tier's cards from the engine's preview (`DecisionContext.upgrades`,
 * the same selection the Keep question then offers) with the item each one gives if it is the one
 * not kept (p.16). Under each card, the cards it names (Gydion's spells, NamedCards.kt): Lesser
 * Evocation vs Lesser Necromancy is Burning Hands / Fireball vs Vampiric Touch / Create Undead. Only
 * the hand card answers; the others open in the viewer to read.
 */

/**
 * One card the level up may upgrade and the cards of the next Tier it would become; [named] gives,
 * by card id, the extra cards each of them names (empty for every hero but Gydion).
 */
internal data class UpgradeRow(
    val card: Card,
    val choice: Choice.Card,
    val upgrades: List<Card>,
    val named: Map<String, List<Card>> = emptyMap(),
) {
    fun namedBy(card: Card): List<Card> = named[card.id].orEmpty()
}

/** True while the question is a level up's (which card to upgrade, which to keep). */
internal fun isLevelUp(table: TableUi): Boolean =
    table.decision?.kind == DecisionKind.UPGRADE_CARD || table.decision?.kind == DecisionKind.KEEP_UPGRADE

/** The rows of an UPGRADE_CARD question, in the engine's option order; empty for any other question. */
internal fun upgradeRows(table: TableUi): List<UpgradeRow> {
    val decision = table.decision?.takeIf { it.kind == DecisionKind.UPGRADE_CARD } ?: return emptyList()
    return table.cardOptions.mapNotNull { choice ->
        val card = table.card(choice.cardId) ?: return@mapNotNull null
        val upgrades = decision.context.upgrades[choice.cardId].orEmpty().mapNotNull(table::card)
        val named = (listOf(card) + upgrades).associate { it.id to table.namedBy(it) }.filterValues { it.isNotEmpty() }
        UpgradeRow(card, choice, upgrades, named)
    }
}

/**
 * The viewer's cards for the level up, row by row: each hand card (the answer, "tap it again to
 * upgrade it") followed by its upgrades, captioned "Upgrade of Liquid Leap", which can't be confirmed
 * and point back to it; after each card, the spells it names, to read. The answers are, per card,
 * the choice confirming it gives (null for an upgrade or a spell).
 */
internal fun upgradeFocus(rows: List<UpgradeRow>): FocusPages {
    val cards = mutableListOf<FocusCard>()
    val answers = mutableListOf<Choice.Card?>()
    for (row in rows) {
        // The upgrades follow it in the viewer, large: the hand card's sheet only says so.
        val pages = mutableListOf(FocusCard(card = row.card, notes = listOf(CardNote(upgradeLine(row)))))
        val choices = mutableListOf<Choice.Card?>(row.choice)
        for (next in row.upgrades) {
            pages += FocusCard(
                card = next,
                enabled = false,
                caption = "Upgrade of ${row.card.name}",
                reason = upgradeReason(row, next),
                answeredBy = row.card.id,
            )
            choices += null
        }
        val withNamed = FocusPages(pages, choices).withNamed(
            named = row::namedBy,
            answerKey = { row.card.id },
            reason = { namer -> namedReason(row, namer.card) },
        )
        cards += withNamed.cards
        answers += withNamed.answers
    }
    return FocusPages(cards, answers)
}

/** Under a spell a row's card names, in the viewer: whose spell it is and which card to choose. */
internal fun namedReason(row: UpgradeRow, namer: Card): String = if (namer.id == row.card.id) {
    "${namer.name}, in your hand, casts this spell. Upgrading it replaces it (p.15)."
} else {
    "${namer.name} casts this spell. " + upgradeReason(row, namer).replace("this card", namer.name)
}

/** Under the hand card in the viewer: what upgrading it means; its upgrades are the next cards. */
internal fun upgradeLine(row: UpgradeRow): String = when (row.upgrades.size) {
    0 -> "No card of the next Tier is left in your deck in this colour."

    1 -> "${row.card.name} is replaced by ${row.upgrades.single().name}, the only card of the next Tier left " +
        "in your deck: it goes to your hand (p.15). Swipe to see it."

    else -> "${row.card.name} is replaced by ${row.upgrades.joinToString(" or ") { it.name }}: you keep one, " +
        "the other becomes an item (p.15, p.16). Swipe to see them."
}

/** Under an upgrade card in the viewer: it isn't the answer; which card is. */
internal fun upgradeReason(row: UpgradeRow, next: Card): String {
    val other = row.upgrades.firstOrNull { it.id != next.id }
    return if (other == null) {
        "Upgrade ${row.card.name} to take this card into your hand."
    } else {
        "Upgrade ${row.card.name} to choose between this card and ${other.name} next; the other becomes an item."
    }
}

/** What TalkBack hears on a row's hand card: the card, its spells, then what upgrading it gives. */
internal fun spokenUpgradeRow(row: UpgradeRow): String = listOfNotNull(
    spokenCard(row.card),
    spokenNamed(row.namedBy(row.card)),
    when (row.upgrades.size) {
        0 -> "no card of the next Tier left in your deck"
        1 -> "becomes ${row.upgrades.single().name}"
        else -> "becomes " + row.upgrades.joinToString(" or ") { it.name }
    },
).joinToString(", ")

/** Level up: one row per card that may be upgraded, its upgrades beside it; tap to read, again to upgrade. */
@Composable
internal fun UpgradePicker(table: TableUi, onAnswer: (Choice) -> Unit, focus: CardFocusState, busy: Boolean) {
    val decision = table.decision ?: return
    FocusLifecycle(focus, decision, busy)
    val rows = upgradeRows(table)
    val shown = upgradeFocus(rows)
    val open: (String) -> Unit = { key ->
        focus.open(
            shown.cards,
            shown.indexOf(key),
            selection = CardSelection(
                action = { cardAction(decision, it) },
                context = decision.prompt,
                onConfirm = { at -> shown.answers.getOrNull(at)?.let(onAnswer) },
            ),
            title = focusTitle(table, decision),
            owner = decision.id,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Three cards and the arrow across the panel, as large as fits up to a readable size.
        val width = ((maxWidth - ARROW - Spacing.sm * 3) / 3).coerceIn(MIN_UPGRADE_CARD, MAX_UPGRADE_CARD)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            rows.forEach { row -> UpgradeRowView(row, width, focus, open) }
        }
    }
    if (rows.isNotEmpty()) TapHint("Tap a card to see it large; tap it again to upgrade it.")
    Text(
        "The card on the left is the choice: it goes back to your deck, and you keep one of the two beside it; " +
            "the other is tucked under your hero as an item (p.15, p.16).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UpgradeRowView(row: UpgradeRow, width: Dp, focus: CardFocusState, onOpen: (String) -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // The answer: the hand card, framed in its colour, then the spells it casts.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Surface(
                onClick = { onOpen(row.card.id) },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(2.dp, cardColor(row.card.color)),
                modifier = Modifier
                    .cardFocusOrigin(focus, row.card.id)
                    .clearAndSetSemantics {
                        contentDescription = spokenUpgradeRow(row)
                        role = Role.Button
                        onClick(label = "Look at the card") {
                            onOpen(row.card.id)
                            true
                        }
                    },
            ) { TableCard(row.card, width = width) }
            NamedThumbs(FocusCard(row.card), row.namedBy(row.card), width, focus, onOpen)
        }
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = width / CARD_ASPECT / 2 - ARROW / 2)
                .size(ARROW),
        )
        if (row.upgrades.isEmpty()) {
            Text(
                upgradeLine(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(width * 2 + Spacing.sm),
            )
        }
        row.upgrades.forEach { next -> UpgradeThumb(row, next, width, focus, onOpen) }
    }
}

/**
 * A card an upgrade would bring: the scan, under it the item it gives if it isn't kept (p.16), then
 * the spells it casts.
 */
@Composable
private fun UpgradeThumb(row: UpgradeRow, card: Card, width: Dp, focus: CardFocusState, onOpen: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(width),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = listOfNotNull(
                    "Upgrade of ${row.card.name}: ${spokenCard(card)}",
                    spokenNamed(row.namedBy(card)),
                ).joinToString(", ")
                stateDescription = card.item?.let { "As an item: +1 ${PlayText.stat(it)}" }.orEmpty()
                role = Role.Button
                onClick(label = "Look at the card") {
                    onOpen(card.id)
                    true
                }
            },
        ) {
            Surface(
                onClick = { onOpen(card.id) },
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
                modifier = Modifier.cardFocusOrigin(focus, card.id),
            ) { TableCard(card, width = width) }
            card.item?.let { ItemCaption(it) }
        }
        NamedThumbs(FocusCard(card), row.namedBy(card), width, focus, onOpen)
    }
}

/**
 * The extra cards [namer] names (Gydion's spells), small and landscape as printed, [width] wide, one
 * under the other; a tap opens each in the viewer at its own page ([namedKey]).
 */
@Composable
internal fun NamedThumbs(
    namer: FocusCard,
    named: List<Card>,
    width: Dp,
    focus: CardFocusState,
    onOpen: (String) -> Unit,
) {
    if (named.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        named.forEach { card ->
            val key = namedKey(namer, card)
            Surface(
                onClick = { onOpen(key) },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
                modifier = Modifier
                    .cardFocusOrigin(focus, key)
                    .clearAndSetSemantics {
                        contentDescription = "${namedCaption(namer.card, card)}: ${spokenCard(card)}"
                        role = Role.Button
                        onClick(label = "Look at the card") {
                            onOpen(key)
                            true
                        }
                    },
            ) {
                // TableCard's width is a portrait card's: this landscape card comes out [width] wide.
                TableCard(card, width = width * CARD_ASPECT / cardAspect(card))
            }
        }
    }
}

private val ARROW = 20.dp
private val MIN_UPGRADE_CARD = 60.dp
private val MAX_UPGRADE_CARD = 100.dp
