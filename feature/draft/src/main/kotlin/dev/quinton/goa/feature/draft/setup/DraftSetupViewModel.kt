package dev.quinton.goa.feature.draft.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
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
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.model.settings.AppSettings
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.data.MethodLibrary
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.StepToken
import dev.quinton.goa.feature.draft.ui.tokens
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStage(val title: String) {
    PLAYERS("Players"),
    TEAMS("Teams"),
    HEROES("Heroes"),
    COIN("Tie Breaker"),
}

enum class TeamMode(val label: String) {
    MANUAL("Manual"),
    BALANCED("Balance by Elo"),
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

data class ResumeInfo(val title: String, val detail: String)

/** The setup form; everything else in [SetupUiState] is derived. */
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
    val starting: Boolean = false,
)

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
) : ViewModel() {
    private val form = MutableStateFlow(SetupForm())
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

    val uiState: StateFlow<SetupUiState> = combine(inputs, form, drafts.active, message) { inputs, form, active, msg ->
        derive(inputs, form, active, msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState())

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
        )
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
        val plan = DraftPlanner.plan(method.sequence, countA, countB)
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
            val detail = if (s.isComplete) {
                "Complete — assign seats and save"
            } else {
                "Step ${s.history.size + 1} of ${s.plan.size}"
            }
            ResumeInfo(s.method.name, detail)
        }

        is DraftProgress.Players -> ResumeInfo("Player Draft · ${progress.session.method.name}", "In progress")
    }

    // --- Players ---

    fun togglePlayer(id: String) = form.update { f ->
        val selected = if (id in f.selected) f.selected - id else f.selected + id
        f.copy(selected = selected).withDefaultTeams()
    }

    fun quickAddPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val count = players.observePlayers(includeArchived = true).first().size
            val player = Player(
                id = env.newId(),
                name = trimmed,
                color = QUICK_ADD_COLORS[count % QUICK_ADD_COLORS.size],
                avatarId = count % AVATAR_COUNT,
                createdAt = env.nowMillis(),
            )
            players.upsert(player)
            form.update { it.copy(selected = it.selected + player.id).withDefaultTeams() }
        }
    }

    // --- Teams ---

    fun setTeamMode(mode: TeamMode) {
        form.update { it.copy(teamMode = mode, teamA = null) }
        if (mode == TeamMode.BALANCED) balance()
        if (mode == TeamMode.PLAYER_DRAFT) suggestCaptains()
    }

    /** Moves a player to the other team (tap to move). */
    fun movePlayer(id: String) = form.update { f ->
        when (id) {
            in f.red -> f.copy(red = f.red - id, blue = f.blue + id, balanceDifference = null, locked = f.locked - id)
            in f.blue -> f.copy(blue = f.blue - id, red = f.red + id, balanceDifference = null, locked = f.locked - id)
            else -> f
        }
    }

    fun toggleLock(id: String) = form.update { f ->
        val team = if (id in f.red) Team.RED else Team.BLUE
        f.copy(locked = if (id in f.locked) f.locked - id else f.locked + (id to team))
    }

    fun balance() {
        viewModelScope.launch {
            val ratings = uiState.value.players.associate { it.player.id to (it.rating?.value ?: Rating.START) }
            form.update { f ->
                val split = TeamBalancer.balance(f.selected, ratings, f.locked, env.newSeed()) ?: return@update f
                f.copy(
                    red = split.red,
                    blue = split.blue,
                    balanceDifference = split.difference,
                    teamMode = TeamMode.BALANCED,
                )
            }
        }
    }

    fun setOddMode(mode: OddSeatMode) = form.update { it.copy(oddMode = mode) }

    fun setDoublePilot(id: String) = form.update { it.copy(doublePilot = id) }

    fun setCaptain(team: Team, id: String) = form.update { f ->
        when (team) {
            Team.RED -> f.copy(redCaptain = id, blueCaptain = f.blueCaptain.takeIf { it != id })
            Team.BLUE -> f.copy(blueCaptain = id, redCaptain = f.redCaptain.takeIf { it != id })
        }
    }

    private fun suggestCaptains() {
        val state = uiState.value
        val games = state.players.associate { it.player.id to (it.rating?.games ?: 0) }
        val values = state.players.associate { it.player.id to (it.rating?.value ?: Rating.START) }
        val captains = PlayerDraft.suggestCaptains(form.value.selected, games, values)
        form.update { it.copy(redCaptain = captains.getOrNull(0), blueCaptain = captains.getOrNull(1)) }
    }

    // --- Heroes ---

    fun selectMethod(id: String) = form.update { it.copy(methodId = id) }

    fun setPoolSource(source: PoolSource) = form.update { it.copy(poolSource = source) }

    fun toggleCustomHero(id: String) = form.update { f ->
        f.copy(customSelection = if (id in f.customSelection) f.customSelection - id else f.customSelection + id)
    }

    fun setCustomSelection(ids: Set<String>) = form.update { it.copy(customSelection = ids) }

    fun setMaxComplexity(max: Int?) = form.update { it.copy(maxComplexity = max) }

    fun setStepTimer(enabled: Boolean) = form.update { it.copy(stepTimer = enabled) }

    fun setStepSeconds(seconds: Int) = form.update { it.copy(stepSeconds = seconds, stepTimer = true) }

    // --- Coin ---

    fun flipCoin() = form.update {
        it.copy(teamA = TieBreakerCoin.flip(Random(env.newSeed())), flipCount = it.flipCount + 1)
    }

    /** The table flipped the physical coin. */
    fun setTeamA(team: Team) = form.update { it.copy(teamA = team) }

    // --- Navigation ---

    fun next() {
        val state = uiState.value
        if (state.blocker != null) return
        form.update { f ->
            val nextStage = SetupStage.entries.getOrNull(f.stage.ordinal + 1) ?: return@update f
            f.copy(stage = nextStage)
        }
    }

    fun back(): Boolean {
        val stage = form.value.stage
        if (stage == SetupStage.PLAYERS) return false
        form.update { it.copy(stage = SetupStage.entries[stage.ordinal - 1]) }
        return true
    }

    fun goTo(stage: SetupStage) {
        if (stage.ordinal < form.value.stage.ordinal) form.update { it.copy(stage = stage) }
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
        form.update { it.copy(starting = true) }
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
                    form.value =
                        SetupForm(methodId = f.methodId, poolSource = f.poolSource, maxComplexity = f.maxComplexity)
                    events.send(SetupEvent.OpenBoard)
                },
                onFailure = { e ->
                    message.value = errorMessage(e)
                    form.update { it.copy(starting = false) }
                },
            )
        }
    }

    private fun SetupForm.withDefaultTeams(): SetupForm {
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

    companion object {
        private const val AVATAR_COUNT = 24
        private val QUICK_ADD_COLORS = listOf(
            0xFF3FC1B0,
            0xFFC9A35B,
            0xFFE26459,
            0xFF4F8FE0,
            0xFFB18CE0,
            0xFF6CC27A,
            0xFFE0A35B,
            0xFFD27BB0,
        )

        fun errorMessage(e: Throwable): String = when (e) {
            is DraftError.PoolTooSmall -> "Needs ${e.required} heroes, only ${e.available} available. Widen the pool."
            is DraftError -> e.message ?: "Can't start this draft"
            else -> "Can't start this draft"
        }
    }
}
