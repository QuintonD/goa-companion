package dev.quinton.goa.web.play.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.data.game.GameInProgress
import dev.quinton.goa.core.data.game.StoredGame
import dev.quinton.goa.core.game.AiLevel
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
import dev.quinton.goa.feature.board.setup.LineupSource
import dev.quinton.goa.feature.board.setup.MapOption
import dev.quinton.goa.feature.board.setup.NewBoardAction
import dev.quinton.goa.feature.board.setup.NewBoardForm
import dev.quinton.goa.feature.board.setup.NewBoardRules
import dev.quinton.goa.feature.board.setup.lineupSources
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.engine.EngineRequest
import dev.quinton.goa.web.engine.EngineResponse
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.play.table.HeroPortrait
import dev.quinton.goa.web.play.table.Overline
import dev.quinton.goa.web.play.table.Quiet
import dev.quinton.goa.web.play.table.teamCss
import dev.quinton.goa.web.play.ui.TeamMark
import dev.quinton.goa.web.storage.scoped
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
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
)

/**
 * New game against the computer (#/play/new, PRODUCT §9) in two steps, as on the phone: the line-up
 * (alternate picks like New board, "Use a draft or match"; heroes not playable yet are shown veiled,
 * "Later"), then who you control, the computer's level (one for all, or per seat), Quick or Long,
 * the map, Coach; the app flips the Tie Breaker coin and Start saves the game and opens it. The form
 * survives a reload (this browser's store), like the phone's saved state.
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
        NewGameInputs(
            heroes = heroes,
            maps = maps,
            playable = playable,
            playableMaps = info?.playableMaps.orEmpty().mapNotNull { id -> maps.firstOrNull { it.id == id } },
            owned = runCatching { data.content.settings.settings.first() }.getOrDefault(AppSettings()).ownedProducts,
            sources = lineupSources(drafts, matches, heroes, MAX_SOURCES)
                .filter { s -> (s.lineup.red + s.lineup.blue).all { it in playable } },
            replacesGame = data.play.gameSave.current.first() !is StoredGame.None,
            stored = store.get(FORM_KEY),
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
                    "The app is learning every rule and every card. Heroes join as soon as each of their " +
                        "cards is tested.",
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
        actions = {
            if (!options) {
                IconButton("restart", "Clear the line-up", { act(NewGameAction.Lineup(NewBoardAction.Clear)) })
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
        BottomSheet("Use a draft or match", onDismiss = { sourcesOpen = false }) {
            inputs.sources.forEach { source ->
                Button({
                    classes("goa-source-row")
                    attr("type", "button")
                    onClick {
                        act(NewGameAction.Lineup(NewBoardAction.Use(source)))
                        sourcesOpen = false
                    }
                }) {
                    Span({ classes("t-overline", "goa-table-overline") }) { Text(source.kind.label) }
                    Span({ classes("t-body-medium") }) {
                        Text(
                            source.red.joinToString(", ") { it.name } + " vs " +
                                source.blue.joinToString(", ") { it.name },
                        )
                    }
                }
            }
        }
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
                        router.navigate(Route.Play, replace = true)
                    }
                }, enabled = !starting)
            },
        ) {
            P({ classes("goa-coin-big", teamCss(coin)) }) { TeamMark(coin) }
            Text(
                "The app flipped the Tie Breaker coin and placed it by the board (p.5). When heroes of both teams " +
                    "tie on initiative, the team it shows acts first, then the coin is flipped (p.7).",
            )
        }
    }
}

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
    Div({ classes("goa-lineup") }) {
        Team.entries.forEach { team ->
            val ids = lineup.team(team)
            Div({ classes("goa-lineup-team", teamCss(team)) }) {
                P({ classes("t-overline", "goa-team-label") }) {
                    TeamMark(team)
                    Text(" ${BoardText.team(team)}")
                }
                if (ids.isEmpty()) Quiet("No hero yet")
                ids.forEach { id ->
                    val hero = inputs.heroes.hero(id)
                    Div({ classes("goa-lineup-hero") }) {
                        HeroPortrait(hero, team, 32)
                        Span({ classes("t-label-large", "name") }) { Text(hero.name) }
                        IconButton("flip", "Move ${hero.name} to the other team", {
                            act(NewGameAction.Lineup(NewBoardAction.SwitchTeam(id)))
                        })
                        IconButton("close", "Remove ${hero.name}", {
                            act(NewGameAction.Lineup(NewBoardAction.Remove(id)))
                        })
                    }
                }
            }
        }
    }
    P({ classes("t-body-small", "goa-quiet") }) { Text(lineupNote(lineup.red.size, lineup.blue.size)) }
    Div({ classes("goa-filling") }) {
        Span({ classes("t-label-large") }) { Text("Adding to") }
        Div({
            classes("goa-segmented")
            attr("role", "radiogroup")
            attr("aria-label", "Adding to")
        }) {
            Team.entries.forEach { team ->
                Button({
                    classes("goa-segment", teamCss(team))
                    if (lineup.filling == team) classes("selected")
                    attr("type", "button")
                    attr("role", "radio")
                    attr("aria-checked", (lineup.filling == team).toString())
                    onClick { act(NewGameAction.Lineup(NewBoardAction.Fill(team))) }
                }) {
                    TeamMark(team)
                    Text(" ${BoardText.team(team)}")
                }
            }
        }
        Input(InputType.Search) {
            classes("goa-search")
            attr("placeholder", "Search")
            attr("aria-label", "Search heroes")
            value(query)
            onInput { query = it.value }
        }
    }
    val all = inputs.heroes.values.sortedWith(compareBy<BoardHero> { it.id in unavailable }.thenBy { it.name })
    val shown = (if (lineup.showAll) all else all.filter { it.ownedIn(inputs.owned) || lineup.contains(it.id) })
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val later = inputs.heroes.size - inputs.playable.size
    if (later > 0) {
        Quiet(
            "${BoardText.count(
                inputs.playable.size,
                "hero",
            )} can play so far; $later more join as their cards are tested.",
        )
    }
    Div({ classes("goa-hero-grid") }) {
        shown.forEach { hero ->
            val picked = lineup.contains(hero.id)
            val team = when (hero.id) {
                in lineup.red -> Team.RED
                in lineup.blue -> Team.BLUE
                else -> null
            }
            val off = hero.id in unavailable
            Button({
                classes("goa-grid-hero")
                if (picked) classes("picked")
                if (off) classes("later")
                attr("type", "button")
                attr("aria-pressed", picked.toString())
                attr(
                    "aria-label",
                    hero.name + when {
                        team != null -> ", ${BoardText.team(team)}"
                        off -> ", coming later"
                        else -> ""
                    },
                )
                if (off && !picked) attr("aria-disabled", "true")
                onClick { act(NewGameAction.Lineup(NewBoardAction.Tap(hero.id))) }
            }) {
                HeroPortrait(hero, team, 52, dim = off)
                Span({ classes("goa-grid-name") }) { Text(hero.name) }
                if (off) Span({ classes("goa-later") }) { Text("Later") }
            }
        }
    }
    val hidden = inputs.heroes.size - inputs.heroes.values.count { it.ownedIn(inputs.owned) || lineup.contains(it.id) }
    if (inputs.owned.isNotEmpty() && (hidden > 0 || lineup.showAll)) {
        GoaButton(
            if (lineup.showAll) "Only the heroes you own" else "Show all heroes ($hidden more)",
            { act(NewGameAction.Lineup(NewBoardAction.ShowAll(!lineup.showAll))) },
            ButtonKind.TEXT,
        )
    }
    Div({ classes("goa-step-bar") }) {
        GoaButton("Next", { act(NewGameAction.Step(NewGameStep.OPTIONS)) }, enabled = canContinue, block = true)
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
    Overline("Computer level")
    AiStrengthPicker(AiLevel.of(form.strength), "Computer level", onChange = { act(NewGameAction.SetStrength(it.strength)) })
    Label(attrs = { classes("goa-switch-row") }) {
        Span({ classes("t-label-large", "goa-switch-text") }) { Text("Advanced: a level per seat") }
        Input(InputType.Checkbox) {
            classes("goa-switch")
            checked(form.advanced)
            onChange { act(NewGameAction.SetAdvanced(it.value)) }
        }
    }
    if (form.advanced) {
        NewGameRules.aiSeats(form, control, inputs.heroes).forEach { seat ->
            Div({ classes("goa-seat-level") }) {
                P({ classes("t-label-large") }) {
                    TeamMark(seat.team)
                    Text(" ${seat.hero.name}")
                }
                AiStrengthPicker(AiLevel.of(seat.strength), "${seat.hero.name}'s level", onChange = {
                    act(NewGameAction.SetSeatStrength(seat.hero.id, it.strength))
                }, showNote = false)
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
            }
        }
    }
    Overline("Coach")
    Label(attrs = { classes("goa-switch-row") }) {
        Span({ classes("goa-switch-text") }) {
            Span({ classes("t-title-small") }) { Text("Coach") }
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
