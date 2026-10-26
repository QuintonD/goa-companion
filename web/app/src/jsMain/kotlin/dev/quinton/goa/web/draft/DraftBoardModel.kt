package dev.quinton.goa.web.draft

import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.catalog.Catalog
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.draft.board.BoardLocal
import dev.quinton.goa.feature.draft.board.BoardUiState
import dev.quinton.goa.feature.draft.board.DraftBoardText
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.web.AppServices
import dev.quinton.goa.web.table.resilientStarting
import kotlin.js.Date
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What finishing the draft asks the screen to do next. */
sealed interface BoardEvent {
    data class Finished(val matchId: String, val openMatchSetup: Boolean) : BoardEvent

    data object Closed : BoardEvent
}

/**
 * The draft board's state and actions (the phone's `DraftBoardViewModel`): every ban and pick goes
 * through the shared [DraftEngine] and is stored at once, so a reload or a locked phone loses nothing.
 * The step timer counts from when the step started (not ticks), so it is right after Safari paused it.
 */
@OptIn(ExperimentalUuidApi::class)
class DraftBoardModel(
    private val services: AppServices,
    private val scope: CoroutineScope,
    private val onEvent: (BoardEvent) -> Unit,
) {
    private val drafts = services.data.table.drafts
    private val local = MutableStateFlow(BoardLocal())
    private var timerJob: Job? = null

    private val heroes = resilientStarting<Catalog?>("The catalog", null) {
        services.data.content.catalog.catalog.map { it }
    }
        .map { c -> c?.heroes?.associate { it.id to HeroInfo.of(it) }.orEmpty() }

    private val playerMap = resilientStarting<List<Player>>("Players", emptyList()) {
        services.data.group.players.observePlayers(includeArchived = true)
    }.map { list -> list.associateBy { it.id } }

    private val loaded = MutableStateFlow(false)

    val uiState: StateFlow<BoardUiState> = combine(drafts.active, heroes, playerMap, local, loaded) {
            progress,
            h,
            p,
            l,
            ready,
        ->
        BoardUiState(loading = !ready, progress = progress, heroes = h, players = p, local = l)
    }.stateIn(scope, SharingStarted.Eagerly, BoardUiState())

    init {
        scope.launch {
            drafts.load()
            loaded.value = true
        }
        // Restart the step timer whenever the current step changes.
        scope.launch {
            drafts.active
                .map {
                    (it as? DraftProgress.Board)?.session?.let { s ->
                        Triple(s.id, s.currentStepIndex, s.currentStep?.seconds)
                    }
                }
                .distinctUntilChanged()
                .collect { restartTimer(it?.third) }
        }
    }

    private fun restartTimer(seconds: Int?) {
        timerJob?.cancel()
        local.update { it.copy(remainingSeconds = seconds) }
        if (seconds == null) return
        val startedAt = Date.now()
        timerJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                val left = seconds - ((Date.now() - startedAt) / MILLIS).toInt()
                local.update { it.copy(remainingSeconds = left) }
                if (left <= -MAX_OVERTIME_S) break
            }
        }
    }

    private suspend fun current(): DraftProgress? = drafts.activeNow()

    private fun updateBoard(transform: (DraftSession) -> Result<DraftSession>) {
        scope.launch {
            val session = (current() as? DraftProgress.Board)?.session ?: return@launch
            transform(session).fold(
                onSuccess = { save(DraftProgress.Board(it)) },
                onFailure = { e -> show(message(e)) },
            )
        }
    }

    private suspend fun save(progress: DraftProgress?) {
        try {
            drafts.saveActive(progress)
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            console.warn("Couldn't store the draft", e)
            show("Couldn't save the draft on this device. Keep this page open.")
        }
    }

    private fun show(text: String) = local.update { it.copy(message = text) }

    // --- Hero board

    fun tapHero(heroId: String) {
        val s = uiState.value.board ?: return
        if (s.isComplete) return
        local.update { it.copy(confirm = heroId, confirmStep = s.currentStepIndex, redo = null) }
    }

    fun cancelConfirm() = local.update { it.copy(confirm = null, confirmStep = null) }

    fun confirm() {
        val pending = local.value
        val heroId = pending.confirm ?: return
        val stepIndex = pending.confirmStep ?: return
        local.update { it.copy(confirm = null, confirmStep = null) }
        updateBoard { s ->
            // The command names the step it was made for: a double tap can't apply it to the next step.
            val step = s.plan.getOrNull(stepIndex)
            DraftEngine.apply(s, DraftCommand(heroId, step?.team, step?.action, step = stepIndex))
        }
    }

    /** Before the first choice: replace a pool hero with a random eligible one. */
    fun swapOut(heroId: String) {
        local.update { it.copy(confirm = null, confirmStep = null) }
        val session = uiState.value.board ?: return
        if (session.fromPlayerDraft) {
            show(message(DraftError.PlayerDraftFixed))
            return
        }
        val options = DraftEngine.swapCandidates(session)
        if (options.isEmpty()) {
            show(DraftBoardText.ALL_IN_POOL)
            return
        }
        updateBoard { s ->
            DraftEngine.swapPoolHero(s, heroId, options[Random(Random.nextLong()).nextInt(options.size)])
        }
    }

    fun reroll() = updateBoard { DraftEngine.reroll(it, Random.nextLong()) }

    fun undo() {
        local.update { it.copy(confirm = null, confirmStep = null, selectedSeat = null, redo = null) }
        scope.launch {
            val board = current() as? DraftProgress.Board ?: return@launch
            val origin = board.playerDraft
            if (!board.session.canUndo && origin != null) {
                // A finished Player Draft: take back the captains' last pick (heroes stay chosen).
                PlayerDraft.undo(origin.captains, origin.selection.players).fold(
                    onSuccess = { save(DraftProgress.Players(origin.copy(captains = it))) },
                    onFailure = { e -> show(message(e)) },
                )
            } else {
                val session = board.session
                val last = session.history.lastOrNull()
                DraftEngine.undo(session).fold(
                    onSuccess = { undone ->
                        save(DraftProgress.Board(undone))
                        if (last != null) {
                            // A choice still hidden from the other team is never named.
                            val text = DraftBoardText.undone(session, last, uiState.value.heroes)
                            local.update { it.copy(message = text, redo = last) }
                        }
                    },
                    onFailure = { e -> show(message(e)) },
                )
            }
        }
    }

    /** Re-applies the step the last undo took back, if the draft is still at that step. */
    fun redo() {
        val event = local.value.redo ?: return
        local.update { it.copy(redo = null) }
        updateBoard { s ->
            DraftEngine.apply(s, DraftCommand(event.heroId, event.team, event.action, step = event.step))
        }
    }

    /** A dimmed tile in a dealt-hands draft: say why it can't be chosen. */
    fun tapUnavailable(heroId: String) {
        DraftBoardText.notDealt(uiState.value, heroId)?.let(::show)
    }

    fun acknowledgePrivacy() {
        val index = uiState.value.board?.currentStepIndex ?: return
        local.update { it.copy(privacyAcknowledged = index) }
    }

    // --- Completion

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
        DraftEngine.setHandicap(
            s,
            seatId,
            s.seat(seatId)?.handicap != true,
        )
    }

    /** Saves the draft record and a PENDING match into the group's log, clears the draft in progress. */
    fun finish(openMatchSetup: Boolean) {
        if (local.value.saving) return
        local.update { it.copy(saving = true) }
        scope.launch {
            try {
                val session = (current() as? DraftProgress.Board)?.session ?: return@launch
                val matchId = Uuid.random().toString()
                DraftEngine.toPendingMatch(session, matchId, Date.now().toLong()).fold(
                    onSuccess = { match ->
                        services.data.group.matches.upsert(match)
                        services.data.group.draftRecords.save(DraftEngine.toRecord(session, matchId))
                        drafts.saveActive(null)
                        onEvent(BoardEvent.Finished(matchId, openMatchSetup))
                    },
                    onFailure = { e -> show(message(e)) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                console.warn("Couldn't save the drafted match", e)
                show("Couldn't save the match. The draft is kept: try again.")
            } finally {
                local.update { it.copy(saving = false) }
            }
        }
    }

    fun discard() {
        scope.launch {
            save(null)
            onEvent(BoardEvent.Closed)
        }
    }

    fun dismissMessage() = local.update { it.copy(message = null, redo = null) }

    // --- Player Draft

    private fun updatePlayers(transform: (PlayerDraftSession) -> Result<PlayerDraftSession>) {
        scope.launch {
            val session = (current() as? DraftProgress.Players)?.session ?: return@launch
            transform(session).fold(
                onSuccess = { updated ->
                    val next = if (updated.phase == PlayerDraftPhase.DONE) {
                        updated.toDraftSession().map { DraftProgress.Board(it, playerDraft = updated) }
                            .getOrElse { DraftProgress.Players(updated) }
                    } else {
                        DraftProgress.Players(updated)
                    }
                    save(next)
                },
                onFailure = { e -> show(message(e)) },
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

    /** A captain's pick; the forced last pick follows automatically. */
    fun draftPlayer(
        playerId: String,
    ) = updatePlayers { s -> PlayerDraft.pickForcingLast(s.captains, playerId).map { s.copy(captains = it) } }

    fun undoPlayerPick() = updatePlayers { s ->
        PlayerDraft.undo(s.captains, s.selection.players).map { s.copy(captains = it) }
    }

    private fun message(e: Throwable): String = DraftBoardText.message(e, uiState.value.heroes)

    private companion object {
        const val TICK_MS = 250L
        const val MILLIS = 1_000.0
        const val MAX_OVERTIME_S = 3_600
    }
}
