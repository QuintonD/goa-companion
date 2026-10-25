package dev.quinton.goa.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.cardtext.cardTextAnnotated
import dev.quinton.goa.core.designsystem.component.CARD_ASPECT
import dev.quinton.goa.core.designsystem.component.CardScan
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.DeckGroup
import dev.quinton.goa.core.domain.catalog.DeckGroupKind
import dev.quinton.goa.core.domain.catalog.DeckLayout
import dev.quinton.goa.core.domain.catalog.PROFILE_SCALE
import dev.quinton.goa.core.domain.catalog.ProfileBar
import dev.quinton.goa.core.domain.catalog.ProfileSegment
import dev.quinton.goa.core.domain.catalog.SPELLBOOK_NOTE
import dev.quinton.goa.core.domain.catalog.StartZone
import dev.quinton.goa.core.domain.catalog.StartingCards
import dev.quinton.goa.core.domain.catalog.TierRow
import dev.quinton.goa.core.domain.catalog.blockMarkerLabel
import dev.quinton.goa.core.domain.catalog.cardValues
import dev.quinton.goa.core.domain.catalog.extraRowTitle
import dev.quinton.goa.core.domain.catalog.handicapBasis
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.multipleGoldNote
import dev.quinton.goa.core.domain.catalog.profileBars
import dev.quinton.goa.core.domain.catalog.spokenHandicapBasis
import dev.quinton.goa.core.domain.catalog.spokenSummary
import dev.quinton.goa.core.domain.catalog.startsIn
import dev.quinton.goa.core.domain.catalog.tierNumeral
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge
import dev.quinton.goa.feature.catalog.ui.ValueChip

/**
 * Hero Profile bars from 1 ("Very poor") to 8 ("Exceptional"): solid up to the base value,
 * brighter up to the max — the conditional part (level-up choices or card text).
 */
@Composable
internal fun StatBars(stats: HeroStats, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        stats.profileBars().forEach { bar -> StatBar(bar) }
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
private fun StatBar(bar: ProfileBar) {
    val stat = bar.stat
    val icon = GameIcons.forStat(stat)
    val description = bar.spoken
    val rangeText = bar.rangeText
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon.vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(stat.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                rangeText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(PROFILE_SCALE) { i ->
                val segment = bar.segment(i + 1)
                val conditional = segment == ProfileSegment.CONDITIONAL
                val color = when (segment) {
                    ProfileSegment.BASE -> MaterialTheme.colorScheme.primary
                    ProfileSegment.CONDITIONAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ProfileSegment.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
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
        // The cards themselves, in tier order (I, II A, II B, III A, III B): each scan prints its
        // tier numeral, so the grid doesn't repeat it.
        DeckCardGrid(group.cards, onCardClick)
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
            DeckCardGrid(
                row.cards,
                onCardClick,
                aspect = if (spellbook) SPELL_ASPECT else CARD_ASPECT,
                minWidth = if (spellbook) SPELL_MIN_WIDTH else CARD_MIN_WIDTH,
            )
        }
    }
}

/** Width ÷ height of a spellbook page scan (landscape, about 737 × 510). */
private const val SPELL_ASPECT = 737f / 510f
private val CARD_MIN_WIDTH = 100.dp
private val SPELL_MIN_WIDTH = 150.dp

/**
 * The official card scans in a grid: as many columns of at least [minWidth] as fit (three on a
 * phone held upright, a whole colour's five in a row on a wider screen), each card with its name
 * under it. The scan is the card (its values and text are printed on it); TalkBack hears the
 * card's full summary and text, and a tap opens the card viewer. A card without a scan shows the
 * designed tile instead.
 */
@Composable
internal fun DeckCardGrid(
    cards: List<Card>,
    onCardClick: (Card) -> Unit,
    modifier: Modifier = Modifier,
    isHandicap: Boolean = false,
    aspect: Float = CARD_ASPECT,
    minWidth: Dp = CARD_MIN_WIDTH,
) {
    // Large text: wider cards, so a caption never breaks a word and the printed name reads.
    val scaledMin = minWidth * LocalDensity.current.fontScale.coerceIn(1f, MAX_WIDTH_SCALE)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = Spacing.sm
        val columns = ((maxWidth + gap) / (scaledMin + gap)).toInt().coerceIn(1, MAX_COLUMNS)
        val cardWidth = (maxWidth - gap * (columns - 1)) / columns
        // From this width the name printed on the scan is readable: no caption repeats it.
        val showName = cardWidth < NAME_PRINTED_WIDTH
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            cards.chunked(columns).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                    chunk.forEach { card ->
                        DeckCard(card, isHandicap, aspect, showName, onClick = {
                            onCardClick(card)
                        }, Modifier.weight(1f))
                    }
                    repeat(columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val MAX_COLUMNS = 6
private const val MAX_WIDTH_SCALE = 1.6f
private val NAME_PRINTED_WIDTH = 140.dp

@Composable
private fun DeckCard(
    card: Card,
    isHandicap: Boolean,
    aspect: Float,
    showName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (card.image == null) {
        CardTile(card, onClick = onClick, isHandicap = isHandicap, modifier = modifier.heightIn(min = 96.dp))
        return
    }
    val summary = card.spokenSummary(isHandicap) + card.text.takeIf { it.isNotBlank() }?.let {
        ". " + cardTextAnnotated(it, Color.Unspecified).text
    }.orEmpty()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CardScan(
            image = card.image,
            contentDescription = summary,
            onClick = onClick,
            aspect = aspect,
            modifier = Modifier.fillMaxWidth(),
        ) {}
        // A small card's printed name is too small to read, so it is repeated under it; anything the
        // card doesn't print (where a basic card starts, what a Handicap card stands in for) follows.
        val note = (if (isHandicap) card.handicapBasis() else null) ?: card.startsIn()
        if (showName) {
            Text(
                card.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xs).clearAndSetSemantics {},
            )
        }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (showName) 0.dp else Spacing.xs).clearAndSetSemantics {},
            )
        }
    }
}

/** Designed tiles (cards without a scan) in fixed columns so tiers line up into an upgrade tree. */
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

/** One printed value; [plus] when the card prints it with a `+` (it grows per the card text). */
internal data class PrintedValue(val icon: GameIcon, val value: Int, val plus: Boolean) {
    /** As printed: "4" or "4+". */
    val printed: String get() = if (plus) "$value+" else "$value"

    /** For TalkBack: "Attack 4" or "Attack 4 plus". */
    val spoken: String get() = "${icon.label} " + if (plus) "$value plus" else "$value"
}

/** Non-null printed values in a stable order (the shared [cardValues]) with their glyphs. */
internal fun Card.printedValues(): List<PrintedValue> =
    cardValues().map { PrintedValue(GameIcons.forStat(it.stat), it.value, it.plus) }

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
                card.printedValues().forEach { ValueChip(it.icon, it.printed, iconSize = 14.dp, spoken = it.spoken) }
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
