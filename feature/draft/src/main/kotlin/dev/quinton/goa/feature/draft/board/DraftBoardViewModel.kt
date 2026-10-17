package dev.quinton.goa.feature.draft.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.quinton.goa.core.data.CatalogRepository
import dev.quinton.goa.core.data.DraftRecordRepository
import dev.quinton.goa.core.data.MatchRepository
import dev.quinton.goa.core.data.PlayerRepository
import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.draft.data.DraftEnvironment
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.data.DraftRepository
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.teamName
import javax.inject.Inject
import kotlin.random.Random
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

/** Transient, screen-only state (everything else is the persisted draft). */
data class BoardLocal(
    /** Hero waiting for confirm-before-commit. */
    val confirm: String? = null,
    /** Step index [confirm] was chosen for; the commit is refused if the draft moved on. */
    val confirmStep: Int? = null,
    /** Step index whose "pass the phone" screen was acknowledged. */
    val privacyAcknowledged: Int? = null,
    /** Seconds left on the step timer; negative = overtime. */
    val remainingSeconds: Int? = null,
    /** First seat tapped on the completion screen (tap another to swap heroes). */
    val selectedSeat: String? = null,
    /** Player Draft: whose hero is being chosen. */
    val choosingFor: String? = null,
    val saving: Boolean = false,
    val message: String? = null,
)

data class BoardUiState(
    val loading: Boolean = true,
    val progress: DraftProgress? = null,
    val heroes: Map<String, HeroInfo> = emptyMap(),
    val players: Map<String, Player> = emptyMap(),
    val local: BoardLocal = BoardLocal(),
) {
    val board: DraftSession? get() = (progress as? DraftProgress.Board)?.session
    val playerDraft: PlayerDraftSession? get() = (progress as? DraftProgress.Players)?.session

    /** "Pass the phone" gate before a hidden step. */
    val privacyGate: Boolean
        get() {
            val s = board ?: return false
            val step = s.currentStep ?: return false
            return step.hidden && local.privacyAcknowledged != s.currentStepIndex
        }

    /**
     * Undo is offered for the last real board step, the captains' last pick in a Player Draft,
     * and — after a Player Draft was handed off to the board — that final captain pick.
     */
    val canUndo: Boolean
        get() = when (val p = progress) {
            is DraftProgress.Board -> p.session.canUndo || p.playerDraft?.captains?.picks?.isNotEmpty() == true
            is DraftProgress.Players -> p.session.captains.picks.isNotEmpty()
            null -> false
        }

    fun playerName(id: String?): String = id?.let { players[it]?.name } ?: "Unknown player"
}

sealed interface BoardEvent {
    data class Finished(val matchId: String, val openMatchSetup: Boolean) : BoardEvent

    data object Closed : BoardEvent
}

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
        if (s.isComplete) return
        local.update { it.copy(confirm = heroId, confirmStep = s.currentStepIndex) }
    }

    fun cancelConfirm() = local.update { it.copy(confirm = null, confirmStep = null) }

    fun confirm() {
        val pending = local.value
        val heroId = pending.confirm ?: return
        val stepIndex = pending.confirmStep ?: return
        local.update { it.copy(confirm = null, confirmStep = null) }
        updateBoard { s ->
            // The command names the step it was made for: if the draft moved on in between
            // (a double tap, a restored screen) the engine refuses it instead of applying it to
            // the next step.
            val step = s.plan.getOrNull(stepIndex)
            DraftEngine.apply(s, DraftCommand(heroId, step?.team, step?.action, step = stepIndex))
        }
    }

    /** Before the first choice: replace a pool hero with a random eligible one. */
    fun swapOut(heroId: String) {
        local.update { it.copy(confirm = null, confirmStep = null) }
        val session = uiState.value.board ?: return
        if (session.fromPlayerDraft) {
            local.update { it.copy(message = message(DraftError.PlayerDraftFixed)) }
            return
        }
        val options = DraftEngine.swapCandidates(session)
        if (options.isEmpty()) {
            local.update { it.copy(message = "Every eligible hero is already in the pool") }
            return
        }
        updateBoard { s -> DraftEngine.swapPoolHero(s, heroId, options[Random(env.newSeed()).nextInt(options.size)]) }
    }

    fun reroll() = updateBoard { DraftEngine.reroll(it, env.newSeed()) }

    fun undo() {
        local.update { it.copy(confirm = null, confirmStep = null, selectedSeat = null) }
        viewModelScope.launch {
            val board = current() as? DraftProgress.Board ?: return@launch
            val origin = board.playerDraft
            if (!board.session.canUndo && origin != null) {
                // A finished Player Draft: take back the captains' last pick (heroes stay chosen).
                PlayerDraft.undo(origin.captains, origin.selection.players).fold(
                    onSuccess = { drafts.saveActive(DraftProgress.Players(origin.copy(captains = it))) },
                    onFailure = { e -> local.update { it.copy(message = message(e)) } },
                )
            } else {
                updateBoard { DraftEngine.undo(it) }
            }
        }
    }

    fun acknowledgePrivacy() {
        val index = uiState.value.board?.currentStepIndex ?: return
        local.update { it.copy(privacyAcknowledged = index) }
    }

    // --- Completion ---

    fun selectSeat(seatId: String) {
        uiState.value.board?.seatLock?.let { lock ->
            local.update { it.copy(selectedSeat = null, message = message(lock)) }
            return
        }
        val first = local.value.selectedSeat
        when {
            first == null -> local.update { it.copy(selectedSeat = seatId) }

            first == seatId -> local.update { it.copy(selectedSeat = null) }

            else -> {
                local.update { it.copy(selectedSeat = null) }
                updateBoard { DraftEngine.swapSeats(it, first, seatId) }
            }
        }
    }

    fun toggleHandicap(seatId: String) = updateBoard { s ->
        DraftEngine.setHandicap(s, seatId, s.seat(seatId)?.handicap != true)
    }

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
            DraftEngine.toPendingMatch(session, matchId, env.nowMillis()).fold(
                onSuccess = { match ->
                    matches.upsert(match)
                    records.save(DraftEngine.toRecord(session, matchId))
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

    fun dismissMessage() = local.update { it.copy(message = null) }

    // --- Player Draft ---

    private fun updatePlayers(transform: (PlayerDraftSession) -> Result<PlayerDraftSession>) {
        viewModelScope.launch {
            val session = (current() as? DraftProgress.Players)?.session ?: return@launch
            transform(session).fold(
                onSuccess = { updated ->
                    val next = if (updated.phase == PlayerDraftPhase.DONE) {
                        updated.toDraftSession().map {
                            DraftProgress.Board(it, playerDraft = updated)
                        }.getOrElse { DraftProgress.Players(updated) }
                    } else {
                        DraftProgress.Players(updated)
                    }
                    drafts.saveActive(next)
                },
                onFailure = { e -> local.update { it.copy(message = message(e)) } },
            )
        }
    }

    fun chooseHeroFor(playerId: String?) = local.update { it.copy(choosingFor = playerId) }

    fun assignHero(heroId: String) {
        val playerId = local.value.choosingFor ?: return
        local.update { it.copy(choosingFor = null) }
        updatePlayers { s -> HeroSelections.choose(s.selection, playerId, heroId).map { s.copy(selection = it) } }
    }

    fun clearHero(playerId: String) = updatePlayers { s ->
        Result.success(s.copy(selection = HeroSelections.clear(s.selection, playerId)))
    }

    fun draftPlayer(playerId: String) = updatePlayers { s ->
        PlayerDraft.pick(s.captains, playerId).map { s.copy(captains = it) }
    }

    fun undoPlayerPick() = updatePlayers { s ->
        PlayerDraft.undo(s.captains, s.selection.players).map { s.copy(captains = it) }
    }

    private fun message(e: Throwable): String {
        val heroes = uiState.value.heroes
        fun hero(id: String) = heroes[id]?.name ?: "That hero"
        return when (e) {
            // Neutral on purpose: never says who concealed the hero or how.
            DraftError.Unavailable -> "Choose a different hero"
            is DraftError.AlreadyBanned -> "${hero(e.heroId)} is banned"
            is DraftError.AlreadyPicked -> "${hero(e.heroId)} was already picked by ${teamName(e.team)}"
            is DraftError.Taken -> "${hero(e.heroId)} is already taken"
            is DraftError.NotInHand -> "${hero(e.heroId)} wasn't dealt to this seat"
            is DraftError.NotInPool -> "${hero(e.heroId)} isn't in the pool"
            is DraftError.InvalidSeats -> e.reason
            is DraftError -> e.message ?: "That isn't allowed right now"
            else -> "Something went wrong"
        }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val MAX_OVERTIME_S = 3_600
    }
}
