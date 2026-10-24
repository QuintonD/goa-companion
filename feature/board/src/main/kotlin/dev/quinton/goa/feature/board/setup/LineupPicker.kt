package dev.quinton.goa.feature.board.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.hero

/*
 * Picking a line-up, shared by New board and New game: both teams in pick order (tap a pick to move,
 * switch or remove it), the "Adding to Red | Blue" switch and the hero grid sized to fit.
 */

/**
 * What the line-up picker shows. [heroes]: the heroes to pick from (the owned products' unless
 * showing all); [unavailable]: heroes shown but not pickable, with the reason read out and shown
 * under them (New game: "Coming later", heroes the rules engine can't play yet).
 */
@Immutable
data class LineupPicker(
    val form: NewBoardForm,
    val heroes: List<BoardHero>,
    val heroById: Map<String, BoardHero>,
    val hiddenHeroes: Int,
    val ownedOnly: Boolean,
    val unavailable: Map<String, String> = emptyMap(),
) {
    fun team(team: Team): List<BoardHero> = form.team(team).map(heroById::hero)

    val full: Boolean get() = form.red.size >= NewBoardUiState.MAX_PER_TEAM &&
        form.blue.size >= NewBoardUiState.MAX_PER_TEAM
}

/**
 * The heroes to pick from. In portrait the columns are chosen so every hero fits without
 * scrolling ([heroGridColumns]); with large text, fewer and bigger tiles that scroll.
 */
@Composable
internal fun ColumnScope.HeroGrid(
    shown: List<BoardHero>,
    state: LineupPicker,
    onAction: (NewBoardAction) -> Unit,
    large: Boolean,
    start: Dp,
    end: Dp,
) {
    val density = LocalDensity.current
    val label = with(density) { MaterialTheme.typography.labelSmall.lineHeight.toDp() } + LABEL_GAP
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        if (large) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TILE_LARGE),
                contentPadding = PaddingValues(start = start, end = end, bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                heroTiles(shown, state, onAction, pad = 0.dp)
            }
        } else {
            // Each cell is its tile plus half the gap on every side, all of it the tap target: a
            // 42 dp portrait still takes a 48 dp tap.
            val half = TILE_GAP / 2
            val extra = if (state.ownedOnly) OWNED_ROW + Spacing.sm else 0.dp
            val columns = heroGridColumns(
                count = shown.size.coerceAtLeast(1),
                width = (maxWidth - start - end + TILE_GAP).value,
                height = (maxHeight - Spacing.md).value,
                label = label.value,
                spacing = 0f,
                extra = extra.value,
                minTile = (TILE_MIN + TILE_GAP).value,
                maxTile = (TILE_MAX + TILE_GAP).value,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = start - half, end = end - half, bottom = Spacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                heroTiles(shown, state, onAction, pad = half)
            }
        }
    }
}

private fun LazyGridScope.heroTiles(
    heroes: List<BoardHero>,
    state: LineupPicker,
    onAction: (NewBoardAction) -> Unit,
    pad: Dp,
) {
    items(heroes, key = { it.id }) { hero -> HeroTile(hero, state, onAction, pad) }
    if (heroes.isEmpty()) {
        item(key = "none", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "No hero matches this search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
    if (state.ownedOnly) {
        item(key = "owned", span = { GridItemSpan(maxLineSpan) }) {
            val all = state.form.showAll
            TextButton(
                onClick = { onAction(NewBoardAction.ShowAll(!all)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    if (all) "Only heroes you own" else "Show all heroes (${state.hiddenHeroes} you don't own)",
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** "Adding to": the team the next tapped hero joins. */
@Composable
internal fun TeamSwitch(filling: Team, onPick: (Team) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.semantics { contentDescription = "Adding to" }) {
        Team.entries.forEachIndexed { i, team ->
            val color = teamColor(team)
            SegmentedButton(
                selected = filling == team,
                onClick = { onPick(team) },
                shape = SegmentedButtonDefaults.itemShape(i, Team.entries.size),
                icon = { TeamMark(team, markSize = 10.dp) },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = color.copy(alpha = 0.18f),
                    activeBorderColor = color,
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                    inactiveBorderColor = GoaTheme.colors.engravedBorder,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = if (filling == team) "Next pick" else "" },
            ) {
                Text(BoardText.team(team), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun Teams(state: LineupPicker, onAction: (NewBoardAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Team.entries.forEach { team -> TeamRow(state, team, onAction) }
    }
}

/** One team's heroes in pick order; tap the row to pick for this team next. */
@Composable
private fun TeamRow(state: LineupPicker, team: Team, onAction: (NewBoardAction) -> Unit) {
    val color = teamColor(team)
    val filling = state.form.filling == team
    val heroes = state.team(team)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (filling) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (filling) 2.dp else 1.dp, if (filling) color else GoaTheme.colors.engravedBorder),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = filling, role = Role.RadioButton) { onAction(NewBoardAction.Fill(team)) }
            .semantics { stateDescription = if (filling) "Next pick" else "" },
    ) {
        Row(Modifier.padding(start = Spacing.md, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            TeamMark(team, markSize = scaledIconSize(11.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(
                BoardText.team(team),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 40.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            if (heroes.isEmpty()) {
                Text(
                    if (filling) "Tap heroes below" else "Tap here to pick for ${BoardText.team(team)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Every pick stays in sight: the chips share the row, a long name gives way first.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                ) {
                    heroes.forEachIndexed { index, hero ->
                        key(hero.id) {
                            SlotChip(hero, team, index, heroes.size, state, onAction, Modifier.weight(1f, fill = false))
                        }
                    }
                }
            }
            // Spelled out while the team is empty; once it has heroes the border marks it (and the
            // "Adding to" switch), so the words never push a pick out of the row.
            if (filling && heroes.isEmpty() && LocalDensity.current.fontScale <= LARGE_FONT) {
                Text(
                    "Next pick",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}

/** A hero in a team: its pick number and name; tap for Move earlier / later, Switch team, Remove. */
@Composable
private fun SlotChip(
    hero: BoardHero,
    team: Team,
    index: Int,
    count: Int,
    state: LineupPicker,
    onAction: (NewBoardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val color = teamColor(team)
    Box(modifier) {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, color.copy(alpha = 0.8f)),
            modifier = Modifier
                .heightIn(min = 40.dp)
                .semantics { contentDescription = "Pick ${index + 1}: ${hero.name}. Change" },
        ) {
            Row(
                Modifier.padding(start = 4.dp, end = Spacing.sm, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroPortrait(
                    hero.id,
                    hero.portrait,
                    null,
                    Modifier.size(scaledIconSize(30.dp, maxScale = 1.4f)).border(1.5.dp, color, CircleShape),
                    shape = CircleShape,
                    framed = false,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    hero.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (index > 0) {
                DropdownMenuItem(
                    text = { Text("Move earlier") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                    onClick = {
                        menu = false
                        onAction(NewBoardAction.Reorder(hero.id, -1))
                    },
                )
            }
            if (index < count - 1) {
                DropdownMenuItem(
                    text = { Text("Move later") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                    onClick = {
                        menu = false
                        onAction(NewBoardAction.Reorder(hero.id, 1))
                    },
                )
            }
            if (state.form.team(team.opponent).size < NewBoardUiState.MAX_PER_TEAM) {
                DropdownMenuItem(
                    text = { Text("Move to ${BoardText.team(team.opponent)}") },
                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, null) },
                    onClick = {
                        menu = false
                        onAction(NewBoardAction.SwitchTeam(hero.id))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                onClick = {
                    menu = false
                    onAction(NewBoardAction.Remove(hero.id))
                },
            )
        }
    }
}

@Composable
private fun HeroTile(hero: BoardHero, state: LineupPicker, onAction: (NewBoardAction) -> Unit, pad: Dp) {
    val team = when (hero.id) {
        in state.form.red -> Team.RED
        in state.form.blue -> Team.BLUE
        else -> null
    }
    val pick = team?.let { state.form.team(it).indexOf(hero.id) + 1 }
    val filling = state.form.filling
    val full = team == null && state.full
    // Shown but not pickable (New game: a hero the engine can't play yet).
    val unavailable = state.unavailable[hero.id]?.takeIf { team == null }
    val description = when {
        team != null -> "${hero.name}, ${BoardText.team(team)} pick $pick"
        unavailable != null -> "${hero.name}, $unavailable"
        else -> hero.name
    }
    val action = when {
        team != null -> "Remove from ${BoardText.team(team)}"
        full || unavailable != null -> null
        state.form.team(filling).size < NewBoardUiState.MAX_PER_TEAM -> "Add to ${BoardText.team(filling)}"
        else -> "Add to ${BoardText.team(filling.opponent)}"
    }
    val color = team?.let { teamColor(it) }
    // The whole cell, name and [pad] included, takes the tap: small tiles stay easy to hit.
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = action != null) { onAction(NewBoardAction.Tap(hero.id)) }
            .padding(pad)
            .clearAndSetSemantics {
                contentDescription = description
                if (action != null) {
                    onClick(label = action) {
                        onAction(NewBoardAction.Tap(hero.id))
                        true
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = color?.let { BorderStroke(2.dp, it) },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box {
                HeroPortrait(hero.id, hero.portrait, null, Modifier.fillMaxSize())
                if (unavailable != null) {
                    // Stepped back by a scrim of the canvas colour.
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.62f)))
                }
                if (team != null && pick != null && color != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, color),
                        modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TeamMark(team, markSize = 8.dp)
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "$pick",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        // Shrinks to fit a small tile rather than cutting the name ("Silverarrow", not "Silvera…").
        val style = MaterialTheme.typography.labelSmall
        BasicText(
            hero.name,
            style = style.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
                color = if (action == null && team == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
            // One line that may wrap, and Clip: a name too wide then overflows the line and the size
            // steps down until it fits (an ellipsis or no wrapping would count as fitting).
            maxLines = 1,
            overflow = TextOverflow.Clip,
            autoSize = TextAutoSize.StepBased(minFontSize = LABEL_MIN, maxFontSize = style.fontSize),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** Portrait tiles: as big as fits, between these; with the gap around it a tile is at least a 48 dp tap. */
private val TILE_MIN = 42.dp
private val TILE_MAX = 64.dp
private val TILE_GAP = 6.dp
private val LABEL_GAP = 2.dp
private val LABEL_MIN = 7.sp
private val OWNED_ROW = 48.dp
private val TILE_LARGE = 104.dp
private const val LARGE_FONT = 1.3f
