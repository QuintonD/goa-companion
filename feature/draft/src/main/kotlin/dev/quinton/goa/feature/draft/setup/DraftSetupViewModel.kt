package dev.quinton.goa.feature.draft.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.data.RatingsRepository
import dev.quinton.goa.core.data.SettingsRepository
import dev.quinton.goa.core.data.rating.EloConfigRepository
import dev.quinton.goa.core.designsystem.theme.PlayerColors
import dev.quinton.goa.core.domain.draft.PoolSource
import dev.quinton.goa.core.domain.teams.OddSeatMode
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.rating.Rating
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import dev.quinton.goa.core.presentation.draft.DraftSetupPresenter
import dev.quinton.goa.core.presentation.draft.QuickAddPlayer
import dev.quinton.goa.core.presentation.draft.SetupForm
import dev.quinton.goa.core.presentation.draft.SetupInputs
import dev.quinton.goa.core.presentation.draft.SetupStage
import dev.quinton.goa.core.presentation.draft.SetupUiState
import dev.quinton.goa.core.presentation.draft.TeamMode
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.data.DraftSetupPrefills
import javax.inject.Inject
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

/**
 * The draft setup wizard. The form, its edits, the derived state and starting the draft are the
 * shared `core:presentation` code ([SetupForm], [DraftSetupPresenter]) the hub's web page runs
 * too (ADR-030 §1); this class wires them to the repositories and persists every change (ADR-017).
 */
@HiltViewModel
class DraftSetupViewModel @Inject constructor(
    private val players: PlayerRepository,
    ratings: RatingsRepository,
    eloConfig: EloConfigRepository,
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

    /** Player ratings and the group's starting rating (what an unrated player counts as). */
    private val rated: Flow<Pair<Map<String, Rating>, Double>> = combine(
        ratings.observePlayerRatings().onStart { emit(emptyMap()) },
        eloConfig.config,
    ) { r, config -> r to config.initialRating }

    private val inputs: Flow<SetupInputs> = combine(
        players.observePlayers(),
        rated,
        catalogRepository.catalog,
        settings.settings,
        drafts.methods,
    ) { p, (r, start), c, s, m -> SetupInputs(p, r, c, s, m, initialRating = start) }

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
        if (name.isBlank()) return
        viewModelScope.launch {
            val everyone = players.observePlayers(includeArchived = true).first()
            val candidate = QuickAddPlayer.create(env.newId(), name, everyone, env.nowMillis(), QUICK_ADD_COLORS)
                ?: return@launch
            // A known name (archived players included) selects that player rather than
            // creating a second one whose games would split the rating.
            val player = players.ensurePlayer(candidate)
            // Re-typing someone already ticked must not count them twice.
            edit { it.withAddedPlayer(player.id) }
        }
    }

    // --- Teams ---

    fun setTeamMode(mode: TeamMode) {
        edit { it.withTeamModeOnly(mode) }
        if (mode == TeamMode.BALANCED) balance()
        if (mode == TeamMode.PLAYER_DRAFT) suggestCaptains()
    }

    /** Moves a player to the other team (tap to move). */
    fun movePlayer(id: String) = edit { it.movePlayer(id) }

    fun toggleLock(id: String) = edit { it.toggleLock(id) }

    fun balance() {
        viewModelScope.launch {
            val ratings = SetupForm.balanceRatings(uiState.value.players)
            edit { it.balanced(ratings, env.newSeed()) }
        }
    }

    fun setOddMode(mode: OddSeatMode) = edit { it.withOddMode(mode) }

    fun setDoublePilot(id: String) = edit { it.withDoublePilot(id) }

    fun setCaptain(team: Team, id: String) = edit { it.withCaptain(team, id) }

    private fun suggestCaptains() {
        val options = uiState.value.players
        edit { it.withSuggestedCaptains(options) }
    }

    // --- Heroes ---

    fun selectMethod(id: String) = edit { it.withMethod(id) }

    fun setPoolSource(source: PoolSource) = edit { it.withPoolSource(source) }

    fun toggleCustomHero(id: String) = edit { it.toggleCustomHero(id) }

    fun setCustomSelection(ids: Set<String>) = edit { it.withCustomSelection(ids) }

    fun setMaxComplexity(max: Int?) = edit { it.withMaxComplexity(max) }

    fun setStepTimer(enabled: Boolean) = edit { it.withStepTimer(enabled) }

    fun setStepSeconds(seconds: Int) = edit { it.withStepSeconds(seconds) }

    // --- Coin ---

    fun flipCoin() = edit { it.flipped(env.newSeed()) }

    /** The table flipped the physical coin. */
    fun setTeamA(team: Team) = edit { it.withTeamA(team) }

    // --- Navigation ---

    fun next() {
        val blocker = uiState.value.blocker
        edit { it.next(blocker) }
    }

    fun back(): Boolean {
        if (current.back() == null) return false
        edit { it.back() ?: it }
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
            val result = DraftSetupPresenter.start(inputs.first(), state, env) ?: return@launch
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
        /**
         * Palette colours for quick-added players, minus Coral and Sky: next to the Red and Blue
         * team colours they read as a team (docs/DESIGN.md).
         */
        private val TEAM_LIKE = setOf("Coral", "Sky")
        private val QUICK_ADD_COLORS = PlayerColors.all.filter { it.name !in TEAM_LIKE }.map { it.argb }

        fun errorMessage(e: Throwable): String = DraftSetupPresenter.errorMessage(e)
    }
}
