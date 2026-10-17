package dev.quinton.goa.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.cardColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.Card
import dev.quinton.goa.core.model.catalog.CardColor
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.feature.catalog.model.DeckGroup
import dev.quinton.goa.feature.catalog.model.DeckGroupKind
import dev.quinton.goa.feature.catalog.model.TierRow
import dev.quinton.goa.feature.catalog.model.tierNumeral
import dev.quinton.goa.feature.catalog.ui.GameIcon
import dev.quinton.goa.feature.catalog.ui.GameIcons
import dev.quinton.goa.feature.catalog.ui.InitiativeBadge
import dev.quinton.goa.feature.catalog.ui.ValueChip
import dev.quinton.goa.feature.catalog.ui.kindLabel
import dev.quinton.goa.feature.catalog.ui.label

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
        val fullWidth = group.kind == DeckGroupKind.ULTIMATE
        group.rows.forEach { row -> TierRowView(row, group.kind, fullWidth, onCardClick) }
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
    CardTileGrid(row.cards, columns = if (fullWidth) 1 else 2, onCardClick = onCardClick)
    Spacer(Modifier.height(Spacing.sm))
}

/** Tiles in fixed columns so tiers line up into an upgrade tree. */
@Composable
internal fun CardTileGrid(cards: List<Card>, columns: Int, onCardClick: (Card) -> Unit, isHandicap: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        cards.chunked(columns).forEach { chunk ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                chunk.forEach { card ->
                    CardTile(
                        card = card,
                        isHandicap = isHandicap,
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
    valueIcons().forEach { (icon, value) -> add("${icon.label} $value") }
    if (ranged) add("Ranged")
    if (block) add("Block")
    item?.let { add("Item: plus 1 ${it.label}") }
}.joinToString(". ")

/** Non-null printed values in a stable order. */
internal fun Card.valueIcons(): List<Pair<GameIcon, Int>> = listOfNotNull(
    values.attack?.let { GameIcons.attack to it },
    values.defense?.let { GameIcons.defense to it },
    values.movement?.let { GameIcons.movement to it },
    values.range?.let { GameIcons.range to it },
    values.radius?.let { GameIcons.radius to it },
)

/** Compact deck tile: colour accent, name, initiative, primary action, values, markers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardTile(card: Card, onClick: () -> Unit, modifier: Modifier = Modifier, isHandicap: Boolean = false) {
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
            Spacer(Modifier.weight(1f).heightIn(min = 6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                card.valueIcons().forEach { (icon, value) -> ValueChip(icon, value.toString(), iconSize = 14.dp) }
                if (card.ranged) Marker(GameIcons.ranged, "Ranged")
                if (card.block) Marker(GameIcons.block, "Block")
                card.item?.let { Marker(GameIcons.item, "+1 ${it.label}") }
            }
        }
    }
}

@Composable
private fun Marker(icon: GameIcon, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon.vector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
        )
    }
}
