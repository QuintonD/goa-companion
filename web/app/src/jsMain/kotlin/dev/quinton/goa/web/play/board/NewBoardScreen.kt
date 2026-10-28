package dev.quinton.goa.web.play.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.defaultMap
import dev.quinton.goa.feature.board.ownedIn
import dev.quinton.goa.feature.board.setup.LineupPicker
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.MapOption
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardForm
import dev.quinton.goa.feature.board.setup.NewBoardRules
import dev.quinton.goa.feature.board.setup.NewBoardUiState
import dev.quinton.goa.feature.board.setup.heroGridColumns
import dev.quinton.goa.feature.board.setup.lineupSources
import dev.quinton.goa.feature.board.size
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.rememberRetained
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconSize
import dev.quinton.goa.web.ui.ListRow
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.RowList
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.Segmented
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.Element

/** The New board form, kept for this screen's history entry (Back to it finds it filled in). */
internal class NewBoardKeep(var form: NewBoardForm? = null)

private class NewBoardInputs(
    val heroes: Map<String, BoardHero>,
    val maps: List<BoardMap>,
    val owned: Set<String>,
    val sources: List<LineupSource>,
)

/**
 * New board (PRODUCT §8, the phone's NewBoardScreen): both teams in pick order (the latest draft's
 * or match's line-up in their place until the first pick), search with "Adding to Red | Blue", a
 * grid of the owned heroes sized so they all fit without scrolling, the map and Set up. Picks
 * alternate like a draft (core:play NewBoardRules).
 */
@Composable
fun NewBoardScreen(route: Route.NewBoard) {
    val services = LocalServices.current
    val router = services.router
    val keep = rememberRetained("new-board") { NewBoardKeep() }
    var form by remember {
        mutableStateOf(
            keep.form ?: NewBoardForm(
                red = route.red.distinct().take(NewBoardUiState.MAX_PER_TEAM),
                blue = route.blue.distinct().filterNot { it in route.red }.take(NewBoardUiState.MAX_PER_TEAM),
                filling = NewBoardRules.nextTeam(route.red.size, route.blue.size, Team.BLUE),
                mapId = route.mapId,
            ),
        )
    }
    val onAction: (NewBoardAction) -> Unit = {
        form = NewBoardRules.reduce(form, it)
        keep.form = form
    }
    val inputs by remember { newBoardInputs(services.data) }.collectAsState(null)
    var sources by remember { mutableStateOf(false) }
    val loaded = inputs
    val state = loaded?.let { readyState(form, it) }

    Screen("New board", fill = true, actions = {
        if (state != null) {
            if (form.red.isNotEmpty() ||
                form.blue.isNotEmpty()
            ) {
                GoaButton("Clear", { onAction(NewBoardAction.Clear) }, ButtonKind.TEXT)
            }
            if (state.sources.isNotEmpty()) IconButton("list", "Use a draft or match", onClick = { sources = true })
        }
    }) {
        if (state == null) {
            LoadingState()
            return@Screen
        }
        NewBoardContent(state, onAction, onSetUp = {
            if (state.canSetUp) {
                router.navigate(
                    Route.OpenBoard(red = state.form.red, blue = state.form.blue, mapId = state.map?.id),
                )
            }
        }) { router.navigate(openRoute(it)) }
    }
    if (state != null && sources) {
        SourcesSheet(state.sources, onUse = { onAction(NewBoardAction.Use(it)) }, onDismiss = { sources = false })
    }
}

/** "Use a draft or match": finished drafts and logged matches, newest first (New board and New game). */
@Composable
internal fun SourcesSheet(sources: List<LineupSource>, onUse: (LineupSource) -> Unit, onDismiss: () -> Unit) {
    BottomSheet("Use a draft or match", onDismiss = onDismiss) {
        Span({ classes("t-body-medium", "goa-board-muted") }) { Text("Fills both teams in pick order.") }
        if (sources.isEmpty()) {
            P({ classes("t-body-medium") }) { Text("Finished drafts and logged matches appear here.") }
        }
        RowList {
            sources.forEach { source ->
                ListRow(
                    title = listOf(source.red, source.blue).filter { it.isNotEmpty() }
                        .joinToString(" vs ") { team -> team.joinToString(", ") { it.name } },
                    subtitle = "${source.kind.label} · ${shortDay(source.at)}",
                    onClick = {
                        onUse(source)
                        onDismiss()
                    },
                )
            }
        }
    }
}

private fun newBoardInputs(data: dev.quinton.goa.web.data.WebServices): Flow<NewBoardInputs> {
    val heroes = flow { emit(data.content.catalog.catalog.first().boardHeroes()) }.catch { emit(emptyMap()) }
    val maps = data.play.boardMaps.maps.catch { emit(emptyList()) }
    val owned = flow { emit(ownedProducts(data.content.settings)) }
    val sources = combine(drafts(data), matches(data)) { d, m -> d to m }
    return combine(heroes, maps, owned, sources) { h, m, o, (d, ma) ->
        NewBoardInputs(h, m, o, lineupSources(d, ma, h, MAX_SOURCES))
    }
}

/** The phone's NewBoardViewModel state, from the same pieces. */
private fun readyState(form: NewBoardForm, inputs: NewBoardInputs): NewBoardUiState.Ready {
    val all = inputs.heroes.values.sortedBy { it.name }
    // Heroes already picked always show, owned or not (a draft may have used a friend's copy).
    val shown = if (form.showAll) all else all.filter { it.ownedIn(inputs.owned) || form.contains(it.id) }
    return NewBoardUiState.Ready(
        form = form,
        heroes = shown,
        heroById = inputs.heroes,
        hiddenHeroes = all.size - shown.size,
        ownedOnly = inputs.owned.isNotEmpty(),
        maps = inputs.maps.map { it.option() },
        map = defaultMap(inputs.maps, form.lineup.size, form.mapId)?.option(),
        sources = inputs.sources,
    )
}

private fun BoardMap.option() = MapOption(id, name, lanes.size, verified)

@Composable
private fun NewBoardContent(
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
    onSetUp: () -> Unit,
    onSetUpLatest: (LineupSource) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val shown = state.heroes.filter {
        q.isEmpty() || it.name.contains(q, ignoreCase = true) ||
            it.title.contains(q, ignoreCase = true)
    }
    // The latest line-up is offered until the teams are being picked by hand.
    val latest = state.latest?.takeIf { state.form.red.isEmpty() && state.form.blue.isEmpty() }
    Div({ classes("goa-board-new") }) {
        Div({ classes("goa-board-new-side") }) {
            Div({ classes("goa-board-new-top") }) {
                if (latest != null) {
                    LatestCard(latest, onSetUp = {
                        onSetUpLatest(latest)
                    }, onEdit = { onAction(NewBoardAction.Use(latest)) })
                } else {
                    Teams(state.picker, onAction)
                }
            }
            SetUpBar(state, onAction, onSetUp, extra = "wide")
        }
        Div({ classes("goa-board-new-main") }) {
            Div({ classes("goa-board-new-search") }) {
                SearchField(query, "Search") { query = it }
                TeamSwitch(state.form.filling) { onAction(NewBoardAction.Fill(it)) }
            }
            HeroGrid(shown, state.picker, onAction)
            SetUpBar(state, onAction, onSetUp, extra = "narrow")
        }
    }
}

/** "Adding to": the team the next tapped hero joins. */
@Composable
internal fun TeamSwitch(filling: Team, onPick: (Team) -> Unit) {
    Div({ classes("goa-board-switch-row") }) {
        Span({ classes("t-label-medium", "goa-board-muted") }) { Text("Adding to") }
        Segmented("Adding to", Team.entries, filling, { BoardText.team(it) }) { onPick(it) }
    }
}

/** Both teams in pick order; tap a team to pick for it next, a pick to move or remove it. */
@Composable
internal fun Teams(state: LineupPicker, onAction: (NewBoardAction) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    Div({ classes("goa-board-teams") }) {
        Team.entries.forEach { team ->
            val filling = state.form.filling == team
            val picks = state.team(team)
            Div({
                classes("goa-board-team", teamClass(team))
                if (filling) classes("filling")
            }) {
                Button({
                    classes("goa-board-team-label")
                    attr("type", "button")
                    attr(
                        "aria-label",
                        "${BoardText.team(team)} team" +
                            if (filling) ", next pick" else ", pick for ${BoardText.team(team)} next",
                    )
                    onClick { onAction(NewBoardAction.Fill(team)) }
                }) {
                    TeamMark(team)
                    Span({ classes("t-label-large") }) { Text(BoardText.team(team)) }
                    if (filling) Span({ classes("goa-board-next") }) { Text("Next pick") }
                }
                Div({ classes("goa-board-slots") }) {
                    if (picks.isEmpty()) {
                        Button({
                            classes("goa-board-slot-empty", "t-body-small")
                            attr("type", "button")
                            onClick { onAction(NewBoardAction.Fill(team)) }
                        }) { Text(if (filling) "Tap heroes below" else "Tap here to pick for ${BoardText.team(team)}") }
                    }
                    picks.forEachIndexed { index, hero ->
                        Button({
                            classes("goa-board-slot")
                            attr("type", "button")
                            attr("aria-label", "Pick ${index + 1}: ${hero.name}. Change")
                            onClick { editing = hero.id }
                        }) {
                            HeroPortrait(hero, team, "small")
                            Span({ classes("goa-board-slot-n") }) { Text("${index + 1}") }
                            Span({ classes("t-label-medium", "goa-board-ellipsis") }) { Text(hero.name) }
                        }
                    }
                }
            }
        }
    }
    val heroId = editing ?: return
    val hero = state.heroById[heroId] ?: return
    val team = if (heroId in state.form.red) Team.RED else Team.BLUE
    val list = state.form.team(team)
    val index = list.indexOf(heroId)
    BottomSheet(hero.name, onDismiss = { editing = null }) {
        RowList {
            fun act(action: NewBoardAction) {
                onAction(action)
                editing = null
            }
            if (index > 0) ListRow("Move earlier", onClick = { act(NewBoardAction.Reorder(heroId, -1)) })
            if (index in
                0 until list.lastIndex
            ) {
                ListRow("Move later", onClick = { act(NewBoardAction.Reorder(heroId, 1)) })
            }
            if (state.form.team(team.opponent).size < NewBoardUiState.MAX_PER_TEAM) {
                ListRow("Move to ${BoardText.team(team.opponent)}", onClick = {
                    act(NewBoardAction.SwitchTeam(heroId))
                })
            }
            ListRow("Remove", leadingIcon = "delete", onClick = { act(NewBoardAction.Remove(heroId)) })
        }
    }
}

/**
 * The heroes to pick from, as many columns as it takes to show them all without scrolling (portraits
 * 42–64 px, each with its gap a 48 px tap; core:play heroGridColumns), "Show all heroes" below.
 */
@Composable
internal fun HeroGrid(heroes: List<BoardHero>, state: LineupPicker, onAction: (NewBoardAction) -> Unit) {
    var size by remember { mutableStateOf(0f to 0f) }
    val extra = if (state.hiddenHeroes > 0 || state.form.showAll) SHOW_ALL_PX else 0f
    val columns = if (size.first <= 0f) {
        MIN_COLUMNS
    } else {
        heroGridColumns(
            count = heroes.size,
            width = size.first,
            height = size.second,
            label = LABEL_PX,
            spacing = GAP_PX,
            extra = extra,
            minTile = MIN_TILE,
            maxTile = MAX_TILE,
        )
    }
    Div({
        classes("goa-board-grid-box")
        ref { el ->
            val stop = observe(el) { w, h -> size = w to h }
            onDispose { stop() }
        }
    }) {
        Div({
            classes("goa-board-grid")
            style { property("grid-template-columns", "repeat($columns, minmax(0, 1fr))") }
        }) {
            if (heroes.isEmpty()) Span({ classes("t-body-medium") }) { Text("No hero matches this search.") }
            heroes.forEach { hero -> HeroTile(hero, state, onAction) }
        }
        if (state.hiddenHeroes > 0 || state.form.showAll) {
            GoaButton(
                if (state.form.showAll) {
                    "Only heroes you own"
                } else {
                    "Show all heroes (${state.hiddenHeroes} you don't own)"
                },
                { onAction(NewBoardAction.ShowAll(!state.form.showAll)) },
                ButtonKind.TEXT,
                block = true,
            )
        }
    }
}

@Composable
private fun HeroTile(hero: BoardHero, state: LineupPicker, onAction: (NewBoardAction) -> Unit) {
    val team = when (hero.id) {
        in state.form.red -> Team.RED
        in state.form.blue -> Team.BLUE
        else -> null
    }
    val pick = team?.let { state.form.team(it).indexOf(hero.id) + 1 }
    val later = hero.id in state.unavailable
    val filling = state.form.filling
    val description = when {
        team != null -> "${hero.name}, ${BoardText.team(team)} pick $pick"
        later -> "${hero.name}, coming later"
        else -> hero.name
    }
    val action = when {
        later -> null
        team != null -> "Remove from ${BoardText.team(team)}"
        state.form.team(filling).size < NewBoardUiState.MAX_PER_TEAM -> "Add to ${BoardText.team(filling)}"
        else -> "Add to ${BoardText.team(filling.opponent)}"
    }
    Button({
        classes("goa-board-tile")
        team?.let { classes("picked", teamClass(it)) }
        if (later) classes("later")
        attr("type", "button")
        attr("aria-label", description + (action?.let { ". $it" } ?: ""))
        if (later) attr("aria-disabled", "true")
        onClick { if (!later) onAction(NewBoardAction.Tap(hero.id)) }
    }) {
        Span({ classes("goa-board-tile-art") }) {
            HeroPortrait(hero, team, "fill")
            if (pick != null) Span({ classes("goa-board-tile-pick", teamClass(team)) }) { Text("$pick") }
            if (later) Span({ classes("goa-board-tile-later") }) { Text("Later") }
        }
        Span({ classes("goa-board-tile-name") }) { Text(hero.name) }
    }
}

/** The map ("Map: Forgotten Island ▾") and Set up. */
@Composable
private fun SetUpBar(
    state: NewBoardUiState.Ready,
    onAction: (NewBoardAction) -> Unit,
    onSetUp: () -> Unit,
    extra: String,
) {
    var choosing by remember { mutableStateOf(false) }
    val map = state.map
    Div({ classes("goa-board-setup-bar", extra) }) {
        Button({
            classes("goa-board-map-pick")
            attr("type", "button")
            attr(
                "aria-label",
                "Map: ${map?.name ?: "none"}" + (if (map?.verified == false) ", unverified" else "") + ". Change map",
            )
            onClick { choosing = true }
        }) {
            Span({ classes("goa-board-overline") }) { Text("MAP") }
            Span({ classes("t-label-large", "goa-board-ellipsis") }) {
                Text(map?.name ?: "No map in this build")
                if (map?.verified == false) Span({ classes("goa-board-unverified") }) { Text("Unverified") }
            }
            Icon("chevron_down", IconSize.SMALL)
        }
        GoaButton(
            "Set up ${state.form.red.size} v ${state.form.blue.size}",
            onSetUp,
            ButtonKind.FILLED,
            enabled = state.canSetUp,
        )
    }
    if (choosing) {
        BottomSheet("Map", onDismiss = { choosing = false }) {
            RowList {
                state.maps.forEach { option ->
                    ListRow(
                        option.name,
                        subtitle = listOfNotNull(
                            if (option.lanes > 1) "${option.lanes} lanes" else "Single lane",
                            "Unverified".takeIf { !option.verified },
                        ).joinToString(" · "),
                        trailing = if (option.id == map?.id) "Chosen" else null,
                        onClick = {
                            onAction(NewBoardAction.ChooseMap(option.id))
                            choosing = false
                        },
                    )
                }
            }
        }
    }
}

/** Watches [element]'s size; returns the way to stop. */
private fun observe(element: Element, onSize: (Float, Float) -> Unit): () -> Unit {
    val callback: (dynamic) -> Unit = {
        val rect = element.getBoundingClientRect()
        onSize(rect.width.toFloat(), rect.height.toFloat())
    }
    val observer: dynamic = js("new ResizeObserver(callback)")
    observer.observe(element)
    callback(null)
    return { observer.disconnect() }
}

private const val MAX_SOURCES = 10
private const val MIN_COLUMNS = 5
private const val LABEL_PX = 18f
private const val GAP_PX = 6f
private const val SHOW_ALL_PX = 48f
private const val MIN_TILE = 42f
private const val MAX_TILE = 64f
