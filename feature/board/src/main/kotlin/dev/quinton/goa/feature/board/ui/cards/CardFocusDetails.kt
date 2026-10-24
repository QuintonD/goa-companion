package dev.quinton.goa.feature.board.ui.cards

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.cardColorName
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.feature.board.board.tierLabel
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.ItemIcon
import dev.quinton.goa.feature.board.play.table.ItemLine
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.ValueChip

/**
 * The focused card's words: the question's context, name, tier and colour, the verdict, why it
 * can't be chosen, what choosing it means, its printed values and text verbatim; then the hint
 * and the Confirm button (with a selection) or how to move on and close (view only).
 */
@Composable
internal fun FocusDetails(
    request: CardFocusRequest,
    index: Int,
    reveal: Float,
    onConfirm: () -> Unit,
    landscape: Boolean,
    modifier: Modifier,
) {
    val item = request.cards.getOrNull(index) ?: return
    Surface(
        modifier = modifier.graphicsLayer {
            alpha = reveal
            translationY = (1f - reveal) * 48.dp.toPx()
        },
        shape = if (landscape) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Column {
            Box(Modifier.weight(1f)) {
                Crossfade(index, animationSpec = tween(Motion.SHORT_MS), label = "card details") { shown ->
                    request.cards.getOrNull(shown)?.let { DetailsBody(it, request.selection?.context) }
                }
            }
            val selection = request.selection
            if (selection != null) {
                ConfirmBar(item, selection.action(item.card), request.canConfirm, onConfirm)
            } else {
                ViewHint(request.cards.size > 1)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsBody(item: FocusCard, context: String?) {
    val card = item.card
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        context?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column {
            Text(card.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                listOfNotNull("${tierLabel(card)} ${cardColorName(card.color)}", item.caption).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.verdict?.let { VerdictLine(it) }
        if (!item.enabled) ReasonLine(item.reason ?: "This card can't be chosen for this question.")
        item.notes.forEach { note ->
            if (note.item != null) {
                ItemLine(note.item, note.text)
            } else {
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            card.initiative?.let { ValueChip(GameIcons.initiative, "Initiative $it") }
            card.values.attack?.let { ValueChip(GameIcons.attack, "Attack $it") }
            card.values.defense?.let { ValueChip(GameIcons.defense, "Defense $it") }
            card.values.movement?.let { ValueChip(GameIcons.movement, "Movement $it") }
            card.values.range?.let { ValueChip(GameIcons.range, "Range $it") }
            card.values.radius?.let { ValueChip(GameIcons.radius, "Radius $it") }
        }
        if (item.related.isNotEmpty()) Related(item)
        if (card.text.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            ) {
                CardTextView(
                    card.text,
                    style = MaterialTheme.typography.bodyLarge,
                    iconTint = cardColor(card.color),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
        card.item?.let { stat -> ItemLine(stat, "As an item: +1 ${PlayText.stat(stat)} (p.16)") }
    }
}

/** The verdict in words with its icon, and why ("Defense 7 against Attack 6"). */
@Composable
private fun VerdictLine(verdict: CardVerdict) {
    val color = when (verdict.holds) {
        true -> MaterialTheme.colorScheme.secondary
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        if (verdict.holds != null) {
            Icon(
                if (verdict.holds) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                null,
                tint = color,
                modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
            )
            Spacer(Modifier.width(Spacing.sm))
        }
        Column {
            Text(verdict.label, style = MaterialTheme.typography.titleMedium, color = color)
            Text(verdict.detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReasonLine(reason: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(
            Icons.Outlined.Block,
            null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(reason, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The cards that come with this one (upgrading: the two of the next Tier), each with its item stat. */
@Composable
private fun Related(item: FocusCard) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        item.relatedLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            item.related.forEach { other -> RelatedCard(other) }
        }
    }
}

@Composable
private fun RelatedCard(card: Card) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(RELATED_CARD)
            .clearAndSetSemantics {
                contentDescription = card.name + (card.item?.let { ". As an item: +1 ${PlayText.stat(it)}" } ?: "")
            },
    ) {
        TableCard(card, width = RELATED_CARD)
        Text(
            card.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        card.item?.let { ItemIcon(it, 14.dp) }
    }
}

/** "Tap the card again to discard Magnetic Dagger", and the same as a button (TalkBack, switch access). */
@Composable
private fun ConfirmBar(item: FocusCard, action: String, canConfirm: Boolean, onConfirm: () -> Unit) {
    HorizontalDivider(color = GoaTheme.colors.engravedBorder)
    Column(
        Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        val can = item.enabled && canConfirm
        if (can) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.TouchApp,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "Tap the card again to ${lowerFirst(action)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Button(
            onClick = onConfirm,
            enabled = can,
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(CONFIRM_TAG),
        ) {
            Text(
                when {
                    !item.enabled -> "Not an option"
                    !canConfirm -> "Wait…"
                    else -> action
                },
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Swipe up or go back to choose nothing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** View only: how to see the others and close. */
@Composable
private fun ViewHint(many: Boolean) {
    HorizontalDivider(color = GoaTheme.colors.engravedBorder)
    Row(
        Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.SwipeUp,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            if (many) "Swipe sideways for the other cards; swipe up or go back to close." else "Swipe up or go back to close.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RELATED_CARD = 72.dp
