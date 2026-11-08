package dev.quinton.goa.feature.catalog.list

import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.BarAction
import dev.quinton.goa.core.designsystem.component.BarActions
import dev.quinton.goa.core.designsystem.component.ChipIconSize
import dev.quinton.goa.core.designsystem.component.ChipLabel
import dev.quinton.goa.core.designsystem.component.EdgeFadeGutter
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaFilterChip
import dev.quinton.goa.core.designsystem.component.GoaOutlinedButton
import dev.quinton.goa.core.designsystem.component.GoaPlate
import dev.quinton.goa.core.designsystem.component.GoaPlateTheme
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.GoaSegment
import dev.quinton.goa.core.designsystem.component.GoaSegmentedRow
import dev.quinton.goa.core.designsystem.component.GoaSheet
import dev.quinton.goa.core.designsystem.component.LARGE_FONT_SCALE
import dev.quinton.goa.core.designsystem.component.PlateGround
import dev.quinton.goa.core.designsystem.component.SearchFieldIcon
import dev.quinton.goa.core.designsystem.component.chipSize
import dev.quinton.goa.core.designsystem.component.horizontalEdgeFade
import dev.quinton.goa.core.designsystem.component.scaledIconSize
import dev.quinton.goa.core.designsystem.component.selectionFrame
import dev.quinton.goa.core.designsystem.theme.GoaTextStyles
import dev.quinton.goa.core.designsystem.theme.GoaTheme
import dev.quinton.goa.core.designsystem.theme.Spacing
import dev.quinton.goa.core.domain.catalog.CatalogFilter
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.domain.catalog.FilterOptions
import dev.quinton.goa.core.domain.catalog.activeFilterChips
import dev.quinton.goa.core.domain.catalog.catalogResultsKey
import dev.quinton.goa.core.domain.catalog.complexityLabel
import dev.quinton.goa.core.domain.catalog.label
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.domain.catalog.resultSummary
import dev.quinton.goa.core.model.settings.CatalogLayout
import dev.quinton.goa.feature.catalog.ui.goaInputChipBorder
import dev.quinton.goa.feature.catalog.ui.goaInputChipColors
import dev.quinton.goa.feature.catalog.ui.rememberPlateArt

/** Heroes tab: searchable, filterable grid/list of every hero in the catalog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    query: String,
    onAction: (CatalogAction) -> Unit,
    onHeroClick: (heroId: String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCardClick: (heroId: String, cardId: String) -> Unit = { _, _ -> },
    onRulesClick: () -> Unit = {},
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val ready = state as? CatalogUiState.Ready
    // The first screen is usable: what "fully drawn" means to the system and the startup measure (ADR-057).
    ReportDrawnWhen { ready != null }
    // Back with a search typed clears the search instead of leaving the app.
    BackHandler(enabled = query.isNotEmpty()) { onAction(CatalogAction.Search("")) }
    GoaScreen(
        title = "Heroes",
        modifier = modifier,
        actions = {
            // Rules and Settings (also on More). The grid/list switch sits by the results it changes.
            BarActions(
                listOf(
                    BarAction("Rules", Icons.AutoMirrored.Outlined.MenuBook, onRulesClick),
                    BarAction("Settings", Icons.Outlined.Settings, onSettingsClick),
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                CatalogUiState.Loading -> LoadingGrid()

                is CatalogUiState.Unavailable -> GoaPlate(
                    modifier = Modifier.align(Alignment.Center).padding(Spacing.screen),
                ) {
                    EmptyState(
                        icon = if (state.error) Icons.Outlined.CloudOff else Icons.Outlined.Shield,
                        title = if (state.error) "Couldn't load the heroes" else "No heroes yet",
                        message = if (state.error) {
                            "The bundled hero data couldn't be read. Reinstalling the latest build should fix it."
                        } else {
                            "Hero data isn't bundled with this build yet. " +
                                "It appears here as soon as the catalog is imported."
                        },
                    )
                }

                is CatalogUiState.Ready -> ReadyContent(
                    state = state,
                    query = query,
                    onAction = onAction,
                    onHeroClick = onHeroClick,
                    onCardClick = onCardClick,
                    onOpenFilters = { showFilters = true },
                )
            }
        }
    }
    if (showFilters && ready != null) {
        GoaSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CatalogFilterContent(
                filter = ready.controls.filter,
                options = ready.options,
                resultCount = ready.heroes.size,
                onFilterChange = { onAction(CatalogAction.SetFilter(it)) },
                onClear = { onAction(CatalogAction.ClearFilters) },
                onDone = { showFilters = false },
            )
        }
    }
}

@Composable
private fun ReadyContent(
    state: CatalogUiState.Ready,
    query: String,
    onAction: (CatalogAction) -> Unit,
    onHeroClick: (String) -> Unit,
    onCardClick: (heroId: String, cardId: String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    // A search that finds both cards and heroes: All, or one kind. A new search starts on All.
    var tab by rememberSaveable(query) { mutableStateOf(SearchTab.ALL) }
    val tabbed = state.cards.isNotEmpty() && state.heroes.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        SearchField(query = query, onQueryChange = { onAction(CatalogAction.Search(it)) })
        ControlsRow(state = state, onAction = onAction, onOpenFilters = onOpenFilters)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ResultSummary(state = state, query = query, modifier = Modifier.weight(1f))
            LayoutToggle(state.controls.layout, onSelect = { onAction(CatalogAction.SetLayout(it)) })
        }
        if (tabbed) SearchTabs(tab, cards = state.cards.size, heroes = state.heroes.size, onSelect = { tab = it })
        if (state.heroes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                NoResults(state, query, onAction)
            }
        } else {
            AnimatedContent(
                targetState = state.controls.layout,
                transitionSpec = { fadeIn(tween(MOTION_MS)) togetherWith fadeOut(tween(MOTION_MS)) },
                label = "catalogLayout",
            ) { layout ->
                Results(
                    state = state,
                    layout = layout,
                    query = query,
                    tab = if (tabbed) tab else SearchTab.ALL,
                    onHeroClick = onHeroClick,
                    onCardClick = onCardClick,
                    onAllCards = { tab = SearchTab.CARDS },
                )
            }
        }
    }
}

@Composable
private fun NoResults(state: CatalogUiState.Ready, query: String, onAction: (CatalogAction) -> Unit) {
    val hidden = state.hiddenByCollection
    val filtered = state.controls.filter.activeCount > 0
    if (hidden > 0) {
        // The collection (Settings → owned products) hides heroes that would match.
        GoaPlate(Modifier.padding(Spacing.screen)) {
            EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "Not in your collection",
                message = "${plural(hidden, "hero", "heroes")} from products you don't own " +
                    (if (hidden == 1) "matches" else "match") + ". Your collection is set in Settings.",
                action = {
                    GoaOutlinedButton(
                        onClick = {
                            onAction(CatalogAction.SetFilter(state.controls.filter.copy(includeUnowned = true)))
                        },
                    ) { Text("Show all products") }
                },
            )
        }
        return
    }
    GoaPlate(Modifier.padding(Spacing.screen)) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "No heroes match",
            message = when {
                query.isBlank() -> "No hero fits all of these filters. Remove one to widen the search."
                filtered -> "Nothing matches “${query.trim()}” with these filters. Try another word or clear them."
                else -> "Nothing matches “${query.trim()}”. Try another word, or fewer words."
            },
            action = {
                GoaOutlinedButton(onClick = { onAction(CatalogAction.ClearAll) }) {
                    Text(if (filtered) "Clear search and filters" else "Clear search")
                }
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    // With large text the whole hint doesn't fit beside the icons on one line: it takes two, every word kept.
    val hintLines = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) 2 else 1
    // The slot on the plate: the slate, or the light theme paper, under the banner.
    GoaPlateTheme {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.sm, bottom = Spacing.sm),
            placeholder = { Text(SEARCH_HINT, maxLines = hintLines, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { SearchFieldIcon() },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GoaTheme.colors.plate,
                unfocusedContainerColor = GoaTheme.colors.plate,
                unfocusedBorderColor = SEARCH_EDGE,
            ),
        )
    }
}

@Composable
private fun ControlsRow(state: CatalogUiState.Ready, onAction: (CatalogAction) -> Unit, onOpenFilters: () -> Unit) {
    val filter = state.controls.filter
    val row = rememberLazyListState()
    GoaPlateTheme {
        LazyRow(
            state = row,
            modifier = Modifier.horizontalEdgeFade(row),
            contentPadding = EdgeFadeGutter,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "filters") {
                GoaFilterChip(
                    selected = filter.activeCount > 0,
                    onClick = onOpenFilters,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = null,
                            Modifier.size(scaledIconSize(ChipIconSize)),
                        )
                    },
                ) { Text(if (filter.activeCount > 0) "Filters · ${filter.activeCount}" else "Filters") }
            }
            item(key = "sort") { SortChip(state.controls.sort) { onAction(CatalogAction.SetSort(it)) } }
            // The collection hides heroes: said where the filters are, and removable like them.
            if (state.collectionLimited) {
                item(key = "collection") {
                    InputChip(
                        colors = goaInputChipColors(),
                        border = goaInputChipBorder(),
                        selected = true,
                        onClick = { onAction(CatalogAction.SetFilter(filter.copy(includeUnowned = true))) },
                        label = { ChipLabel { Text("Owned only") } },
                        trailingIcon = {
                            Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(scaledIconSize(16.dp)))
                        },
                        modifier = Modifier.chipSize().semantics {
                            contentDescription = "Only heroes from products you own. Show all products"
                        },
                    )
                }
            }
            items(activeFilterChips(filter, state.options), key = { it.key }) { chip ->
                InputChip(
                    colors = goaInputChipColors(),
                    border = goaInputChipBorder(),
                    selected = true,
                    onClick = { onAction(CatalogAction.SetFilter(chip.remove(filter))) },
                    label = { ChipLabel { Text(chip.label) } },
                    trailingIcon = {
                        Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(scaledIconSize(16.dp)))
                    },
                    modifier = Modifier.chipSize().semantics { contentDescription = "Remove filter ${chip.label}" },
                )
            }
        }
    }
}

/** What a search shows: everything, or only the cards or only the heroes it found. */
internal enum class SearchTab { ALL, CARDS, HEROES }

/** All / Cards n / Heroes n over a search that found both, as one segmented bar. */
@Composable
private fun SearchTabs(tab: SearchTab, cards: Int, heroes: Int, onSelect: (SearchTab) -> Unit) {
    GoaPlateTheme {
        GoaSegmentedRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.sm),
        ) {
            SearchTab.entries.forEach { option ->
                GoaSegment(selected = tab == option, onClick = { onSelect(option) }, icon = {}) {
                    Text(
                        when (option) {
                            SearchTab.ALL -> "All"
                            SearchTab.CARDS -> "Cards $cards"
                            SearchTab.HEROES -> "Heroes $heroes"
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortChip(sort: CatalogSort, onSort: (CatalogSort) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        GoaFilterChip(
            selected = false,
            onClick = { open = true },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    Modifier.size(scaledIconSize(ChipIconSize)),
                )
            },
        ) { Text("Sort: ${sort.label}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CatalogSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                    trailingIcon = {
                        if (option == sort) Icon(Icons.Filled.Check, contentDescription = "Selected")
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultSummary(state: CatalogUiState.Ready, query: String, modifier: Modifier = Modifier) {
    // A search that found cards too: one summary of both ("6 cards · 4 heroes"); the tabs carry each count.
    val text = if (state.cards.isNotEmpty()) {
        plural(state.cards.size, "card") + " · " + plural(state.heroes.size, "hero", "heroes")
    } else {
        resultSummary(state.heroes.size, state.totalInScope, query)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = Spacing.screen, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Large grid, small grid or list, as three segments that show every choice, the current one in the selected bronze:
 * no glyph to decode. Each is a 48 dp target that TalkBack names ("Show as small grid", selected or not).
 */
@Composable
private fun LayoutToggle(layout: CatalogLayout, onSelect: (CatalogLayout) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    GoaPlateTheme {
        Row(
            Modifier
                .padding(end = Spacing.screen - Spacing.xs)
                .selectableGroup(),
        ) {
            LayoutOption(CatalogLayout.LARGE_GRID, "large grid", Icons.Outlined.GridView, layout, shape, onSelect)
            LayoutOption(CatalogLayout.SMALL_GRID, "small grid", Icons.Outlined.Apps, layout, shape, onSelect)
            LayoutOption(CatalogLayout.LIST, "list", Icons.AutoMirrored.Outlined.ViewList, layout, shape, onSelect)
        }
    }
}

@Composable
private fun LayoutOption(
    option: CatalogLayout,
    name: String,
    icon: ImageVector,
    current: CatalogLayout,
    shape: Shape,
    onSelect: (CatalogLayout) -> Unit,
) {
    val selected = option == current
    Box(
        Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(option) })
            .semantics { contentDescription = "Show as $name" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(width = 40.dp, height = 32.dp).selectionFrame(selected, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) GoaTheme.colors.selection else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Heroes (large grid, small grid or list) and, while searching, the matching cards. Cards come first when
 * they answer the query better than any hero (a card phrase beats a hero matched only through
 * its title and another card). The large grid's columns are as wide as its longest name needs; the small
 * grid keeps three columns on a phone (its names step down to fit) and two with large text.
 *
 * The list has its own scroll state and returns to the top whenever the query, filters or sort
 * change: a lazy grid otherwise keeps the first visible item's key in place, which leaves the
 * best result scrolled off the top. The position is kept when nothing changed (e.g. back from a
 * hero page).
 */
@Composable
@Suppress("LongParameterList")
private fun Results(
    state: CatalogUiState.Ready,
    layout: CatalogLayout,
    query: String,
    tab: SearchTab,
    onHeroClick: (String) -> Unit,
    onCardClick: (heroId: String, cardId: String) -> Unit,
    onAllCards: () -> Unit,
) {
    val gap = when (layout) {
        CatalogLayout.LARGE_GRID -> Spacing.sm
        CatalogLayout.SMALL_GRID -> SMALL_GRID_GAP
        CatalogLayout.LIST -> Spacing.xs
    }
    val showCards = state.cards.isNotEmpty() && tab != SearchTab.HEROES
    val showHeroes = tab != SearchTab.CARDS
    // On All, a few cards then the heroes: the rest of the cards are a tap away (the Cards tab).
    val cards = if (tab == SearchTab.ALL && state.heroes.isNotEmpty()) state.cards.take(CARDS_ON_ALL) else state.cards
    val gridState = rememberLazyGridState()
    val resultsKey = resultsKey(query, state.controls) + "|" + tab
    val shownFor = remember { arrayOf(resultsKey) }
    if (shownFor[0] != resultsKey) {
        shownFor[0] = resultsKey
        // Applied at the next measure, before key-based position keeping can scroll the top away.
        gridState.requestScrollToItem(0)
    }
    val fontScale = LocalDensity.current.fontScale
    // As many columns as keep every name at 14 sp or more on its plate (and the tile's text whole) at this font scale.
    val baseCell = if (fontScale > LARGE_FONT_SCALE) GRID_MIN_CELL_LARGE_FONT else GRID_MIN_CELL
    val largeCell = maxOf(baseCell, rememberTileNameWidth(state.heroes))
    val columns = when (layout) {
        CatalogLayout.LARGE_GRID -> GridCells.Adaptive(largeCell)

        CatalogLayout.SMALL_GRID ->
            GridCells.Adaptive(if (fontScale > SMALL_GRID_LARGE_FONT) SMALL_MIN_CELL_LARGE_FONT else SMALL_MIN_CELL)

        CatalogLayout.LIST -> GridCells.Fixed(1)
    }
    val plateArt = rememberPlateArt()
    val footnotes =
        remember(state.heroes, state.controls.sort) { TileFootnoteRoom.of(state.heroes, state.controls.sort) }
    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        fun cardSection() {
            item(key = "cards-header", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                ResultsHeader("Cards")
            }
            items(
                cards,
                key = { "card-" + it.card.id },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { CARD },
            ) { result ->
                CardResultRow(result, query, onClick = { onCardClick(result.hero.id, result.card.id) })
            }
            if (cards.size < state.cards.size) {
                item(key = "cards-more", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                    GoaOutlinedButton(onClick = onAllCards, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("All ${state.cards.size} cards")
                    }
                }
            }
        }
        if (showCards && (state.cardsFirst || !showHeroes)) cardSection()
        if (showCards && showHeroes) {
            item(key = "heroes-header", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                ResultsHeader("Heroes")
            }
        }
        // Content types let a tile scrolled out be reused for the next tile, not rebuilt.
        items(if (showHeroes) state.heroes else emptyList(), key = { it.hero.id }, contentType = { HERO }) { item ->
            val onClick = { onHeroClick(item.hero.id) }
            when (layout) {
                CatalogLayout.LARGE_GRID ->
                    HeroGridTile(item, state.controls.sort, plateArt, footnotes, onClick, Modifier.animateItem())

                CatalogLayout.SMALL_GRID ->
                    HeroSmallTile(item, state.controls.sort, plateArt, footnotes, onClick, Modifier.animateItem())

                CatalogLayout.LIST -> HeroListRow(item, state.controls.sort, plateArt, onClick, Modifier.animateItem())
            }
        }
        if (showCards && showHeroes && !state.cardsFirst) cardSection()
        item(key = "end", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/** What the result list shows; a change scrolls it back to the top. */
internal fun resultsKey(query: String, controls: CatalogControls): String =
    catalogResultsKey(query, controls.filter, controls.sort)

@Composable
private fun ResultsHeader(title: String) {
    Text(
        title,
        style = GoaTextStyles.sectionHead,
        color = GoaTheme.colors.heading,
        modifier = Modifier.padding(top = Spacing.sm, bottom = 2.dp).semantics { heading() },
    )
}

/** Skeleton tiles while the catalog loads (usually a few frames). */
@Composable
private fun LoadingGrid() {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val tint = GoaTheme.colors.plate
    Column(Modifier.semantics { contentDescription = "Loading heroes" }) {
        Box(
            Modifier
                .padding(horizontal = Spacing.screen)
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(tint.copy(alpha = pulse)) },
        )
        Spacer(Modifier.height(Spacing.xl))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GRID_MIN_CELL),
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            userScrollEnabled = false,
        ) {
            items(SKELETON_TILES) {
                Row {
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(tint.copy(alpha = 0.5f))
                            .drawBehind { drawRect(tint.copy(alpha = pulse)) },
                    )
                }
            }
        }
    }
}

/** 3 columns on a 411 dp phone (1080 px at 420 dpi): (411 - 32 + 8) / (110 + 8) = 3. */
private val GRID_MIN_CELL = 110.dp

/** 2 columns on the same phone at large font scales: (411 - 32 + 8) / (150 + 8) = 2. */
private val GRID_MIN_CELL_LARGE_FONT = 150.dp

/** Small grid: 3 columns on a 360 to 411 dp phone ((384 - 32 + 6) / (100 + 6) = 3), 4 from about 460 dp. */
private val SMALL_MIN_CELL = 100.dp
private val SMALL_GRID_GAP = 6.dp

/** Small grid with large text (above 150 %): two columns on a phone, so a name keeps a readable size. */
private val SMALL_MIN_CELL_LARGE_FONT = 150.dp
private const val SMALL_GRID_LARGE_FONT = 1.5f

/** Font scales from Android's 1.3 step up: a tile's plate and texts no longer fit a third of a phone. */
private const val LARGE_FONT_SCALE = 1.2f

private const val SEARCH_HINT = "Search heroes & cards"
private val SEARCH_EDGE = Color(0xFF5D6B70)
private const val MOTION_MS = 250
private const val HEADER = "header"
private const val HERO = "hero"
private const val CARD = "card"
private const val PULSE_MS = 900

/** Cards shown on a search's All tab before "All n cards", so the heroes it found are on the first screen. */
private const val CARDS_ON_ALL = 3
private const val SKELETON_TILES = 6
