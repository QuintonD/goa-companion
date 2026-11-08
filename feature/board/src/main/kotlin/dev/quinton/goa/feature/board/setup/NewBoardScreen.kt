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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.quinton.goa.core.designsystem.component.BannerTextButton
import dev.quinton.goa.core.designsystem.component.EngravedRule
import dev.quinton.goa.core.designsystem.component.GoaButton
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.HeroPortrait
import dev.quinton.goa.core.designsystem.component.HeroSymbol
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
import dev.quinton.goa.feature.board.map.PreloadMapPainting
import dev.quinton.goa.feature.board.play.table.SpellbookSheet
import dev.quinton.goa.feature.board.play.table.catalogSpellbook
import dev.quinton.goa.feature.board.ui.PlateSurface
import dev.quinton.goa.feature.board.ui.PlateWell
import dev.quinton.goa.feature.board.ui.cards.CardFocusHost
import dev.quinton.goa.feature.board.ui.cards.rememberCardFocusState

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
    initialSpellbook: String? = null,
) {
    var sources by rememberSaveable { mutableStateOf(initialSources) }
    var spellbook by rememberSaveable { mutableStateOf(initialSpellbook) }
    val focus = rememberCardFocusState()
    val ready = state as? NewBoardUiState.Ready
    GoaScreen(
        title = "New board",
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (ready != null) {
                if (ready.form.lineup.red.isNotEmpty() || ready.form.lineup.blue.isNotEmpty()) {
                    BannerTextButton("Clear", onClick = { onAction(NewBoardAction.Clear) })
                }
                // Past line-ups in words on the bar ("History"), there only when there is one to use.
                if (ready.sources.isNotEmpty()) HistoryButton(onClick = { sources = true })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (ready == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                NewBoardContent(ready, onAction, onSetUp, onSetUpLatest, onSpellbook = { spellbook = it })
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
    val book = spellbook?.let { id -> ready?.heroById?.get(id) }?.let(::catalogSpellbook)
    if (book != null) SpellbookSheet(book, focus, onDismiss = { spellbook = null })
    CardFocusHost(focus)
}

@Composable
private fun NewBoardContent(
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
    onSetUp: () -> Unit,
    onSetUpLatest: (LineupSource) -> Unit,
    onSpellbook: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val shown = state.heroes.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
            it.title.contains(query.trim(), ignoreCase = true)
    }
    val large = LocalDensity.current.fontScale > LARGE_FONT
    // The latest line-up is offered until the teams are being picked by hand.
    val latest = state.latest?.takeIf { state.form.red.isEmpty() && state.form.blue.isEmpty() }
    // Search with the team switch beside it; with large text, the switch goes under it.
    val search = @Composable { mod: Modifier ->
        val field = @Composable { fieldModifier: Modifier ->
            SearchField(
                query,
                { query = it },
                onSearch = { focus.clearFocus() },
                modifier = fieldModifier,
                horizontalPadding = 0.dp,
                placeholder = "Search",
            )
        }
        // Plate-dark in both themes, as the sheets' search and the teams above it.
        GoaPlateTheme {
            if (large) {
                Column(mod) {
                    field(Modifier)
                    TeamSwitch(state.form.filling, onPick = { onAction(NewBoardAction.Fill(it)) })
                }
            } else {
                Row(mod, verticalAlignment = Alignment.CenterVertically) {
                    field(Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.sm))
                    TeamSwitch(state.form.filling, onPick = { onAction(NewBoardAction.Fill(it)) })
                }
            }
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
                        latest?.let {
                            LatestCard(it, { onSetUpLatest(it) }, onEdit = { onAction(NewBoardAction.Use(it)) })
                        }
                        Teams(state.picker, onAction, onSpellbook)
                    }
                    SetUpBar(state, onAction, onSetUp)
                }
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    search(Modifier.padding(start = Spacing.sm, end = Spacing.screen))
                    HeroGrid(shown, state.picker, onAction, large, start = Spacing.sm, end = Spacing.screen)
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // The latest line-up and the teams take turns in one slot as tall as the taller of
                // the two, so the first pick never moves the hero grid under the next tap.
                OneOf(
                    first = latest != null,
                    modifier = Modifier.padding(horizontal = Spacing.screen).padding(top = Spacing.sm),
                    one = {
                        state.latest?.let {
                            LatestCard(it, { onSetUpLatest(it) }, onEdit = { onAction(NewBoardAction.Use(it)) })
                        }
                    },
                    other = { Teams(state.picker, onAction, onSpellbook) },
                )
                search(Modifier.padding(horizontal = Spacing.screen))
                HeroGrid(shown, state.picker, onAction, large, start = Spacing.screen, end = Spacing.screen)
                SetUpBar(state, onAction, onSetUp)
            }
        }
    }
}

/**
 * Shows [one] when [first], else [other], in a slot as tall as the taller of the two (both are
 * measured; only the shown one is placed, so only it draws, takes touches and is heard).
 */
@Composable
private fun OneOf(
    first: Boolean,
    one: @Composable () -> Unit,
    other: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = constraints.copy(minHeight = 0)
        val a = subcompose(OneOfSlot.ONE, one).map { it.measure(loose) }
        val b = subcompose(OneOfSlot.OTHER, other).map { it.measure(loose) }
        val height = maxOf(a.maxOfOrNull { it.height } ?: 0, b.maxOfOrNull { it.height } ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) { (if (first) a else b).forEach { it.place(0, 0) } }
    }
}

private enum class OneOfSlot { ONE, OTHER }

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
    val label = with(density) { tileName().lineHeight.toDp() } + LABEL_GAP
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        if (large) {
            val columns = ((maxWidth - start - end + Spacing.sm) / (TILE_LARGE + Spacing.sm)).toInt().coerceAtLeast(1)
            val nameSize = gridNameSize(shown, (maxWidth - start - end - Spacing.sm * (columns - 1)) / columns)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(TILE_LARGE),
                contentPadding = PaddingValues(start = start, end = end, bottom = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                heroTiles(shown, state, onAction, pad = 0.dp, nameSize = nameSize)
            }
        } else {
            // Each cell is its tile plus half the gap on every side, all of it the tap target: a
            // 42 dp portrait still takes a 48 dp tap.
            val half = TILE_GAP / 2
            val extra = if (state.ownedOnly) OWNED_ROW + Spacing.sm else 0.dp
            val width = maxWidth - start - end + TILE_GAP
            val height = maxHeight - Spacing.md - extra
            val count = shown.size.coerceAtLeast(1)
            // Names first (sol #15, sol-b #4): the fewest columns whose every name reads at 13 sp or more on one line
            // and whose rows still fit with portraits a little wider than tall (32 heroes on a 411 × 891 phone: 5
            // columns, not 6 with every name shrunk to 11 sp for Silverarrow's sake).
            val sizes = gridNameSizes(shown, width, (TILE_MIN + TILE_GAP).value.let { (width.value / it).toInt() })
            val readable = sizes.entries.firstOrNull { (columns, size) ->
                val cell = width / columns
                val rows = (count + columns - 1) / columns
                size >= READABLE_NAME.value && cell <= READABLE_TILE_MAX + TILE_GAP &&
                    ((cell - TILE_GAP) * MIN_TALL + TILE_GAP + nameLabel(size, density)) * rows <= height
            }?.key
            val columns = readable ?: heroGridColumns(
                count = count,
                width = width.value,
                height = height.value + extra.value,
                label = label.value,
                spacing = 0f,
                extra = extra.value,
                minTile = (TILE_MIN + TILE_GAP).value,
                maxTile = (TILE_MAX + TILE_GAP).value,
            )
            // One name size for the whole grid: the size the longest name needs across its cell, never per tile.
            val nameSize = (sizes[columns] ?: LABEL_MIN.value).sp
            val nameHeight = if (readable != null) nameLabel(nameSize.value, density) else label
            // Every hero on one screen (PRODUCT §8): the height the columns leave goes to taller portraits, so the
            // grid fills the screen rather than ending above an empty band; short of room, a little shorter.
            val cell = width / columns
            val rows = (count + columns - 1) / columns
            val tall = ((height / rows) - nameHeight - TILE_GAP)
                .coerceIn((cell - TILE_GAP) * (if (readable != null) MIN_TALL else 1f), (cell - TILE_GAP) * MAX_TALL)
            val aspect = (cell - TILE_GAP) / tall
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(start = start - half, end = end - half, bottom = Spacing.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                heroTiles(shown, state, onAction, pad = half, aspect = aspect, nameSize = nameSize)
            }
        }
    }
}

private fun LazyGridScope.heroTiles(
    heroes: List<BoardHero>,
    state: LineupPicker,
    onAction: (NewBoardAction) -> Unit,
    pad: Dp,
    aspect: Float = 1f,
    nameSize: TextUnit = TextUnit.Unspecified,
) {
    items(heroes, key = { it.id }) { hero -> HeroTile(hero, state, onAction, pad, aspect, nameSize) }
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
                    // Opaque: over the light canvas a see-through tint left its light words unreadable.
                    activeContainerColor = color.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface),
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
internal fun Teams(state: LineupPicker, onAction: (NewBoardAction) -> Unit, onSpellbook: ((String) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Team.entries.forEach { team -> TeamRow(state, team, onAction, onSpellbook) }
    }
}

/** One team's heroes in pick order; tap the row to pick for this team next. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamRow(
    state: LineupPicker,
    team: Team,
    onAction: (NewBoardAction) -> Unit,
    onSpellbook: ((String) -> Unit)?,
) {
    val color = teamColor(team)
    val filling = state.form.filling == team
    val heroes = state.team(team)
    PlateSurface(
        tint = if (filling) color.copy(alpha = 0.08f) else Color.Transparent,
        brackets = true,
        border = if (filling) BorderStroke(2.dp, color) else null,
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
                val chip = @Composable { index: Int, hero: BoardHero, chipModifier: Modifier ->
                    key(hero.id) {
                        SlotChip(hero, team, index, heroes.size, state, onAction, chipModifier, onSpellbook)
                    }
                }
                if (LocalDensity.current.fontScale > LARGE_FONT) {
                    // Large text: chips sharing a row are cut to a letter, so each keeps its name and they wrap.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                    ) { heroes.forEachIndexed { index, hero -> chip(index, hero, Modifier) } }
                } else {
                    // Every pick stays in sight: the chips share the row, a long name gives way first.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                    ) {
                        heroes.forEachIndexed { index, hero -> chip(index, hero, Modifier.weight(1f, fill = false)) }
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
    onSpellbook: ((String) -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val color = teamColor(team)
    Box(modifier) {
        Surface(
            onClick = { menu = true },
            shape = RoundedCornerShape(50),
            color = PlateWell,
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
            // Gydion: his 22 spell cards before the game (ADR-040), from the catalog.
            if (onSpellbook != null && hero.spells.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("${hero.name}'s spellbook") },
                    leadingIcon = { HeroSymbol(hero.id, hero.symbol, Modifier.size(24.dp)) },
                    onClick = {
                        menu = false
                        onSpellbook(hero.id)
                    },
                )
            }
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
private fun HeroTile(
    hero: BoardHero,
    state: LineupPicker,
    onAction: (NewBoardAction) -> Unit,
    pad: Dp,
    aspect: Float = 1f,
    nameSize: TextUnit = TextUnit.Unspecified,
) {
    val team = when (hero.id) {
        in state.form.red -> Team.RED
        in state.form.blue -> Team.BLUE
        else -> null
    }
    val pick = team?.let { state.form.team(it).indexOf(hero.id) + 1 }
    val filling = state.form.filling
    val full = team == null && state.full
    val later = team == null && hero.id in state.unavailable
    val description = when {
        team != null -> "${hero.name}, ${BoardText.team(team)} pick $pick"
        later -> "${hero.name}, coming later"
        else -> hero.name
    }
    val action = when {
        team != null -> "Remove from ${BoardText.team(team)}"
        full || later -> null
        state.form.team(filling).size < NewBoardUiState.MAX_PER_TEAM -> "Add to ${BoardText.team(filling)}"
        else -> "Add to ${BoardText.team(filling.opponent)}"
    }
    val color = team?.let { teamColor(it) }
    // The whole cell, name and [pad] included, takes the tap: small tiles stay easy to hit.
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = action != null) { onAction(NewBoardAction.Tap(hero.id)) }
            .padding(vertical = pad)
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
        PlateSurface(
            border = color?.let { BorderStroke(2.dp, it) },
            modifier = Modifier.padding(horizontal = pad).fillMaxWidth().aspectRatio(aspect),
        ) {
            Box {
                HeroPortrait(hero.id, hero.portrait, null, Modifier.fillMaxSize())
                if (later) ComingLater(Modifier.align(Alignment.BottomCenter))
                if (team != null && pick != null && color != null) {
                    val edge = teamColor(team)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                        border = BorderStroke(1.5.dp, edge),
                        modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                    ) {
                        // The pick badge reads across the table: the team's mark and its number, a size up.
                        Row(
                            Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TeamMark(team, markSize = 10.dp)
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "$pick",
                                style = MaterialTheme.typography.labelLarge,
                                color = edge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        // The grid's one size ([gridNameSize]), across the whole cell: every name whole, and all alike.
        val style = tileName().let {
            if (nameSize.isSpecified) it.copy(fontSize = nameSize, lineHeight = nameSize * NAME_LINE) else it
        }
        BasicText(
            hero.name,
            style = style.copy(
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
                fontWeight = if (team != null) FontWeight.Bold else style.fontWeight,
                color = if (action == null && team == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/**
 * For each column count from 1 to [most] across [width]: the size every name of the grid takes, the tile name's size
 * stepped down until the longest of [heroes] (set bold, as a pick is) fits its cell on one line; never below
 * [LABEL_MIN]. In sp, by column count (ascending).
 */
@Composable
private fun gridNameSizes(heroes: List<BoardHero>, width: Dp, most: Int): Map<Int, Float> {
    val measurer = rememberTextMeasurer()
    val style = tileName().copy(letterSpacing = 0.sp, fontWeight = FontWeight.Bold)
    val density = LocalDensity.current
    val names = heroes.map { it.name }
    return remember(names, width, most, style, density) {
        (1..most.coerceAtLeast(1)).associateWith { columns ->
            val px = with(density) { (width / columns).roundToPx() }
            var size = style.fontSize.value
            while (size > LABEL_MIN.value &&
                names.any { measurer.measure(it, style.copy(fontSize = size.sp), maxLines = 1).size.width > px }
            ) {
                size -= NAME_STEP
            }
            size
        }
    }
}

/** A name's line at [size] sp, as the tile sets it, with the gap above it. */
private fun nameLabel(size: Float, density: Density): Dp = with(density) { (size * NAME_LINE).sp.toDp() } + LABEL_GAP

/**
 * The size every name of the grid takes: the tile name's size, stepped down until the longest of [heroes] (set bold,
 * as a pick is) fits [width] on one line; never below [LABEL_MIN].
 */
@Composable
private fun gridNameSize(heroes: List<BoardHero>, width: Dp): TextUnit {
    val measurer = rememberTextMeasurer()
    val style = tileName().copy(letterSpacing = 0.sp, fontWeight = FontWeight.Bold)
    val px = with(LocalDensity.current) { width.roundToPx() }
    val names = heroes.map { it.name }
    return remember(names, px, style) {
        var size = style.fontSize.value
        while (size > LABEL_MIN.value &&
            names.any { measurer.measure(it, style.copy(fontSize = size.sp), maxLines = 1).size.width > px }
        ) {
            size -= NAME_STEP
        }
        size.sp
    }
}

/** A tile's name: 14 sp semibold, read at arm's length; it shrinks only for a name too long for its tile. */
@Composable
@ReadOnlyComposable
private fun tileName(): TextStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

/**
 * A hero that can't be seated yet (New game: not every card of it is built and tested): the
 * portrait is veiled and marked "Later", so the grid keeps its place and the hero reads as coming.
 */
@Composable
private fun ComingLater(modifier: Modifier = Modifier) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.62f)))
    PlateSurface(shape = RoundedCornerShape(50), modifier = modifier.padding(bottom = 3.dp)) {
        Text(
            "Later",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
    }
}

/** The map ("Map: Forgotten Island ▾", a menu of the bundled maps) and Set up. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SetUpBar(state: NewBoardUiState.Ready, onAction: (NewBoardAction) -> Unit, onSetUp: () -> Unit) {
    PreloadMapPainting(state.map?.painting)
    var menu by remember { mutableStateOf(false) }
    // Large text: the map's name above Set up, not cut to its first letters beside it.
    val stacked = LocalDensity.current.fontScale > LARGE_FONT
    Column(Modifier.fillMaxWidth()) {
        EngravedRule()
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.sm, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.sm),
            maxItemsInEachRow = if (stacked) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
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
            GoaButton(
                onClick = onSetUp,
                enabled = state.canSetUp,
                modifier = Modifier.heightIn(min = 56.dp).then(if (stacked) Modifier.fillMaxWidth() else Modifier),
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

/** The line-ups of past drafts and matches: the bar menu's line and the sheet's title (New board, New game). */
internal const val RECENT_LINEUPS = "Use a draft or match"

/** "History" on the bar of New board and New game: opens the line-ups of past drafts and matches. */
@Composable
internal fun HistoryButton(onClick: () -> Unit) {
    BannerTextButton(
        "History",
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "History: $RECENT_LINEUPS" },
    )
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
        SheetTitle(RECENT_LINEUPS, "Fills both teams in pick order.", onDone)
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
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
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

/** Portrait tiles: as big as fits, between these; with the gap around it a tile is at least a 48 dp tap. */
private val TILE_MIN = 42.dp
private val TILE_MAX = 64.dp
private val TILE_GAP = 6.dp
private val LABEL_GAP = 2.dp
private val LABEL_MIN = 9.sp
private const val NAME_STEP = 0.5f

/** A portrait may grow this much taller than wide to fill the screen's height. */
private const val MAX_TALL = 1.3f

/** ...and as short as this, wider than tall, so every hero fits with names that read. */
private const val MIN_TALL = 0.74f

/** The largest a portrait grows when the names choose the columns (5 across a 411 dp phone). */
private val READABLE_TILE_MAX = 76.dp

/** The smallest name the grid prefers more columns to: below it, fewer columns with names that read. */
private val READABLE_NAME = 13.sp

/** A tile name's line height for its size. */
private const val NAME_LINE = 1.22f
private val OWNED_ROW = 48.dp
private val TILE_LARGE = 104.dp
private const val LARGE_FONT = 1.3f
private const val SHEET_HEIGHT = 0.9f
