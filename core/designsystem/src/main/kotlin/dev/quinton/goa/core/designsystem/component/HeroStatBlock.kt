package dev.quinton.goa.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.cardtext.GameIcons
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.catalog.HeroStats
import dev.quinton.goa.core.model.catalog.Stat
import dev.quinton.goa.core.model.catalog.StatRange

/*
 * One stat readout everywhere (DESIGN.md "Stat readout"): the game's item icon, the value as base → max, the stat's
 * name, in that order. The hero page sets them as tiles, the draft's peek adds the 1..8 bar, a sheet over the map
 * puts them on one line. Players learn it once.
 */

/** How a [HeroStatBlock] or [StatReadout] is set. */
enum class StatReadoutStyle {
    /** Icon over value over name, centred: a row of four on a plate (the hero page). */
    Tile,

    /** Icon, name, the 1..8 bar, value: a list of bars (the draft's peek). */
    Bar,

    /** Icon, value, name on one line, for a flowing row of them (a sheet over the map). */
    Compact,
}

/** The Hero Profile's stats in the order the card prints them (rulebook p.21). */
val HeroProfileStats: List<Stat> = listOf(Stat.ATTACK, Stat.INITIATIVE, Stat.DEFENSE, Stat.MOVEMENT)

/**
 * A hero's [stat] from the Hero Profile: [range] base → max on the card's 1..8 scale ("5 → 6", one number when they
 * are equal, "—" unknown). TalkBack hears "Defense: 5 of 8, up to 6 conditionally".
 */
@Composable
fun HeroStatBlock(
    stat: Stat,
    range: StatRange?,
    modifier: Modifier = Modifier,
    style: StatReadoutStyle = StatReadoutStyle.Tile,
) {
    val name = statName(stat)
    if (style == StatReadoutStyle.Bar) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            StatIcon(stat, BAR_ICON)
            Spacer(Modifier.width(Spacing.sm))
            StatBar(name, range, Modifier.weight(1f))
        }
        return
    }
    val spoken = when {
        range == null -> "$name: unknown"
        range.max > range.base -> "$name: ${range.base} of $STAT_SCALE, up to ${range.max} conditionally"
        else -> "$name: ${range.base} of $STAT_SCALE"
    }
    StatReadout(stat, rangeText(range), modifier, spoken, style)
}

/** The hero's four profile stats as [HeroStatBlock]s in the printed order, each [style], then [HeroRatingsNote]. */
@Composable
fun HeroStatBlocks(stats: HeroStats, modifier: Modifier = Modifier, style: StatReadoutStyle = StatReadoutStyle.Bar) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HeroProfileStats.forEach { HeroStatBlock(it, stats.get(it), style = style) }
        HeroRatingsNote(Modifier.padding(top = Spacing.xs))
    }
}

/**
 * The head over a hero's profile stats wherever they show (the hero page, the draft's peek): they are the Hero
 * Profile's comparative ratings on its 1..8 scale, not numbers printed on a card, so they are never titled "Stats".
 */
const val HERO_RATINGS_TITLE = "Hero ratings · 1–8"

/** What the arrow and the scale mean and what the figures are, two short lines under every [HeroStatBlock] set. */
const val HERO_RATINGS_NOTE = "Base → upgraded · 1 very poor, 8 exceptional\nComparative ratings, not card values"

/** [HERO_RATINGS_NOTE], quiet, under the readouts; [align] centres it under tiles. */
@Composable
fun HeroRatingsNote(modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        HERO_RATINGS_NOTE,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A printed value with its item icon: a card's ("Range 2+") or a hero's. [value] is the number as printed, [spoken]
 * what TalkBack hears (default "Range 2+").
 */
@Composable
fun StatReadout(
    stat: Stat,
    value: String,
    modifier: Modifier = Modifier,
    spoken: String = "${statName(stat)} $value",
    style: StatReadoutStyle = StatReadoutStyle.Compact,
) {
    val name = statName(stat)
    when (style) {
        StatReadoutStyle.Tile -> Column(
            modifier.clearAndSetSemantics { contentDescription = spoken },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatIcon(stat, TILE_ICON)
            Text(
                value,
                style = GoaTextStyles.numeric.copy(fontSize = 16.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        StatReadoutStyle.Bar, StatReadoutStyle.Compact -> Row(
            modifier.clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatIcon(stat, COMPACT_ICON)
            Spacer(Modifier.width(Spacing.xs))
            Text(value, style = GoaTextStyles.numeric, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(Spacing.xs))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A stat's official item icon (the dashboard's), [height] tall; the game glyph in bronze until it loads. */
@Composable
fun StatIcon(stat: Stat, height: Dp, modifier: Modifier = Modifier) {
    val h = scaledIconSize(height, 1.4f)
    OfficialIcon(
        OfficialArt.itemIcon(stat),
        modifier.size(width = h * ITEM_ICON_RATIO, height = h),
        fallback = {
            Icon(
                GameIcons.forStat(stat).vector,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(h),
            )
        },
    )
}

/** "5", "5 → 6" or "—". */
fun rangeText(range: StatRange?): String = when {
    range == null -> "—"
    range.max > range.base -> "${range.base} → ${range.max}"
    else -> "${range.base}"
}

/** The stat's name as the cards print it. */
fun statName(stat: Stat): String = when (stat) {
    Stat.ATTACK -> "Attack"
    Stat.DEFENSE -> "Defense"
    Stat.INITIATIVE -> "Initiative"
    Stat.MOVEMENT -> "Movement"
    Stat.RANGE -> "Range"
    Stat.RADIUS -> "Radius"
}

/** The item icons are wider than tall (the dashboard's 40 × 27). */
private const val ITEM_ICON_RATIO = 1.48f
private val TILE_ICON = 27.dp
private val BAR_ICON = 20.dp
private val COMPACT_ICON = 16.dp
