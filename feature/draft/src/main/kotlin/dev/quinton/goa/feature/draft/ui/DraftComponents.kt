package dev.quinton.goa.feature.draft.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.DoneMark
import dev.quinton.goa.core.designsystem.component.DoneTick
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateShape
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.TintTag
import dev.quinton.goa.core.designsystem.component.bleedHorizontally
import dev.quinton.goa.core.designsystem.component.fadingHorizontalScroll
import dev.quinton.goa.core.designsystem.component.mutedOutline
import dev.quinton.goa.core.designsystem.component.selectableChipColors
import dev.quinton.goa.core.designsystem.component.selectableSegmentColors
import dev.quinton.goa.core.designsystem.component.slatePlate
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.board.DraftBoardText
import kotlin.math.roundToInt

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
 * [onLongClick] opens the hero peek; a tile without [onClick] then opens it on tap too. A tile with both
 * shows the peek as a visible inspect button in its corner (48 dp, a named TalkBack action too), so
 * looking at a hero is never a hidden gesture and never the same tap as choosing it. The draft board's pool
 * passes only [onClick], which opens the peek with the Pick or Ban in it, and names that tap in [clickLabel].
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
    clickLabel: String? = null,
) {
    GoaPlateTheme { HeroTileBody(hero, state, onClick, modifier, selected, onLongClick, compact, clickLabel) }
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
    clickLabel: String?,
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
            selected -> MaterialTheme.colorScheme.primary
            state is TileState.Picked -> teamColor(state.team)
            else -> TILE_EDGE
        },
        animationSpec = tween(if (reduced) 0 else DraftMotion.SHORT),
        label = "tileBorder",
    )
    val stateText = state.description()
    val emphasised = selected || state is TileState.Picked
    val inspect = onLongClick?.takeIf { onClick != null }
    Column(
        modifier
            .clip(TILE_SHAPE)
            .background(GoaTheme.colors.plate)
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

                    onClick != null -> Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = clickLabel ?: "Choose ${hero.name}",
                        onClick = onClick,
                    )

                    else -> Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = hero.name
                stateDescription = stateText
                if (inspect != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(inspectLabel(hero)) {
                            inspect()
                            true
                        },
                    )
                }
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
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(2.dp),
                )
            } else if (inspect != null) {
                InspectButton(hero, inspect, Modifier.align(Alignment.TopEnd).offset(x = TILE_INSET, y = -TILE_INSET))
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

/**
 * The tile's inspect button: a small ⓘ disc in the art's corner, inside a 48 dp target that reaches to
 * the tile's edge. Its own tap, apart from the tile's, which chooses the hero (and asks to confirm).
 */
@Composable
private fun InspectButton(hero: HeroInfo, onInspect: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = inspectLabel(hero), onClick = onInspect)
            .semantics { contentDescription = inspectLabel(hero) },
        contentAlignment = Alignment.TopEnd,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(INSPECT_INSET)
                .size(INSPECT_DISC)
                .background(CHIP_GROUND, CircleShape)
                .border(1.dp, INSPECT_EDGE, CircleShape)
                .padding(2.dp),
        )
    }
}

internal fun inspectLabel(hero: HeroInfo) = "Inspect ${hero.name}: roles, stats and starting cards"

private val INSPECT_DISC = 24.dp
private val INSPECT_INSET = 5.dp
private val INSPECT_EDGE = Color(0xFF9FB1BD)

/** The name on a parchment plate while the hero is in play; plain on the dark plate once out of it. */
@Composable
private fun TileName(name: String, state: TileState, compact: Boolean) {
    val parchment = state == TileState.Available || state is TileState.Picked
    // Cinzel is wide: past the large-text point the plate keeps Inter, so a name stays whole.
    val large = LocalDensity.current.fontScale > LARGE_FONT_SCALE
    val style = when {
        large -> MaterialTheme.typography.labelMedium

        // Read at arm's length: as large as the tile allows; only the longest names (Silverarrow) step down.
        compact -> GoaTextStyles.plateCap.copy(letterSpacing = 0.sp, fontSize = TILE_NAME_MAX, lineHeight = 18.sp)

        else -> GoaTextStyles.plateTitle.copy(fontSize = 14.sp, lineHeight = 18.sp)
    }
    val colors = GoaTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (parchment) Modifier.background(colors.parchment, TILE_ART_SHAPE) else Modifier)
            .padding(horizontal = 1.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Unseen, at the full size: a shrunk name keeps its plate, and so its tile, the height of the others.
        Text("H", style = style, maxLines = 1, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        Text(
            text = name,
            style = style,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            textDecoration = if (state is TileState.Banned) TextDecoration.LineThrough else null,
            color = if (parchment) colors.onParchment else MaterialTheme.colorScheme.onSurfaceVariant,
            autoSize = if (compact && !large) {
                TextAutoSize.StepBased(minFontSize = TILE_NAME_MIN, maxFontSize = TILE_NAME_MAX, stepSize = 0.5.sp)
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val TILE_NAME_MAX = 15.sp
private val TILE_NAME_MIN = 10.sp

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
 * A sequence as numbered steps, each a run of one side's bans or picks with its count ("4 · ◆ Red picks 2"; the
 * shared [DraftStep] numbering). Bans are outlined, picks filled. On the board ([current], a plan index) the step
 * in play is bold with a heavier edge; finished steps step back by losing their fill and team edge (a tick, a muted
 * edge, the words in the readable muted token, never faded: DESIGN.md "Muted states"); a sideways strip keeps the
 * step before, the step in play and the next in view. [named] false shows a side by its mark only.
 */
@Composable
internal fun SequenceStrip(
    tokens: List<StepToken>,
    modifier: Modifier = Modifier,
    current: Int? = null,
    wrap: Boolean = true,
    named: Boolean = true,
    /** The parent column's side padding, which a sideways strip ([wrap] false) runs out to. */
    gutter: Dp = Spacing.screen,
) {
    val steps = remember(tokens) { tokens.steps() }
    val active = current?.let { slot -> steps.indexOfFirst { slot in it } }?.takeIf { it >= 0 }
    val description = remember(steps, active) {
        val now = active?.let { "Now ${steps[it].spoken}. " }.orEmpty()
        now + "Sequence of ${steps.size} steps: " + steps.joinToString("; ") { it.spoken }
    }
    val semantics = Modifier.clearAndSetSemantics { contentDescription = description }
    fun state(i: Int) = when {
        active == null -> StepState.UPCOMING
        i < active -> StepState.DONE
        i == active -> StepState.ACTIVE
        else -> StepState.UPCOMING
    }
    if (wrap) {
        FlowRow(
            modifier.then(semantics),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) { steps.forEachIndexed { i, step -> StepChipView(step, state(i), named) } }
    } else {
        val strip = rememberScrollState()
        val starts = remember(steps) { IntArray(steps.size) }
        // The step before the one in play starts on the gutter, so it, the step in play and the next stay in view.
        LaunchedEffect(active, steps) {
            if (active == null) return@LaunchedEffect
            withFrameNanos { } // the chips' places are known once they are laid out
            strip.animateScrollTo(starts[(active - 1).coerceAtLeast(0)])
        }
        // The rail runs to the screen's edges and its chips start on the gutter: a cut chip is cut at the edge,
        // under the fade and its chevron, never mid-column.
        Row(
            modifier.then(semantics).bleedHorizontally(gutter).fadingHorizontalScroll(strip, gutter),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            steps.forEachIndexed { i, step ->
                StepChipView(
                    step,
                    state(i),
                    named,
                    Modifier.onGloballyPositioned { starts[i] = it.positionInParent().x.roundToInt() },
                )
            }
        }
    }
}

private enum class StepState { DONE, ACTIVE, UPCOMING }

/** The step's countdown: teal digits on a dark face, red in overtime ("+0:05"). The banner's and the peek's. */
@Composable
internal fun StepClock(remaining: Int, modifier: Modifier = Modifier) {
    val over = remaining < 0
    val ink = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(CLOCK_FACE)
            .border(1.dp, ink, RoundedCornerShape(7.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .clearAndSetSemantics { contentDescription = DraftBoardText.spokenStepClock(remaining) },
    ) {
        Text(
            DraftBoardText.stepClock(remaining),
            style = GoaTextStyles.timerDigitsSmall.copy(fontSize = 22.sp, lineHeight = 28.sp),
            color = ink,
        )
    }
}

private val CLOCK_FACE = Color(0xFF060C12)

@Composable
private fun StepChipView(step: DraftStep, state: StepState, named: Boolean, modifier: Modifier = Modifier) {
    val accent = step.team?.let { teamColor(it) } ?: MaterialTheme.colorScheme.primary
    val isBan = step.action == DraftAction.BAN
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .background(
                when {
                    state == StepState.DONE -> Color.Transparent
                    state == StepState.ACTIVE -> accent.copy(alpha = 0.28f)
                    isBan -> Color.Transparent
                    else -> accent.copy(alpha = 0.12f)
                },
                shape,
            )
            .then(
                if (state == StepState.DONE) {
                    Modifier.mutedOutline(shape)
                } else {
                    Modifier.border(if (state == StepState.ACTIVE) 2.dp else 1.dp, accent.copy(alpha = 0.85f), shape)
                },
            )
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == StepState.DONE) {
            DoneMark(size = DoneTick)
            Spacer(Modifier.width(2.dp))
        }
        TeamText(
            text = (if (step.hidden) "🔒 " else "") + step.label(named),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (state == StepState.ACTIVE) FontWeight.Bold else FontWeight.Medium,
            color = when (state) {
                StepState.DONE -> GoaTheme.colors.textMuted
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
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
            .clip(PlateShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        // Always inside a section plate: its brackets are the outer plate's alone.
        brackets = false,
        contentPadding = PaddingValues(0.dp),
        selected = selected,
    ) {
        Row(Modifier.padding(start = Spacing.xs, end = Spacing.md, top = Spacing.md, bottom = Spacing.md)) {
            SelectionRadio(selected, Modifier.padding(end = Spacing.xs))
            ChoiceCardBody(title, body, selected, badge, extra)
        }
    }
}

@Composable
private fun RowScope.ChoiceCardBody(
    title: String,
    body: String,
    selected: Boolean,
    badge: String?,
    extra: (@Composable () -> Unit)?,
) {
    Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            // A tint, not an outline: the choice is a row of its section's plate (DESIGN.md "Framing levels").
            if (badge != null) {
                TintTag(badge, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
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

/*
 * Selection colours: the design system's one selected look (bronze wash and edge; chips add
 * `border = selectableChipBorder(selected)` and `leadingIcon = selectedCheck(selected)`).
 */
@Composable
internal fun goaFilterChipColors(): SelectableChipColors = selectableChipColors()

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
internal fun goaSegmentedColors(): SegmentedButtonColors = selectableSegmentColors()

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

/** The dark theme's bronze, for an edge drawn outside a plate's theme; a picked plate is `GoaPlate(selected =)`. */
internal val PLATE_BRONZE = Color(0xFFC9A35B)

/** The radio of a selectable plate: bronze, and only a mark (the plate carries the click and the role). */
@Composable
internal fun SelectionRadio(selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    RadioButton(
        selected = selected,
        onClick = null,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(selectedColor = GoaTheme.colors.selection),
        modifier = modifier,
    )
}

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
