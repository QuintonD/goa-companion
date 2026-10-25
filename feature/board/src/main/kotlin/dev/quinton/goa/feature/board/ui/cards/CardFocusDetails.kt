package dev.quinton.goa.feature.board.ui.cards

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Motion
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.feature.board.board.cardKindLabel
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.table.ItemIcon
import dev.quinton.goa.feature.board.play.table.ItemLine
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.ValueChip

/**
 * Under the focused card, only what the card does not print (ADR-041/042: the scan is the content):
 * the question, the verdict, why it can't be chosen, what choosing it means, its item icon, the
 * Handicap basis; the card's words as text behind "Read the card as text" (collapsed); then the hint
 * and the Confirm button (with a selection) or how to move on and close (view only). The sheet is as
 * tall as its words (up to the room left), so the card keeps the screen.
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
            Box(Modifier.weight(1f, fill = false)) {
                Crossfade(index, animationSpec = tween(Motion.SHORT_MS), label = "card details") { shown ->
                    request.cards.getOrNull(shown)?.let {
                        DetailsBody(it, request.selection?.context, landscape, request.asText) {
                            request.asText = !request.asText
                        }
                    }
                }
            }
            val selection = request.selection
            if (selection != null) {
                ConfirmBar(item, selection.action(item.card), request.canConfirm, onConfirm, landscape)
            } else {
                ViewHint(request.cards.size > 1)
            }
        }
    }
}

/**
 * Scrolls; a fade at the bottom says there is more. In landscape (a phone's height is what is short)
 * the tier sits beside the name and the padding is tighter.
 */
@Composable
private fun DetailsBody(
    item: FocusCard,
    context: String?,
    landscape: Boolean,
    asText: Boolean,
    onToggleText: () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = Spacing.lg, vertical = if (landscape) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(if (landscape) Spacing.xs else Spacing.sm),
        ) {
            NotOnTheCard(item, context)
            ReadAsText(asText, onToggleText)
            if (asText) CardAsText(item.card, landscape)
        }
        if (scroll.canScrollForward) {
            val surface = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(MORE_FADE)
                    .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface))),
            )
        }
    }
}

/**
 * What the card doesn't print: the question, where it sits (the turn track's "Turn 2 · Active"), the
 * verdict, why it can't be chosen, what choosing it means, the next Tier's cards, what it gives as an
 * item (the icon at its foot, p.16) and, for a Handicap card, the basic card it stands in for.
 */
@Composable
private fun NotOnTheCard(item: FocusCard, context: String?) {
    val card = item.card
    context?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    item.caption?.let {
        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (item.related.isNotEmpty()) Related(item)
    handicapBasis(card)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    card.item?.let { stat -> ItemLine(stat, "As an item: +1 ${PlayText.stat(stat)} (p.16)") }
}

/**
 * A Handicap card's basis, which the card itself doesn't print: derived from its frame colour
 * (rulebook p.20: "weaker versions of the regular basic cards (Usually Gold)"), as the catalog says it.
 */
internal fun handicapBasis(card: Card): String? {
    if (!card.id.endsWith(HANDICAP_SUFFIX)) return null
    val colour = when (card.color) {
        CardColor.GOLD -> "Gold"
        CardColor.SILVER -> "Silver"
        else -> return null
    }
    return "Handicap: a weaker version of the $colour card (frame colour, p.20)."
}

/** "Read the card as text", collapsed: the scan says it all; this is the accessible alternative. */
@Composable
private fun ReadAsText(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Article,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "Read the card as text",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(scaledIconSize(20.dp, 1.3f)),
        )
    }
}

/** The card's printed words as text: name, kind, values, the text verbatim. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardAsText(card: Card, landscape: Boolean) {
    // Opened on a short sheet (a phone held sideways), the words would start below the fold.
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(card.id) { requester.bringIntoView() }
    Column(Modifier.bringIntoViewRequester(requester), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(card.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(
            cardKindLabel(card),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        if (card.text.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
            ) {
                CardTextView(
                    card.text,
                    style = if (landscape) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    iconTint = cardColor(card.color),
                    modifier = Modifier.fillMaxWidth().padding(if (landscape) Spacing.sm else Spacing.md),
                )
            }
        }
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
private fun ConfirmBar(
    item: FocusCard,
    action: String,
    canConfirm: Boolean,
    onConfirm: () -> Unit,
    landscape: Boolean,
) {
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
        // Large text or landscape: the card and its words need the room; × still says how to close.
        if (!landscape && LocalDensity.current.fontScale <= LARGE_FONT_SCALE) {
            Text(
                "Swipe up or go back to choose nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            if (many) "Swipe sideways for the other cards; swipe up or go back to close." else CLOSE_HINT,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val RELATED_CARD = 72.dp
private val MORE_FADE = 24.dp
private const val CLOSE_HINT = "Swipe up or go back to close."

/** A hero's Handicap card is `<hero>-handicap` in the catalog (the hero JSON's `handicap`). */
private const val HANDICAP_SUFFIX = "-handicap"
