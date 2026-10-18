package dev.quinton.goa.feature.board.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.SectionHeader
import dev.quinton.goa.core.designsystem.component.TeamMark
import dev.quinton.goa.core.designsystem.component.UnverifiedBadge
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.teamColor
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.board.BoardSheet
import dev.quinton.goa.feature.board.board.SearchField
import dev.quinton.goa.feature.board.board.SheetTitle
import dev.quinton.goa.feature.board.list.LineupHeroes
import dev.quinton.goa.feature.board.size
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * New board: tap heroes into the team being filled (pick order), or fill both teams from a draft or a
 * match in one tap, choose the map, then Set up.
 */
@Composable
fun NewBoardScreen(
    state: NewBoardUiState,
    onAction: (NewBoardAction) -> Unit,
    onSetUp: () -> Unit,
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
            if (ready != null && ready.form.lineup.size > 0) {
                TextButton(onClick = { onAction(NewBoardAction.Clear) }) { Text("Clear") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ready == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                NewBoardContent(ready, onAction, onSetUp, onSources = { sources = true })
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
    onSources: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val shown = state.heroes.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.title.contains(query.trim(), ignoreCase = true)
    }
    val large = LocalDensity.current.fontScale > LARGE_FONT
    val cells = GridCells.Adaptive(if (large) TILE_LARGE else TILE)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.45f).fillMaxHeight()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen),
                    ) {
                        Setup(state, onAction, onSources)
                    }
                    SetUpBar(state, onSetUp)
                }
                Column(Modifier.weight(0.55f).fillMaxHeight()) {
                    SearchField(query, { query = it }, onSearch = { focus.clearFocus() })
                    LazyVerticalGrid(
                        columns = cells,
                        contentPadding = PaddingValues(start = Spacing.sm, end = Spacing.screen, bottom = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.weight(1f),
                    ) {
                        heroTiles(shown, state, onAction)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = cells,
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    item(key = "setup", span = { GridItemSpan(maxLineSpan) }) { Column { Setup(state, onAction, onSources) } }
                    item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            SectionHeader("Heroes")
                            SearchField(query, { query = it }, onSearch = { focus.clearFocus() }, horizontalPadding = 0.dp)
                        }
                    }
                    heroTiles(shown, state, onAction)
                }
                SetUpBar(state, onSetUp)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.heroTiles(
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
}

@Composable
private fun Setup(state: NewBoardUiState.Ready, onAction: (NewBoardAction) -> Unit, onSources: () -> Unit) {
    Spacer(Modifier.height(Spacing.md))
    Team.entries.forEach { team ->
        TeamBlock(state, team, onAction)
        Spacer(Modifier.height(Spacing.sm))
    }
    OutlinedButton(
        onClick = onSources,
        enabled = state.sources.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Icon(Icons.Outlined.History, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text("Use a draft or match")
    }
    if (state.sources.isEmpty()) {
        Text(
            "Finished drafts and logged matches appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
    MapChoice(state, onAction)
}

/** One team's heroes in pick order; tap the block to add heroes to this team. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamBlock(state: NewBoardUiState.Ready, team: Team, onAction: (NewBoardAction) -> Unit) {
    val color = teamColor(team)
    val filling = state.form.filling == team
    val heroes = state.team(team)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (filling) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (filling) 2.dp else 1.dp, if (filling) color else GoaTheme.colors.engravedBorder),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = filling, role = Role.RadioButton) { onAction(NewBoardAction.Fill(team)) }
            .semantics { stateDescription = if (filling) "Adding heroes here" else "" },
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamMark(team, markSize = scaledIconSize(12.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    BoardText.team(team),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    "${heroes.size} of ${NewBoardUiState.MAX_PER_TEAM}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (filling) {
                    Text("Adding here", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
                }
            }
            if (heroes.isEmpty()) {
                Text(
                    if (filling) "Tap heroes below to add them in pick order." else "Tap here, then tap heroes to add them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    heroes.forEachIndexed { index, hero ->
                        SlotChip(hero, team, index, heroes.size, state, onAction)
                    }
                }
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
            Row(Modifier.padding(start = 4.dp, end = Spacing.md, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroPortrait(
                    hero.id,
                    hero.portrait,
                    null,
                    Modifier.size(scaledIconSize(32.dp, maxScale = 1.4f)).border(1.5.dp, color, CircleShape),
                    shape = CircleShape,
                    framed = false,
                )
                Spacer(Modifier.width(Spacing.sm))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapChoice(state: NewBoardUiState.Ready, onAction: (NewBoardAction) -> Unit) {
    SectionHeader("Map")
    if (state.maps.isEmpty()) {
        Text("No map is bundled with this build.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.maps.size == 1) {
            Text(state.maps.first().name, style = MaterialTheme.typography.titleSmall)
        } else {
            state.maps.forEach { map ->
                GoaFilterChip(
                    selected = map.id == state.map?.id,
                    onClick = { onAction(NewBoardAction.ChooseMap(map.id)) },
                    label = map.name + if (map.lanes > 1) " · ${map.lanes} lanes" else "",
                )
            }
        }
        if (state.map?.verified == false) UnverifiedBadge()
    }
    Text(
        "Set up places every piece on its Spawn point for the opening (p.6); heroes go in pick order.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.md),
    )
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
        Modifier
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
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamMark(team, markSize = 9.dp)
                            Spacer(Modifier.width(3.dp))
                            Text("$pick", style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Text(
            hero.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = if (action == null && team == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SetUpBar(state: NewBoardUiState.Ready, onSetUp: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        EngravedRule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${state.form.red.size} vs ${state.form.blue.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    state.map?.name ?: "No map",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Button(
                onClick = onSetUp,
                enabled = state.canSetUp,
                modifier = Modifier.heightIn(min = 56.dp),
                contentPadding = PaddingValues(horizontal = Spacing.xl),
            ) {
                Text("Set up", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Finished drafts, pending and logged matches, newest first: one tap fills both teams. */
@Composable
internal fun SourcesContent(sources: List<LineupSource>, onUse: (LineupSource) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT)) {
        SheetTitle("Use a draft or match", "Fills both teams in pick order.", onDone)
        LazyColumn(Modifier.weight(1f)) {
            items(sources, key = { it.key }) { source -> SourceRow(source, onClick = { onUse(source) }) }
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
                Text(day(source.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                listOf(source.red, source.blue).filter { it.isNotEmpty() }.joinToString(" vs ") { team -> team.joinToString(", ") { it.name } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

private fun day(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dayFormat)

private val TILE = 84.dp
private val TILE_LARGE = 120.dp
private const val LARGE_FONT = 1.3f
private const val SHEET_HEIGHT = 0.9f
