package dev.quinton.goa.web.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.catalog.CardResult
import dev.quinton.goa.core.domain.catalog.CatalogResults
import dev.quinton.goa.core.domain.catalog.CatalogSort
import dev.quinton.goa.core.domain.catalog.HeroListItem
import dev.quinton.goa.core.domain.catalog.activeFilterChips
import dev.quinton.goa.core.domain.catalog.catalogResults
import dev.quinton.goa.core.domain.catalog.cleared
import dev.quinton.goa.core.domain.catalog.kindLabel
import dev.quinton.goa.core.domain.catalog.matchHint
import dev.quinton.goa.core.domain.catalog.matchingParagraph
import dev.quinton.goa.core.domain.catalog.metricLine
import dev.quinton.goa.core.domain.catalog.plural
import dev.quinton.goa.core.domain.catalog.rangedLabel
import dev.quinton.goa.core.domain.catalog.resultSummary
import dev.quinton.goa.core.domain.catalog.spokenLabel
import dev.quinton.goa.core.domain.catalog.traitsLine
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RouteLink
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberLoad
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Section
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLElement

private sealed interface CatalogSheet {
    data object Filters : CatalogSheet

    data object Sort : CatalogSheet

    data object More : CatalogSheet
}

/**
 * The Heroes tab (feature:catalog `CatalogScreen`): search heroes and cards, filters and sort, the
 * portrait grid or the compact list, matching cards while searching. The results are the phone's
 * (`catalogResults` in core); the search, filters and sort last while the page is open.
 */
@Composable
fun CatalogScreen() {
    val services = LocalServices.current
    val content = services.data.content
    val browse = content.browse
    val indexed = rememberLoad(Unit) { browse.indexed() }
    val settings by remember { optional<AppSettings?>(null) { content.settings.settings } }.collectAsState(null)
    val ratings by remember { optional(emptyMap()) { services.data.group.ratings.observeHeroRatings() } }
        .collectAsState(emptyMap())
    val usage by remember { optional(emptyMap()) { services.data.group.ratings.observeHeroUsage() } }
        .collectAsState(emptyMap())
    var sheet by remember { mutableStateOf<CatalogSheet?>(null) }
    val scope = rememberCoroutineScope()
    val listView = settings?.catalogListView == true
    fun setListView(list: Boolean) {
        scope.launch { content.settings.update { it.copy(catalogListView = list) } }
    }

    Screen(
        "Heroes",
        actions = {
            IconButton(
                if (listView) "grid_view" else "view_agenda",
                if (listView) "Show as grid" else "Show as list",
                onClick = { setListView(!listView) },
            )
            IconLink("rules", "Rules", Route.Rules)
            IconLink("board", "Board", Route.Boards, extraClass = "collapsible")
            IconLink("trivia", "Card trivia", Route.Trivia(), extraClass = "collapsible")
            IconButton("more", "More options", onClick = { sheet = CatalogSheet.More }, extraClass = "more-only")
            IconLink("settings", "Settings", Route.Settings)
        },
    ) {
        LoadContent(indexed, "Couldn't load the heroes") { ready ->
            val owned = settings?.ownedProducts
            if (owned == null) {
                LoadingState("Loading heroes")
            } else {
                val results = remember(ready, owned, browse.query, browse.filter, browse.sort, ratings, usage) {
                    catalogResults(
                        ready.catalog,
                        ready.index,
                        owned,
                        browse.query,
                        browse.filter,
                        browse.sort,
                        ratings,
                        usage,
                    )
                }
                if (results == null) {
                    EmptyState(
                        "nav_heroes",
                        "No heroes yet",
                        "Hero data isn't bundled with this build yet. " +
                            "It appears here as soon as the catalog is imported.",
                    )
                } else {
                    CatalogContent(browse, results, listView, onOpen = { sheet = it })
                    when (sheet) {
                        CatalogSheet.Filters -> FilterSheet(browse, results, onDone = { sheet = null })

                        CatalogSheet.Sort -> SortSheet(browse.sort, onPick = {
                            browse.sort = it
                            sheet = null
                        }, onDismiss = { sheet = null })

                        else -> Unit
                    }
                }
            }
        }
    }
    if (sheet == CatalogSheet.More) {
        BottomSheet("More", onDismiss = { sheet = null }) {
            RowList {
                ListRow("Board", leadingIcon = "board", route = Route.Boards)
                ListRow("Card trivia", leadingIcon = "trivia", route = Route.Trivia())
            }
        }
    }
}

@Composable
private fun CatalogContent(
    browse: CatalogBrowse,
    results: CatalogResults,
    listView: Boolean,
    onOpen: (CatalogSheet) -> Unit,
) {
    SearchField(browse.query, onChange = { browse.query = it })
    ControlsRow(browse, results, onOpen)
    P({
        classes("goa-catalog-summary", "t-label-medium", "c-variant")
        attr("aria-live", "polite")
    }) { Text(resultSummary(results.heroes.size, results.totalInScope, browse.query)) }
    if (results.heroes.isEmpty()) {
        NoResults(browse, results)
        return
    }
    val showCards = results.cards.isNotEmpty()
    val cardsFirst = showCards && results.cardsFirst
    // A new search, filter or sort starts at the top, as on the phone (the best result first).
    val resultsKey = "${browse.query.trim()}|${browse.filter}|${browse.sort}"
    ScrollToTopOn(resultsKey)
    if (cardsFirst) CardResults(results.cards, browse.query)
    if (showCards) ResultsHeader("Heroes", results.heroes.size)
    Div({
        classes(if (listView) "goa-hero-list" else "goa-hero-grid")
        attr("role", "list")
    }) {
        results.heroes.forEach { item ->
            if (listView) HeroListRow(item, browse.sort) else HeroGridTile(item, browse.sort)
        }
    }
    if (showCards && !cardsFirst) CardResults(results.cards, browse.query)
}

/** Scrolls the page back to the top when [key] changes (not on first show: Back keeps the place). */
@Composable
private fun ScrollToTopOn(key: String) {
    val shown = remember { arrayOf(key) }
    LaunchedEffect(key) {
        if (shown[0] != key) {
            shown[0] = key
            kotlinx.browser.window.scrollTo(0.0, 0.0)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    Div({ classes("goa-search") }) {
        Span({ classes("goa-search-icon") }) { Icon("search", IconSize.SMALL) }
        Input(InputType.Search) {
            classes("goa-search-input", "t-body-large")
            value(query)
            attr("placeholder", SEARCH_HINT)
            attr("aria-label", SEARCH_HINT)
            attr("enterkeyhint", "search")
            attr("autocomplete", "off")
            attr("autocorrect", "off")
            attr("autocapitalize", "off")
            attr("spellcheck", "false")
            onInput { onChange(it.value) }
            // Enter / the keyboard's Search key closes the keyboard so the results show.
            onKeyDown { if (it.key == "Enter") (it.target as? HTMLElement)?.blur() }
        }
        if (query.isNotEmpty()) {
            IconButton("close", "Clear search", onClick = { onChange("") }, extraClass = "goa-search-clear")
        }
    }
}

@Composable
private fun ControlsRow(browse: CatalogBrowse, results: CatalogResults, onOpen: (CatalogSheet) -> Unit) {
    val filter = results.filter
    Div({ classes("goa-chip-row", "goa-catalog-controls") }) {
        ChipButton(
            if (filter.activeCount > 0) "Filters · ${filter.activeCount}" else "Filters",
            icon = "tune",
            selected = filter.activeCount > 0,
            haspopup = true,
        ) { onOpen(CatalogSheet.Filters) }
        ChipButton("Sort: ${browse.sort.label}", icon = "sort", haspopup = true) { onOpen(CatalogSheet.Sort) }
        activeFilterChips(filter, results.options).forEach { chip ->
            Button({
                type(ButtonType.Button)
                classes("goa-chip", "goa-chip-input")
                attr("aria-label", "Remove filter ${chip.label}")
                onClick { browse.filter = chip.remove(filter) }
            }) {
                Text(chip.label)
                Icon("close", IconSize.SMALL)
            }
        }
    }
}

/** A chip that does something (opens a sheet): [selected] tints it like an active filter. */
@Composable
internal fun ChipButton(
    label: String,
    icon: String? = null,
    selected: Boolean = false,
    haspopup: Boolean = false,
    onClick: () -> Unit,
) {
    Button({
        type(ButtonType.Button)
        classes("goa-chip")
        if (selected) classes("active")
        if (haspopup) attr("aria-haspopup", "dialog")
        onClick { onClick() }
    }) {
        icon?.let { Icon(it, IconSize.SMALL) }
        Text(label)
    }
}

@Composable
private fun NoResults(browse: CatalogBrowse, results: CatalogResults) {
    val hidden = results.hiddenByCollection
    val filtered = results.filter.activeCount > 0
    val query = browse.query
    if (hidden > 0) {
        // The collection (Settings → owned products) hides heroes that would match.
        EmptyState(
            "search_off",
            "Not in your collection",
            "${plural(hidden, "hero", "heroes")} from products you don't own " +
                (if (hidden == 1) "matches" else "match") + ". Your collection is set in Settings.",
        ) {
            GoaButton("Show all products", {
                browse.filter = results.filter.copy(includeUnowned = true)
            }, ButtonKind.OUTLINED)
        }
        return
    }
    EmptyState(
        "search_off",
        "No heroes match",
        when {
            query.isBlank() -> "No hero fits all of these filters. Remove one to widen the search."
            filtered -> "Nothing matches “${query.trim()}” with these filters. Try another word or clear them."
            else -> "Nothing matches “${query.trim()}”. Try another word, or fewer words."
        },
    ) {
        GoaButton(if (filtered) "Clear search and filters" else "Clear search", {
            browse.query = ""
            browse.filter = browse.filter.cleared()
        }, ButtonKind.OUTLINED)
    }
}

@Composable
private fun ResultsHeader(title: String, count: Int) {
    H2({ classes("goa-results-header", "t-title-small") }) {
        Text(title)
        Span({ classes("t-label-medium", "c-variant") }) { Text(count.toString()) }
    }
}

@Composable
private fun CardResults(cards: List<CardResult>, query: String) {
    Section({ attr("aria-label", "Cards") }) {
        ResultsHeader("Cards", cards.size)
        Div({
            classes("goa-card-results")
            attr("role", "list")
        }) {
            cards.forEach { CardResultRow(it, query) }
        }
    }
}

/** One card in the search results: name, hero and kind, the paragraph that matched, initiative. */
@Composable
private fun CardResultRow(result: CardResult, query: String) {
    val card = result.card
    val snippet = remember(card.text, query) { matchingParagraph(card.text, query) }
    Div({ attr("role", "listitem") }) {
        RouteLink(Route.CardViewer(result.hero.id, card.id), {
            classes("goa-card-result", card.color.cssClass)
        }) {
            Div({ classes("goa-card-result-text") }) {
                Span({ classes("goa-card-result-name", "t-title-small") }) { Text(card.name) }
                Span({ classes("t-label-medium", "c-variant", "goa-ellipsis") }) {
                    Text("${result.hero.name} · ${card.kindLabel(result.isHandicap)}")
                }
                if (snippet.isNotBlank()) CardTextView(snippet, "t-body-medium", clampLines = SNIPPET_LINES)
            }
            InitiativeBadge(card.initiative, card.color)
        }
    }
}

/**
 * Compact grid tile (three per row on a phone): short portrait with stars and deck symbol, then
 * name, title, roles and pills. The whole tile is one link, read as [spokenLabel].
 */
@Composable
private fun HeroGridTile(item: HeroListItem, sort: CatalogSort) {
    val hero = item.hero
    Div({ attr("role", "listitem") }) {
        RouteLink(Route.HeroDetail(hero.id), {
            classes("goa-hero-tile")
            attr("aria-label", item.spokenLabel(sort))
        }) {
            Div({ classes("goa-hero-tile-art") }) {
                HeroArt(hero, "tile")
                Span({ classes("goa-hero-tile-stars") }) { ComplexityStars(hero.complexity) }
                HeroSymbol(hero, "tile")
                if (!hero.verified) Span({ classes("goa-hero-tile-badge") }) { UnverifiedBadge(compact = true) }
            }
            Div({
                classes("goa-hero-tile-text")
                attr("aria-hidden", "true")
            }) {
                Span({ classes("goa-hero-tile-name", "t-title-small") }) { Text(hero.name) }
                Span({ classes("goa-hero-tile-title", "t-label-small", "c-variant") }) { Text(hero.title) }
                val traits = item.traitsLine()
                if (traits.isNotEmpty()) Span({ classes("goa-hero-traits", "t-label-small") }) { Text(traits) }
                Div({ classes("goa-pills") }) {
                    MetaPill(item.productName)
                    item.rangedLabel?.let { MetaPill(it, primary = true) }
                }
                TileFootnotes(item, sort)
            }
        }
    }
}

@Composable
private fun TileFootnotes(item: HeroListItem, sort: CatalogSort) {
    item.metricLine(sort)?.let { P({ classes("goa-hero-metric", "t-label-medium") }) { Text(it) } }
    item.matchHint()?.let { hint ->
        P({ classes("goa-hero-hint", "t-label-small", "c-variant") }) {
            Icon("search", IconSize.SMALL)
            Text(hint)
        }
    }
}

/** Compact list row (six or more per screen): small portrait, name and title, stars and pills, roles. */
@Composable
private fun HeroListRow(item: HeroListItem, sort: CatalogSort) {
    val hero = item.hero
    Div({ attr("role", "listitem") }) {
        RouteLink(Route.HeroDetail(hero.id), {
            classes("goa-hero-row")
            attr("aria-label", item.spokenLabel(sort))
        }) {
            Div({ classes("goa-hero-row-art") }) {
                HeroArt(hero, "row")
                HeroSymbol(hero, "row")
            }
            Div({
                classes("goa-hero-row-text")
                attr("aria-hidden", "true")
            }) {
                P({ classes("goa-hero-row-names") }) {
                    Span({ classes("t-title-medium") }) { Text(hero.name) }
                    Span({ classes("t-body-small", "c-variant") }) { Text(hero.title) }
                }
                Div({ classes("goa-pills") }) {
                    ComplexityStars(hero.complexity)
                    MetaPill(item.productName)
                    item.rangedLabel?.let { MetaPill(it, primary = true) }
                    if (!hero.verified) UnverifiedBadge(compact = true)
                }
                val traits = item.traitsLine()
                if (traits.isNotEmpty()) {
                    P({
                        classes("goa-hero-traits", "t-label-small", "goa-ellipsis")
                    }) { Text(traits) }
                }
                TileFootnotes(item, sort)
            }
            Span({ classes("goa-hero-row-chevron", "c-variant") }) { Icon("chevron_right", IconSize.SMALL) }
        }
    }
}

@Composable
private fun SortSheet(current: CatalogSort, onPick: (CatalogSort) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Sort by", onDismiss = onDismiss) {
        Div({
            classes("goa-radio-list")
            attr("role", "radiogroup")
            attr("aria-label", "Sort by")
        }) {
            CatalogSort.entries.forEach { option ->
                Button({
                    type(ButtonType.Button)
                    classes("goa-radio-row", "t-body-large")
                    attr("role", "radio")
                    attr("aria-checked", (option == current).toString())
                    onClick { onPick(option) }
                }) {
                    Span { Text(option.label) }
                    if (option == current) Icon("check", IconSize.SMALL)
                }
            }
        }
    }
}

/** A labelled on/off switch row (a real checkbox with the switch role). */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Label(attrs = { classes("goa-switch-row") }) {
        Span({ classes("goa-switch-text") }) {
            Span({ classes("t-body-large") }) { Text(title) }
            subtitle?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
        }
        Input(InputType.Checkbox) {
            classes("goa-switch")
            attr("role", "switch")
            checked(checked)
            onChange { onChange(it.value) }
        }
    }
}

private const val SEARCH_HINT = "Search heroes & cards"
private const val SNIPPET_LINES = 3
