package dev.quinton.goa.feature.draft.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftMethod
import dev.quinton.goa.core.domain.draft.DraftPlanner
import dev.quinton.goa.core.domain.draft.DraftSetup
import dev.quinton.goa.core.domain.draft.HeroPool
import dev.quinton.goa.core.domain.draft.PoolRule
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.draft.Seat
import dev.quinton.goa.core.domain.draft.SequenceRule
import dev.quinton.goa.core.domain.draft.TieBreakerCoin
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.domain.teams.SeatLayouts
import dev.quinton.goa.core.domain.teams.TeamBalancer
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.MatchStatus
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.data.DraftSetupPrefills
import dev.quinton.goa.feature.draft.data.MethodLibrary
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.plural
import dev.quinton.goa.feature.draft.ui.tokens
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class SetupStage(val title: String, val shortTitle: String = title) {
    PLAYERS("Players"),
    TEAMS("Teams"),
    HEROES("Heroes"),
    COIN("Tie Breaker", "Coin"),
}

@Serializable
enum class TeamMode(val label: String) {
    MANUAL("Manual"),
    BALANCED("By Elo"),
    PLAYER_DRAFT("Player Draft"),
}

data class PlayerOption(val player: Player, val rating: Rating?)

data class MethodOption(
    val method: DraftMethod,
    val preview: List<StepToken>,
    /** Why the method can't run for the current layout, or null. */
    val unavailable: String?,
    /** Heroes the pool must hold for this layout. */
    val heroesNeeded: Int,
)

data class ResumeInfo(val title: String, val detail: String, val complete: Boolean = false)

/**
 * The setup form; everything else in [SetupUiState] is derived. Persisted on every change
 * ([DraftRepository.saveSetupForm]) so it survives leaving the tab, Back from the board and
 * process death; after a draft starts it becomes the next game's default ([forNextGame]).
 */
@Serializable
data class SetupForm(
    val stage: SetupStage = SetupStage.PLAYERS,
    val selected: List<String> = emptyList(),
    val teamMode: TeamMode = TeamMode.MANUAL,
    val red: List<String> = emptyList(),
    val blue: List<String> = emptyList(),
    val locked: Map<String, Team> = emptyMap(),
    val balanceDifference: Double? = null,
    val oddMode: OddSeatMode = OddSeatMode.DOUBLE_PILOT,
    val doublePilot: String? = null,
    val redCaptain: String? = null,
    val blueCaptain: String? = null,
    val methodId: String? = null,
    val poolSource: PoolSource = PoolSource.OWNED,
    val customSelection: Set<String> = emptySet(),
    val maxComplexity: Int? = null,
    val stepTimer: Boolean = false,
    val stepSeconds: Int = 30,
    val teamA: Team? = null,
    val flipCount: Int = 0,
    /** Players and teams were carried over from the last game (shown with a Clear action). */
    val lastGame: Boolean = false,
    @Transient val starting: Boolean = false,
) {
    /** The next game's default: same players, teams and method; a fresh coin flip. */
    fun forNextGame(): SetupForm =
        copy(stage = SetupStage.PLAYERS, teamA = null, flipCount = 0, starting = false, lastGame = true)

    /** Players, teams and captains cleared; method, pool and timer choices kept. */
    fun cleared(): SetupForm = SetupForm(
        methodId = methodId,
        poolSource = poolSource,
        customSelection = customSelection,
        maxComplexity = maxComplexity,
        stepTimer = stepTimer,
        stepSeconds = stepSeconds,
    )

    /** Drops players who no longer exist (deleted or archived since the form was saved). */
    fun keepOnly(known: Set<String>): SetupForm {
        val kept = selected.filter { it in known }
        if (kept == selected) return this
        return copy(selected = kept, stage = SetupStage.PLAYERS, teamA = null).withDefaultTeams()
    }

    /** Keeps teams in step with [selected]: leavers drop out, newcomers join the smaller team. */
    fun withDefaultTeams(): SetupForm {
        val keepRed = red.filter { it in selected }
        val keepBlue = blue.filter { it in selected }
        val newcomers = selected.filter { it !in keepRed && it !in keepBlue }
        val r = keepRed.toMutableList()
        val b = keepBlue.toMutableList()
        for (id in newcomers) if (r.size <= b.size) r += id else b += id
        return copy(
            red = r,
            blue = b,
            locked = locked.filterKeys { it in selected },
            balanceDifference = null,
            redCaptain = redCaptain?.takeIf { it in selected },
            blueCaptain = blueCaptain?.takeIf { it in selected },
            doublePilot = doublePilot?.takeIf { it in selected },
        )
    }
}

data class SetupUiState(
    val loading: Boolean = true,
    val form: SetupForm = SetupForm(),
    val players: List<PlayerOption> = emptyList(),
    val seats: List<Seat> = emptyList(),
    val methods: List<MethodOption> = emptyList(),
    val heroes: List<HeroInfo> = emptyList(),
    val candidateCount: Int = 0,
    val ownedProductsLabel: String = "All products",
    val resume: ResumeInfo? = null,
    val message: String? = null,
    /** A House-draft style pool that will be dealt smaller than the method asks for. */
    val poolNote: String? = null,
    /**
     * "Same players and teams as last game" when the carried-over group really played the last
     * completed match; null before any game was played or when the group differs.
     */
    val lastGameNote: String? = null,
) {
    val stage: SetupStage get() = form.stage
    val selectedMethod: MethodOption? get() = methods.firstOrNull { it.method.id == form.methodId }
    val playerCount: Int get() = form.selected.size

    fun player(id: String): PlayerOption? = players.firstOrNull { it.player.id == id }

    /** Null when the current stage is complete, else what's missing (shown under the button). */
    val blocker: String?
        get() = when (form.stage) {
            SetupStage.PLAYERS -> when {
                playerCount < 2 -> "Choose at least 2 players"
                playerCount > MAX_PLAYERS -> "Up to $MAX_PLAYERS players"
                else -> null
            }

            SetupStage.TEAMS -> when (form.teamMode) {
                TeamMode.PLAYER_DRAFT -> when {
                    playerCount < PlayerDraft.MIN_PLAYERS -> "Player Draft needs ${PlayerDraft.MIN_PLAYERS}+ players"
                    form.redCaptain == null || form.blueCaptain == null -> "Choose both captains"
                    else -> null
                }

                else -> if (seats.isEmpty()) "Teams may differ by at most one player" else null
            }

            SetupStage.HEROES -> when {
                selectedMethod == null -> "Choose a method"

                selectedMethod?.unavailable != null -> selectedMethod?.unavailable

                (selectedMethod?.heroesNeeded ?: 0) > candidateCount ->
                    "Needs ${selectedMethod?.heroesNeeded} heroes, only $candidateCount match the filters"

                else -> null
            }

            SetupStage.COIN -> if (form.teamA == null) "Flip the Tie Breaker coin" else null
        }

    companion object {
        const val MAX_PLAYERS = 10
    }
}

sealed interface SetupEvent {
    data object OpenBoard : SetupEvent
}

@HiltViewModel
class DraftSetupViewModel @Inject constructor(
    private val players: PlayerRepository,
    ratings: RatingsRepository,
    catalogRepository: CatalogRepository,
    settings: SettingsRepository,
    private val drafts: DraftRepository,
    private val env: DraftEnvironment,
    /** The match log: the "last game" note needs a completed game by this group. */
    matches: MatchRepository,
    /** "Draft again" from a rematch while this setup is already open (shell keeps tab state). */
    private val prefills: DraftSetupPrefills = DraftSetupPrefills(),
) : ViewModel() {
    /** Null until the saved form has been read. */
    private val form = MutableStateFlow<SetupForm?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val events = Channel<SetupEvent>(Channel.BUFFERED)
    val setupEvents: Flow<SetupEvent> = events.receiveAsFlow()

    private val inputs: Flow<Inputs> = combine(
        players.observePlayers(),
        ratings.observePlayerRatings().onStart { emit(emptyMap()) },
        catalogRepository.catalog,
        settings.settings,
        drafts.methods,
    ) { p, r, c, s, m -> Inputs(p, r, c, s, m) }

    val uiState: StateFlow<SetupUiState> =
        combine(
            inputs,
            form.filterNotNull(),
            drafts.active,
            message,
            matches.observeMatches().onStart { emit(emptyList()) },
        ) { inputs, form, active, msg, log ->
            derive(inputs, form, active, msg).copy(lastGameNote = lastGameNote(form, log))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

    init {
        viewModelScope.launch {
            val saved = drafts.setupForm.first()
            val known = players.observePlayers().first().mapTo(mutableSetOf()) { it.id }
            form.value = (saved ?: SetupForm()).keepOnly(known)
            // Every change is written straight away: the screen can be left at any moment.
            form.filterNotNull().drop(1).collect { drafts.saveSetupForm(it) }
        }
        viewModelScope.launch {
            prefills.requests.collect { pushed ->
                val known = players.observePlayers().first().mapTo(mutableSetOf()) { it.id }
                form.value = pushed.keepOnly(known)
            }
        }
    }

    /** The current form (defaults until the saved one has loaded). */
    private val current: SetupForm get() = form.value ?: SetupForm()

    private fun edit(transform: (SetupForm) -> SetupForm) = form.update { f -> f?.let(transform) }

    private data class Inputs(
        val players: List<Player>,
        val ratings: Map<String, Rating>,
        val catalog: Catalog,
        val settings: AppSettings,
        val methods: MethodLibrary,
    )

    private fun derive(inputs: Inputs, form: SetupForm, active: DraftProgress?, msg: String?): SetupUiState {
        val options = inputs.players.map { PlayerOption(it, inputs.ratings[it.id]) }
        val seats = if (form.teamMode == TeamMode.PLAYER_DRAFT) {
            emptyList()
        } else {
            SeatLayouts.build(form.red, form.blue, form.oddMode, form.doublePilot).getOrNull().orEmpty()
        }
        val candidates = candidates(inputs, form)
        val methods = inputs.methods.all.map { methodOption(it, form, seats) }
        val methodId = form.methodId?.takeIf { id -> methods.any { it.method.id == id } }
            ?: inputs.methods.default?.id
        val selected = methods.firstOrNull { it.method.id == methodId }
        return SetupUiState(
            loading = false,
            form = form.copy(methodId = methodId),
            players = options,
            seats = seats,
            methods = methods,
            heroes = inputs.catalog.heroes.map { HeroInfo.of(it) },
            candidateCount = candidates.size,
            ownedProductsLabel = ownedLabel(inputs),
            resume = active?.let { resumeInfo(it) },
            message = msg,
            poolNote = selected?.let { poolNote(it, candidates.size) },
        )
    }

    /**
     * The carried-over group is only "as last game" if the most recent completed match had
     * exactly these players; "and teams" only if they sat on the same sides (either colour).
     */
    private fun lastGameNote(form: SetupForm, log: List<Match>): String? {
        if (!form.lastGame || form.selected.isEmpty()) return null
        val last = log.filter { it.status == MatchStatus.COMPLETED }
            .maxWithOrNull(compareBy<Match>({ it.playedAt }, { it.createdAt })) ?: return null
        if (last.playerIds != form.selected.toSet()) return null
        val red = last.team(Team.RED).mapTo(mutableSetOf()) { it.playerId }
        val blue = last.team(Team.BLUE).mapTo(mutableSetOf()) { it.playerId }
        val sides = setOf(form.red.toSet(), form.blue.toSet())
        return if (sides == setOf(red, blue)) "Same players and teams as last game" else "Same players as last game"
    }

    /**
     * A random pool larger than the eligible roster is quietly dealt smaller (House draft); say
     * so. Nothing to say when the method can't run at all (the blocker covers that).
     */
    private fun poolNote(option: MethodOption, eligible: Int): String? {
        val size = (option.method.pool as? PoolRule.Random)?.size ?: return null
        if (eligible >= size || eligible < option.heroesNeeded) return null
        return "Only ${plural(eligible, "hero", "heroes")} match your filters, " +
            "so the pool is $eligible instead of $size."
    }

    private fun candidates(inputs: Inputs, form: SetupForm): List<String> = HeroPool.candidates(
        heroes = inputs.catalog.heroes,
        source = form.poolSource,
        ownedProducts = inputs.settings.ownedProducts,
        customSelection = form.customSelection,
        maxComplexity = form.maxComplexity,
    )

    private fun ownedLabel(inputs: Inputs): String {
        val owned = inputs.settings.ownedProducts
        val total = inputs.catalog.products.size
        return if (owned.isEmpty() || owned.size >= total) "All products" else "${owned.size} of $total products"
    }

    private fun methodOption(method: DraftMethod, form: SetupForm, seats: List<Seat>): MethodOption {
        if (form.teamMode == TeamMode.PLAYER_DRAFT) {
            val supported = HeroSelections.supports(method)
            val n = form.selected.size
            val needed = when (val p = method.pool) {
                PoolRule.All, is PoolRule.Random -> n
                is PoolRule.SeatsPlus -> n + p.extra
                is PoolRule.DealPerSeat -> n * p.perSeat
            }
            return MethodOption(
                method = method,
                preview = emptyList(),
                unavailable = if (supported) null else "Not available with Player Draft (needs teams)",
                heroesNeeded = needed,
            )
        }
        val teamA = form.teamA ?: Team.RED
        val countA = seats.count { it.team == teamA }
        val countB = seats.size - countA
        val plan = DraftPlanner.plan(method, countA, countB)
        val steps = plan.getOrNull()
        val needed = when (val p = method.pool) {
            is PoolRule.SeatsPlus -> seats.size + p.extra
            is PoolRule.DealPerSeat -> seats.size * p.perSeat
            else -> steps?.size ?: 0
        }
        val unavailable = when (val e = plan.exceptionOrNull()) {
            null -> null
            is DraftError.NotSupported -> e.reason
            else -> "Doesn't fit ${countA}v$countB seats"
        }
        return MethodOption(
            method,
            steps?.tokens(form.teamA).orEmpty(),
            unavailable,
            if (method.sequence is SequenceRule.Lineup) 0 else needed,
        )
    }

    private fun resumeInfo(progress: DraftProgress): ResumeInfo = when (progress) {
        is DraftProgress.Board -> {
            val s = progress.session
            if (s.isComplete) {
                ResumeInfo(s.method.name, "Assign seats and save the match", complete = true)
            } else {
                ResumeInfo(s.method.name, "Step ${s.history.size + 1} of ${s.plan.size}")
            }
        }

        is DraftProgress.Players -> ResumeInfo("Player Draft · ${progress.session.method.name}", "In progress")
    }

    // --- Players ---

    fun togglePlayer(id: String) = edit { f ->
        val selected = if (id in f.selected) f.selected - id else f.selected + id
        f.copy(selected = selected, lastGame = false).withDefaultTeams()
    }

    /** Forgets last game's players and teams (method and pool choices stay). */
    fun clearPlayers() = edit { it.cleared() }

    fun quickAddPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val count = players.observePlayers(includeArchived = true).first().size
            // A known name (archived players included) selects that player rather than
            // creating a second one whose games would split the rating.
            val player = players.ensurePlayer(
                Player(
                    id = env.newId(),
                    name = trimmed,
                    color = QUICK_ADD_COLORS[count % QUICK_ADD_COLORS.size],
                    avatarId = count % AVATAR_COUNT,
                    createdAt = env.nowMillis(),
                ),
            )
            // Re-typing someone already ticked must not count them twice.
            edit { f ->
                if (player.id in f.selected) f else f.copy(selected = f.selected + player.id).withDefaultTeams()
            }
        }
    }

    // --- Teams ---

    fun setTeamMode(mode: TeamMode) {
        edit { it.copy(teamMode = mode, teamA = null) }
        if (mode == TeamMode.BALANCED) balance()
        if (mode == TeamMode.PLAYER_DRAFT) suggestCaptains()
    }

    /** Moves a player to the other team (tap to move). */
    fun movePlayer(id: String) = edit { f ->
        when (id) {
            in f.red -> f.copy(red = f.red - id, blue = f.blue + id, balanceDifference = null, locked = f.locked - id)
            in f.blue -> f.copy(blue = f.blue - id, red = f.red + id, balanceDifference = null, locked = f.locked - id)
            else -> f
        }
    }

    fun toggleLock(id: String) = edit { f ->
        val team = if (id in f.red) Team.RED else Team.BLUE
        f.copy(locked = if (id in f.locked) f.locked - id else f.locked + (id to team))
    }

    fun balance() {
        viewModelScope.launch {
            val ratings = uiState.value.players.associate { it.player.id to (it.rating?.value ?: Rating.START) }
            edit { f ->
                val split = TeamBalancer.balance(f.selected, ratings, f.locked, env.newSeed()) ?: return@edit f
                f.copy(
                    red = split.red,
                    blue = split.blue,
                    balanceDifference = split.difference,
                    teamMode = TeamMode.BALANCED,
                )
            }
        }
    }

    fun setOddMode(mode: OddSeatMode) = edit { it.copy(oddMode = mode) }

    fun setDoublePilot(id: String) = edit { it.copy(doublePilot = id) }

    fun setCaptain(team: Team, id: String) = edit { f ->
        when (team) {
            Team.RED -> f.copy(redCaptain = id, blueCaptain = f.blueCaptain.takeIf { it != id })
            Team.BLUE -> f.copy(blueCaptain = id, redCaptain = f.redCaptain.takeIf { it != id })
        }
    }

    private fun suggestCaptains() {
        val state = uiState.value
        val games = state.players.associate { it.player.id to (it.rating?.games ?: 0) }
        val values = state.players.associate { it.player.id to (it.rating?.value ?: Rating.START) }
        val captains = PlayerDraft.suggestCaptains(current.selected, games, values)
        edit { it.copy(redCaptain = captains.getOrNull(0), blueCaptain = captains.getOrNull(1)) }
    }

    // --- Heroes ---

    fun selectMethod(id: String) = edit { it.copy(methodId = id) }

    fun setPoolSource(source: PoolSource) = edit { it.copy(poolSource = source) }

    fun toggleCustomHero(id: String) = edit { f ->
        f.copy(customSelection = if (id in f.customSelection) f.customSelection - id else f.customSelection + id)
    }

    fun setCustomSelection(ids: Set<String>) = edit { it.copy(customSelection = ids) }

    fun setMaxComplexity(max: Int?) = edit { it.copy(maxComplexity = max) }

    fun setStepTimer(enabled: Boolean) = edit { it.copy(stepTimer = enabled) }

    fun setStepSeconds(seconds: Int) = edit { it.copy(stepSeconds = seconds, stepTimer = true) }

    // --- Coin ---

    fun flipCoin() = edit {
        it.copy(teamA = TieBreakerCoin.flip(Random(env.newSeed())), flipCount = it.flipCount + 1)
    }

    /** The table flipped the physical coin. */
    fun setTeamA(team: Team) = edit { it.copy(teamA = team) }

    // --- Navigation ---

    fun next() {
        val state = uiState.value
        if (state.blocker != null) return
        edit { f ->
            val nextStage = SetupStage.entries.getOrNull(f.stage.ordinal + 1) ?: return@edit f
            f.copy(stage = nextStage)
        }
    }

    fun back(): Boolean {
        val stage = current.stage
        if (stage == SetupStage.PLAYERS) return false
        edit { it.copy(stage = SetupStage.entries[stage.ordinal - 1]) }
        return true
    }

    fun goTo(stage: SetupStage) {
        if (stage.ordinal < current.stage.ordinal) edit { it.copy(stage = stage) }
    }

    fun dismissMessage() = message.update { null }

    fun discardActive() {
        viewModelScope.launch { drafts.saveActive(null) }
    }

    fun resume() {
        viewModelScope.launch { events.send(SetupEvent.OpenBoard) }
    }

    fun start() {
        val state = uiState.value
        val f = state.form
        val option = state.selectedMethod ?: return
        val teamA = f.teamA ?: return
        if (state.blocker != null || f.starting) return
        edit { it.copy(starting = true) }
        viewModelScope.launch {
            val inputs = inputs.first()
            val candidates = candidates(inputs, f)
            val result = if (f.teamMode == TeamMode.PLAYER_DRAFT) {
                PlayerDraftSession.start(
                    id = env.newId(),
                    createdAt = env.nowMillis(),
                    method = option.method,
                    players = f.selected,
                    redCaptain = f.redCaptain.orEmpty(),
                    blueCaptain = f.blueCaptain.orEmpty(),
                    teamA = teamA,
                    candidates = candidates,
                    seed = env.newSeed(),
                ).map { DraftProgress.Players(it) }
            } else {
                DraftEngine.start(
                    DraftSetup(
                        id = env.newId(),
                        createdAt = env.nowMillis(),
                        method = option.method,
                        seats = state.seats,
                        teamA = teamA,
                        candidates = candidates,
                        seed = env.newSeed(),
                        stepSeconds = if (f.stepTimer) f.stepSeconds else null,
                    ),
                ).map { DraftProgress.Board(it) }
            }
            result.fold(
                onSuccess = { progress ->
                    drafts.saveActive(progress)
                    // The next game starts from the same table: players, teams and method stay.
                    form.value = f.forNextGame()
                    events.send(SetupEvent.OpenBoard)
                },
                onFailure = { e ->
                    message.value = errorMessage(e)
                    edit { it.copy(starting = false) }
                },
            )
        }
    }

    companion object {
        private const val AVATAR_COUNT = 24

        /**
         * Palette colours for quick-added players, minus Coral and Sky: next to the Red and Blue
         * team colours they read as a team (docs/DESIGN.md).
         */
        private val TEAM_LIKE = setOf("Coral", "Sky")
        private val QUICK_ADD_COLORS = PlayerColors.all.filter { it.name !in TEAM_LIKE }.map { it.argb }

        fun errorMessage(e: Throwable): String = when (e) {
            is DraftError.PoolTooSmall -> "Needs ${e.required} heroes, only ${e.available} available. Widen the pool."
            is DraftError -> e.message ?: "Can't start this draft"
            else -> "Can't start this draft"
        }
    }
}
