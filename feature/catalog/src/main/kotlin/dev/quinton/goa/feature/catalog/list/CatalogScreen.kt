package dev.quinton.goa.feature.catalog.list

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.quinton.goa.core.designsystem.component.EmptyState
import dev.quinton.goa.core.designsystem.component.GoaScreen
import dev.quinton.goa.core.designsystem.component.SettingsActionButton
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
import dev.quinton.goa.feature.catalog.ui.goaFilterChipColors
import dev.quinton.goa.feature.catalog.ui.goaInputChipColors

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
    onTriviaClick: () -> Unit = {},
    onBoardClick: () -> Unit = {},
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    // Five actions and the title fit side by side on a phone at normal text size; when they
    // don't (narrow screen, large text), Board and Card trivia move into "More options".
    val widthDp = LocalConfiguration.current.screenWidthDp
    val compact = TOP_BAR_ICONS_DP + TITLE_DP * LocalDensity.current.fontScale > widthDp
    val ready = state as? CatalogUiState.Ready
    // Back with a search typed clears the search instead of leaving the app.
    BackHandler(enabled = query.isNotEmpty()) { onAction(CatalogAction.Search("")) }
    GoaScreen(
        title = "Heroes",
        modifier = modifier,
        actions = {
            if (ready != null) {
                val grid = ready.controls.layout == CatalogLayout.GRID
                IconButton(
                    onClick = {
                        onAction(CatalogAction.SetLayout(if (grid) CatalogLayout.LIST else CatalogLayout.GRID))
                    },
                ) {
                    Icon(
                        if (grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                        contentDescription = if (grid) "Show as list" else "Show as grid",
                    )
                }
            }
            IconButton(onClick = onRulesClick) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = "Rules")
            }
            if (compact) {
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(
                            text = { Text("Board") },
                            leadingIcon = { Icon(Icons.Outlined.Hexagon, contentDescription = null) },
                            onClick = {
                                more = false
                                onBoardClick()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Card trivia") },
                            leadingIcon = { Icon(Icons.Outlined.Quiz, contentDescription = null) },
                            onClick = {
                                more = false
                                onTriviaClick()
                            },
                        )
                    }
                }
            } else {
                IconButton(onClick = onBoardClick) {
                    Icon(Icons.Outlined.Hexagon, contentDescription = "Board")
                }
                IconButton(onClick = onTriviaClick) {
                    Icon(Icons.Outlined.Quiz, contentDescription = "Card trivia")
                }
            }
            SettingsActionButton(onSettingsClick)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                CatalogUiState.Loading -> LoadingGrid()

                is CatalogUiState.Unavailable -> EmptyState(
                    icon = if (state.error) Icons.Outlined.CloudOff else Icons.Outlined.Shield,
                    title = if (state.error) "Couldn't load the heroes" else "No heroes yet",
                    message = if (state.error) {
                        "The bundled hero data couldn't be read. Reinstalling the latest build should fix it."
                    } else {
                        "Hero data isn't bundled with this build yet. It appears here as soon as the catalog is imported."
                    },
                    modifier = Modifier.align(Alignment.Center),
                )

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
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
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
    Column(Modifier.fillMaxSize()) {
        SearchField(query = query, onQueryChange = { onAction(CatalogAction.Search(it)) })
        ControlsRow(state = state, onAction = onAction, onOpenFilters = onOpenFilters)
        ResultSummary(state = state, query = query)
        if (state.heroes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                NoResults(state, query, onAction)
            }
        } else {
            AnimatedContent(
                targetState = state.controls.layout,
                transitionSpec = { fadeIn(tween(MOTION_MS)) togetherWith fadeOut(tween(MOTION_MS)) },
                label = "catalogLayout",
            ) { layout -> Results(state, layout, query, onHeroClick, onCardClick) }
        }
    }
}

@Composable
private fun NoResults(state: CatalogUiState.Ready, query: String, onAction: (CatalogAction) -> Unit) {
    val hidden = state.hiddenByCollection
    val filtered = state.controls.filter.activeCount > 0
    if (hidden > 0) {
        // The collection (Settings → owned products) hides heroes that would match.
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "Not in your collection",
            message = "${plural(hidden, "hero", "heroes")} from products you don't own " +
                (if (hidden == 1) "matches" else "match") + ". Your collection is set in Settings.",
            action = {
                OutlinedButton(
                    onClick = { onAction(CatalogAction.SetFilter(state.controls.filter.copy(includeUnowned = true))) },
                ) { Text("Show all products") }
            },
        )
        return
    }
    EmptyState(
        icon = Icons.Outlined.SearchOff,
        title = "No heroes match",
        message = when {
            query.isBlank() -> "No hero fits all of these filters. Remove one to widen the search."
            filtered -> "Nothing matches “${query.trim()}” with these filters. Try another word or clear them."
            else -> "Nothing matches “${query.trim()}”. Try another word, or fewer words."
        },
        action = {
            OutlinedButton(onClick = { onAction(CatalogAction.ClearAll) }) {
                Text(if (filtered) "Clear search and filters" else "Clear search")
            }
        },
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.sm),
        placeholder = { Text(SEARCH_HINT, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
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
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

@Composable
private fun ControlsRow(state: CatalogUiState.Ready, onAction: (CatalogAction) -> Unit, onOpenFilters: () -> Unit) {
    val filter = state.controls.filter
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "filters") {
            FilterChip(
                colors = goaFilterChipColors(),
                selected = filter.activeCount > 0,
                onClick = onOpenFilters,
                label = { Text(if (filter.activeCount > 0) "Filters · ${filter.activeCount}" else "Filters") },
                leadingIcon = {
                    Icon(Icons.Outlined.Tune, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
                },
            )
        }
        item(key = "sort") { SortChip(state.controls.sort) { onAction(CatalogAction.SetSort(it)) } }
        items(activeFilterChips(filter, state.options), key = { it.key }) { chip ->
            InputChip(
                colors = goaInputChipColors(),
                selected = true,
                onClick = { onAction(CatalogAction.SetFilter(chip.remove(filter))) },
                label = { Text(chip.label) },
                trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, Modifier.size(16.dp)) },
                modifier = Modifier.semantics { contentDescription = "Remove filter ${chip.label}" },
            )
        }
    }
}

@Composable
private fun SortChip(sort: CatalogSort, onSort: (CatalogSort) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            colors = goaFilterChipColors(),
            selected = false,
            onClick = { open = true },
            label = { Text("Sort: ${sort.label}") },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
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
private fun ResultSummary(state: CatalogUiState.Ready, query: String) {
    val text = resultSummary(state.heroes.size, state.totalInScope, query)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * Heroes (grid or compact list) and, while searching, the matching cards. Cards come first when
 * they answer the query better than any hero (a card phrase beats a hero matched only through
 * its title and another card). The grid keeps three columns on a phone up to font scale 1.3 and
 * drops to two above that, so a hero's name never has to break inside a word.
 *
 * The list has its own scroll state and returns to the top whenever the query, filters or sort
 * change: a lazy grid otherwise keeps the first visible item's key in place, which leaves the
 * best result scrolled off the top. The position is kept when nothing changed (e.g. back from a
 * hero page).
 */
@Composable
private fun Results(
    state: CatalogUiState.Ready,
    layout: CatalogLayout,
    query: String,
    onHeroClick: (String) -> Unit,
    onCardClick: (heroId: String, cardId: String) -> Unit,
) {
    val grid = layout == CatalogLayout.GRID
    val gap = if (grid) Spacing.sm else Spacing.xs
    val showCards = state.cards.isNotEmpty()
    val gridState = rememberLazyGridState()
    val resultsKey = resultsKey(query, state.controls)
    val shownFor = remember { arrayOf(resultsKey) }
    if (shownFor[0] != resultsKey) {
        shownFor[0] = resultsKey
        // Applied at the next measure, before key-based position keeping can scroll the top away.
        gridState.requestScrollToItem(0)
    }
    val minCell = if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) GRID_MIN_CELL_LARGE_FONT else GRID_MIN_CELL
    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(minCell) else GridCells.Fixed(1),
        state = gridState,
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        fun cardSection() {
            item(key = "cards-header", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                ResultsHeader("Cards", state.cards.size)
            }
            items(
                state.cards,
                key = { "card-" + it.card.id },
                span = { GridItemSpan(maxLineSpan) },
                contentType = { CARD },
            ) { result ->
                CardResultRow(result, query, onClick = { onCardClick(result.hero.id, result.card.id) })
            }
        }
        if (showCards && state.cardsFirst) cardSection()
        if (showCards) {
            item(key = "heroes-header", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                ResultsHeader("Heroes", state.heroes.size)
            }
        }
        // Content types let a tile scrolled out be reused for the next tile, not rebuilt.
        items(state.heroes, key = { it.hero.id }, contentType = { HERO }) { item ->
            val onClick = { onHeroClick(item.hero.id) }
            if (grid) {
                HeroGridTile(item, state.controls.sort, onClick, Modifier.animateItem())
            } else {
                HeroListRow(item, state.controls.sort, onClick, Modifier.animateItem())
            }
        }
        if (showCards && !state.cardsFirst) cardSection()
        item(key = "end", span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/** What the result list shows; a change scrolls it back to the top. */
internal fun resultsKey(query: String, controls: CatalogControls): String =
    catalogResultsKey(query, controls.filter, controls.sort)

@Composable
private fun ResultsHeader(title: String, count: Int) {
    Row(
        Modifier.padding(top = Spacing.sm, bottom = 2.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.size(Spacing.sm))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    val tint = MaterialTheme.colorScheme.surfaceVariant
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

/** Font scales above Android's 1.3 step (the next steps are 1.5, 1.8 and 2.0). */
private const val LARGE_FONT_SCALE = 1.4f

/** Width (dp) of the top bar's five action icons plus its padding. */
private const val TOP_BAR_ICONS_DP = 5 * 48 + 24

/** Width (dp) the "Heroes" title needs at font scale 1. */
private const val TITLE_DP = 100
private const val SEARCH_HINT = "Search heroes & cards"
private const val MOTION_MS = 250
private const val HEADER = "header"
private const val HERO = "hero"
private const val CARD = "card"
private const val PULSE_MS = 900
private const val SKELETON_TILES = 6
