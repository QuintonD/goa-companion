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
import dev.quinton.goa.feature.board.ui.cards.cardFocusOrigin

/*
 * Level up, the first question (p.15, ADR-045): which card to upgrade, with the two cards each one
 * would become shown beside it before the colour is chosen. One row per card: the hand card (the
 * answer), an arrow, then the next Tier's cards from the engine's preview (`DecisionContext.upgrades`,
 * the same selection the Keep question then offers) with the item each one gives if it is the one
 * not kept (p.16). Only the hand card answers; the others open in the viewer to read.
 */

/** One card the level up may upgrade and the cards of the next Tier it would become. */
internal data class UpgradeRow(val card: Card, val choice: Choice.Card, val upgrades: List<Card>)

/** The rows of an UPGRADE_CARD question, in the engine's option order; empty for any other question. */
internal fun upgradeRows(table: TableUi): List<UpgradeRow> {
    val decision = table.decision?.takeIf { it.kind == DecisionKind.UPGRADE_CARD } ?: return emptyList()
    return table.cardOptions.mapNotNull { choice ->
        val card = table.card(choice.cardId) ?: return@mapNotNull null
        UpgradeRow(card, choice, decision.context.upgrades[choice.cardId].orEmpty().mapNotNull(table::card))
    }
}

/**
 * The viewer's cards for the level up, row by row: each hand card (the answer, "tap it again to
 * upgrade it") followed by its upgrades, captioned "Upgrade of Liquid Leap", which can't be confirmed
 * and point back to it. [answers] is, per card, the choice confirming it gives (null for an upgrade).
 */
internal data class UpgradeFocus(val cards: List<FocusCard>, val answers: List<Choice.Card?>)

internal fun upgradeFocus(rows: List<UpgradeRow>): UpgradeFocus {
    val cards = mutableListOf<FocusCard>()
    val answers = mutableListOf<Choice.Card?>()
    for (row in rows) {
        // The upgrades follow it in the viewer, large: the hand card's sheet only says so.
        cards += FocusCard(card = row.card, notes = listOf(CardNote(upgradeLine(row))))
        answers += row.choice
        for (next in row.upgrades) {
            cards += FocusCard(
                card = next,
                enabled = false,
                caption = "Upgrade of ${row.card.name}",
                reason = upgradeReason(row, next),
                answeredBy = row.card.id,
            )
            answers += null
        }
    }
    return UpgradeFocus(cards, answers)
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

/** What TalkBack hears on a row's hand card: the card, then what upgrading it gives. */
internal fun spokenUpgradeRow(row: UpgradeRow): String = listOf(
    spokenCard(row.card),
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
    val open: (Int) -> Unit = { i ->
        focus.open(
            shown.cards,
            i,
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
            var index = 0
            rows.forEach { row ->
                val at = index
                UpgradeRowView(row, width, focus, onOpen = { open(at) }, onOpenUpgrade = { k -> open(at + 1 + k) })
                index += 1 + row.upgrades.size
            }
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
private fun UpgradeRowView(
    row: UpgradeRow,
    width: Dp,
    focus: CardFocusState,
    onOpen: () -> Unit,
    onOpenUpgrade: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // The answer: the hand card, framed in its colour.
        Surface(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, cardColor(row.card.color)),
            modifier = Modifier
                .cardFocusOrigin(focus, row.card.id)
                .clearAndSetSemantics {
                    contentDescription = spokenUpgradeRow(row)
                    role = Role.Button
                    onClick(label = "Look at the card") {
                        onOpen()
                        true
                    }
                },
        ) { TableCard(row.card, width = width) }
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
        row.upgrades.forEachIndexed { k, next -> UpgradeThumb(row, next, width, focus) { onOpenUpgrade(k) } }
    }
}

/** A card an upgrade would bring: the scan and, under it, the item it gives if it isn't kept (p.16). */
@Composable
private fun UpgradeThumb(row: UpgradeRow, card: Card, width: Dp, focus: CardFocusState, onOpen: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Upgrade of ${row.card.name}: ${spokenCard(card)}"
            stateDescription = card.item?.let { "As an item: +1 ${PlayText.stat(it)}" }.orEmpty()
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
        card.item?.let { ItemIcon(it, 14.dp) }
    }
}

private val ARROW = 20.dp
private val MIN_UPGRADE_CARD = 60.dp
private val MAX_UPGRADE_CARD = 100.dp
