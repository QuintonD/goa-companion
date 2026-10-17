package dev.quinton.goa.feature.catalog.list

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import dev.quinton.goa.feature.catalog.model.CatalogFilter
import dev.quinton.goa.feature.catalog.model.CatalogSort
import dev.quinton.goa.feature.catalog.ui.complexityLabel
import dev.quinton.goa.feature.catalog.ui.goaFilterChipColors
import dev.quinton.goa.feature.catalog.ui.goaInputChipColors
import dev.quinton.goa.feature.catalog.ui.label
import dev.quinton.goa.feature.catalog.ui.plural

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
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val ready = state as? CatalogUiState.Ready
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
        message = if (query.isNotBlank()) {
            "Nothing matches “${query.trim()}” with these filters. Try another word or clear them."
        } else {
            "No hero fits all of these filters. Remove one to widen the search."
        },
        action = {
            OutlinedButton(onClick = { onAction(CatalogAction.ClearAll) }) {
                Text("Clear search and filters")
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

private data class ActiveChip(val key: String, val label: String, val remove: (CatalogFilter) -> CatalogFilter)

private fun activeFilterChips(filter: CatalogFilter, options: FilterOptions): List<ActiveChip> = buildList {
    filter.complexities.sorted().forEach { level ->
        add(ActiveChip("c$level", complexityLabel(level)) { it.copy(complexities = it.complexities - level) })
    }
    filter.roles.sortedBy { it.ordinal }.forEach { role ->
        add(ActiveChip("r${role.name}", role.label) { it.copy(roles = it.roles - role) })
    }
    filter.attackStyle?.let { style -> add(ActiveChip("a", style.label) { it.copy(attackStyle = null) }) }
    filter.products.forEach { id ->
        val name = options.products.firstOrNull { it.id == id }?.name ?: id
        add(ActiveChip("p$id", name) { it.copy(products = it.products - id) })
    }
}

@Composable
private fun ResultSummary(state: CatalogUiState.Ready, query: String) {
    val text = when {
        query.isBlank() && !state.isFiltered -> plural(state.totalInScope, "hero", "heroes")
        else -> "${state.heroes.size} of ${plural(state.totalInScope, "hero", "heroes")}"
    }
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
 * its title and another card). Cell width does not grow with the font scale, so the grid keeps
 * three columns on a phone; tiles wrap or ellipsize their text instead.
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
    LazyVerticalGrid(
        columns = if (grid) GridCells.Adaptive(GRID_MIN_CELL) else GridCells.Fixed(1),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier.fillMaxSize(),
    ) {
        fun cardSection() {
            item(key = "cards-header", span = { GridItemSpan(maxLineSpan) }) {
                ResultsHeader("Cards", state.cards.size)
            }
            items(state.cards, key = { "card-" + it.card.id }, span = { GridItemSpan(maxLineSpan) }) { result ->
                CardResultRow(result, query, onClick = { onCardClick(result.hero.id, result.card.id) })
            }
        }
        if (showCards && state.cardsFirst) cardSection()
        if (showCards) {
            item(key = "heroes-header", span = { GridItemSpan(maxLineSpan) }) {
                ResultsHeader("Heroes", state.heroes.size)
            }
        }
        items(state.heroes, key = { it.hero.id }) { item ->
            val onClick = { onHeroClick(item.hero.id) }
            if (grid) {
                HeroGridTile(item, state.controls.sort, onClick, Modifier.animateItem())
            } else {
                HeroListRow(item, state.controls.sort, onClick, Modifier.animateItem())
            }
        }
        if (showCards && !state.cardsFirst) cardSection()
        item(key = "end", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(Spacing.sm)) }
    }
}

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
private const val SEARCH_HINT = "Search heroes and card text"
private const val MOTION_MS = 250
private const val PULSE_MS = 900
private const val SKELETON_TILES = 6
