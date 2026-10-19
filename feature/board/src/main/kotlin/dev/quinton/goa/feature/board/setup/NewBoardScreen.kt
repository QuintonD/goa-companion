package dev.quinton.goa.feature.board.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SearchField
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.list.LineupHeroes

/**
 * New board, built to need no scrolling: the latest draft's line-up (Set up in one tap), both teams
 * in pick order, search with the "Adding to Red | Blue" switch beside it, the hero grid, and the
 * map and Set up at the bottom. Picks alternate like a draft (the team with fewer heroes picks
 * next); tapping Red or Blue picks for that team.
 */
@Composable
fun NewBoardScreen(
    state: NewBoardUiState,
    onAction: (NewBoardAction) -> Unit,
    onSetUp: () -> Unit,
    onSetUpLatest: (LineupSource) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialSources: Boolean = false,
) {
    var sources by rememberSaveable { mutableStateOf(initialSources) }
    val ready = state as? NewBoardUiState.Ready
    GoaScreen(
        title = "New board",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (ready != null) {
                if (ready.form.lineup.red.isNotEmpty() || ready.form.lineup.blue.isNotEmpty()) {
                    TextButton(onClick = { onAction(NewBoardAction.Clear) }) { Text("Clear") }
                }
                IconButton(onClick = { sources = true }, enabled = ready.sources.isNotEmpty()) {
                    Icon(Icons.Outlined.History, contentDescription = "Use a draft or match")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ready == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                NewBoardContent(ready, onAction, onSetUp, onSetUpLatest)
            }
        }
    }
    if (ready != null && sources) {
        BoardSheet(onDismiss = { sources = false }) {
            SourcesContent(
                ready.sources,
                onUse = {
                    onAction(NewBoardAction.Use(it))
                    sources = false
                },
                onDone = { sources = false },
            )
        }
    }
}

@Composable
private fun NewBoardContent(
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
    onSetUp: () -> Unit,
    onSetUpLatest: (LineupSource) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val shown = state.heroes.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.title.contains(query.trim(), ignoreCase = true)
    }
    val large = LocalDensity.current.fontScale > LARGE_FONT
    val cells = GridCells.Adaptive(if (large) TILE_LARGE else TILE)
    // The latest line-up is offered until the teams are being picked by hand.
    val latest = state.latest?.takeIf { state.form.red.isEmpty() && state.form.blue.isEmpty() }
    val search = @Composable { mod: Modifier ->
        Row(mod, verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query,
                { query = it },
                onSearch = { focus.clearFocus() },
                modifier = Modifier.weight(1f),
                horizontalPadding = 0.dp,
            )
            Spacer(Modifier.width(Spacing.sm))
            TeamSwitch(state.form.filling, onPick = { onAction(NewBoardAction.Fill(it)) })
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        latest?.let { LatestCard(it, { onSetUpLatest(it) }, onEdit = { onAction(NewBoardAction.Use(it)) }) }
                        Teams(state, onAction)
                    }
                    SetUpBar(state, onAction, onSetUp)
                }
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    search(Modifier.padding(start = Spacing.sm, end = Spacing.screen))
                    HeroGrid(cells, shown, state, onAction, start = Spacing.sm, end = Spacing.screen)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    latest?.let { LatestCard(it, { onSetUpLatest(it) }, onEdit = { onAction(NewBoardAction.Use(it)) }) }
                    Teams(state, onAction)
                }
                search(Modifier.padding(horizontal = Spacing.screen))
                HeroGrid(cells, shown, state, onAction, start = Spacing.screen, end = Spacing.screen)
                SetUpBar(state, onAction, onSetUp)
            }
        }
    }
}

@Composable
private fun ColumnScope.HeroGrid(
    cells: GridCells,
    shown: List<BoardHero>,
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
    start: Dp,
    end: Dp,
) {
    LazyVerticalGrid(
        columns = cells,
        contentPadding = PaddingValues(start = start, end = end, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.weight(1f),
    ) {
        heroTiles(shown, state, onAction)
    }
}

private fun LazyGridScope.heroTiles(
    heroes: List<BoardHero>,
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
) {
    items(heroes, key = { it.id }) { hero -> HeroTile(hero, state, onAction) }
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
private fun TeamSwitch(filling: Team, onPick: (Team) -> Unit) {
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
private fun Teams(state: NewBoardUiState.Ready, onAction: (NewBoardAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Team.entries.forEach { team -> TeamRow(state, team, onAction) }
    }
}

/** One team's heroes in pick order; tap the row to pick for this team next. */
@Composable
private fun TeamRow(state: NewBoardUiState.Ready, team: Team, onAction: (NewBoardAction) -> Unit) {
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
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(heroes, key = { _, hero -> hero.id }) { index, hero ->
                        SlotChip(hero, team, index, heroes.size, state, onAction)
                    }
                }
            }
            if (filling) {
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
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val color = teamColor(team)
    Box {
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
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(Spacing.xs))
                Text(hero.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
private fun HeroTile(hero: BoardHero, state: NewBoardUiState.Ready, onAction: (NewBoardAction) -> Unit) {
    val team = when (hero.id) {
        in state.form.red -> Team.RED
        in state.form.blue -> Team.BLUE
        else -> null
    }
    val pick = team?.let { state.form.team(it).indexOf(hero.id) + 1 }
    val filling = state.form.filling
    val full = team == null && state.full
    val description = when {
        team != null -> "${hero.name}, ${BoardText.team(team)} pick $pick"
        else -> hero.name
    }
    val action = when {
        team != null -> "Remove from ${BoardText.team(team)}"
        full -> null
        state.form.team(filling).size < NewBoardUiState.MAX_PER_TEAM -> "Add to ${BoardText.team(filling)}"
        else -> "Add to ${BoardText.team(filling.opponent)}"
    }
    val color = team?.let { teamColor(it) }
    Column(
        Modifier.clearAndSetSemantics {
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
            onClick = { onAction(NewBoardAction.Tap(hero.id)) },
            enabled = action != null,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = color?.let { BorderStroke(2.dp, it) },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        ) {
            Box {
                HeroPortrait(hero.id, hero.portrait, null, Modifier.fillMaxSize())
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
                            Text("$pick", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Text(
            hero.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (action == null && team == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The map ("Map: Forgotten Island ▾", a menu of the bundled maps) and Set up. */
@Composable
private fun SetUpBar(state: NewBoardUiState.Ready, onAction: (NewBoardAction) -> Unit, onSetUp: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        EngravedRule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                val map = state.map
                TextButton(
                    onClick = { menu = true },
                    enabled = state.maps.size > 1,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = "Map: ${map?.name ?: "none"}" +
                            (if (map?.verified == false) ", unverified" else "") + ". Change map"
                    },
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("MAP", style = GoaTextStyles.overline, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                map?.name ?: "No map in this build",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (state.maps.size > 1) Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        if (map?.verified == false) UnverifiedBadge(Modifier.padding(top = 2.dp))
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    state.maps.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.name)
                                    Text(
                                        listOfNotNull(
                                            if (option.lanes > 1) "${option.lanes} lanes" else "Single lane",
                                            "Unverified".takeIf { !option.verified },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (option.verified) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            GoaTheme.colors.unverified
                                        },
                                    )
                                }
                            },
                            leadingIcon = {
                                if (option.id == state.map?.id) {
                                    Icon(Icons.Outlined.Check, contentDescription = "Chosen")
                                } else {
                                    Spacer(Modifier.size(24.dp))
                                }
                            },
                            onClick = {
                                menu = false
                                onAction(NewBoardAction.ChooseMap(option.id))
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Button(
                onClick = onSetUp,
                enabled = state.canSetUp,
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) {
                Text(
                    "Set up ${state.form.red.size} v ${state.form.blue.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** Finished drafts, pending and logged matches, newest first: one tap fills both teams. */
@Composable
internal fun SourcesContent(
    sources: List<LineupSource>,
    onUse: (LineupSource) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
        SheetTitle("Use a draft or match", "Fills both teams in pick order.", onDone)
        LazyColumn(Modifier.weight(1f)) {
            items(sources, key = { it.key }) { source -> SourceRow(source, onClick = { onUse(source) }) }
            if (sources.isEmpty()) {
                item { Text("Finished drafts and logged matches appear here.", Modifier.padding(Spacing.screen)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(source: LineupSource, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(horizontal = Spacing.screen, vertical = Spacing.md)
                .semantics(mergeDescendants = true) {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.kind.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    day(source.at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                LineupHeroes(Team.RED, source.red)
                LineupHeroes(Team.BLUE, source.blue)
            }
            Text(
                listOf(source.red, source.blue).filter {
                    it.isNotEmpty()
                }.joinToString(" vs ") { team -> team.joinToString(", ") { it.name } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

private val TILE = 58.dp
private val TILE_LARGE = 104.dp
private const val LARGE_FONT = 1.3f
private const val SHEET_HEIGHT = 0.9f
