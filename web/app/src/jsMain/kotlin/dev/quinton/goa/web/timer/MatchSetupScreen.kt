package dev.quinton.goa.web.timer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.match.RematchKind
import dev.quinton.goa.core.domain.setup.MatchSetup
import dev.quinton.goa.core.domain.timer.TimerStatus
import dev.quinton.goa.core.model.match.GameMode
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.timer.ui.setup.CountersUi
import dev.quinton.goa.feature.timer.ui.setup.LayoutOptionUi
import dev.quinton.goa.feature.timer.ui.setup.LinkedMatchUi
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupChoices
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupPresenter
import dev.quinton.goa.feature.timer.ui.setup.MatchSetupUiState
import dev.quinton.goa.feature.timer.ui.setup.ModeOptionUi
import dev.quinton.goa.feature.timer.ui.setup.RematchRequest
import dev.quinton.goa.feature.timer.ui.timer.TimerUiMapper
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.Badge
import dev.quinton.goa.web.table.BadgeKind
import dev.quinton.goa.web.table.Note
import dev.quinton.goa.web.table.RadioCard
import dev.quinton.goa.web.table.RadioGroup
import dev.quinton.goa.web.table.SmallHeading
import dev.quinton.goa.web.table.Stepper
import dev.quinton.goa.web.table.TableFooter
import dev.quinton.goa.web.table.TeamBadge
import dev.quinton.goa.web.table.TeamChip
import dev.quinton.goa.web.table.resilient
import dev.quinton.goa.web.table.teamClass
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.ErrorState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.Screen
import kotlin.js.Date
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Ul

/** Match setup's state (the phone's `MatchSetupViewModel`), built by the shared [MatchSetupPresenter]. */
@OptIn(ExperimentalUuidApi::class)
private class MatchSetupModel(
    private val services: AppServices,
    private val matchId: String?,
    private val rematch: RematchRequest?,
    scope: CoroutineScope,
) {
    private val repository = services.data.table.timerStore
    private val controller = services.data.table.timer

    /** The id the rematch gets if its timer is started. */
    private val rematchId = Uuid.random().toString()
    private val choices = MutableStateFlow(MatchSetupChoices())
    private val failed = MutableStateFlow(false)

    private val match: Flow<Match?> = when {
        matchId != null -> resilient<Match?>("The match", null) { services.data.group.matches.observeMatch(matchId) }

        rematch != null -> resilient<Match?>("The match", null) {
            services.data.group.matches.observeMatch(rematch.sourceId)
        }
            .map { source -> source?.let(::buildRematch) }

        else -> flowOf(null)
    }

    private fun buildRematch(source: Match): Match? =
        MatchSetupPresenter.rematch(source, rematch!!, rematchId, Date.now().toLong())

    private val rules = flow {
        repository.load()
        emit(
            runCatching { repository.rules() }.getOrElse {
                failed.value = true
                null
            },
        )
    }

    val loadFailed: StateFlow<Boolean> = failed

    val uiState: StateFlow<MatchSetupUiState> = combine(
        rules,
        match,
        choices,
        combine(repository.presets, repository.selectedPresetId) { p, s -> p to s },
        controller.snapshot,
    ) { rules, match, choice, (presets, selected), running ->
        if (rules == null) {
            MatchSetupUiState()
        } else {
            MatchSetupPresenter.build(
                rules,
                match,
                choice,
                presets.map(TimerUiMapper::presetOption),
                choice.presetId ?: selected,
                rematch,
            ).copy(replacesRunningTimer = running != null && running.status != TimerStatus.ENDED)
        }
    }.stateIn(scope, SharingStarted.Eagerly, MatchSetupUiState())

    fun setPlayers(players: Int) = choices.update { it.withPlayers(players) }

    fun selectLayout(id: String) = choices.update { it.copy(layoutId = id) }

    fun selectMode(mode: GameMode) = choices.update { it.copy(mode = mode) }

    fun selectPreset(id: String) = choices.update { it.copy(presetId = id) }

    fun flipCoin() = choices.update { it.flipped(MatchSetup.flipTieBreaker(Random.Default)) }

    suspend fun setCoin(team: Team) {
        val placed = match.first()?.tieBreaker
        choices.update { it.coinSet(team, placed) }
    }

    fun correctCoin() = choices.update { it.copy(correctingCoin = true) }

    suspend fun startTimer() {
        val state = uiState.first { !it.loading }
        val preset = state.selectedPresetId?.let { repository.preset(it) } ?: repository.selectedPreset()
        val linked = when {
            matchId != null -> match.first()

            // A rematch is saved only now, as the game actually starts.
            rematch != null -> match.first()

            else -> null
        }
        if (linked != null) {
            // The coin as placed at setup is part of the match record (draft coin, or corrected here).
            MatchSetupPresenter.matchToSave(linked, state, isNew = matchId == null)?.let { toSave ->
                runCatching { services.data.group.matches.upsert(toSave) }
                    .onFailure { services.messages.show("Couldn't save the match setup to the match log.") }
            }
        }
        services.data.table.cues.unlock()
        controller.start(
            preset = preset,
            actionSlots = state.heroes,
            matchId = linked?.id,
            mode = state.selectedMode,
            tieBreaker = state.tieBreaker,
        )
    }
}

/** Match setup: players, teams, mode, the counters to lay out, the Tie Breaker coin and the preset. */
@Composable
fun MatchSetupScreen(route: Route.MatchSetup) {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val rematch = route.rematchOf?.let { source ->
        RematchKind.entries.firstOrNull { it.name == route.rematchKind }?.let { RematchRequest(source, it) }
    }
    val model = remember { MatchSetupModel(services, route.matchId, rematch, scope) }
    val state by model.uiState.collectAsState()
    val failed by model.loadFailed.collectAsState()
    var starting by remember { mutableStateOf(false) }
    Screen("Match setup") {
        when {
            failed -> ErrorState(
                "Couldn't load the setup rules",
                "Check the connection and try again. Once the app has been opened online, this works offline too.",
            )

            state.loading -> LoadingState()

            else -> Div({ classes("goa-table-stack", "goa-table-has-footer") }) {
                state.linkedMatch?.let { LinkedMatchBanner(it) }
                SmallHeading("Players")
                PlayersCard(state, model::setPlayers)
                if (state.layouts.size > 1 || state.layouts.firstOrNull()?.guidance != null) {
                    SmallHeading("Teams")
                    RadioGroup("Teams") {
                        state.layouts.forEach { layout ->
                            LayoutCard(layout, layout.id == state.selectedLayoutId, state.layouts.size > 1) {
                                model.selectLayout(layout.id)
                            }
                        }
                    }
                }
                SmallHeading("Mode")
                RadioGroup("Mode") {
                    state.modes.forEach { mode ->
                        ModeCard(mode, mode.mode == state.selectedMode) { model.selectMode(mode.mode) }
                    }
                }
                Note(state.firstGameAdvice)
                state.counters?.let {
                    SmallHeading("Lay out")
                    CountersBoard(it)
                }
                SmallHeading("Tie Breaker coin")
                TieBreakerCard(state, model)
                SmallHeading("Timer preset")
                Div({ classes("goa-chip-row", "wrap") }) {
                    state.presets.forEach { preset ->
                        Chip(preset.name, preset.id == state.selectedPresetId, onClick = {
                            model.selectPreset(preset.id)
                        })
                    }
                }
                state.presets.firstOrNull { it.id == state.selectedPresetId }?.let {
                    P({ classes("t-body-small", "c-variant") }) { Text(it.summary) }
                }
            }
        }
        if (!state.loading && !failed) {
            TableFooter {
                if (state.replacesRunningTimer) {
                    P({
                        classes("t-body-small", "c-variant")
                    }) { Text("A timer is already running; starting replaces it.") }
                }
                GoaButton("Start timer", {
                    if (!starting) {
                        starting = true
                        scope.launch {
                            try {
                                model.startTimer()
                                // Leave setup for the running timer (its parent, or the Timer screen it came from).
                                services.router.back()
                            } finally {
                                starting = false
                            }
                        }
                    }
                }, icon = "play", enabled = !starting, block = true)
            }
        }
    }
}

@Composable
private fun LinkedMatchBanner(match: LinkedMatchUi) {
    Div({ classes("goa-card", "goa-table-inline-card") }) {
        Div({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-small") }) { Text(match.title) }
            Span({
                classes("t-body-small", "c-variant")
            }) { Text("Mode, Tie Breaker coin and duration are saved to this match.") }
        }
        if (match.redSeats + match.blueSeats > 0) {
            Div({ classes("goa-table-team-counts") }) {
                Span {
                    TeamBadge(Team.RED)
                    Text(" ${match.redSeats}")
                }
                Span {
                    TeamBadge(Team.BLUE)
                    Text(" ${match.blueSeats}")
                }
            }
        }
    }
}

@Composable
private fun PlayersCard(state: MatchSetupUiState, onPlayers: (Int) -> Unit) {
    Div({ classes("goa-card") }) {
        Div({ classes("goa-table-inline") }) {
            Div({ classes("goa-table-row-text") }) {
                Span({ classes("t-title-medium") }) { Text("${state.players} players") }
                Span({
                    classes("t-body-small", if (state.recommended) "goa-table-secondary" else "c-variant")
                }) { Text(state.playersHint) }
            }
            if (!state.playersLocked) Stepper(state.players, state.playerRange, onPlayers, "players")
        }
        state.extendedNote?.let { Note(it) }
    }
}

@Composable
private fun LayoutCard(layout: LayoutOptionUi, selected: Boolean, selectable: Boolean, onClick: () -> Unit) {
    val body: @Composable () -> Unit = {
        Span({ classes("goa-table-inline") }) {
            Span({ classes("t-title-small") }) { Text(layout.title) }
            if (layout.official) Badge("Official", BadgeKind.PRIMARY) else Badge("House rule")
        }
        Span({ classes("t-body-medium") }) { Text(layout.teams) }
        layout.guidance?.let { Span({ classes("t-body-small", "c-variant") }) { Text(it) } }
    }
    if (selectable) {
        RadioCard(selected, onClick) { body() }
    } else {
        Div({ classes("goa-card", "goa-table-static-card") }) { body() }
    }
}

@Composable
private fun ModeCard(mode: ModeOptionUi, selected: Boolean, onClick: () -> Unit) {
    // No dimming: the "why unavailable" text must stay readable; the disabled radio says it can't be chosen.
    RadioCard(selected, onClick, enabled = mode.available) {
        Span({ classes("goa-table-inline") }) {
            Span({
                classes("t-title-small", if (mode.available) "goa-table-plain" else "c-variant")
            }) { Text(mode.title) }
            mode.requiredLabel?.let { Badge(it, if (it == "Required") BadgeKind.PRIMARY else BadgeKind.SECONDARY) }
        }
        Span({ classes("t-body-small", "c-variant") }) { Text(mode.reason ?: "${mode.waves} · ${mode.life}") }
    }
}

@Composable
private fun CountersBoard(counters: CountersUi) {
    Div({ classes("goa-card", "goa-table-counters") }) {
        P({ classes("t-title-small") }) { Text("Wave counters") }
        repeat(counters.lanes) { lane ->
            TokenRow(
                label = if (counters.lanes > 1) "Lane ${lane + 1}" else null,
                team = null,
                count = counters.wavesPerLane,
                hexagon = true,
                description = if (counters.lanes > 1) {
                    "Lane ${lane + 1}: ${counters.wavesPerLane} Wave counters"
                } else {
                    "${counters.wavesPerLane} Wave counters"
                },
            )
        }
        P({ classes("t-title-small") }) { Text("Life counters per team") }
        val life = counters.lifePerTeam
        if (life == null) {
            Note(counters.lifeNotSpecified)
        } else {
            Team.entries.forEach { team ->
                TokenRow(
                    null,
                    team,
                    life,
                    hexagon = false,
                    description = "${if (team == Team.RED) "Red" else "Blue"}: $life Life counters",
                )
            }
        }
        counters.houseRuleNote?.let { P({ classes("goa-table-house-rule", "t-body-small") }) { Text(it) } }
        P({ classes("t-body-small", "c-variant") }) { Text(counters.seatsLine) }
        counters.modeNote?.let { RulesNoteBlock(it.title, it.points) }
    }
}

@Composable
private fun TokenRow(label: String?, team: Team?, count: Int, hexagon: Boolean, description: String) {
    Div({
        classes("goa-table-token-row")
        attr("role", "img")
        attr("aria-label", description)
    }) {
        Span({
            classes("goa-table-token-label", "t-label-medium", "c-variant")
            attr("aria-hidden", "true")
        }) {
            when {
                team != null -> TeamBadge(team)
                label != null -> Text(label)
            }
        }
        Span({
            classes("goa-table-tokens")
            attr("aria-hidden", "true")
        }) {
            repeat(count.coerceAtMost(MAX_TOKENS)) {
                Span({
                    classes("goa-table-token", if (hexagon) "wave" else "life")
                    team?.let { classes(teamClass(it)) }
                })
            }
        }
        Span({
            classes("t-title-small", "goa-table-token-count")
            team?.let { classes(teamClass(it)) }
            attr("aria-hidden", "true")
        }) { Text("×$count") }
    }
}

/** A titled list of rule points (from the rules data), as short bullets that read at a glance. */
@Composable
private fun RulesNoteBlock(title: String, points: List<String>) {
    if (points.isEmpty()) return
    Div({ classes("goa-table-rules-note") }) {
        Note(title)
        Ul({ classes("t-body-small", "c-variant") }) { points.forEach { Li { Text(it) } } }
    }
}

@Composable
private fun TieBreakerCard(state: MatchSetupUiState, model: MatchSetupModel) {
    val scope = rememberCoroutineScope()
    val coin = state.tieBreaker
    Div({ classes("goa-card", "goa-table-coin-card") }) {
        CoinFace(coin, state.flips)
        P({
            classes("t-body-medium", "goa-table-center")
            attr("aria-live", "polite")
        }) { Text(state.coinMessage) }
        if (coin != null) {
            P({ classes("t-body-small", "c-variant", "goa-table-center") }) {
                Text(
                    "Team ${if (coin == Team.RED) "Red" else "Blue"} acts first on the next cross-team initiative tie.",
                )
            }
        }
        when {
            // The coin was flipped once already (rulebook p.5): correct a mistake, don't flip again.
            state.coinPlaced && !state.correctingCoin -> GoaButton(
                "Correct the coin",
                model::correctCoin,
                ButtonKind.OUTLINED,
            )

            state.coinPlaced -> CoinSideChips(coin, "Which side is showing?") { team ->
                scope.launch { model.setCoin(team) }
            }

            else -> {
                GoaButton(if (coin == null) "Flip coin" else "Flip again", model::flipCoin, ButtonKind.OUTLINED)
                CoinSideChips(coin, "Flipped the real coin?") { team -> scope.launch { model.setCoin(team) } }
            }
        }
        RulesNoteBlock("How the coin is used", state.tieBreakerRule)
    }
}

@Composable
private fun CoinSideChips(selected: Team?, label: String, onSet: (Team) -> Unit) {
    Div({ classes("goa-table-coin-sides") }) {
        Span({ classes("t-label-large", "c-variant") }) { Text(label) }
        Div({ classes("goa-chip-row", "wrap", "goa-table-centered-row") }) {
            Team.entries.forEach { team ->
                TeamChip(team, "${if (team == Team.RED) "Red" else "Blue"} shows", selected == team) { onSet(team) }
            }
        }
    }
}

/** The Tie Breaker coin face: ◆ Red or ● Blue; spins when flipped (not with reduced motion). */
@Composable
fun CoinFace(team: Team?, flips: Int) {
    var spinKey by remember { mutableStateOf(flips) }
    LaunchedEffect(flips) { spinKey = flips }
    androidx.compose.runtime.key(spinKey) {
        Span({
            classes("goa-table-coin")
            team?.let { classes(teamClass(it)) }
            if (spinKey > 0) classes("spin")
            attr("role", "img")
            attr(
                "aria-label",
                team?.let { "Tie Breaker coin showing ${if (it == Team.RED) "Red" else "Blue"}" } ?: "Coin not flipped",
            )
        }) {
            Span({ attr("aria-hidden", "true") }) {
                Text(
                    when (team) {
                        Team.RED -> "◆"
                        Team.BLUE -> "●"
                        null -> "?"
                    },
                )
            }
        }
    }
}

private const val MAX_TOKENS = 8
