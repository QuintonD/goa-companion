package dev.quinton.goa.web.play.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiStrength
import dev.quinton.goa.core.game.GameLength
import dev.quinton.goa.core.model.board.BoardMap
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.board.BoardHero
import dev.quinton.goa.feature.board.BoardText
import dev.quinton.goa.feature.board.boardHeroes
import dev.quinton.goa.feature.board.hero
import dev.quinton.goa.feature.board.ownedIn
import dev.quinton.goa.feature.board.play.PlayText
import dev.quinton.goa.feature.board.play.setup.Control
import dev.quinton.goa.feature.board.play.setup.NewGameAction
import dev.quinton.goa.feature.board.play.setup.NewGameForm
import dev.quinton.goa.feature.board.play.setup.NewGameForms
import dev.quinton.goa.feature.board.play.setup.NewGameRules
import dev.quinton.goa.feature.board.play.setup.NewGameStep
import dev.quinton.goa.feature.board.play.setup.lineupNote
import dev.quinton.goa.feature.board.setup.LineupPicker
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.MapOption
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardForm
import dev.quinton.goa.feature.board.setup.NewBoardRules
import dev.quinton.goa.feature.board.setup.lineupSources
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.catalog.UnverifiedBadge
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.board.HeroGrid
import dev.quinton.goa.web.play.board.SearchField
import dev.quinton.goa.web.play.board.SourcesSheet
import dev.quinton.goa.web.play.board.TeamSwitch
import dev.quinton.goa.web.play.board.Teams
import dev.quinton.goa.web.play.table.Overline
import dev.quinton.goa.web.play.table.Quiet
import dev.quinton.goa.web.play.table.teamCss
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.play.ui.TieBreakerCoin
import dev.quinton.goa.web.storage.scoped
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.Load
import dev.quinton.goa.web.ui.LoadContent
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.rememberLoad
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** What New game reads once: the heroes, the playable maps and heroes, sources, the stored game. */
private class NewGameInputs(
    val heroes: Map<String, BoardHero>,
    val maps: List<BoardMap>,
    val playable: Set<String>,
    val playableMaps: List<BoardMap>,
    val owned: Set<String>,
    val sources: List<LineupSource>,
    val replacesGame: Boolean,
    val stored: String?,
    /** The strength of the last game started here (ADR-039): a fresh form starts at it. */
    val lastStrength: Int,
)

/**
 * New game against the computer (#/play/new, PRODUCT §9) in two steps, as on the phone: the line-up
 * (alternate picks like New board, "Use a draft or match"; heroes not playable yet are shown veiled,
 * "Later"), then who you control, the computer's strength 1–10 (one for all, or per seat,
 * ADR-039; a fresh form starts at the last game's), Quick or Long, the map, Coach; the app flips
 * the Tie Breaker coin and Start saves the game and opens it. The form survives a reload (this
 * browser's store), like the phone's saved state.
 */
@Composable
fun NewGameScreen(route: Route.NewGame) {
    val services = LocalServices.current
    val data = services.data
    val store = remember { data.store.scoped("play/") }
    val loaded = rememberLoad(route) {
        val catalog = runCatching { data.content.catalog.catalog.first() }.getOrDefault(Catalog.EMPTY)
        val maps = runCatching { data.play.boardMaps.maps.first() }.getOrDefault(emptyList())
        val info = data.play.engine.call(EngineRequest.Info) as? EngineResponse.Info
        val heroes = catalog.boardHeroes()
        val drafts = runCatching { data.group.draftRecords.observeDrafts().first() }.getOrDefault(emptyList())
        val matches = runCatching { data.group.matches.observeMatches().first() }.getOrDefault(emptyList())
        val playable = info?.playableHeroes.orEmpty()
        val settings = runCatching { data.content.settings.settings.first() }.getOrDefault(AppSettings())
        NewGameInputs(
            heroes = heroes,
            maps = maps,
            playable = playable,
            playableMaps = info?.playableMaps.orEmpty().mapNotNull { id -> maps.firstOrNull { it.id == id } },
            owned = settings.ownedProducts,
            sources = lineupSources(drafts, matches, heroes, MAX_SOURCES)
                .filter { s -> (s.lineup.red + s.lineup.blue).all { it in playable } },
            replacesGame = data.play.gameSave.current.first() !is StoredGame.None,
            stored = store.get(FORM_KEY),
            lastStrength = AiStrength.coerce(settings.computerStrength),
        )
    }
    val ready = (loaded.state as? Load.Ready)?.value
    if (ready == null) {
        Screen("New game") { LoadContent(loaded, "Couldn't load the heroes") { } }
        return
    }
    ready.let { inputs ->
        if (inputs.playable.size < 2 || inputs.playableMaps.isEmpty()) {
            Screen("New game") {
                EmptyState(
                    "swords",
                    "Playing against the computer is coming",
                    "The app is learning every rule and every card. Heroes join as soon as each of their cards " +
                        "is built and tested, so the computer never plays a card wrong. Until then, set up " +
                        "positions on the board.",
                )
            }
        } else {
            NewGameForm(route, inputs)
        }
    }
}

@Composable
private fun NewGameForm(route: Route.NewGame, inputs: NewGameInputs) {
    val services = LocalServices.current
    val data = services.data
    val router = services.router
    val scope = rememberCoroutineScope()
    val store = remember { data.store.scoped("play/") }
    val prefilled = route.red.isNotEmpty() || route.blue.isNotEmpty()
    var form by remember {
        mutableStateOf(
            inputs.stored?.takeIf { !prefilled }?.let(NewGameForms::decode)?.copy(coin = null) ?: NewGameForm(
                lineup = NewBoardForm(
                    red = route.red.distinct(),
                    blue = route.blue.distinct().filterNot { it in route.red },
                    filling = NewBoardRules.nextTeam(route.red.size, route.blue.size, Team.BLUE),
                ),
                strength = inputs.lastStrength,
            ),
        )
    }
    var starting by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    val unavailable = inputs.heroes.keys - inputs.playable
    fun act(action: NewGameAction) {
        form = NewGameRules.reduce(form, action, unavailable) { if (Random.nextBoolean()) Team.RED else Team.BLUE }
        val encoded = NewGameForms.encode(form)
        scope.launch { store.put(FORM_KEY, encoded) }
    }
    val controls = NewGameRules.controls(form.lineup)
    val control = form.control?.takeIf { it in controls } ?: controls.firstOrNull()
    val mapChoice = inputs.playableMaps.firstOrNull { it.id == form.mapId } ?: inputs.playableMaps.first()
    val map = MapOption(mapChoice.id, mapChoice.name, mapChoice.lanes.size, mapChoice.verified)
    val canContinue = form.lineup.red.isNotEmpty() && form.lineup.red.size == form.lineup.blue.size
    val options = form.step == NewGameStep.OPTIONS && canContinue
    Screen(
        if (options) "Game options" else "New game",
        fill = !options,
        // As on the phone: Back from the options returns to the line-up.
        onBack = if (options) ({ act(NewGameAction.Step(NewGameStep.LINEUP)) }) else null,
        actions = {
            if (!options) {
                if (form.lineup.red.isNotEmpty() || form.lineup.blue.isNotEmpty()) {
                    GoaButton("Clear", { act(NewGameAction.Lineup(NewBoardAction.Clear)) }, ButtonKind.TEXT)
                }
                if (inputs.sources.isNotEmpty()) IconButton("list", "Use a draft or match", { sourcesOpen = true })
            }
        },
    ) {
        if (options) {
            OptionsStep(form, inputs, controls, control, map, ::act)
        } else {
            LineupStep(form, inputs, unavailable, canContinue, ::act)
        }
    }
    if (sourcesOpen) {
        SourcesSheet(inputs.sources, onUse = { act(NewGameAction.Lineup(NewBoardAction.Use(it))) }, onDismiss = {
            sourcesOpen = false
        })
    }
    val coin = form.coin
    if (coin != null) {
        AlertDialog(
            "${BoardText.team(coin)} shows",
            onDismiss = { act(NewGameAction.CancelCoin) },
            buttons = {
                GoaButton("Back", { act(NewGameAction.CancelCoin) }, ButtonKind.TEXT)
                GoaButton("Start the game", {
                    if (starting) return@GoaButton
                    val setup = NewGameRules.setup(form, control, map.id) ?: return@GoaButton
                    starting = true
                    scope.launch {
                        val response = data.play.engine.call(EngineRequest.GameStart(setup)) as? EngineResponse.Game
                        if (response == null) {
                            starting = false
                            services.messages.show("This game couldn't be set up.")
                            return@launch
                        }
                        val now = data.clock.now()
                        data.play.gameSave.save(
                            GameInProgress(
                                game = response.game.saved.copy(savedAt = now),
                                coach = form.coach,
                                startedAt = now,
                            ),
                        )
                        store.delete(FORM_KEY)
                        val strength = form.strength
                        runCatching { data.content.settings.update { it.copy(computerStrength = strength) } }
                        router.navigate(Route.Play, replace = true)
                    }
                }, enabled = !starting)
            },
        ) {
            P({ classes("goa-coin-big", teamCss(coin)) }) {
                TieBreakerCoin(coin, big = true, spoken = "Tie Breaker coin showing ${BoardText.team(coin)}")
            }
            Text(
                "The app flipped the Tie Breaker coin and placed it by the board (p.5). When heroes of both teams " +
                    "tie on initiative, the team it shows acts first, then the coin is flipped (p.7).",
            )
        }
    }
}

/**
 * The phone's line-up step: both teams in pick order (move a pick earlier, later or to the other team),
 * search with "Adding to Red | Blue", the hero grid (heroes not playable yet veiled, "Later"), the
 * line-up's size and Next. The same Teams, HeroGrid and TeamSwitch as New board, over the picker the
 * phone's NewGameViewModel builds.
 */
@Composable
private fun LineupStep(
    form: NewGameForm,
    inputs: NewGameInputs,
    unavailable: Set<String>,
    canContinue: Boolean,
    act: (NewGameAction) -> Unit,
) {
    val lineup = form.lineup
    var query by remember { mutableStateOf("") }
    val all = inputs.heroes.values.sortedWith(compareBy<BoardHero> { it.id in unavailable }.thenBy { it.name })
    val owned = all.filter { it.ownedIn(inputs.owned) || lineup.contains(it.id) }
    val listed = if (lineup.showAll) all else owned
    val picker = LineupPicker(
        form = lineup,
        heroes = listed,
        heroById = inputs.heroes,
        hiddenHeroes = all.size - listed.size,
        ownedOnly = inputs.owned.isNotEmpty(),
        unavailable = unavailable,
    )
    val q = query.trim()
    val shown = listed.filter {
        q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true)
    }
    val pick: (NewBoardAction) -> Unit = { act(NewGameAction.Lineup(it)) }
    Div({ classes("goa-board-new") }) {
        Div({ classes("goa-board-new-side") }) {
            Div({ classes("goa-board-new-top") }) {
                Teams(picker, pick)
                PlayableNote(inputs)
            }
            LineupBar(lineup, canContinue, act, extra = "wide")
        }
        Div({ classes("goa-board-new-main") }) {
            Div({ classes("goa-board-new-search") }) {
                SearchField(query, "Search") { query = it }
                TeamSwitch(lineup.filling) { pick(NewBoardAction.Fill(it)) }
            }
            HeroGrid(shown, picker, pick)
            LineupBar(lineup, canContinue, act, extra = "narrow")
        }
    }
}

/** How many heroes can be played so far: the rest are veiled in the grid. */
@Composable
private fun PlayableNote(inputs: NewGameInputs) {
    val later = inputs.heroes.size - inputs.playable.size
    if (later <= 0) return
    val playable = BoardText.count(inputs.playable.size, "hero")
    Quiet("$playable can play so far; $later more join as their cards are tested.")
}

/** The line-up's size in the rulebook's terms (p.20), and Next. */
@Composable
private fun LineupBar(lineup: NewBoardForm, canContinue: Boolean, act: (NewGameAction) -> Unit, extra: String) {
    Div({ classes("goa-board-setup-bar", extra) }) {
        Div({ classes("goa-lineup-size") }) {
            P({ classes("t-title-medium") }) { Text("${lineup.red.size} v ${lineup.blue.size}") }
            P({ classes("t-body-small", "goa-quiet") }) { Text(lineupNote(lineup.red.size, lineup.blue.size)) }
        }
        GoaButton("Next", { act(NewGameAction.Step(NewGameStep.OPTIONS)) }, enabled = canContinue)
    }
}

@Composable
private fun OptionsStep(
    form: NewGameForm,
    inputs: NewGameInputs,
    controls: List<Control>,
    control: Control?,
    map: MapOption,
    act: (NewGameAction) -> Unit,
) {
    Button({
        classes("goa-source-row")
        attr("type", "button")
        attr("aria-label", "Line-up. Change")
        onClick { act(NewGameAction.Step(NewGameStep.LINEUP)) }
    }) {
        Span({ classes("t-overline", "goa-table-overline") }) { Text("Line-up · Change") }
        Span({ classes("t-body-medium") }) {
            Text(
                form.lineup.red.joinToString(", ") { inputs.heroes.hero(it).name } + " vs " +
                    form.lineup.blue.joinToString(", ") { inputs.heroes.hero(it).name },
            )
        }
    }
    Overline("You play")
    Div({
        classes("goa-radio-list")
        attr("role", "radiogroup")
        attr("aria-label", "You play")
    }) {
        controls.forEach { option ->
            val selected = option == control
            val (title, note) = controlText(form, option, inputs)
            Button({
                classes("goa-radio-row")
                if (selected) classes("selected")
                attr("type", "button")
                attr("role", "radio")
                attr("aria-checked", selected.toString())
                onClick { act(NewGameAction.SetControl(option)) }
            }) {
                Span({ classes("t-title-small") }) { Text(title) }
                Span({ classes("t-body-small", "goa-quiet") }) { Text(note) }
            }
        }
    }
    Overline("Computer strength")
    AiStrengthSlider(form.strength, "Computer strength") { act(NewGameAction.SetStrength(it)) }
    P({ classes("t-body-small", "goa-quiet") }) { Text(PlayText.strengthNote(form.strength)) }
    val seats = NewGameRules.aiSeats(form, control, inputs.heroes)
    if (seats.size > 1) {
        Label(attrs = { classes("goa-switch-row") }) {
            Span({ classes("t-label-large", "goa-switch-text") }) { Text("Advanced: a strength per seat") }
            Input(InputType.Checkbox) {
                classes("goa-switch")
                checked(form.advanced)
                onChange { act(NewGameAction.SetAdvanced(it.value)) }
            }
        }
        if (form.advanced) {
            seats.forEach { seat ->
                Div({ classes("goa-seat-level") }) {
                    AiStrengthSlider(
                        seat.strength,
                        "${seat.hero.name}'s strength",
                        title = {
                            Span({ classes("t-title-small") }) {
                                TeamMark(seat.team)
                                Text(" ${seat.hero.name}")
                            }
                        },
                    ) { act(NewGameAction.SetSeatStrength(seat.hero.id, it)) }
                }
            }
        }
    }
    Overline("Length")
    Div({
        classes("goa-segmented")
        attr("role", "radiogroup")
        attr("aria-label", "Length")
    }) {
        GameLength.entries.forEach { length ->
            Button({
                classes("goa-segment")
                if (length == form.length) classes("selected")
                attr("type", "button")
                attr("role", "radio")
                attr("aria-checked", (length == form.length).toString())
                onClick { act(NewGameAction.SetLength(length)) }
            }) { Text(PlayText.length(length)) }
        }
    }
    Quiet(PlayText.lengthNote(form.length))
    Overline("Map")
    Div({
        classes("goa-radio-list")
        attr("role", "radiogroup")
        attr("aria-label", "Map")
    }) {
        inputs.playableMaps.forEach { m ->
            val selected = m.id == map.id
            Button({
                classes("goa-radio-row")
                if (selected) classes("selected")
                attr("type", "button")
                attr("role", "radio")
                attr("aria-checked", selected.toString())
                onClick { act(NewGameAction.SetMap(m.id)) }
            }) {
                Span({ classes("t-title-small") }) { Text(m.name) }
                Span({ classes("t-body-small", "goa-quiet") }) {
                    Text(if (m.lanes.size > 1) "${m.lanes.size} lanes" else "Single lane")
                }
                if (!m.verified) UnverifiedBadge()
            }
        }
    }
    Overline("Coach")
    Label(attrs = { classes("goa-switch-row") }) {
        Span({ classes("goa-switch-text") }) {
            Span({ classes("t-title-small") }) { Text(if (form.coach) "On" else "Off") }
            Span({ classes("t-body-small", "goa-quiet") }) {
                Text(
                    "A gentle nudge before a clearly worse choice, and the turning points after the game. It never " +
                        "chooses for you; switch it in the game too.",
                )
            }
        }
        Input(InputType.Checkbox) {
            classes("goa-switch")
            checked(form.coach)
            onChange { act(NewGameAction.SetCoach(it.value)) }
        }
    }
    Div({ classes("goa-step-bar") }) {
        if (inputs.replacesGame) Quiet("Starting replaces the game in progress.")
        GoaButton("Flip the Tie Breaker coin", { act(NewGameAction.FlipCoin) }, enabled = control != null, block = true)
    }
}

/** A control choice in words, as on the phone. */
private fun controlText(
    form: NewGameForm,
    control: Control,
    inputs: NewGameInputs,
): Pair<String, String> = when (control) {
    is Control.WholeTeam -> {
        val ids = form.lineup.team(control.team)
        "All of ${BoardText.team(control.team)}" to
            (ids.joinToString(", ") { inputs.heroes.hero(it).name } + ". The other team is the computer.")
    }

    is Control.Hero -> {
        val team = if (control.heroId in form.lineup.red) Team.RED else Team.BLUE
        val mates = form.lineup.team(team).filter { it != control.heroId }
        inputs.heroes.hero(control.heroId).name to (
            "${BoardText.team(team)}" + if (mates.isEmpty()) {
                ""
            } else {
                ". Your teammate${if (mates.size > 1) "s" else ""} " +
                    mates.joinToString(", ") { inputs.heroes.hero(it).name } +
                    " play${if (mates.size > 1) "" else "s"} as the computer."
            }
            )
    }
}

private const val FORM_KEY = "new-game-form"
private const val MAX_SOURCES = 10
