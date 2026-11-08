package dev.quinton.goa.feature.catalog.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.quinton.goa.core.designsystem.component.ActionIcon
import dev.quinton.goa.core.designsystem.component.CardTitleSpan
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
import dev.quinton.goa.core.domain.catalog.MatchField
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.matchHint
import dev.quinton.goa.core.domain.catalog.metricLine
import dev.quinton.goa.core.domain.catalog.rangedLabel
import dev.quinton.goa.core.domain.catalog.spokenLabel
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
 * Every row keeps its height whether a tile uses it or not, so the tiles of a line are one height: the name
 * stays on one line, a long one ("Silverarrow") in a smaller size. Other text wraps or ellipsizes; the tile
 * never grows wider with the font scale. Frame and art share one draw node
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
                .padding(start = 3.dp, end = 3.dp, top = 3.dp, bottom = 6.dp),
        ) {
            SharedTitlePlate(art.titlePlate, SmallPlate, Modifier.fillMaxWidth().zIndex(1f)) {
                // Unseen, at the full size: a shrunk name leaves its plate, and so its tile, the height of the others.
                Text("H", style = TILE_NAME, maxLines = 1, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                Text(
                    hero.name,
                    style = TILE_NAME,
                    color = GoaTheme.colors.onParchment,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Never under 14 sp: the grid's columns are as wide as the longest name needs at that size.
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = tileNameFloor(),
                        maxFontSize = TILE_NAME.fontSize,
                        stepSize = TILE_NAME_STEP,
                    ),
                )
            }
            TileArtwork(item)
            TileText(item, sort, footnotes)
        }
    }
}

/**
 * Small grid tile (three per row on a phone, four or more on a wider screen): the art and the name only, in the
 * large tile's plate look. The name steps down to fit a narrow plate and ellipsizes past its floor; the title, stars
 * and roles are on the large tile, the hero page and in the tile's TalkBack label.
 */
@Composable
internal fun HeroSmallTile(
    item: HeroListItem,
    sort: CatalogSort,
    art: PlateArt,
    footnotes: TileFootnoteRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GoaPlateTheme {
        Column(
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = item.spokenLabel(sort) }
                .tilePlate(art)
                .clip(TileShape)
                .clickable(onClick = onClick)
                .padding(3.dp),
        ) {
            SharedTitlePlate(art.titlePlate, SmallPlate, Modifier.fillMaxWidth().zIndex(1f)) {
                Text("H", style = SMALL_NAME, maxLines = 1, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
                Text(
                    item.hero.name,
                    style = SMALL_NAME,
                    color = GoaTheme.colors.onParchment,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = SMALL_NAME_FLOOR,
                        maxFontSize = SMALL_NAME.fontSize,
                        stepSize = TILE_NAME_STEP,
                    ),
                )
            }
            TileArtwork(item, ratio = SMALL_PORTRAIT_RATIO, symbolSize = SMALL_SYMBOL)
            if (footnotes.match || footnotes.metric) {
                Column(Modifier.padding(start = 4.dp, end = 2.dp, top = 3.dp, bottom = 5.dp)) {
                    if (footnotes.match) MatchLine(item, lines = MATCH_LINES)
                    // Two lines on every tile, so a row's tiles are one height (TalkBack has the whole line).
                    TileFootnotes(item, sort, footnotes, lines = SMALL_METRIC_LINES)
                }
            }
        }
    }
}

@Composable
private fun TileArtwork(item: HeroListItem, ratio: Float = PORTRAIT_RATIO, symbolSize: Dp = GRID_SYMBOL) {
    val hero = item.hero
    Box(
        Modifier
            .overlapUp(PLATE_OVERLAP)
            .padding(horizontal = 1.dp)
            .fillMaxWidth()
            .aspectRatio(ratio),
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
                Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = PLATE_OVERLAP + 2.dp).size(symbolSize),
            )
        }
        if (!hero.verified) {
            UnverifiedBadge(compact = true, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp))
        }
    }
}

/**
 * Why a search found this hero, as one compact line under the portrait (never over the art): "Matches: Runic Dagger",
 * the card's name set as every card name is ([CardTitleSpan]); "Card text: …" when its text matched, "Matches an
 * alias" for a nickname. Wraps rather than cuts. TalkBack hears it in the tile's label. Nothing when the hero matched
 * by name, title or role, or nothing is searched; [lines] keeps the room a row's other tiles use.
 */
@Composable
internal fun MatchLine(item: HeroListItem, modifier: Modifier = Modifier, lines: Int = 1) {
    val card = item.hit?.card?.name
    val text = when {
        item.matchHint() == null -> null

        card == null -> AnnotatedString("Matches an alias")

        else -> buildAnnotatedString {
            append(if (item.hit?.field == MatchField.CARD_TEXT) "Card text: " else "Matches: ")
            withStyle(CardTitleSpan.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = MATCH_NAME_SIZE)) {
                append(card)
            }
        }
    }
    Text(
        text ?: AnnotatedString(""),
        style = MATCH_LINE,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = lines,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
private fun TileText(item: HeroListItem, sort: CatalogSort, footnotes: TileFootnoteRoom) {
    val hero = item.hero
    // Larger text wraps: every tile keeps the extra lines, so the plates of a row match.
    val lines = tileLines(LocalDensity.current.fontScale)
    Column(Modifier.padding(start = 6.dp, end = 4.dp, top = 3.dp)) {
        if (footnotes.match) MatchLine(item, Modifier.padding(bottom = 2.dp), lines = MATCH_LINES)
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
        // The stars lead the tile's text (14 dp, bronze); the roles under them are the quieter line.
        Row(
            Modifier.heightIn(min = STARS_LINE).padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ComplexityStars(hero.complexity, starSize = TILE_STAR)
            if (item.rangedLabel != null) ActionIcon(LookAction.RANGE, size = RANGED_ICON)
        }
        // Two roles, one a line at a readable size (the hero page and TalkBack have them all). Every tile keeps
        // both lines, so the tiles of a row stay one height.
        val traits = hero.roles.take(TILE_ROLES).joinToString("\n") { it.label }
        Text(
            traits,
            style = TILE_ROLE,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = TILE_ROLES,
            maxLines = TILE_ROLES,
            overflow = TextOverflow.Ellipsis,
        )
        TileFootnotes(item, sort, footnotes)
    }
}

/**
 * Which footnote rows the grid's tiles keep. A lazy grid doesn't stretch the tiles of a line, so when any tile
 * of the results has a sort metric or a search hint, every tile keeps the room for it.
 */
internal class TileFootnoteRoom(val metric: Boolean, val match: Boolean = false) {
    companion object {
        fun of(heroes: List<HeroListItem>, sort: CatalogSort) = TileFootnoteRoom(
            metric = heroes.any { it.metricLine(sort) != null },
            match = heroes.any { it.matchHint() != null },
        )
    }
}

@Composable
private fun TileFootnotes(
    item: HeroListItem,
    sort: CatalogSort,
    room: TileFootnoteRoom = NO_ROOM,
    lines: Int = Int.MAX_VALUE,
) {
    val metric = item.metricLine(sort)
    if (metric != null || room.metric) {
        Text(
            metric.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            minLines = if (lines == Int.MAX_VALUE) 1 else lines,
            maxLines = lines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/**
 * List row: the portrait fills the row's left side and fades into the plate, then the name, the title, the stars
 * (with the game's Range icon for a ranged hero) and two roles on one quiet line, as on the large tile. The product,
 * the complexity in words and every role are on the hero page and in the row's TalkBack label.
 */
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
        Box(
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = item.spokenLabel(sort) }
                .tilePlate(art)
                .clip(TileShape)
                .clickable(onClick = onClick)
                .heightIn(min = LIST_ROW_MIN),
        ) {
            // As tall as the row's text, whatever the font scale: the art follows, cropped.
            Box(Modifier.matchParentSize()) { ListArt(item, Modifier.width(LIST_ART).fillMaxHeight().padding(3.dp)) }
            Row(
                Modifier.padding(start = LIST_ART + Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        hero.name,
                        style = GoaTextStyles.sectionHead,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        hero.title,
                        style = TILE_TITLE,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier.heightIn(min = STARS_LINE).padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ComplexityStars(hero.complexity, starSize = TILE_STAR)
                        if (item.rangedLabel != null) ActionIcon(LookAction.RANGE, size = RANGED_ICON)
                        if (!hero.verified) UnverifiedBadge(compact = true)
                    }
                    if (hero.roles.isNotEmpty()) {
                        Text(
                            hero.roles.take(TILE_ROLES).joinToString(" · ") { it.label },
                            style = TILE_ROLE,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // One line at 100 %; large text wraps the second role rather than cutting it.
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TileFootnotes(item, sort)
                    if (item.matchHint() != null) MatchLine(item, Modifier.padding(top = 2.dp))
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** The list row's portrait: cropped to the row, the deck symbol at its corner, its right edge fading into the plate. */
@Composable
private fun ListArt(item: HeroListItem, modifier: Modifier) {
    val hero = item.hero
    val plate = GoaTheme.colors.plate
    Box(modifier) {
        HeroPortrait(
            heroId = hero.id,
            portrait = hero.portrait,
            contentDescription = null,
            shape = LIST_ART_SHAPE,
            framed = false,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val fade = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        1f to plate,
                        startX = size.width - LIST_ART_FADE.toPx(),
                        endX = size.width,
                    )
                    val center = Offset(SYMBOL_CENTER_X.toPx() - 2.dp.toPx(), SYMBOL_CENTER_Y.toPx() - 6.dp.toPx())
                    val shade = Brush.radialGradient(
                        listOf(SYMBOL_SHADE, Color.Transparent),
                        center = center,
                        radius = SYMBOL_SHADE_RADIUS.toPx(),
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(fade)
                        if (hero.symbol != null) drawCircle(shade, SYMBOL_SHADE_RADIUS.toPx(), center)
                    }
                },
        )
        if (hero.symbol != null) {
            HeroSymbol(hero.id, hero.symbol, Modifier.align(Alignment.TopStart).padding(3.dp).size(LIST_SYMBOL))
        }
    }
}

/** Width / height of the portrait on a tile: near the art's own 1.09, a little cropped so three rows fit a phone. */
private const val PORTRAIT_RATIO = 1.14f

/** Lines a tile keeps for its title and roles: they wrap at 1.15 (three columns) and from 1.8 (two columns). */
private fun tileLines(fontScale: Float): Int = if (fontScale in 1.1f..1.2f || fontScale > 1.6f) 2 else 1

private const val TILE_ROLES = 2

/** Lines every grid tile keeps for a search's match line when any tile has one: a long card name wraps once. */
private const val MATCH_LINES = 2
private val NO_ROOM = TileFootnoteRoom(metric = false)
private val STARS_LINE = 20.dp
private val TILE_STAR = 14.dp
private val RANGED_ICON = 16.dp

private val PORTRAIT_SHAPE = RoundedCornerShape(2.dp)

/** How far the portrait runs up under the title plate. */
private val PLATE_OVERLAP = 5.dp
private val GRID_SYMBOL = 27.dp
private val LIST_SYMBOL = 24.dp
private val SMALL_SYMBOL = 22.dp
private const val SMALL_METRIC_LINES = 2

/** The small tile's portrait: near the art's own 1.09, so a narrow tile still shows the figure whole. */
private const val SMALL_PORTRAIT_RATIO = 1.05f
private val LIST_ROW_MIN = 96.dp
private val LIST_ART = 108.dp

/** How far into the list row's portrait its right edge fades into the plate. */
private val LIST_ART_FADE = 28.dp
private val LIST_ART_SHAPE = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)
private val SYMBOL_CENTER_X = 16.dp
private val SYMBOL_CENTER_Y = 20.dp
private val SYMBOL_SHADE_RADIUS = 24.dp
private val SYMBOL_SHADE = Color(0x8C000000)
private val TILE_ROLE @Composable get() =
    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = 0.sp)
private val MATCH_LINE @Composable get() =
    MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp)
private val MATCH_NAME_SIZE = 15.sp
private val TILE_NAME = GoaTextStyles.plateTitle.copy(fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.sp)

/** The small tile's name: a size under the large tile's, stepping down to [SMALL_NAME_FLOOR] for a long one. */
private val SMALL_NAME = TILE_NAME.copy(fontSize = 14.sp, lineHeight = 17.sp)
private val SMALL_NAME_FLOOR = 10.sp

/** The least a tile's name is set at: a long one ("Silverarrow") steps down to it, never further. */
private val TILE_NAME_FLOOR = 14.sp

/**
 * With large text (above 130 %) the floor is held at 19 dp, a third over the 14 sp one at 100 %, so two columns
 * stay on a phone at 200 % (360 dp wide too) rather than one tile a row; every name is still larger than at 100 %.
 */
private val LARGE_TEXT_NAME_FLOOR = 19.dp
private const val LARGE_TEXT = 1.3f

@Composable
private fun tileNameFloor(): TextUnit {
    val density = LocalDensity.current
    return if (density.fontScale > LARGE_TEXT) with(density) { LARGE_TEXT_NAME_FLOOR.toSp() } else TILE_NAME_FLOOR
}
private val TILE_NAME_STEP = 0.25.sp

/** Around a tile's name: the plate's two rounded ends and the tile's own inset. */
private val TILE_NAME_CHROME = 24.dp
private val TILE_TITLE = GoaTextStyles.printedSmall.copy(
    fontStyle = FontStyle.Italic,
    fontSize = 15.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.sp,
)

/**
 * The narrowest a grid tile may be so the longest name of [heroes] fits its plate at its floor on one line,
 * at the current font scale: the grid's columns follow (three on a 411 dp phone at 100 %, fewer with larger text or a
 * narrower phone). Only grows while the screen lives, so filtering never changes the column count under the player.
 */
@Composable
internal fun rememberTileNameWidth(heroes: List<HeroListItem>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val floor = tileNameFloor()
    val widest = remember(heroes, density, floor) {
        val style = TILE_NAME.copy(fontSize = floor)
        val px = heroes.maxOfOrNull { measurer.measure(it.hero.name, style, maxLines = 1).size.width } ?: 0
        with(density) { px.toDp() } + TILE_NAME_CHROME
    }
    val most = remember(density) { mutableStateOf(0.dp) }
    if (widest > most.value) most.value = widest
    return most.value
}
