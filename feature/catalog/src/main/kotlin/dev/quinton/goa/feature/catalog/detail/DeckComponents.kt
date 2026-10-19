package dev.quinton.goa.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.StartZone
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.presentation.catalog.DeckGroup
import dev.quinton.goa.core.presentation.catalog.DeckGroupKind
import dev.quinton.goa.core.presentation.catalog.DeckLayout
import dev.quinton.goa.core.presentation.catalog.SPELLBOOK_NOTE
import dev.quinton.goa.core.presentation.catalog.StatBar
import dev.quinton.goa.core.presentation.catalog.StatSegment
import dev.quinton.goa.core.presentation.catalog.TierRow
import dev.quinton.goa.core.presentation.catalog.bars
import dev.quinton.goa.core.presentation.catalog.blockMarkerLabel
import dev.quinton.goa.core.presentation.catalog.extraRowTitle
import dev.quinton.goa.core.presentation.catalog.handicapBasis
import dev.quinton.goa.core.presentation.catalog.kindLabel
import dev.quinton.goa.core.presentation.catalog.label
import dev.quinton.goa.core.presentation.catalog.multipleGoldNote
import dev.quinton.goa.core.presentation.catalog.printedValues
import dev.quinton.goa.core.presentation.catalog.spokenHandicapBasis
import dev.quinton.goa.core.presentation.catalog.spokenSummary
import dev.quinton.goa.core.presentation.catalog.startsIn
import dev.quinton.goa.core.presentation.catalog.tierNumeral
import dev.quinton.goa.feature.catalog.ui.CardTextView
import dev.quinton.goa.feature.catalog.ui.GameIcon
import dev.quinton.goa.feature.catalog.ui.GameIcons
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge
import dev.quinton.goa.feature.catalog.ui.ValueChip

/**
 * Hero Profile bars from 1 ("Very poor") to 8 ("Exceptional"): solid up to the base value,
 * brighter up to the max — the conditional part (level-up choices or card text).
 */
@Composable
internal fun StatBars(stats: HeroStats, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        stats.bars().forEach { bar -> StatBarRow(bar) }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            Text(
                "1 · Very poor",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Exceptional · 8",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendSwatch(MaterialTheme.colorScheme.primary)
            Text(
                " Base   ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegendSwatch(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), outlined = true)
            Text(
                " Conditional (level-ups, card text)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, outlined: Boolean = false) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .size(width = 14.dp, height = 8.dp)
            .clip(shape)
            .background(color)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
    )
}

@Composable
private fun StatBarRow(bar: StatBar) {
    val icon = GameIcons.forStat(bar.stat)
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = bar.spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon.vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(bar.stat.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                bar.rangeText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bar.segments.forEach { segment ->
                val conditional = segment == StatSegment.CONDITIONAL
                val color = when (segment) {
                    StatSegment.BASE -> MaterialTheme.colorScheme.primary
                    StatSegment.CONDITIONAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    StatSegment.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
                }
                val segmentShape = RoundedCornerShape(3.dp)
                val conditionalBorder = if (conditional) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, segmentShape)
                } else {
                    Modifier
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(segmentShape)
                        .background(color)
                        .then(conditionalBorder),
                )
            }
        }
    }
}

/** One deck colour (or Basic / Ultimate) with its tier rows. */
@Composable
internal fun DeckGroupSection(group: DeckGroup, onCardClick: (Card) -> Unit, modifier: Modifier = Modifier) {
    val accent = groupAccent(group.kind)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = Spacing.lg, bottom = Spacing.sm).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(Spacing.sm))
            Text(group.kind.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "${group.cards.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.kind == DeckGroupKind.BASIC) MultipleGoldNote(group.cards)
        val fullWidth = group.kind == DeckGroupKind.ULTIMATE
        group.rows.forEach { row -> TierRowView(row, group.kind, fullWidth, onCardClick) }
    }
}

/**
 * Takahide has three gold cards. Their own text says where each starts and how they swap
 * ("Swap this card with a different gold card in your deck."), so the note points there and
 * the tiles carry "Starts in hand" / "Starts in deck" from each card's reminder text.
 */
@Composable
private fun MultipleGoldNote(cards: List<Card>) {
    val note = multipleGoldNote(cards) ?: return
    Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.sm),
    )
}

/** The hero's extra cards by tier; titled "Spellbook" for Gydion's spell cards. */
@Composable
internal fun ExtrasSection(layout: DeckLayout, onCardClick: (Card) -> Unit, modifier: Modifier = Modifier) {
    if (layout.extras.isEmpty()) return
    val spellbook = layout.extrasAreSpellbook
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(top = Spacing.xl, bottom = Spacing.xs).semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (spellbook) "Spellbook" else "Extras", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "${layout.extras.size} cards",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (spellbook) {
            Text(
                SPELLBOOK_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        layout.extraRows.forEach { row ->
            Text(
                extraRowTitle(row),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs).semantics { heading() },
            )
            CardTileGrid(row.cards, columns = 2, onCardClick = onCardClick, showText = spellbook)
        }
    }
}

@Composable
private fun TierRowView(row: TierRow, kind: DeckGroupKind, fullWidth: Boolean, onCardClick: (Card) -> Unit) {
    val tier = row.tier
    if (tier != null && kind != DeckGroupKind.ULTIMATE) {
        Text(
            "Tier ${tierNumeral(tier)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xs),
        )
    }
    CardTileGrid(row.cards, columns = if (fullWidth) 1 else 2, onCardClick = onCardClick, showText = fullWidth)
    Spacer(Modifier.height(Spacing.sm))
}

/** Tiles in fixed columns so tiers line up into an upgrade tree. */
@Composable
internal fun CardTileGrid(
    cards: List<Card>,
    columns: Int,
    onCardClick: (Card) -> Unit,
    isHandicap: Boolean = false,
    showText: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cards.chunked(columns).forEach { chunk ->
            // Equal heights per row via intrinsics; WrapRow reports its wrapped height, so a
            // name on two lines never pushes the markers out of the tile.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                chunk.forEach { card ->
                    CardTile(
                        card = card,
                        isHandicap = isHandicap,
                        showText = showText,
                        onClick = { onCardClick(card) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun groupAccent(kind: DeckGroupKind): Color = when (kind) {
    DeckGroupKind.BASIC -> cardColor(CardColor.GOLD)
    DeckGroupKind.RED -> cardColor(CardColor.RED)
    DeckGroupKind.GREEN -> cardColor(CardColor.GREEN)
    DeckGroupKind.BLUE -> cardColor(CardColor.BLUE)
    DeckGroupKind.ULTIMATE -> cardColor(CardColor.PURPLE)
    DeckGroupKind.OTHER -> cardColor(CardColor.OTHER)
}

/**
 * Compact deck tile: colour accent, name, initiative, primary action, values, markers.
 * [showText] adds the card text (Ultimate tiles, which span the row). A Handicap tile says which
 * basic card it is a weaker version of, labelled as derived from the frame colour ([handicapBasis]):
 * the card prints nothing about what it replaces.
 */
@Composable
internal fun CardTile(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHandicap: Boolean = false,
    showText: Boolean = false,
) {
    val accent = cardColor(card.color)
    val shape = MaterialTheme.shapes.medium
    val summary = card.spokenSummary(isHandicap)
    Row(
        modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    0f to accent.copy(alpha = 0.14f),
                    0.6f to MaterialTheme.colorScheme.surface,
                ),
            )
            .border(1.dp, GoaTheme.colors.engravedBorder, shape)
            .clickable(onClickLabel = "View card", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = summary
                role = Role.Button
                onClick(label = "View card") {
                    onClick()
                    true
                }
            },
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(start = 10.dp, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm).weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = Spacing.xs),
                )
                InitiativeBadge(card.initiative, accent = accent, size = 28.dp)
            }
            card.primary?.let { primary ->
                val icon = GameIcons.forPrimary(primary)
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon.vector, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        icon.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val handicapBasis = if (isHandicap) card.handicapBasis() else null
            if (handicapBasis != null) {
                Text(
                    handicapBasis,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (showText && card.text.isNotBlank()) {
                CardTextView(
                    card.text,
                    style = MaterialTheme.typography.bodyMedium,
                    iconTint = accent,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
            WrapRow(horizontalGap = 10.dp, verticalGap = 2.dp) {
                card.printedValues().forEach {
                    ValueChip(GameIcons.forStat(it.stat), it.printed, iconSize = 14.dp, spoken = it.spoken)
                }
                if (card.ranged) Marker(GameIcons.ranged, "Ranged")
                card.blockMarkerLabel()?.let { Marker(GameIcons.block, it) }
                card.item?.let { Marker(GameIcons.item, "+1 ${it.label}") }
                card.startsIn()?.let { Marker(GameIcons.item, it, showIcon = false) }
            }
        }
    }
}

@Composable
private fun Marker(icon: GameIcon, text: String, showIcon: Boolean = true) {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(
                icon.vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
        )
    }
}

/**
 * A wrapping row like FlowRow whose intrinsic height is the wrapped height at the given width,
 * so a parent measuring with IntrinsicSize.Min (equal-height deck tiles) reserves room for
 * every line of markers.
 */
@Composable
private fun WrapRow(horizontalGap: Dp, verticalGap: Dp, content: @Composable () -> Unit) {
    val policy = remember(horizontalGap, verticalGap) { WrapRowPolicy(horizontalGap, verticalGap) }
    Layout(content = content, measurePolicy = policy)
}

private class WrapRowPolicy(private val hGap: Dp, private val vGap: Dp) : MeasurePolicy {
    /** Lines of child indices for [widths] packed greedily into [maxWidth]. */
    private fun lines(widths: List<Int>, maxWidth: Int, gap: Int): List<List<Int>> {
        val lines = mutableListOf<MutableList<Int>>()
        var used = 0
        widths.forEachIndexed { i, w ->
            val line = lines.lastOrNull()
            if (line == null || used + gap + w > maxWidth) {
                lines += mutableListOf(i)
                used = w
            } else {
                line += i
                used += gap + w
            }
        }
        return lines
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val gap = hGap.roundToPx()
        val lineGap = vGap.roundToPx()
        val lines = lines(placeables.map { it.width }, constraints.maxWidth, gap)
        val heights = lines.map { line -> line.maxOf { placeables[it].height } }
        val width = lines.maxOfOrNull { line -> line.sumOf { placeables[it].width } + gap * (line.size - 1) } ?: 0
        val height = heights.sum() + lineGap * (lines.size - 1).coerceAtLeast(0)
        return layout(
            width.coerceIn(constraints.minWidth, constraints.maxWidth),
            height.coerceIn(constraints.minHeight, constraints.maxHeight),
        ) {
            var y = 0
            lines.forEachIndexed { n, line ->
                var x = 0
                line.forEach { i ->
                    val p = placeables[i]
                    p.placeRelative(x, y + (heights[n] - p.height) / 2)
                    x += p.width + gap
                }
                y += heights[n] + lineGap
            }
        }
    }

    private fun IntrinsicMeasureScope.wrappedHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        if (measurables.isEmpty()) return 0
        val widths = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val maxWidth = if (width == Constraints.Infinity) Int.MAX_VALUE else width
        val lines = lines(widths, maxWidth, hGap.roundToPx())
        val heights = lines.map { line -> line.maxOf { measurables[it].minIntrinsicHeight(widths[it]) } }
        return heights.sum() + vGap.roundToPx() * (lines.size - 1)
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        wrappedHeight(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        wrappedHeight(measurables, width)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        measurables.sumOf { it.maxIntrinsicWidth(height) } + hGap.roundToPx() * (measurables.size - 1).coerceAtLeast(0)
}
