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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwipeUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
import dev.quinton.goa.feature.board.play.table.CARD_ASPECT
import dev.quinton.goa.feature.board.play.table.ItemLine
import dev.quinton.goa.feature.board.play.table.TableCard
import dev.quinton.goa.feature.board.play.table.ValueChip

/**
 * Under the focused card, only what the card does not print (ADR-041/042: the scan is the content):
 * the question, the verdict, why it can't be chosen, what choosing it means, its item icon, the
 * Handicap basis; the card's words as text behind "Read the card as text" (collapsed); then the hint
 * and the Confirm button (with a selection) or how to move on and close (view only). The sheet is as
 * tall as its words; the viewer gives the card what is left (FocusLayout), so no line is squeezed.
 * [measuring]: laid out once per card, unscrolled and still, only to learn how tall the sheet gets.
 */
@Composable
internal fun FocusDetails(
    request: CardFocusRequest,
    index: Int,
    reveal: Float,
    onConfirm: () -> Unit,
    onGoTo: (Int) -> Unit,
    landscape: Boolean,
    modifier: Modifier,
    measuring: Boolean = false,
) {
    val item = request.cards.getOrNull(index) ?: return
    Surface(
        // Measured only: not shown, so not in the semantics tree either (TalkBack, tests).
        modifier = if (measuring) {
            modifier.clearAndSetSemantics {}
        } else {
            modifier.graphicsLayer {
                alpha = reveal
                translationY = (1f - reveal) * 48.dp.toPx()
            }
        },
        shape = if (landscape) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, GoaTheme.colors.engravedBorder),
    ) {
        Column {
            Box(Modifier.weight(1f, fill = false)) {
                if (measuring) {
                    DetailsBody(request, item, landscape, asText = false, measuring = true, onGoTo = {}) {}
                } else {
                    Crossfade(index, animationSpec = tween(Motion.SHORT_MS), label = "card details") { shown ->
                        request.cards.getOrNull(shown)?.let {
                            DetailsBody(request, it, landscape, request.asText, measuring = false, onGoTo = onGoTo) {
                                request.asText = !request.asText
                            }
                        }
                    }
                }
            }
            val selection = request.selection
            val answer = answerIndex(request, item)
            if (selection != null && answer != null) {
                GoToAnswerBar(item, request.cards[answer].card, { onGoTo(answer) }, landscape)
            } else if (selection != null) {
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
    request: CardFocusRequest,
    item: FocusCard,
    landscape: Boolean,
    asText: Boolean,
    measuring: Boolean,
    onGoTo: (Int) -> Unit,
    onToggleText: () -> Unit,
) {
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (measuring) Modifier else Modifier.verticalScroll(scroll))
                .padding(horizontal = Spacing.lg, vertical = if (landscape) Spacing.sm else Spacing.md),
            verticalArrangement = Arrangement.spacedBy(if (landscape) Spacing.xs else Spacing.sm),
        ) {
            NotOnTheCard(item, request.selection?.context)
            Related(request, item, measuring, onGoTo)
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
    // A card an answer leads to says what to choose in its bar instead (GoToAnswerBar); with large
    // text here, where it scrolls with the rest, so the bar under it stays one button.
    if (!item.enabled && item.answeredBy == null) {
        ReasonLine(item.reason ?: "This card can't be chosen for this question.")
    }
    if (item.answeredBy != null && largeText()) item.reason?.let { InfoLine(it) }
    item.notes.forEach { note ->
        if (note.item != null) {
            ItemLine(note.item, note.text)
        } else {
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    handicapBasis(card)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    if (item.itemLine) card.item?.let { stat -> ItemLine(stat, "As an item: +1 ${PlayText.stat(stat)} (p.16)") }
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

/**
 * The cards that come with this one (the spells a Gydion card names), small and landscape as printed,
 * each a button to its own page in the viewer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Related(request: CardFocusRequest, item: FocusCard, measuring: Boolean, onGoTo: (Int) -> Unit) {
    val pages = item.related.mapNotNull { key ->
        request.cards.indexOfFirst { it.key == key }.takeIf { it >= 0 }
    }
    if (pages.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        item.relatedLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            pages.forEach { page -> RelatedCard(request.cards[page].card, measuring) { onGoTo(page) } }
        }
    }
}

@Composable
private fun RelatedCard(card: Card, measuring: Boolean, onOpen: () -> Unit) {
    val portrait = RELATED_HEIGHT * CARD_ASPECT
    val width = cardWidth(card, portrait)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = card.name
                role = Role.Button
                onClick(label = "Show the card") {
                    onOpen()
                    true
                }
            },
    ) {
        // Measured only, the scan's room is enough (no image is loaded for a sheet never shown).
        if (measuring) Spacer(Modifier.size(width, RELATED_HEIGHT)) else TableCard(card, width = portrait)
        // The name grows with the font scale only up to 1.3×: a word never breaks under the card.
        val density = LocalDensity.current
        val style = MaterialTheme.typography.labelMedium
        val size = style.fontSize * (minOf(density.fontScale, NAME_MAX_SCALE) / density.fontScale)
        Text(
            card.name,
            style = style.copy(fontSize = size, lineHeight = size * 1.3f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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
        // Large text: the button says it; the hint would take the room the card's sheet needs.
        if (can && !largeText()) {
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

/** The card that answers for [item] (it is what that answer leads to), when it is in the viewer. */
internal fun answerIndex(request: CardFocusRequest, item: FocusCard): Int? =
    item.answeredBy?.let { key -> request.cards.indexOfFirst { it.key == key }.takeIf { it >= 0 } }

/**
 * A card shown with a question that isn't its answer (an upgrade card, ADR-045): what to choose
 * instead in a calm line (not the "Not an option" warning), and a button to go to that card, where
 * a second tap answers as everywhere else.
 */
@Composable
private fun GoToAnswerBar(item: FocusCard, answer: Card, onGoTo: () -> Unit, landscape: Boolean) {
    HorizontalDivider(color = GoaTheme.colors.engravedBorder)
    Column(
        Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Large text: the reason is in the sheet above (NotOnTheCard), the bar is the button.
        if (!largeText()) item.reason?.let { InfoLine(it) }
        OutlinedButton(
            onClick = onGoTo,
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(GO_TO_ANSWER_TAG),
        ) {
            Text("Go to ${answer.name}", textAlign = TextAlign.Center)
        }
        if (!landscape && LocalDensity.current.fontScale <= LARGE_FONT_SCALE) {
            Text(
                "Swipe up or go back to choose nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A calm line with the info icon: what to choose instead of a card an answer leads to. */
@Composable
private fun InfoLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(
            Icons.Outlined.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(scaledIconSize(18.dp, 1.3f)),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun largeText(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

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

private val RELATED_HEIGHT = 52.dp
private const val NAME_MAX_SCALE = 1.3f
private val MORE_FADE = 24.dp
private const val CLOSE_HINT = "Swipe up or go back to close."

/** A hero's Handicap card is `<hero>-handicap` in the catalog (the hero JSON's `handicap`). */
private const val HANDICAP_SUFFIX = "-handicap"
