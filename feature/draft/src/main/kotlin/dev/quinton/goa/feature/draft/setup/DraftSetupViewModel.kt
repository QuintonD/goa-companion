package dev.quinton.goa.feature.draft.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.draft.TieBreakerCoin
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.data.DraftSetupPrefills
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

    private val inputs: Flow<DraftSetupInputs> = combine(
        players.observePlayers(),
        ratings.observePlayerRatings().onStart { emit(emptyMap()) },
        catalogRepository.catalog,
        settings.settings,
        drafts.methods,
    ) { p, r, c, s, m -> DraftSetupInputs(p, r, c, s, m) }

    val uiState: StateFlow<SetupUiState> =
        combine(
            inputs,
            form.filterNotNull(),
            drafts.active,
            message,
            matches.observeMatches().onStart { emit(emptyList()) },
        ) { inputs, form, active, msg, log ->
            DraftSetupPresenter.derive(inputs, form, active, msg)
                .copy(lastGameNote = DraftSetupPresenter.lastGameNote(form, log))
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

    // --- Players ---

    fun togglePlayer(id: String) = edit { it.togglePlayer(id) }

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
                DraftSetupPresenter.quickAddPlayer(trimmed, count, env.newId(), env.nowMillis()),
            )
            // Re-typing someone already ticked must not count them twice.
            edit { it.withPlayer(player.id) }
        }
    }

    // --- Teams ---

    fun setTeamMode(mode: TeamMode) {
        edit { it.copy(teamMode = mode, teamA = null) }
        if (mode == TeamMode.BALANCED) balance()
        if (mode == TeamMode.PLAYER_DRAFT) suggestCaptains()
    }

    /** Moves a player to the other team (tap to move). */
    fun movePlayer(id: String) = edit { it.movePlayer(id) }

    fun toggleLock(id: String) = edit { it.toggleLock(id) }

    fun balance() {
        viewModelScope.launch {
            val players = uiState.value.players
            edit { f -> DraftSetupPresenter.balanced(f, players, env.newSeed()) ?: f }
        }
    }

    fun setOddMode(mode: OddSeatMode) = edit { it.copy(oddMode = mode) }

    fun setDoublePilot(id: String) = edit { it.copy(doublePilot = id) }

    fun setCaptain(team: Team, id: String) = edit { it.withCaptain(team, id) }

    private fun suggestCaptains() {
        val players = uiState.value.players
        edit { DraftSetupPresenter.withSuggestedCaptains(it, players) }
    }

    // --- Heroes ---

    fun selectMethod(id: String) = edit { it.copy(methodId = id) }

    fun setPoolSource(source: PoolSource) = edit { it.copy(poolSource = source) }

    fun toggleCustomHero(id: String) = edit { it.toggleCustomHero(id) }

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
        edit { it.nextStage() }
    }

    fun back(): Boolean {
        val previous = current.previousStage() ?: return false
        edit { previous }
        return true
    }

    fun goTo(stage: SetupStage) = edit { it.goTo(stage) }

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
        if (state.selectedMethod == null || f.teamA == null) return
        if (state.blocker != null || f.starting) return
        edit { it.copy(starting = true) }
        viewModelScope.launch {
            val candidates = DraftSetupPresenter.candidates(inputs.first(), f)
            val result = DraftSetupPresenter.start(state, candidates, env.newId(), env.nowMillis(), env.newSeed())
                ?: return@launch
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
        fun errorMessage(e: Throwable): String = DraftSetupPresenter.errorMessage(e)
    }
}
