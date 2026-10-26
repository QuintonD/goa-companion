package dev.quinton.goa.feature.draft.board

import dev.quinton.goa.core.domain.draft.DraftError
import dev.quinton.goa.core.domain.draft.DraftEvent
import dev.quinton.goa.core.domain.draft.DraftSession
import dev.quinton.goa.core.domain.teams.PlayerDraftPhase
import dev.quinton.goa.core.domain.teams.PlayerDraftSession
import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import dev.quinton.goa.core.model.player.Player
import dev.quinton.goa.feature.draft.data.DraftProgress
import dev.quinton.goa.feature.draft.ui.HeroInfo
import dev.quinton.goa.feature.draft.ui.TileState
import dev.quinton.goa.feature.draft.ui.actionName
import dev.quinton.goa.feature.draft.ui.info
import dev.quinton.goa.feature.draft.ui.teamLabel
import dev.quinton.goa.feature.draft.ui.teamName

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

    /** The screen's title: the method, "Player Draft", or "Draft". */
    val title: String get() = board?.method?.name ?: playerDraft?.let { "Player Draft" } ?: "Draft"

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
        /** Pools larger than this get search and role filters. */
        const val SEARCH_FROM = 10

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

/**
 * The draft board's words and checks, shared by the phone's `DraftBoardViewModel` and the web app
 * (ADR-043 "Table, W3").
 */
object DraftBoardText {
    /** What a refused step says. Neutral for a concealed hero: never who concealed it or how. */
    fun message(e: Throwable, heroes: Map<String, HeroInfo>): String {
        fun hero(id: String) = heroes[id]?.name ?: "That hero"
        return when (e) {
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

    /** "Undid Ban Arien", or "Undid a hidden pick": a choice still hidden from the other team is never named. */
    fun undone(session: DraftSession, last: DraftEvent, heroes: Map<String, HeroInfo>): String {
        val what = if (session.isConcealed(session.history.lastIndex)) {
            "a hidden ${actionName(last.action).lowercase()}"
        } else {
            "${actionName(last.action)} ${heroes.info(last.heroId).name}"
        }
        return "Undid $what"
    }

    /** A dimmed tile in a dealt-hands draft: why it can't be chosen. Null when no hand is on the clock. */
    fun notDealt(state: BoardUiState, heroId: String): String? {
        val s = state.board ?: return null
        val seat = s.currentStep?.seatId?.let { s.seat(it) } ?: return null
        return "${state.heroes.info(heroId).name} wasn't dealt to ${state.playerName(seat.pilotId)}"
    }

    const val ALL_IN_POOL = "Every eligible hero is already in the pool"

    /** The step banner's headline, e.g. "◆ Red — Pick 2". */
    fun bannerTitle(session: DraftSession): String? {
        val step = session.currentStep ?: return null
        val run = session.currentRun()
        val verb = actionName(step.action) + if (run != null && run.length > 1) " ${run.length}" else ""
        return "${teamLabel(step.team)} — $verb"
    }

    /** The banner's second line: "Team A · Step 3 of 12 · 1 of 2 · for Ben · hidden". */
    fun bannerDetail(session: DraftSession, playerName: (String?) -> String): String? {
        val step = session.currentStep ?: return null
        val run = session.currentRun()
        val teamRole = if (step.team == session.teamA) "Team A" else "Team B"
        val runText = if (run != null && run.length > 1) " · ${run.position} of ${run.length}" else ""
        val seat = step.seatId?.let { id -> session.seat(id) }
        val pilot = seat?.pilotId?.let { " · for ${playerName(it)}" } ?: ""
        return "$teamRole · Step ${(session.currentStepIndex ?: 0) + 1} of ${session.plan.size}$runText$pilot" +
            if (step.hidden) " · hidden" else ""
    }

    /** The step clock: "0:27", or "+0:05" in overtime. */
    fun stepClock(remaining: Int): String {
        val over = remaining < 0
        val left = kotlin.math.abs(remaining)
        return (if (over) "+" else "") + "${left / 60}:${(left % 60).toString().padStart(2, '0')}"
    }

    fun spokenStepClock(remaining: Int): String =
        if (remaining < 0) "Overtime ${kotlin.math.abs(remaining)} seconds" else "$remaining seconds left"

    /** The confirm dialog's line under the hero. */
    fun confirmText(session: DraftSession, pilotName: String?): String? {
        val step = session.currentStep ?: return null
        return when {
            step.action == DraftAction.BAN -> "${teamLabel(step.team)} removes this hero from the draft."
            pilotName != null -> "${teamLabel(step.team)} takes this hero for $pilotName."
            else -> "${teamLabel(step.team)} takes this hero."
        }
    }

    /** "Pass the phone to ◆ Red" gate: the body text. */
    fun privacyText(session: DraftSession): String? {
        val step = session.currentStep ?: return null
        return "Hidden ${actionName(step.action).lowercase()}: only ${teamName(step.team)} should look. " +
            "Earlier hidden choices stay covered."
    }

    /** The Player Draft's banner: title and body for its phase. */
    fun playerDraftBanner(state: BoardUiState, session: PlayerDraftSession): Pair<String, String> {
        val team = session.captains.nextTeam
        return when (session.phase) {
            PlayerDraftPhase.HEROES ->
                "Heroes for every player" to
                    "${session.method.name}: ${session.method.summary} Choose in any order; teams come next."

            PlayerDraftPhase.CAPTAINS -> {
                val captain = state.playerName(team?.let { session.captains.captainOf(it) })
                "${team?.let { teamLabel(it) }} captain — draft a player" to
                    "$captain picks. Team A's captain drafts first, then captains alternate; the last player " +
                    "joins the team whose turn it is."
            }

            PlayerDraftPhase.DONE -> "Teams complete" to ""
        }
    }

    /** The finished draft's line under "Draft complete". */
    fun completionHint(session: DraftSession): String = when (session.seatLock) {
        null -> "Tap two heroes on the same team to swap who plays them."
        DraftError.PlayerDraftFixed -> "Everyone plays the hero chosen for them."
        else -> "Everyone plays the hero they picked from their own hand."
    }

    /** Pick order per team: a board's opening places heroes in that order (p.20). */
    fun lineUp(
        session: DraftSession,
        team: Team,
    ): List<String> = session.picks.filter { it.team == team }.map { it.heroId }
}
