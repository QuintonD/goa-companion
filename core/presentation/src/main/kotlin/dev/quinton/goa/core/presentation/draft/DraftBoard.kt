package dev.quinton.goa.core.presentation.draft

import dev.quinton.goa.core.domain.draft.DraftCommand
import dev.quinton.goa.core.domain.draft.DraftEngine
import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftEvent
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.draft.SeatAssignment
import dev.quinton.goa.core.domain.teams.HeroSelections
import dev.quinton.goa.core.domain.teams.PlayerDraft
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.catalog.Role
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.draft.DraftRecord
import dev.quinton.goa.core.model.match.Match
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * The board's own state beside the persisted draft: what is waiting for a confirm, which hidden
 * step's "pass the phone" gate was acknowledged, the seat tapped first, the last message. Screen
 * state on the phone; kept per browser on the hub (POST/redirect/GET), hence serialisable.
 */
@Serializable
data class BoardLocal(
    /** Hero waiting for confirm-before-commit. */
    val confirm: String? = null,
    /** Step index [confirm] was chosen for; the commit is refused if the draft moved on. */
    val confirmStep: Int? = null,
    /** Step index whose "pass the phone" screen was acknowledged. */
    val privacyAcknowledged: Int? = null,
    /** Seconds left on the step timer; negative = overtime (the phone's ticking clock only). */
    val remainingSeconds: Int? = null,
    /** First seat tapped on the completion screen (tap another to swap heroes). */
    val selectedSeat: String? = null,
    /** Player Draft: whose hero is being chosen. */
    val choosingFor: String? = null,
    val saving: Boolean = false,
    val message: String? = null,
    /** The step the last undo took back; offered as "Redo" with the undo message. */
    val redo: DraftEvent? = null,
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

/** One pool tile and how it is drawn. */
data class PoolTile(val heroId: String, val state: TileState)

/**
 * The pool as the board shows it. A hero chosen in the current hidden run is drawn exactly as
 * if it were still free (never "taken"), so neither the tile nor the counts give it away.
 */
data class PoolView(
    /** Dealt-hands drafts: the pilot whose hand is on the clock. */
    val handOwner: String?,
    /** Not (visibly) taken; in a dealt-hands draft the current hand comes first. */
    val open: List<PoolTile>,
    /** In a dealt-hands draft: the other hands' heroes (dimmed). */
    val otherHands: List<PoolTile>,
    /** Revealed bans and picks, shown compactly. */
    val taken: List<PoolTile>,
) {
    val left: Int get() = open.size + otherHands.size

    companion object {
        fun of(session: DraftSession): PoolView {
            val concealed = session.concealedIds()
            val step = session.currentStep
            val seatId = step?.seatId?.takeIf { step.action == DraftAction.PICK }
            val hand = seatId?.let { session.handOf(it) }
            fun stateOf(id: String): TileState {
                if (id !in concealed) {
                    session.history.lastOrNull { it.heroId == id }?.let { event ->
                        return if (event.action == DraftAction.BAN) {
                            TileState.Banned(event.team)
                        } else {
                            TileState.Picked(event.team)
                        }
                    }
                }
                return if (hand == null || id in hand) TileState.Available else TileState.Inactive
            }
            val tiles = session.pool.map { PoolTile(it, stateOf(it)) }
            val taken = tiles.filter { it.state is TileState.Banned || it.state is TileState.Picked }
            val free = tiles.filter { it !in taken }
            val (mine, others) = if (hand != null) free.partition { it.heroId in hand } else free to emptyList()
            return PoolView(
                handOwner = seatId?.takeIf { hand != null }?.let { session.seat(it)?.pilotId },
                open = mine,
                otherHands = others,
                taken = taken,
            )
        }
    }
}

/** A team's seats and bans as the board shows them; concealed choices are only flagged, never named. */
data class TeamView(
    val team: Team,
    val isTeamA: Boolean,
    /** This team is on the clock. */
    val active: Boolean,
    val seats: List<SeatView>,
    val bans: List<BanView>,
    /** Roles of the revealed picks only, in the official order, so a hidden pick never shows through. */
    val roles: List<Role>,
)

/** [heroId] is null both for an open seat and for a concealed pick ([hidden]). */
data class SeatView(
    val seatId: String,
    val pilotId: String?,
    val handicap: Boolean,
    val heroId: String?,
    val hidden: Boolean,
)

/** [heroId] is null for a concealed ban ([hidden]). */
data class BanView(val heroId: String?, val hidden: Boolean)

/** The step banner: "◆ Red — Pick 2" over "Team A · Step 3 of 10 · 1 of 2 · for Ann · hidden". */
data class StepBannerText(val title: String, val detail: String)

/** A thing the board refuses without a [DraftError] ("Every eligible hero is already in the pool"). */
class BoardRefusal(message: String) : IllegalStateException(message)

/** What an undo took back: the new progress, what to say, and what Redo would put back. */
data class UndoOutcome(val progress: DraftProgress, val message: String?, val redo: DraftEvent?)

/** A finished draft, ready to save: the PENDING match and the draft record that points at it. */
data class FinishedDraft(val match: Match, val record: DraftRecord)

/** Tapping a seat on the completion screen. */
sealed interface SeatTap {
    data class Refused(val message: String) : SeatTap

    /** [seatId] is now the selected seat (null = selection cleared). */
    data class Select(val seatId: String?) : SeatTap

    /** Swap the heroes of [first] and [second] (and clear the selection). */
    data class Swap(val first: String, val second: String) : SeatTap
}

/**
 * Everything the draft board does beyond `core:domain`, for the phone's ViewModel and the hub
 * (ADR-030 §1). Pure: each function takes the current state and returns the next one, or a
 * failure the caller shows with [message].
 */
object DraftBoard {
    /** Confirms the hero waiting in [local] for the step it was chosen for; a moved-on draft refuses it. */
    fun confirm(session: DraftSession, heroId: String, stepIndex: Int): Result<DraftSession> {
        // The command names the step it was made for: if the draft moved on in between (a double
        // tap, a restored screen, a second browser tab) the engine refuses it instead of applying
        // it to the next step.
        val step = session.plan.getOrNull(stepIndex)
        return DraftEngine.apply(session, DraftCommand(heroId, step?.team, step?.action, step = stepIndex))
    }

    /** The hero tapped on the board now waits for a confirm (nothing on a finished board). */
    fun tapHero(session: DraftSession, local: BoardLocal, heroId: String): BoardLocal =
        if (session.isComplete) {
            local
        } else {
            local.copy(
                confirm = heroId,
                confirmStep = session.currentStepIndex,
                redo = null,
            )
        }

    /** Before the first choice: replace a pool hero with a random eligible one ([seed] picks it). */
    fun swapOut(session: DraftSession, heroId: String, seed: Long): Result<DraftSession> {
        if (session.fromPlayerDraft) return Result.failure(DraftError.PlayerDraftFixed)
        val options = DraftEngine.swapCandidates(session)
        if (options.isEmpty()) return Result.failure(BoardRefusal("Every eligible hero is already in the pool"))
        return DraftEngine.swapPoolHero(session, heroId, options[Random(seed).nextInt(options.size)])
    }

    /**
     * Undo on the board: the last real step, or — on a board handed off from a finished Player
     * Draft — the captains' last pick (back to the Player Draft; heroes stay chosen). A choice still
     * hidden from the other team is never named in the message.
     */
    fun undo(progress: DraftProgress.Board, heroes: Map<String, HeroInfo>): Result<UndoOutcome> {
        val origin = progress.playerDraft
        if (!progress.session.canUndo && origin != null) {
            return PlayerDraft.undo(origin.captains, origin.selection.players).map {
                UndoOutcome(DraftProgress.Players(origin.copy(captains = it)), null, null)
            }
        }
        val session = progress.session
        val last = session.history.lastOrNull()
        return DraftEngine.undo(session).map { undone ->
            val message = last?.let {
                val what = if (session.isConcealed(session.history.lastIndex)) {
                    "a hidden ${actionName(it.action).lowercase()}"
                } else {
                    "${actionName(it.action)} ${heroes.info(it.heroId).name}"
                }
                "Undid $what"
            }
            UndoOutcome(DraftProgress.Board(undone), message, last)
        }
    }

    /** Re-applies the step the last undo took back, if the draft is still at that step. */
    fun redo(session: DraftSession, event: DraftEvent): Result<DraftSession> =
        DraftEngine.apply(session, DraftCommand(event.heroId, event.team, event.action, step = event.step))

    /** A dimmed tile in a dealt-hands draft: why it can't be chosen; null when there's nothing to say. */
    fun unavailableMessage(
        session: DraftSession,
        heroId: String,
        heroes: Map<String, HeroInfo>,
        playerName: (String?) -> String,
    ): String? {
        val seat = session.currentStep?.seatId?.let { session.seat(it) } ?: return null
        return "${heroes.info(heroId).name} wasn't dealt to ${playerName(seat.pilotId)}"
    }

    /** A tap on a seat of the finished board: select it, or swap with the seat selected first. */
    fun tapSeat(session: DraftSession, selected: String?, seatId: String, heroes: Map<String, HeroInfo>): SeatTap {
        session.seatLock?.let { return SeatTap.Refused(message(it, heroes)) }
        return when (selected) {
            null -> SeatTap.Select(seatId)
            seatId -> SeatTap.Select(null)
            else -> SeatTap.Swap(selected, seatId)
        }
    }

    fun toggleHandicap(session: DraftSession, seatId: String): Result<DraftSession> =
        DraftEngine.setHandicap(session, seatId, session.seat(seatId)?.handicap != true)

    /**
     * The finished draft as a PENDING match (one participant per seat, the coin carried over,
     * ADR-016) and its [DraftRecord]. The caller saves the match, then the record, then clears the
     * in-progress draft.
     */
    fun finish(session: DraftSession, matchId: String, now: Long): Result<FinishedDraft> =
        DraftEngine.toPendingMatch(session, matchId, now).map {
            FinishedDraft(it, DraftEngine.toRecord(session, matchId))
        }

    // --- Player Draft ---

    /** Applies [transform]; a finished Player Draft is handed off to a complete board. */
    fun updatePlayers(
        session: PlayerDraftSession,
        transform: (PlayerDraftSession) -> Result<PlayerDraftSession>,
    ): Result<DraftProgress> = transform(session).map { updated ->
        val handOff = if (updated.phase == PlayerDraftPhase.DONE) updated.toDraftSession().getOrNull() else null
        if (handOff != null) DraftProgress.Board(handOff, playerDraft = updated) else DraftProgress.Players(updated)
    }

    fun assignHero(session: PlayerDraftSession, playerId: String, heroId: String): Result<DraftProgress> =
        updatePlayers(session) { s ->
            HeroSelections.choose(s.selection, playerId, heroId).map { s.copy(selection = it) }
        }

    fun clearHero(session: PlayerDraftSession, playerId: String): Result<DraftProgress> =
        updatePlayers(session) { s -> Result.success(s.copy(selection = HeroSelections.clear(s.selection, playerId))) }

    /** A captain's pick; the forced last pick follows automatically ([PlayerDraft.pickForcingLast]). */
    fun draftPlayer(session: PlayerDraftSession, playerId: String): Result<DraftProgress> =
        updatePlayers(session) { s -> PlayerDraft.pickForcingLast(s.captains, playerId).map { s.copy(captains = it) } }

    /** Takes back the captains' last pick (with the forced pick it caused). */
    fun undoPlayerPick(session: PlayerDraftSession): Result<DraftProgress> =
        updatePlayers(session) { s -> PlayerDraft.undo(s.captains, s.selection.players).map { s.copy(captains = it) } }

    // --- What the board shows ---

    fun stepBanner(session: DraftSession, playerName: (String?) -> String): StepBannerText? {
        val step = session.currentStep ?: return null
        val run = session.currentRun()
        val verb = actionName(step.action) + if (run != null && run.length > 1) " ${run.length}" else ""
        val teamRole = if (step.team == session.teamA) "Team A" else "Team B"
        val runText = if (run != null && run.length > 1) " · ${run.position} of ${run.length}" else ""
        val seat = step.seatId?.let { id -> session.seat(id) }
        val pilot = seat?.pilotId?.let { " · for ${playerName(it)}" } ?: ""
        return StepBannerText(
            title = "${teamLabel(step.team)} — $verb",
            detail = "$teamRole · Step ${(session.currentStepIndex ?: 0) + 1} of ${session.plan.size}$runText$pilot" +
                if (step.hidden) " · hidden" else "",
        )
    }

    /** "Ban 1 · Pick 2 …" tokens for the whole plan, with teams resolved. */
    fun sequenceTokens(session: DraftSession): List<StepToken> =
        session.plan.map { StepToken("${actionName(it.action)} ${teamGlyph(it.team)}", it.action, it.team, it.hidden) }

    /** Both teams, Team A first, as the board shows them: concealed choices flagged, never named. */
    fun teams(session: DraftSession, heroes: Map<String, HeroInfo>): List<TeamView> {
        val concealed = session.concealedIds()
        val assignments = DraftEngine.assignments(session)
        return listOf(session.teamA, session.teamB).map { team ->
            val picks = assignments.filter { it.seat.team == team }
            val covered = picks.mapNotNull { it.heroId?.takeIf { id -> id !in concealed } }
                .flatMap { heroes.info(it).roles }
                .toSet()
            TeamView(
                team = team,
                isTeamA = team == session.teamA,
                active = session.currentStep?.team == team,
                seats = picks.map { seatView(it, concealed) },
                bans = session.bans.filter { it.team == team }.map { ban ->
                    val hidden = ban.heroId in concealed
                    BanView(if (hidden) null else ban.heroId, hidden)
                },
                roles = Role.entries.filter { it in covered },
            )
        }
    }

    private fun seatView(assignment: SeatAssignment, concealed: Set<String>): SeatView {
        val hidden = assignment.heroId != null && assignment.heroId in concealed
        return SeatView(
            seatId = assignment.seat.id,
            pilotId = assignment.seat.pilotId,
            handicap = assignment.seat.handicap,
            heroId = if (hidden) null else assignment.heroId,
            hidden = hidden,
        )
    }

    /** The line under "Draft complete": whether heroes may still move between seats. */
    fun completionHint(session: DraftSession): String = when (session.seatLock) {
        null -> "Tap two heroes on the same team to swap who plays them."
        DraftError.PlayerDraftFixed -> "Everyone plays the hero chosen for them."
        else -> "Everyone plays the hero they picked from their own hand."
    }

    /** The confirm dialog's line: "◆ Red removes this hero from the draft." */
    fun confirmText(session: DraftSession, playerName: (String?) -> String): String? {
        val step = session.currentStep ?: return null
        val pilot = step.seatId?.let { session.seat(it) }?.pilotId?.let { playerName(it) }
        return when {
            step.action == DraftAction.BAN -> "${teamLabel(step.team)} removes this hero from the draft."
            pilot != null -> "${teamLabel(step.team)} takes this hero for $pilot."
            else -> "${teamLabel(step.team)} takes this hero."
        }
    }

    /** "Pass the phone to ◆ Red" and the line under it, before a hidden step. */
    fun privacyText(session: DraftSession): StepBannerText? {
        val step = session.currentStep ?: return null
        return StepBannerText(
            title = "Pass the phone to ${teamLabel(step.team)}",
            detail = "Hidden ${actionName(step.action).lowercase()}: only ${teamName(step.team)} should look. " +
                "Earlier hidden choices stay covered.",
        )
    }

    /** The Player Draft's banner: which half is running and who is on the clock. */
    fun playerDraftBanner(session: PlayerDraftSession, playerName: (String?) -> String): StepBannerText {
        val team = session.captains.nextTeam
        return when (session.phase) {
            PlayerDraftPhase.HEROES -> StepBannerText(
                "Heroes for every player",
                "${session.method.name}: ${session.method.summary} Choose in any order; teams come next.",
            )

            PlayerDraftPhase.CAPTAINS -> {
                val captain = playerName(team?.let { session.captains.captainOf(it) })
                StepBannerText(
                    "${team?.let { teamLabel(it) }} captain — draft a player",
                    "$captain picks. Team A's captain drafts first, then captains alternate; the last player " +
                        "joins the team whose turn it is.",
                )
            }

            PlayerDraftPhase.DONE -> StepBannerText("Teams complete", "")
        }
    }

    /** What to tell the table when the engine refuses something. */
    fun message(e: Throwable, heroes: Map<String, HeroInfo>): String {
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

            is BoardRefusal -> e.message ?: "That isn't allowed right now"

            else -> "Something went wrong"
        }
    }
}
