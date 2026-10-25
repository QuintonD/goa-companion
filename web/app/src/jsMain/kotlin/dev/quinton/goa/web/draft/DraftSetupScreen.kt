package dev.quinton.goa.web.draft

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.draft.TieBreakerCoin
import dev.quinton.goa.core.domain.teams.ComplexityFilter
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.draft.methods.MethodEdits
import dev.quinton.goa.feature.draft.setup.DraftSetupInputs
import dev.quinton.goa.feature.draft.setup.DraftSetupPresenter
import dev.quinton.goa.feature.draft.setup.PlayerOption
import dev.quinton.goa.feature.draft.setup.ResumeInfo
import dev.quinton.goa.feature.draft.setup.SetupForm
import dev.quinton.goa.feature.draft.setup.SetupStage
import dev.quinton.goa.feature.draft.setup.SetupUiState
import dev.quinton.goa.feature.draft.setup.TeamMode
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.plural
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.LocalServices
import dev.quinton.goa.web.nav.Route
import dev.quinton.goa.web.table.InfoCard
import dev.quinton.goa.web.table.RadioCard
import dev.quinton.goa.web.table.RadioGroup
import dev.quinton.goa.web.table.SectionCard
import dev.quinton.goa.web.table.SequenceStrip
import dev.quinton.goa.web.table.SwitchRow
import dev.quinton.goa.web.table.TableFooter
import dev.quinton.goa.web.table.TeamBadge
import dev.quinton.goa.web.table.TeamChip
import dev.quinton.goa.web.table.TextField
import dev.quinton.goa.web.table.resilient
import dev.quinton.goa.web.table.resilientStarting
import dev.quinton.goa.web.table.teamClass
import dev.quinton.goa.web.timer.CoinFace
import dev.quinton.goa.web.ui.AlertDialog
import dev.quinton.goa.web.ui.BottomSheet
import dev.quinton.goa.web.ui.ButtonKind
import dev.quinton.goa.web.ui.Chip
import dev.quinton.goa.web.ui.EmptyState
import dev.quinton.goa.web.ui.ErrorState
import dev.quinton.goa.web.ui.GoaButton
import dev.quinton.goa.web.ui.Icon
import dev.quinton.goa.web.ui.IconButton
import dev.quinton.goa.web.ui.IconLink
import dev.quinton.goa.web.ui.LoadingState
import dev.quinton.goa.web.ui.PlayerAvatar
import dev.quinton.goa.web.ui.Screen
import dev.quinton.goa.web.ui.Segmented
import kotlin.js.Date
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.ButtonType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.type
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** The draft setup's state and actions (the phone's `DraftSetupViewModel`), derived by [DraftSetupPresenter]. */
@OptIn(ExperimentalUuidApi::class)
private class DraftSetupModel(private val services: AppServices, private val scope: CoroutineScope) {
    private val drafts = services.data.table.drafts
    private val group = services.data.group

    /** Null until the saved form has been read. */
    private val form = MutableStateFlow<SetupForm?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val players: Flow<List<Player>> = resilient("Players", emptyList()) { group.players.observePlayers() }

    private val inputs: Flow<DraftSetupInputs> = combine(
        players,
        resilientStarting<Map<String, Rating>>("Ratings", emptyMap()) { group.ratings.observePlayerRatings() },
        resilient<Catalog>("The catalog", Catalog.EMPTY) { services.data.content.catalog.catalog },
        resilientStarting("Settings", AppSettings()) { services.data.content.settings.settings },
        drafts.methods,
    ) { p, r, c, s, m -> DraftSetupInputs(p, r, c, s, m) }

    val uiState: StateFlow<SetupUiState> = combine(
        inputs,
        form.filterNotNull(),
        drafts.active,
        message,
        resilientStarting<List<Match>>("The match log", emptyList()) { group.matches.observeMatches() },
    ) { inputs, form, active, msg, log ->
        DraftSetupPresenter.derive(
            inputs,
            form,
            active,
            msg,
        ).copy(lastGameNote = DraftSetupPresenter.lastGameNote(form, log))
    }.stateIn(scope, SharingStarted.Eagerly, SetupUiState())

    val loadError = drafts.loadError

    init {
        scope.launch {
            drafts.load()
            val saved = drafts.setupFormNow()
            val known = players.first().mapTo(mutableSetOf()) { it.id }
            form.value = (saved ?: SetupForm()).keepOnly(known)
            // Every change is written straight away: the screen can be left at any moment.
            form.filterNotNull().drop(1).collect { drafts.saveSetupForm(it) }
        }
        scope.launch {
            drafts.prefills.collect { pushed ->
                val known = players.first().mapTo(mutableSetOf()) { it.id }
                form.value = pushed.keepOnly(known)
            }
        }
    }

    fun retry() = scope.launch { drafts.load() }

    private val current: SetupForm get() = form.value ?: SetupForm()

    private fun edit(transform: (SetupForm) -> SetupForm) = form.update { f -> f?.let(transform) }

    // --- Players

    fun togglePlayer(id: String) = edit { it.togglePlayer(id) }

    fun clearPlayers() = edit { it.cleared() }

    fun quickAddPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            try {
                val count = group.players.observePlayers(includeArchived = true).first().size
                // A known name (archived players included) selects that player rather than creating a
                // second one whose games would split the rating.
                val player = group.players.ensurePlayer(
                    DraftSetupPresenter.quickAddPlayer(trimmed, count, Uuid.random().toString(), Date.now().toLong()),
                )
                // Re-typing someone already ticked must not count them twice.
                edit { it.withPlayer(player.id) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                console.warn("Couldn't add the player", e)
                message.value = "Couldn't add $trimmed. Try again."
            }
        }
    }

    // --- Teams

    fun setTeamMode(mode: TeamMode) {
        edit { it.copy(teamMode = mode, teamA = null) }
        if (mode == TeamMode.BALANCED) balance()
        if (mode == TeamMode.PLAYER_DRAFT) edit { DraftSetupPresenter.withSuggestedCaptains(it, uiState.value.players) }
    }

    fun movePlayer(id: String) = edit { it.movePlayer(id) }

    fun toggleLock(id: String) = edit { it.toggleLock(id) }

    fun balance() {
        val players = uiState.value.players
        edit { f -> DraftSetupPresenter.balanced(f, players, Random.nextLong()) ?: f }
    }

    fun setOddMode(mode: OddSeatMode) = edit { it.copy(oddMode = mode) }

    fun setDoublePilot(id: String) = edit { it.copy(doublePilot = id) }

    fun setCaptain(team: Team, id: String) = edit { it.withCaptain(team, id) }

    // --- Heroes

    fun selectMethod(id: String) = edit { it.copy(methodId = id) }

    fun setPoolSource(source: PoolSource) = edit { it.copy(poolSource = source) }

    fun setCustomSelection(ids: Set<String>) = edit { it.copy(customSelection = ids) }

    fun setMaxComplexity(max: Int?) = edit { it.copy(maxComplexity = max) }

    fun setStepTimer(enabled: Boolean) = edit { it.copy(stepTimer = enabled) }

    fun setStepSeconds(seconds: Int) = edit { it.copy(stepSeconds = seconds, stepTimer = true) }

    // --- Coin

    fun flipCoin() = edit {
        it.copy(teamA = TieBreakerCoin.flip(Random(Random.nextLong())), flipCount = it.flipCount + 1)
    }

    /** The table flipped the physical coin. */
    fun setTeamA(team: Team) = edit { it.copy(teamA = team) }

    // --- Navigation

    fun next() {
        if (uiState.value.blocker != null) return
        edit { it.nextStage() }
    }

    fun back(): Boolean {
        val previous = current.previousStage() ?: return false
        edit { previous }
        return true
    }

    fun goTo(stage: SetupStage) = edit { it.goTo(stage) }

    fun dismissMessage() {
        message.value = null
    }

    fun discardActive() = scope.launch { drafts.saveActive(null) }

    fun start(onStarted: () -> Unit) {
        val state = uiState.value
        val f = state.form
        if (state.selectedMethod == null || f.teamA == null) return
        if (state.blocker != null || f.starting) return
        edit { it.copy(starting = true) }
        scope.launch {
            val candidates = DraftSetupPresenter.candidates(inputs.first(), f)
            val result =
                DraftSetupPresenter.start(
                    state,
                    candidates,
                    Uuid.random().toString(),
                    Date.now().toLong(),
                    Random.nextLong(),
                )
                    ?: return@launch
            result.fold(
                onSuccess = { progress ->
                    drafts.saveActive(progress)
                    // The next game starts from the same table: players, teams and method stay.
                    form.value = f.forNextGame()
                    onStarted()
                },
                onFailure = { e ->
                    message.value = DraftSetupPresenter.errorMessage(e)
                    edit { it.copy(starting = false) }
                },
            )
        }
    }
}

/**
 * The Draft tab: players, teams (manual, by Elo or the official Player Draft), the method and hero
 * pool, then the Tie Breaker coin — four steps, saved as they change, ending in the pass-and-play
 * board. A draft in progress shows on top, to resume or discard.
 */
@Composable
fun DraftSetupScreen() {
    val services = LocalServices.current
    val scope = rememberCoroutineScope()
    val model = remember { DraftSetupModel(services, scope) }
    val state by model.uiState.collectAsState()
    val error by model.loadError.collectAsState()
    LaunchedEffect(state.message) {
        state.message?.let { text ->
            services.messages.show(text)
            model.dismissMessage()
        }
    }
    Screen(
        "Draft",
        actions = {
            IconLink("list", "Draft methods", Route.DraftMethods)
            IconLink("settings", "Settings", Route.Settings)
        },
    ) {
        when {
            error != null && state.loading -> ErrorState(
                "Couldn't load the draft methods",
                "Check the connection and try again. Once the app has been opened online, this works offline too.",
                onRetry = { model.retry() },
            )

            state.loading -> LoadingState()

            else -> {
                StageIndicator(state.stage, model::goTo)
                Div({ classes("goa-table-stack", "goa-table-has-footer") }) {
                    when (state.stage) {
                        SetupStage.PLAYERS -> PlayersStage(state, model)
                        SetupStage.TEAMS -> TeamsStage(state, model)
                        SetupStage.HEROES -> HeroesStage(state, model)
                        SetupStage.COIN -> CoinStage(state, model)
                    }
                }
                BottomBar(state, model) { services.router.navigate(Route.DraftBoard) }
            }
        }
    }
}

@Composable
private fun StageIndicator(current: SetupStage, onSelect: (SetupStage) -> Unit) {
    Div({
        classes("goa-draft-stages")
        attr("aria-label", "Draft setup steps")
    }) {
        SetupStage.entries.forEach { stage ->
            val done = stage.ordinal < current.ordinal
            val active = stage == current
            Button({
                type(ButtonType.Button)
                classes("goa-draft-stage")
                if (done) classes("done")
                if (active) {
                    classes("active")
                    attr("aria-current", "step")
                }
                if (!done) disabled()
                attr(
                    "aria-label",
                    "${stage.ordinal + 1}. ${stage.title}, ${if (active) {
                        "current step"
                    } else if (done) {
                        "done"
                    } else {
                        "not started"
                    }}",
                )
                onClick { onSelect(stage) }
            }) {
                Span({ classes("goa-draft-stage-bar") })
                Span({ classes("t-label-medium") }) { Text("${stage.ordinal + 1}. ${stage.shortTitle}") }
            }
        }
    }
}

@Composable
private fun BottomBar(state: SetupUiState, model: DraftSetupModel, onOpenBoard: () -> Unit) {
    TableFooter {
        state.blocker?.let { P({ classes("t-body-small", "c-variant") }) { Text(it) } }
        Div({ classes("goa-table-footer-row") }) {
            if (state.stage != SetupStage.PLAYERS) GoaButton("Back", { model.back() }, ButtonKind.OUTLINED)
            val last = state.stage == SetupStage.COIN
            Button({
                type(ButtonType.Button)
                classes("goa-button", "filled", "goa-table-grow")
                if (state.blocker != null || state.form.starting) disabled()
                onClick { if (last) model.start(onOpenBoard) else model.next() }
            }) {
                if (last) Icon("play")
                Text(state.primaryLabel)
            }
        }
    }
}

// ---------------------------------------------------------------- Players

@Composable
private fun PlayersStage(state: SetupUiState, model: DraftSetupModel) {
    val router = LocalServices.current.router
    state.resume?.let { ResumeCard(it, model) { router.navigate(Route.DraftBoard) } }
    QuickAddRow(model::quickAddPlayer)
    if (state.players.isEmpty()) {
        EmptyState(
            "person",
            "Who's playing?",
            "Add the people at the table above, or create full profiles in Players.",
        ) {
            GoaButton("New player profile", {
                router.navigate(Route.PlayerEdit())
            }, ButtonKind.OUTLINED, icon = "person_add")
        }
        return
    }
    Div({ classes("goa-table-heading-row") }) {
        Div({ classes("goa-table-row-text") }) {
            P({ classes("t-label-large", "c-variant", "goa-table-tight") }) {
                Text("${state.playerCount} selected · 2–${SetupUiState.MAX_PLAYERS} players")
            }
            state.lastGameNote?.let {
                P({ classes("t-body-small", "goa-table-secondary", "goa-table-tight") }) { Text(it) }
            }
        }
        if (state.playerCount > 0) GoaButton("Clear", model::clearPlayers, ButtonKind.TEXT)
    }
    Div({ classes("goa-draft-players") }) {
        state.players.forEach { option ->
            key(option.player.id) {
                PlayerRow(option, option.player.id in state.form.selected) { model.togglePlayer(option.player.id) }
            }
        }
    }
}

@Composable
private fun ResumeCard(resume: ResumeInfo, model: DraftSetupModel, onResume: () -> Unit) {
    var confirmDiscard by remember { mutableStateOf(false) }
    Div({ classes("goa-draft-resume") }) {
        P({
            classes("t-title-medium", "c-primary", "goa-table-tight")
        }) { Text(if (resume.complete) "Draft complete" else "Draft in progress") }
        P({ classes("t-body-medium", "goa-table-tight") }) { Text("${resume.title} · ${resume.detail}") }
        Div({ classes("goa-button-row", "start") }) {
            GoaButton(if (resume.complete) "Open" else "Resume", onResume)
            GoaButton("Discard", { confirmDiscard = true }, ButtonKind.TEXT)
        }
    }
    if (confirmDiscard) {
        // Same confirmation as the board's "Discard draft".
        AlertDialog(
            "Discard this draft?",
            onDismiss = { confirmDiscard = false },
            buttons = {
                GoaButton(if (resume.complete) "Keep it" else "Keep drafting", {
                    confirmDiscard = false
                }, ButtonKind.TEXT)
                GoaButton("Discard", {
                    confirmDiscard = false
                    model.discardActive()
                }, ButtonKind.DANGER)
            },
        ) {
            Text(
                if (resume.complete) {
                    "The finished draft is thrown away. Nothing is saved to the match log."
                } else {
                    "Bans and picks so far are thrown away. Nothing is saved to the match log."
                },
            )
        }
    }
}

@Composable
private fun QuickAddRow(onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val submit = {
        onAdd(name)
        name = ""
    }
    Div({ classes("goa-draft-quick-add") }) {
        TextField("Quick add a player", name, onValue = { name = it }, capitalize = true, onEnter = submit)
        Button({
            type(ButtonType.Button)
            classes("goa-table-round-button", "tonal")
            attr("aria-label", "Add player")
            title("Add player")
            if (name.isBlank()) disabled()
            onClick { submit() }
        }) { Icon("add") }
    }
}

@Composable
private fun PlayerRow(option: PlayerOption, selected: Boolean, onToggle: () -> Unit) {
    Button({
        type(ButtonType.Button)
        classes("goa-draft-player")
        attr("role", "checkbox")
        attr("aria-checked", selected.toString())
        onClick { onToggle() }
    }) {
        PlayerAvatar(option.player)
        Span({ classes("goa-table-row-text") }) {
            Span({ classes("t-title-medium") }) { Text(option.player.name) }
            Span({ classes("t-body-small", "c-variant") }) { Text(option.ratingLabel) }
        }
        Span({
            classes("goa-table-checkbox")
            attr("aria-hidden", "true")
        }) { if (selected) Icon("check") }
    }
}

// ---------------------------------------------------------------- Teams

@Composable
private fun TeamsStage(state: SetupUiState, model: DraftSetupModel) {
    Segmented(
        "How teams are made",
        TeamMode.entries,
        state.form.teamMode,
        optionLabel = { it.label },
        onSelect = model::setTeamMode,
    )
    if (state.form.teamMode == TeamMode.PLAYER_DRAFT) {
        PlayerDraftSetup(state, model)
    } else {
        TeamColumns(state, model)
        SeatLayoutCard(state, model)
    }
}

@Composable
private fun TeamColumns(state: SetupUiState, model: DraftSetupModel) {
    val form = state.form
    val balanced = form.teamMode == TeamMode.BALANCED
    SectionCard(
        title = if (balanced) "Balanced by Elo" else "Tap a player to switch teams",
        subtitle = form.balanceDifference?.let { "Average Elo differs by ${it.roundToInt()}" }
            ?: if (balanced) "Lock players to keep them on a team, then re-balance" else null,
        trailing = if (balanced) {
            { GoaButton("Re-balance", model::balance, ButtonKind.TONAL, icon = "shuffle") }
        } else {
            null
        },
    ) {
        Div({ classes("goa-draft-team-columns") }) {
            for (team in Team.entries) {
                val ids = if (team == Team.RED) form.red else form.blue
                Div({ classes("goa-draft-team-column") }) {
                    TeamBadge(team)
                    val seats = state.seats.count { it.team == team }
                    P({ classes("t-body-small", "c-variant", "goa-table-tight") }) {
                        Text("${plural(ids.size, "player")} · ${plural(seats, "hero", "heroes")}")
                    }
                    ids.forEach { id ->
                        key(id) {
                            TeamMemberChip(state.player(id), team, id in form.locked, balanced, {
                                model.movePlayer(id)
                            }, { model.toggleLock(id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamMemberChip(
    option: PlayerOption?,
    team: Team,
    locked: Boolean,
    showLock: Boolean,
    onMove: () -> Unit,
    onLock: () -> Unit,
) {
    val name = option?.player?.name ?: "Unknown"
    val other = if (team == Team.RED) "Blue" else "Red"
    Div({ classes("goa-draft-member", teamClass(team)) }) {
        Button({
            type(ButtonType.Button)
            classes("goa-draft-member-move")
            if (locked) disabled()
            attr(
                "aria-label",
                "$name, ${if (team == Team.RED) "Red" else "Blue"} team" +
                    if (locked) ", locked" else ". Tap to move to $other",
            )
            onClick { onMove() }
        }) {
            PlayerAvatar(option?.player, size = 28)
            Span({ classes("t-body-medium") }) { Text(name) }
        }
        if (showLock) {
            IconButton(
                if (locked) "lock" else "lock_open",
                if (locked) "Unlock $name" else "Lock $name to ${if (team == Team.RED) "Red" else "Blue"}",
                onClick = onLock,
                extraClass = if (locked) "goa-table-active" else null,
            )
        }
    }
}

@Composable
private fun SeatLayoutCard(state: SetupUiState, model: DraftSetupModel) {
    val form = state.form
    val n = state.playerCount
    when {
        n == 2 -> InfoCard("Two players", "Each player controls two heroes (rulebook), so the draft fills 2 v 2 seats.")

        n == 3 -> InfoCard(
            "Official 3-player setup",
            "${state.soloPlayerName} controls two heroes and replaces a gold card of one of them with its " +
                "Handicap card. Put your most experienced player alone.",
        )

        SeatLayouts.offersOddChoice(n) -> SectionCard("Odd player count", "How should the teams be evened out?") {
            RadioGroup("Odd player count") {
                ChoiceCard(
                    "Handicap",
                    "Rulebook",
                    "Teams stay uneven. Every hero on the larger team swaps a basic card for its Handicap card.",
                    form.oddMode == OddSeatMode.HANDICAP,
                ) { model.setOddMode(OddSeatMode.HANDICAP) }
                ChoiceCard(
                    "Double pilot",
                    "House rule",
                    "One player on the smaller team controls two heroes, so both teams draft the same " +
                        "number of heroes.",
                    form.oddMode == OddSeatMode.DOUBLE_PILOT,
                ) { model.setOddMode(OddSeatMode.DOUBLE_PILOT) }
            }
            if (form.oddMode == OddSeatMode.DOUBLE_PILOT) {
                P({ classes("t-label-large") }) { Text("Who pilots two heroes?") }
                Div({ classes("goa-chip-row", "wrap") }) {
                    state.doublePilotChoices.forEach { id ->
                        Chip(state.player(id)?.player?.name ?: "Unknown", id == state.currentDoublePilot, onClick = {
                            model.setDoublePilot(id)
                        })
                    }
                }
            }
        }

        else -> Unit
    }
}

/** A large selectable card with a title, an explanation and a badge (the phone's ChoiceCard). */
@Composable
private fun ChoiceCard(title: String, badge: String?, body: String, selected: Boolean, onClick: () -> Unit) {
    RadioCard(selected, onClick) {
        Span({ classes("goa-table-inline") }) {
            Span({ classes("t-title-small", if (selected) "c-primary" else "goa-table-plain") }) { Text(title) }
            badge?.let { dev.quinton.goa.web.table.Badge(it) }
        }
        if (body.isNotEmpty()) Span({ classes("t-body-small", "c-variant") }) { Text(body) }
    }
}

@Composable
private fun PlayerDraftSetup(state: SetupUiState, model: DraftSetupModel) {
    val form = state.form
    InfoCard("Official Player Draft", DraftSetupPresenter.playerDraftExplanation(state.playerCount))
    for (team in Team.entries) {
        SectionCard("${teamLabel(team)} captain", titleClass = teamClass(team)) {
            Div({ classes("goa-chip-row", "wrap") }) {
                form.selected.forEach { id ->
                    val chosen = if (team == Team.RED) form.redCaptain == id else form.blueCaptain == id
                    Chip(state.player(id)?.player?.name ?: "Unknown", chosen, onClick = { model.setCaptain(team, id) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Heroes

@Composable
private fun HeroesStage(state: SetupUiState, model: DraftSetupModel) {
    val router = LocalServices.current.router
    val form = state.form
    var choosing by remember { mutableStateOf(false) }
    SectionCard(
        "Method",
        "How heroes are chosen",
        trailing = { GoaButton("Manage", { router.navigate(Route.DraftMethods) }, ButtonKind.TEXT) },
    ) {
        RadioGroup("Method") {
            state.methods.forEach { option ->
                val selected = option.method.id == form.methodId
                RadioCard(selected, onClick = { model.selectMethod(option.method.id) }) {
                    Span({ classes("goa-table-inline") }) {
                        Span({
                            classes("t-title-small", if (selected) "c-primary" else "goa-table-plain")
                        }) { Text(option.method.name) }
                        dev.quinton.goa.web.table.Badge(DraftSetupPresenter.originLabel(option.method))
                    }
                    Span({ classes("t-body-small", "c-variant") }) { Text(option.unavailable ?: option.method.summary) }
                    if (selected && option.preview.isNotEmpty()) SequenceStrip(option.preview)
                }
            }
        }
    }
    SectionCard("Hero pool", "${plural(state.candidateCount, "hero", "heroes")} eligible") {
        state.poolNote?.let { P({ classes("t-body-medium", "goa-table-unverified") }) { Text(it) } }
        Segmented(
            "Hero pool",
            PoolSource.entries,
            form.poolSource,
            optionLabel = {
                when (it) {
                    PoolSource.ALL -> "All"
                    PoolSource.OWNED -> "Owned"
                    PoolSource.CUSTOM -> "Custom"
                }
            },
            onSelect = model::setPoolSource,
        )
        when (form.poolSource) {
            PoolSource.ALL -> P({ classes("t-body-small", "c-variant") }) { Text("Every hero in the catalog.") }

            PoolSource.OWNED -> P({ classes("t-body-small", "c-variant") }) {
                Text("Heroes from the products you own (${state.ownedProductsLabel}, set in Settings).")
            }

            PoolSource.CUSTOM -> GoaButton("Choose heroes (${form.customSelection.size})", {
                choosing = true
            }, ButtonKind.OUTLINED)
        }
        Div({ classes("goa-table-divider") })
        P({ classes("t-label-large", "goa-table-tight") }) { Text("Least experienced player") }
        P({ classes("t-body-small", "c-variant") }) { Text("Removes heroes above their complexity (rulebook).") }
        Div({ classes("goa-chip-row", "wrap") }) {
            Chip("Any", form.maxComplexity == null, onClick = { model.setMaxComplexity(null) })
            for (stars in 1..MAX_FILTER_STARS) {
                Chip("${"★".repeat(stars)} ${ComplexityFilter.name(stars)}", form.maxComplexity == stars, onClick = {
                    model.setMaxComplexity(stars)
                })
            }
        }
    }
    SectionCard("Step timer", "Optional countdown on every ban and pick") {
        SwitchRow("Step timer", null, form.stepTimer, model::setStepTimer)
        if (form.stepTimer) {
            Div({ classes("goa-chip-row", "wrap") }) {
                MethodEdits.STEP_SECONDS.forEach { s ->
                    Chip("${s}s", form.stepSeconds == s, onClick = { model.setStepSeconds(s) })
                }
            }
        } else {
            P({ classes("t-body-small", "c-variant") }) { Text("Off — no countdown. Overtime is never enforced.") }
        }
    }
    if (choosing) {
        HeroChooserSheet(state, onDone = {
            model.setCustomSelection(it)
            choosing = false
        })
    }
}

/**
 * Custom pool picker. Dismissing the sheet (drag, scrim, Escape) applies the selection too: nothing
 * chosen is lost.
 */
@Composable
private fun HeroChooserSheet(state: SetupUiState, onDone: (Set<String>) -> Unit) {
    var selection by remember { mutableStateOf(state.form.customSelection.toList()) }
    BottomSheet("Custom pool · ${selection.size}", onDismiss = { onDone(selection.toSet()) }) {
        Div({ classes("goa-button-row", "start") }) {
            GoaButton("All", { selection = state.heroes.map { it.id } }, ButtonKind.TEXT)
            GoaButton("None", { selection = emptyList() }, ButtonKind.TEXT)
        }
        Div({ classes("goa-draft-grid") }) {
            state.heroes.forEach { hero ->
                val on = hero.id in selection
                key(hero.id) {
                    HeroTile(hero, if (on) TileState.Available else TileState.Inactive, onClick = {
                        selection = if (on) selection - hero.id else selection + hero.id
                    }, selected = on)
                }
            }
        }
        Div({ classes("goa-draft-sheet-footer") }) {
            GoaButton("Use ${plural(selection.size, "hero", "heroes")}", { onDone(selection.toSet()) }, block = true)
        }
    }
}

// ---------------------------------------------------------------- Coin

@Composable
private fun CoinStage(state: SetupUiState, model: DraftSetupModel) {
    val form = state.form
    val teamA = form.teamA
    Div({ classes("goa-draft-coin-stage") }) {
        P({
            classes("t-body-large", "goa-table-center")
        }) { Text("The team showing on the Tie Breaker coin is Team A. Team A starts the draft.") }
        CoinFace(teamA, form.flipCount)
        P({
            classes("t-headline-small", "goa-table-center", "goa-table-tight")
            teamA?.let { classes(teamClass(it), "goa-table-team-text") } ?: classes("c-variant")
            attr("aria-live", "polite")
        }) { Text(if (teamA == null) "Not flipped yet" else "${teamLabel(teamA)} is Team A") }
        GoaButton(if (teamA == null) "Flip the coin" else "Flip again", model::flipCoin)
        P({ classes("t-label-large", "c-variant", "goa-table-tight") }) { Text("Flipped the real coin?") }
        Div({ classes("goa-chip-row", "wrap", "goa-table-centered-row") }) {
            Team.entries.forEach { team ->
                TeamChip(team, "${teamName(team)} shows", teamA == team) { model.setTeamA(team) }
            }
        }
    }
    SummaryCard(state)
}

@Composable
private fun SummaryCard(state: SetupUiState) {
    val method = state.selectedMethod ?: return
    SectionCard(method.method.name, method.method.summary) {
        if (state.form.teamMode == TeamMode.PLAYER_DRAFT) {
            P({ classes("t-body-small", "c-variant") }) { Text("Heroes first, then captains draft players.") }
        } else {
            SequenceStrip(method.preview)
            Div({ classes("goa-draft-team-columns") }) {
                for (team in Team.entries) {
                    Div({ classes("goa-draft-team-column") }) {
                        TeamBadge(team)
                        state.seats.filter { it.team == team }.forEach { seat ->
                            P({ classes("t-body-medium", "goa-table-tight") }) {
                                Text(
                                    (state.player(seat.pilotId.orEmpty())?.player?.name ?: "—") +
                                        if (seat.handicap) " · Handicap" else "",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The complexity filter offers up to three stars (the rulebook's "least experienced player"). */
private const val MAX_FILTER_STARS = 3
