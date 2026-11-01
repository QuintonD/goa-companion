package dev.quinton.goa.feature.draft.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateGround
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team

/** Motion tokens (docs/DESIGN.md: 200–300 ms emphasised). */
internal object DraftMotion {
    const val SHORT = 200
    const val MEDIUM = 300
}

/** True when the system "Remove animations" setting is on; animations then snap. */
@Composable
internal fun rememberReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * A hero tile as on the Heroes grid, in small: the portrait with the deck symbol, the name on a
 * parchment plate, on the dark plate. Flat rather than slate, so a full roster stays cheap to scroll.
 * Out of play it loses the parchment; banned it is crossed out and dimmed, never colour alone.
 * [onLongClick] opens the hero peek; a tile without [onClick] then opens it on tap too.
 */
@Composable
internal fun HeroTile(
    hero: HeroInfo,
    state: TileState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    GoaPlateTheme { HeroTileBody(hero, state, onClick, modifier, selected, onLongClick, compact) }
}

@Composable
private fun HeroTileBody(
    hero: HeroInfo,
    state: TileState,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    selected: Boolean,
    onLongClick: (() -> Unit)?,
    compact: Boolean,
) {
    val reduced = rememberReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = when (state) {
            TileState.Available -> 1f
            TileState.Inactive -> 0.45f
            is TileState.Banned -> 0.38f
            is TileState.Picked -> 0.6f
        },
        animationSpec = tween(if (reduced) 0 else DraftMotion.MEDIUM),
        label = "tileAlpha",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.secondary
            state is TileState.Picked -> teamColor(state.team)
            else -> TILE_EDGE
        },
        animationSpec = tween(if (reduced) 0 else DraftMotion.SHORT),
        label = "tileBorder",
    )
    val stateText = state.description()
    val emphasised = selected || state is TileState.Picked
    Column(
        modifier
            .clip(TILE_SHAPE)
            .background(PlateGround)
            .border(if (emphasised) 2.dp else 1.dp, borderColor, TILE_SHAPE)
            .then(
                when {
                    onLongClick != null -> Modifier.combinedClickable(
                        role = Role.Button,
                        onClickLabel = when {
                            onClick == null -> "Show ${hero.name}"
                            state == TileState.Inactive -> "Why ${hero.name} can't be chosen"
                            else -> "Choose ${hero.name}"
                        },
                        onLongClickLabel = "Show ${hero.name}",
                        onLongClick = onLongClick,
                        onClick = onClick ?: onLongClick,
                    )

                    onClick != null ->
                        Modifier.clickable(role = Role.Button, onClickLabel = "Choose ${hero.name}", onClick = onClick)

                    else -> Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = hero.name
                stateDescription = stateText
            }
            .padding(TILE_INSET),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
            // The cross stays bright over the dimmed art: it is what says "banned" at a glance.
            HeroPortrait(
                heroId = hero.id,
                portrait = hero.portrait,
                contentDescription = null,
                shape = TILE_ART_SHAPE,
                framed = false,
                modifier = Modifier
                    .matchParentSize()
                    .then(if (state is TileState.Banned) Modifier.crossedOut() else Modifier)
                    .alpha(alpha),
            )
            if (hero.symbol != null) {
                HeroSymbol(
                    hero.id,
                    hero.symbol,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .fillMaxWidth(TILE_SYMBOL)
                        .aspectRatio(1f)
                        .alpha(alpha),
                )
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        .padding(2.dp),
                )
            }
            when (state) {
                // Small: the cross already says "banned"; the chip keeps the team's mark and the ban's symbol.
                is TileState.Banned -> StateChip(
                    text = "Banned".takeUnless { compact },
                    team = state.team,
                    icon = true,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp),
                )

                is TileState.Picked -> StateChip(
                    text = teamName(state.team),
                    team = state.team,
                    icon = false,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp),
                )

                else -> Unit
            }
        }
        Spacer(Modifier.height(TILE_INSET))
        TileName(hero.name, state, compact)
    }
}

/** The name on a parchment plate while the hero is in play; plain on the dark plate once out of it. */
@Composable
private fun TileName(name: String, state: TileState, compact: Boolean) {
    val parchment = state == TileState.Available || state is TileState.Picked
    // Cinzel is wide: past the large-text point the plate keeps Inter, so a name stays whole.
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val style = when {
        large -> MaterialTheme.typography.labelMedium

        // The longest names (Silverarrow) step down so the whole name still fits a dense tile.
        compact -> GoaTextStyles.plateCap.copy(
            letterSpacing = 0.sp,
            fontSize = if (name.length > LONG_NAME) 9.5.sp else GoaTextStyles.plateCap.fontSize,
        )

        else -> GoaTextStyles.plateTitle.copy(fontSize = 14.sp, lineHeight = 18.sp)
    }
    val colors = GoaTheme.colors
    Text(
        text = name,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        textDecoration = if (state is TileState.Banned) TextDecoration.LineThrough else null,
        color = if (parchment) colors.onParchment else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (parchment) Modifier.background(colors.parchment, TILE_ART_SHAPE) else Modifier)
            .padding(horizontal = 1.dp, vertical = 2.dp),
    )
}

/** Crossed out, in red with a dark edge so it shows on any art: banned without relying on colour. */
internal fun Modifier.crossedOut(): Modifier = drawWithContent {
    drawContent()
    val a = size.width * 0.14f
    val b = size.width - a
    val top = size.height * 0.14f
    val bottom = size.height - top
    listOf(CROSS_EDGE to 5.dp, CROSS to 3.dp).forEach { (color, width) ->
        drawLine(color, Offset(a, top), Offset(b, bottom), strokeWidth = width.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(b, top), Offset(a, bottom), strokeWidth = width.toPx(), cap = StrokeCap.Round)
    }
}

private val TILE_SHAPE = RoundedCornerShape(6.dp)
private val TILE_ART_SHAPE = RoundedCornerShape(3.dp)
private val TILE_INSET = 3.dp
private val TILE_EDGE = Color(0xFF5D6B70)
private val CROSS = Color(0xFFFF6B6B)
private val CROSS_EDGE = Color(0xE6060C12)
private const val TILE_SYMBOL = 0.32f
private const val LONG_NAME = 9

@Composable
private fun StateChip(text: String?, team: Team, icon: Boolean, modifier: Modifier = Modifier) {
    val color = teamColor(team)
    Row(
        modifier
            .background(CHIP_GROUND, RoundedCornerShape(50))
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = Spacing.xs, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The team's mark as well as its colour; a banned chip has the ban's symbol too.
        TeamMark(team, markSize = 9.dp)
        if (icon) {
            Spacer(Modifier.width(1.dp))
            Icon(Icons.Filled.Block, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
        }
        if (text != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val CHIP_GROUND = Color(0xE6060C12)

/** A concealed (hidden) pick: shown as a lock until the hidden run ends. */
@Composable
internal fun ConcealedTile(team: Team, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, teamColor(team), MaterialTheme.shapes.medium)
            .semantics { contentDescription = "Hidden pick by ${teamName(team)}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = teamColor(team))
    }
}

/**
 * Compact sequence preview: "Ban A · Pick A · …" chips. Bans are outlined, picks filled; the
 * [current] step is highlighted and finished steps fade.
 */
@Composable
internal fun SequenceStrip(
    tokens: List<StepToken>,
    modifier: Modifier = Modifier,
    current: Int? = null,
    wrap: Boolean = true,
) {
    val description = tokens.spoken()
    val content: @Composable () -> Unit = {
        tokens.forEachIndexed { i, token -> StepChipView(token, index = i, current = current) }
    }
    val semantics = Modifier.semantics(mergeDescendants = true) { contentDescription = description }
    if (wrap) {
        FlowRow(
            modifier.then(semantics),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { content() }
    } else {
        Row(
            modifier.then(semantics).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { content() }
    }
}

@Composable
private fun StepChipView(token: StepToken, index: Int, current: Int?) {
    val accent = token.team?.let { teamColor(it) } ?: MaterialTheme.colorScheme.primary
    val done = current != null && index < current
    val active = current == index
    val isBan = token.action == DraftAction.BAN
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = (if (token.hidden) "🔒 " else "") + token.label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (isBan &&
            !active
        ) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .alpha(if (done) 0.5f else 1f)
            .background(
                when {
                    active -> accent.copy(alpha = 0.28f)
                    isBan -> Color.Transparent
                    else -> accent.copy(alpha = 0.12f)
                },
                shape,
            )
            .border(if (active) 2.dp else 1.dp, accent.copy(alpha = if (done) 0.3f else 0.8f), shape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/** Section card on the dark plate, used across draft screens. [titleTeam] heads it with a team's mark. */
@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleTeam: Team? = null,
    content: @Composable () -> Unit,
) {
    GoaPlate(modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (titleTeam != null) {
                    TeamMark(titleTeam, markSize = 14.dp)
                    Spacer(Modifier.width(Spacing.xs))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = GoaTextStyles.sectionHead,
                        color = titleTeam?.let { teamColor(it) } ?: GoaTheme.colors.heading,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(Spacing.md))
            content()
        }
    }
}

/** Large tappable selectable row/card with a title and explanation (≥ 48 dp). */
@Composable
internal fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    GoaPlate(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .plateEdge(if (selected) PLATE_BRONZE else null)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = Spacing.sm, vertical = 2.dp),
                    )
                }
            }
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra?.let {
                Spacer(Modifier.height(Spacing.sm))
                it()
            }
        }
    }
}

/*
 * Selection colours in the bronze palette. The shared scheme leaves Material's default
 * (purple-grey) secondaryContainer, so selected chips/segments set their colours explicitly.
 */
@Composable
internal fun goaFilterChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
    selectedTrailingIconColor = MaterialTheme.colorScheme.primary,
)

/** Filter chip in a team's colour (Tie Breaker "Red shows" / "Blue shows"). */
@Composable
internal fun teamFilterChipColors(team: Team): SelectableChipColors {
    val color = teamColor(team)
    return FilterChipDefaults.filterChipColors(
        labelColor = color,
        selectedContainerColor = color.copy(alpha = 0.18f),
        selectedLabelColor = color,
        selectedLeadingIconColor = color,
    )
}

@Composable
internal fun goaSegmentedColors(): SegmentedButtonColors = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    activeContentColor = MaterialTheme.colorScheme.primary,
    activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
    inactiveContainerColor = Color.Transparent,
    inactiveBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
)

@Composable
internal fun goaTonalButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
    contentColor = MaterialTheme.colorScheme.secondary,
)

@Composable
internal fun goaTonalIconColors(): IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    contentColor = MaterialTheme.colorScheme.primary,
)

/** An accent edge on a plate: the team on the clock, the selected option. Null draws none. */
internal fun Modifier.plateEdge(color: Color?, width: Dp = 2.dp, shape: Shape = PlateShape): Modifier =
    if (color == null) this else border(width, color, shape)

/** Plates are dark in both themes: a bronze edge picked outside one is the dark theme's. */
internal val PLATE_BRONZE = Color(0xFFC9A35B)

/**
 * The bar pinned under a screen's content, on the dark plate; its contents are themed dark.
 */
@Composable
internal fun PlateBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GoaPlateTheme {
        Column(modifier.fillMaxWidth().slatePlate(RectangleShape, brackets = false), content = content)
    }
}

/** Centered full-size loading/blank helper. */
@Composable
internal fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
