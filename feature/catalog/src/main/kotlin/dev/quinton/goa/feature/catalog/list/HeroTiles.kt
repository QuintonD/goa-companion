package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.quinton.goa.core.designsystem.component.ActionIcon
import dev.quinton.goa.core.designsystem.component.ComplexityStars
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.LookAction
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.domain.catalog.HeroListItem
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.matchHint
import dev.quinton.goa.core.domain.catalog.metricLine
import dev.quinton.goa.core.domain.catalog.rangedLabel
import dev.quinton.goa.core.domain.catalog.spokenLabel
import dev.quinton.goa.core.domain.catalog.traitsLine
import dev.quinton.goa.feature.catalog.ui.MetaPill
import dev.quinton.goa.feature.catalog.ui.PlateArt
import dev.quinton.goa.feature.catalog.ui.SharedTitlePlate
import dev.quinton.goa.feature.catalog.ui.SmallPlate
import dev.quinton.goa.feature.catalog.ui.TileShape
import dev.quinton.goa.feature.catalog.ui.UnverifiedBadge
import dev.quinton.goa.feature.catalog.ui.overlapUp
import dev.quinton.goa.feature.catalog.ui.tilePlate

/**
 * Grid tile (three per row on a phone), as a card is made: the slate plate, the hero's name on the
 * title plate, the portrait under it with the deck symbol at its corner, then the title, stars and roles.
 * Every row keeps its height whether a tile uses it or not, so the tiles of a line are one height. Text
 * wraps or ellipsizes; the tile never grows wider with the font scale. Frame and art share one draw node
 * each, and [art] one decoded bitmap for every tile, so scrolling stays cheap.
 */
@Composable
internal fun HeroGridTile(
    item: HeroListItem,
    sort: CatalogSort,
    art: PlateArt,
    footnotes: TileFootnoteRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hero = item.hero
    GoaPlateTheme {
        Column(
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = item.spokenLabel(sort) }
                .tilePlate(art)
                .clip(TileShape)
                .clickable(onClick = onClick)
                .padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = Spacing.sm),
        ) {
            SharedTitlePlate(art.titlePlate, SmallPlate, Modifier.fillMaxWidth().zIndex(1f)) {
                Text(
                    hero.name,
                    style = TILE_NAME,
                    color = GoaTheme.colors.onParchment,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TileArtwork(item)
            TileText(item, sort, footnotes)
        }
    }
}

@Composable
private fun TileArtwork(item: HeroListItem) {
    val hero = item.hero
    Box(
        Modifier
            .overlapUp(PLATE_OVERLAP)
            .padding(horizontal = 1.dp)
            .fillMaxWidth()
            .aspectRatio(PORTRAIT_RATIO),
    ) {
        // Unframed art, its edge and the shade under the symbol drawn over it in the same node: the
        // fewest layers per tile while the grid scrolls.
        HeroPortrait(
            heroId = hero.id,
            portrait = hero.portrait,
            contentDescription = null,
            shape = PORTRAIT_SHAPE,
            framed = false,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val center = Offset(SYMBOL_CENTER_X.toPx(), SYMBOL_CENTER_Y.toPx())
                    val shade = Brush.radialGradient(
                        listOf(SYMBOL_SHADE, Color.Transparent),
                        center = center,
                        radius = SYMBOL_SHADE_RADIUS.toPx(),
                    )
                    onDrawWithContent {
                        drawContent()
                        if (hero.symbol != null) drawCircle(shade, SYMBOL_SHADE_RADIUS.toPx(), center)
                    }
                },
        )
        // The deck symbol (the hero's card backs), as players know the hero at the table.
        if (hero.symbol != null) {
            HeroSymbol(
                hero.id,
                hero.symbol,
                Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = PLATE_OVERLAP + 2.dp).size(GRID_SYMBOL),
            )
        }
        if (!hero.verified) {
            UnverifiedBadge(compact = true, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
        }
    }
}

@Composable
private fun TileText(item: HeroListItem, sort: CatalogSort, footnotes: TileFootnoteRoom) {
    val hero = item.hero
    // Larger text wraps: every tile keeps the extra lines, so the plates of a row match.
    val lines = tileLines(LocalDensity.current.fontScale)
    Column(Modifier.padding(start = 4.dp, top = 3.dp)) {
        Text(
            hero.title,
            style = TILE_TITLE,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = lines,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
        )
        // A line of a fixed height with no room to wrap: a ranged hero has the game's Range icon beside the
        // stars (the word on the list row, the hero page and for TalkBack).
        Row(
            Modifier.height(STARS_LINE),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComplexityStars(hero.complexity, starSize = 10.dp)
            if (item.rangedLabel != null) ActionIcon(LookAction.RANGE, size = RANGED_ICON)
        }
        // Two roles fit a tile, as on the mock-up; the hero page and TalkBack have them all.
        val traits = hero.roles.take(TILE_ROLES).joinToString(" · ") { it.label }
        if (traits.isNotEmpty()) {
            Text(
                traits,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = lines,
                maxLines = lines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TileFootnotes(item, sort, footnotes)
    }
}

/**
 * Which footnote rows the grid's tiles keep. A lazy grid doesn't stretch the tiles of a line, so when any tile
 * of the results has a sort metric or a search hint, every tile keeps the room for it.
 */
internal class TileFootnoteRoom(val metric: Boolean, val hint: Boolean) {
    companion object {
        fun of(heroes: List<HeroListItem>, sort: CatalogSort) = TileFootnoteRoom(
            metric = heroes.any { it.metricLine(sort) != null },
            hint = heroes.any { it.matchHint() != null },
        )
    }
}

@Composable
private fun TileFootnotes(item: HeroListItem, sort: CatalogSort, room: TileFootnoteRoom = NO_ROOM) {
    val metric = item.metricLine(sort)
    if (metric != null || room.metric) {
        Text(
            metric.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
    val hint = item.matchHint()
    if (hint == null) {
        if (room.hint) {
            Text(
                "",
                style = MaterialTheme.typography.labelSmall,
                minLines = HINT_LINES,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    } else {
        Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = if (room.hint) HINT_LINES else 1,
                maxLines = HINT_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact list row (six or more per screen), a plate like the tile: portrait, name, title, stars, pills, roles. */
@Composable
internal fun HeroListRow(
    item: HeroListItem,
    sort: CatalogSort,
    art: PlateArt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hero = item.hero
    GoaPlateTheme {
        Row(
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = item.spokenLabel(sort) }
                .tilePlate(art)
                .clip(TileShape)
                .clickable(onClick = onClick)
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HeroPortrait(
                    heroId = hero.id,
                    portrait = hero.portrait,
                    contentDescription = null,
                    shape = RoundedCornerShape(4.dp),
                    framed = false,
                    modifier = Modifier.padding(end = 6.dp, bottom = 6.dp).size(48.dp),
                )
                if (hero.symbol != null) {
                    HeroSymbol(hero.id, hero.symbol, Modifier.align(Alignment.BottomEnd).size(LIST_SYMBOL))
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        hero.name,
                        style = GoaTextStyles.sectionHead,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        hero.title,
                        style = TILE_TITLE,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ComplexityStars(hero.complexity, starSize = 12.dp)
                    MetaPill(item.productName, modifier = Modifier.weight(1f, fill = false))
                    item.rangedLabel?.let { MetaPill(it, color = MaterialTheme.colorScheme.primary) }
                    if (!hero.verified) UnverifiedBadge(compact = true)
                }
                val traits = item.traitsLine()
                if (traits.isNotEmpty()) {
                    Text(
                        traits,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TileFootnotes(item, sort)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Width / height of the portrait on a tile: the art's own proportions, nearly uncropped. */
private const val PORTRAIT_RATIO = 1.09f

/** Lines a tile keeps for its title and roles: they wrap at 1.15 (three columns) and from 1.8 (two columns). */
private fun tileLines(fontScale: Float): Int = if (fontScale in 1.1f..1.2f || fontScale > 1.6f) 2 else 1

private const val TILE_ROLES = 2
private const val HINT_LINES = 2
private val NO_ROOM = TileFootnoteRoom(metric = false, hint = false)
private val STARS_LINE = 22.dp
private val RANGED_ICON = 16.dp

private val PORTRAIT_SHAPE = RoundedCornerShape(2.dp)

/** How far the portrait runs up under the title plate. */
private val PLATE_OVERLAP = 5.dp
private val GRID_SYMBOL = 27.dp
private val LIST_SYMBOL = 24.dp
private val SYMBOL_CENTER_X = 16.dp
private val SYMBOL_CENTER_Y = 20.dp
private val SYMBOL_SHADE_RADIUS = 24.dp
private val SYMBOL_SHADE = Color(0x8C000000)
private val TILE_NAME = GoaTextStyles.plateTitle.copy(fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.sp)
private val TILE_TITLE = GoaTextStyles.printedSmall.copy(
    fontStyle = FontStyle.Italic,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp,
)
