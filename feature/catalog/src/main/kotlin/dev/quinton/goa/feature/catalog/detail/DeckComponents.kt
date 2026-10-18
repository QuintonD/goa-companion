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
import dev.quinton.goa.feature.catalog.model.DeckGroup
import dev.quinton.goa.feature.catalog.model.DeckGroupKind
import dev.quinton.goa.feature.catalog.model.DeckLayout
import dev.quinton.goa.feature.catalog.model.TierRow
import dev.quinton.goa.feature.catalog.model.tierNumeral
import dev.quinton.goa.core.designsystem.cardtext.CardTextView
import dev.quinton.goa.core.designsystem.cardtext.GameIcon
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge
import dev.quinton.goa.feature.catalog.ui.ValueChip
import dev.quinton.goa.feature.catalog.ui.blockMarkerLabel
import dev.quinton.goa.feature.catalog.ui.handicapBasis
import dev.quinton.goa.feature.catalog.ui.kindLabel
import dev.quinton.goa.feature.catalog.ui.label
import dev.quinton.goa.feature.catalog.ui.spokenHandicapBasis

private const val STAT_SCALE = 8

/** Order printed on the official Hero Profile card (rulebook p.21). */
private val PROFILE_STATS = listOf(Stat.ATTACK, Stat.INITIATIVE, Stat.DEFENSE, Stat.MOVEMENT)

/**
 * Hero Profile bars from 1 ("Very poor") to 8 ("Exceptional"): solid up to the base value,
 * brighter up to the max — the conditional part (level-up choices or card text).
 */
@Composable
internal fun StatBars(stats: HeroStats, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PROFILE_STATS.forEach { stat -> StatBar(stat, stats) }
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
private fun StatBar(stat: Stat, stats: HeroStats) {
    val range = stats.get(stat)
    val icon = GameIcons.forStat(stat)
    val description = if (range == null) {
        "${stat.label}: unknown"
    } else {
        "${stat.label}: ${range.base} of $STAT_SCALE" +
            if (range.max > range.base) ", up to ${range.max} conditionally" else ""
    }
    val rangeText = when {
        range == null -> "—"
        range.base == range.max -> "${range.base}"
        else -> "${range.base} → ${range.max}"
    }
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
            repeat(STAT_SCALE) { i ->
                val level = i + 1
                val conditional = range != null && level > range.base && level <= range.max
                val color = when {
                    range == null -> MaterialTheme.colorScheme.surfaceVariant
                    level <= range.base -> MaterialTheme.colorScheme.primary
                    conditional -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
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
    val golds = cards.filter { it.color == CardColor.GOLD }
    val hand = golds.count { it.startsIn() == STARTS_IN_HAND }
    val deck = golds.count { it.startsIn() == STARTS_IN_DECK }
    if (golds.size < 2 || hand + deck == 0) return
    Text(
        "${golds.size} gold cards: $hand ${if (hand == 1) "starts" else "start"} in your hand, $deck in your " +
            "deck, as their card text says. Tap a card for how it swaps.",
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
                "Spells by tier. Deck cards name the spells they cast; tap a spell for its full card.",
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

/** "Gold cantrips · 3", "Tier II · 9", "Ultimate · 1". */
private fun extraRowTitle(row: TierRow): String {
    val tier = row.tier
    val name = when {
        tier == null && row.cards.all { it.color == CardColor.GOLD } -> "Gold (no tier)"
        tier == null -> "No tier"
        tier == 4 -> "Ultimate"
        else -> "Tier ${tierNumeral(tier)}"
    }
    return "$name · ${row.cards.size}"
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

/** Spoken summary of a card for TalkBack. */
internal fun Card.spokenSummary(isHandicap: Boolean = false): String = buildList {
    add(name)
    add(kindLabel(isHandicap))
    initiative?.let { add("Initiative $it") }
    primary?.let { add("Primary action ${GameIcons.forPrimary(it).label}") }
    printedValues().forEach { add(it.spoken) }
    if (ranged) add("Ranged")
    blockMarkerLabel()?.let { add(it) }
    item?.let { add("Item: plus 1 ${it.label}") }
    startsIn()?.let { add(it) }
    if (isHandicap) spokenHandicapBasis()?.let { add(it) }
}.joinToString(". ")

/** One printed value; [plus] when the card prints it with a `+` (it grows per the card text). */
internal data class PrintedValue(val icon: GameIcon, val value: Int, val plus: Boolean) {
    /** As printed: "4" or "4+". */
    val printed: String get() = if (plus) "$value+" else "$value"

    /** For TalkBack: "Attack 4" or "Attack 4 plus". */
    val spoken: String get() = "${icon.label} " + if (plus) "$value plus" else "$value"
}

private val VALUE_ORDER = listOf(
    Stat.ATTACK to GameIcons.attack,
    Stat.DEFENSE to GameIcons.defense,
    Stat.MOVEMENT to GameIcons.movement,
    Stat.RANGE to GameIcons.range,
    Stat.RADIUS to GameIcons.radius,
)

/** Non-null printed values in a stable order. */
internal fun Card.printedValues(): List<PrintedValue> = VALUE_ORDER.mapNotNull { (stat, icon) ->
    values.get(stat)?.let { PrintedValue(icon, it, stat in values.plus) }
}

/** Where a basic card starts, from its printed reminder "(This card starts the game in your hand.)". */
internal fun Card.startsIn(): String? = when (StartingCards.startZone(this)) {
    StartZone.HAND -> STARTS_IN_HAND
    StartZone.DECK -> STARTS_IN_DECK
    null -> null
}

private const val STARTS_IN_HAND = "Starts in hand"
private const val STARTS_IN_DECK = "Starts in deck"

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
