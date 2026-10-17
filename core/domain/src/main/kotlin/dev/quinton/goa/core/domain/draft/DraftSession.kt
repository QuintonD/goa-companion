package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team
import kotlinx.serialization.Serializable

/**
 * A hero seat. Drafting fills seats, not players: a double pilot owns two seats on one team and
 * a team may have more seats than players (docs/DOMAIN.md, Draft → Seats).
 */
@Serializable
data class Seat(
    val team: Team,
    /** 0-based position within the team; picks fill seats in this order. */
    val index: Int,
    val pilotId: String? = null,
    /** Plays with its Handicap card (larger team in odd counts). */
    val handicap: Boolean = false,
) {
    val id: String get() = seatId(team, index)

    companion object {
        fun seatId(team: Team, index: Int): String = "${team.name.lowercase()}-$index"
    }
}

/** A concrete step of a session (teams resolved from the coin). */
@Serializable
data class PlannedStep(
    val team: Team,
    val action: DraftAction,
    /** For picks: the seat index (within [team]) this pick fills. */
    val seat: Int? = null,
    val hidden: Boolean = false,
    val seconds: Int? = null,
    /** Filled by the app, not chosen (All Random); cannot be undone. */
    val auto: Boolean = false,
) {
    val seatId: String? get() = seat?.let { Seat.seatId(team, it) }
}

@Serializable
data class DraftEvent(
    val step: Int,
    val team: Team,
    val action: DraftAction,
    val heroId: String,
    val seatId: String? = null,
)

/** Everything needed to start a draft. [candidates] = eligible heroes (owned, filtered). */
data class DraftSetup(
    val id: String,
    val createdAt: Long,
    val method: DraftMethod,
    val seats: List<Seat>,
    val teamA: Team,
    val candidates: List<String>,
    val seed: Long,
    /** Per-step timer for this session in seconds; null = off. Step overrides still apply. */
    val stepSeconds: Int? = method.stepSeconds,
)

/**
 * Immutable, serialisable draft state. Persisted while in progress so it survives process
 * death; [DraftEngine] produces every new state.
 */
@Serializable
data class DraftSession(
    val id: String,
    val createdAt: Long,
    val method: DraftMethod,
    val seed: Long,
    val teamA: Team,
    val seats: List<Seat>,
    /** Eligible heroes the pool was drawn from (re-roll and swaps draw from these). */
    val candidates: List<String>,
    val pool: List<String>,
    /** Single Draft: seat id → the heroes dealt to that seat. */
    val hands: Map<String, List<String>> = emptyMap(),
    val plan: List<PlannedStep>,
    val history: List<DraftEvent> = emptyList(),
    val stepSeconds: Int? = null,
    /** Seat id → hero after the finished draft was rearranged within teams. */
    val assignments: Map<String, String> = emptyMap(),
) {
    val teamB: Team get() = teamA.opponent

    val isComplete: Boolean get() = history.size >= plan.size

    val currentStepIndex: Int? get() = if (isComplete) null else history.size

    val currentStep: PlannedStep? get() = currentStepIndex?.let { plan[it] }

    val isStarted: Boolean get() = history.any { !plan[it.step].auto }

    val canUndo: Boolean get() = history.isNotEmpty() && !plan[history.last().step].auto

    val bans: List<DraftEvent> get() = history.filter { it.action == DraftAction.BAN }

    val picks: List<DraftEvent> get() = history.filter { it.action == DraftAction.PICK }

    val bannedIds: Set<String> get() = bans.mapTo(mutableSetOf()) { it.heroId }

    val pickedIds: Set<String> get() = picks.mapTo(mutableSetOf()) { it.heroId }

    fun seatsOf(team: Team): List<Seat> = seats.filter { it.team == team }.sortedBy { it.index }

    fun seat(id: String): Seat? = seats.firstOrNull { it.id == id }

    fun slotOf(team: Team): TeamSlot = if (team == teamA) TeamSlot.A else TeamSlot.B

    fun teamOf(slot: TeamSlot): Team = if (slot == TeamSlot.A) teamA else teamB

    /** Heroes dealt to [seatId] (Single Draft); null when the method has no hands. */
    fun handOf(seatId: String): List<String>? = hands[seatId]

    /**
     * True while the result of history entry [eventIndex] must stay secret: it was a hidden step
     * and the draft is still inside the same run of hidden steps.
     */
    fun isConcealed(eventIndex: Int): Boolean {
        val event = history.getOrNull(eventIndex) ?: return false
        if (!plan[event.step].hidden) return false
        val current = currentStepIndex ?: return false
        return (event.step + 1..current).all { plan[it].hidden }
    }

    /** Steps in the current run that share the current team and action, e.g. "Pick 1 of 2". */
    fun currentRun(): StepRun? {
        val index = currentStepIndex ?: return null
        val step = plan[index]
        fun same(s: PlannedStep) = s.team == step.team && s.action == step.action
        var start = index
        while (start > 0 && same(plan[start - 1])) start--
        var end = index
        while (end + 1 < plan.size && same(plan[end + 1])) end++
        return StepRun(step.team, step.action, position = index - start + 1, length = end - start + 1)
    }
}

data class StepRun(val team: Team, val action: DraftAction, val position: Int, val length: Int)

/** Hero (if any) assigned to a seat, after any rearranging. */
data class SeatAssignment(val seat: Seat, val heroId: String?)
