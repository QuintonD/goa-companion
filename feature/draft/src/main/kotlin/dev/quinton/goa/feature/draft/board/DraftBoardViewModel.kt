package dev.quinton.goa.feature.draft.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.presentation.draft.BoardLocal
import dev.quinton.goa.core.presentation.draft.BoardUiState
import dev.quinton.goa.core.presentation.draft.DraftBoard
import dev.quinton.goa.core.presentation.draft.DraftEnvironment
import dev.quinton.goa.core.presentation.draft.DraftProgress
import dev.quinton.goa.core.presentation.draft.HeroInfo
import dev.quinton.goa.core.presentation.draft.SeatTap
import dev.quinton.goa.feature.draft.data.DraftRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface BoardEvent {
    data class Finished(val matchId: String, val openMatchSetup: Boolean) : BoardEvent

    data object Closed : BoardEvent
}

/**
 * The pass-and-play board. What each action does to the draft is the shared `core:presentation`
 * [DraftBoard] (the hub's web board runs the same code, ADR-030 §1); this class persists the
 * result, runs the step clock and saves the finished draft.
 */
@HiltViewModel
class DraftBoardViewModel @Inject constructor(
    private val drafts: DraftRepository,
    catalog: CatalogRepository,
    players: PlayerRepository,
    private val matches: MatchRepository,
    private val records: DraftRecordRepository,
    private val env: DraftEnvironment,
) : ViewModel() {
    private val local = MutableStateFlow(BoardLocal())
    private val events = Channel<BoardEvent>(Channel.BUFFERED)
    val boardEvents: Flow<BoardEvent> = events.receiveAsFlow()
    private var timerJob: Job? = null

    private val heroes = catalog.catalog.map { c ->
        c.heroes.associate { it.id to HeroInfo.of(it) }
    }.onStart { emit(emptyMap()) }
    private val playerMap = players.observePlayers(includeArchived = true).map { list -> list.associateBy { it.id } }
        .onStart { emit(emptyMap()) }

    val uiState: StateFlow<BoardUiState> = combine(drafts.active, heroes, playerMap, local) { progress, h, p, l ->
        BoardUiState(loading = false, progress = progress, heroes = h, players = p, local = l)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardUiState())

    init {
        // Restart the step timer whenever the current step changes.
        viewModelScope.launch {
            drafts.active
                .map {
                    (it as? DraftProgress.Board)?.session?.let { s -> s.currentStepIndex to s.currentStep?.seconds }
                }
                .distinctUntilChanged()
                .collect { restartTimer(it?.second) }
        }
    }

    private fun restartTimer(seconds: Int?) {
        timerJob?.cancel()
        local.update { it.copy(remainingSeconds = seconds) }
        if (seconds == null) return
        timerJob = viewModelScope.launch {
            var left = seconds
            while (left > -MAX_OVERTIME_S) {
                delay(TICK_MS)
                left--
                local.update { it.copy(remainingSeconds = left) }
            }
        }
    }

    private suspend fun current(): DraftProgress? = drafts.active.first()

    private fun updateBoard(transform: (DraftSession) -> Result<DraftSession>) {
        viewModelScope.launch {
            val session = (current() as? DraftProgress.Board)?.session ?: return@launch
            transform(session).fold(
                onSuccess = { drafts.saveActive(DraftProgress.Board(it)) },
                onFailure = { e -> local.update { it.copy(message = message(e)) } },
            )
        }
    }

    // --- Hero board ---

    fun tapHero(heroId: String) {
        val s = uiState.value.board ?: return
        local.update { DraftBoard.tapHero(s, it, heroId) }
    }

    fun cancelConfirm() = local.update { it.copy(confirm = null, confirmStep = null) }

    fun confirm() {
        val pending = local.value
        val heroId = pending.confirm ?: return
        val stepIndex = pending.confirmStep ?: return
        local.update { it.copy(confirm = null, confirmStep = null) }
        updateBoard { s -> DraftBoard.confirm(s, heroId, stepIndex) }
    }

    /** Before the first choice: replace a pool hero with a random eligible one. */
    fun swapOut(heroId: String) {
        local.update { it.copy(confirm = null, confirmStep = null) }
        if (uiState.value.board == null) return
        updateBoard { s -> DraftBoard.swapOut(s, heroId, env.newSeed()) }
    }

    fun reroll() = updateBoard { DraftEngine.reroll(it, env.newSeed()) }

    fun undo() {
        local.update { it.copy(confirm = null, confirmStep = null, selectedSeat = null, redo = null) }
        viewModelScope.launch {
            val board = current() as? DraftProgress.Board ?: return@launch
            DraftBoard.undo(board, uiState.value.heroes).fold(
                onSuccess = { outcome ->
                    drafts.saveActive(outcome.progress)
                    if (outcome.message != null) {
                        local.update { it.copy(message = outcome.message, redo = outcome.redo) }
                    }
                },
                onFailure = { e -> local.update { it.copy(message = message(e)) } },
            )
        }
    }

    /** Re-applies the step the last undo took back, if the draft is still at that step. */
    fun redo() {
        val event = local.value.redo ?: return
        local.update { it.copy(redo = null) }
        updateBoard { s -> DraftBoard.redo(s, event) }
    }

    /** A dimmed tile in a dealt-hands draft: say why it can't be chosen. */
    fun tapUnavailable(heroId: String) {
        val state = uiState.value
        val s = state.board ?: return
        val text = DraftBoard.unavailableMessage(s, heroId, state.heroes, state::playerName) ?: return
        local.update { it.copy(message = text) }
    }

    fun acknowledgePrivacy() {
        val index = uiState.value.board?.currentStepIndex ?: return
        local.update { it.copy(privacyAcknowledged = index) }
    }

    // --- Completion ---

    fun selectSeat(seatId: String) {
        val board = uiState.value.board ?: return
        when (val tap = DraftBoard.tapSeat(board, local.value.selectedSeat, seatId, uiState.value.heroes)) {
            is SeatTap.Refused -> local.update { it.copy(selectedSeat = null, message = tap.message) }

            is SeatTap.Select -> local.update { it.copy(selectedSeat = tap.seatId) }

            is SeatTap.Swap -> {
                local.update { it.copy(selectedSeat = null) }
                updateBoard { DraftEngine.swapSeats(it, tap.first, tap.second) }
            }
        }
    }

    fun toggleHandicap(seatId: String) = updateBoard { s -> DraftBoard.toggleHandicap(s, seatId) }

    /** Saves the draft record and a PENDING match, clears the in-progress draft. */
    fun finish(openMatchSetup: Boolean) {
        if (local.value.saving) return
        local.update { it.copy(saving = true) }
        viewModelScope.launch {
            val session = (current() as? DraftProgress.Board)?.session
            if (session == null) {
                local.update { it.copy(saving = false) }
                return@launch
            }
            val matchId = env.newId()
            DraftBoard.finish(session, matchId, env.nowMillis()).fold(
                onSuccess = { finished ->
                    matches.upsert(finished.match)
                    records.save(finished.record)
                    drafts.saveActive(null)
                    events.send(BoardEvent.Finished(matchId, openMatchSetup))
                },
                onFailure = { e -> local.update { it.copy(message = message(e)) } },
            )
            local.update { it.copy(saving = false) }
        }
    }

    fun discard() {
        viewModelScope.launch {
            drafts.saveActive(null)
            events.send(BoardEvent.Closed)
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null, redo = null) }

    // --- Player Draft ---

    private fun updatePlayers(transform: (PlayerDraftSession) -> Result<DraftProgress>) {
        viewModelScope.launch {
            val session = (current() as? DraftProgress.Players)?.session ?: return@launch
            transform(session).fold(
                onSuccess = { drafts.saveActive(it) },
                onFailure = { e -> local.update { it.copy(message = message(e)) } },
            )
        }
    }

    fun chooseHeroFor(playerId: String?) = local.update { it.copy(choosingFor = playerId) }

    fun assignHero(heroId: String) {
        val playerId = local.value.choosingFor ?: return
        local.update { it.copy(choosingFor = null) }
        updatePlayers { s -> DraftBoard.assignHero(s, playerId, heroId) }
    }

    fun clearHero(playerId: String) = updatePlayers { s -> DraftBoard.clearHero(s, playerId) }

    /** A captain's pick; the forced last pick follows automatically. */
    fun draftPlayer(playerId: String) = updatePlayers { s -> DraftBoard.draftPlayer(s, playerId) }

    fun undoPlayerPick() = updatePlayers { s -> DraftBoard.undoPlayerPick(s) }

    private fun message(e: Throwable): String = DraftBoard.message(e, uiState.value.heroes)

    private companion object {
        const val TICK_MS = 1_000L
        const val MAX_OVERTIME_S = 3_600
    }
}
