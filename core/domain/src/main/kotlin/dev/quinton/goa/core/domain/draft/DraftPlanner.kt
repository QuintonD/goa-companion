package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.model.draft.DraftAction

/** A concrete step for one seat layout, before Team A is known (teams are still A/B slots). */
data class SlotStep(
    val team: TeamSlot,
    val action: DraftAction,
    /** For picks: the seat (0-based, within the team) this pick fills. */
    val seat: Int? = null,
    val hidden: Boolean = false,
    val seconds: Int? = null,
)

/**
 * Expands a method's [SequenceRule] into the exact step list for a seat layout. The expansion
 * depends only on seat counts, never on the heroes chosen, so the whole draft is known up front:
 * the method editor previews it and the engine walks it (undo = drop the last result).
 *
 * Rules applied to every method:
 * - a pick for a team whose seats are all filled is skipped (the rulebook's odd-count rule for
 *   Pick and Ban, and how the House draft ends with a single last pick);
 * - the draft ends as soon as every seat is filled (no trailing bans).
 */
object DraftPlanner {
    /** Hard stop for pattern rules that never fill the seats (bad custom methods). */
    private const val MAX_STEPS = 200

    fun plan(sequence: SequenceRule, seatsA: Int, seatsB: Int): Result<List<SlotStep>> = runCatching {
        if (seatsA < 1 || seatsB < 1) throw DraftError.InvalidSeats("Each team needs at least one seat")
        expand(source(sequence, seatsA, seatsB), seatsA, seatsB)
    }

    /** [plan] for a method, rejecting pool/sequence combinations that can't run ([problem]). */
    fun plan(method: DraftMethod, seatsA: Int, seatsB: Int): Result<List<SlotStep>> =
        problem(method)?.let { Result.failure(it) } ?: plan(method.sequence, seatsA, seatsB)

    /**
     * A method-level problem independent of the seat layout; null when the method can run.
     * Dealt hands + bans: bans draw from the whole pool, so they could empty a seat's hand and
     * leave its pick with nothing to choose [APP].
     */
    fun problem(method: DraftMethod): DraftError? = if (method.pool is PoolRule.DealPerSeat && hasBans(method.sequence)) {
        DraftError.NotSupported("Dealt hands can't be combined with bans: a ban could empty a player's hand")
    } else {
        null
    }

    private fun hasBans(sequence: SequenceRule): Boolean = when (sequence) {
        SequenceRule.RandomAssign, is SequenceRule.Lineup -> false
        is SequenceRule.Pattern -> (sequence.opening + sequence.repeat).any { it.action == DraftAction.BAN }
        is SequenceRule.ByCount -> sequence.tables.any { table -> table.steps.any { it.action == DraftAction.BAN } }
    }

    /** The raw step stream before skipping; throws [DraftError] when the rule can't serve the layout. */
    private fun source(sequence: SequenceRule, seatsA: Int, seatsB: Int): Sequence<StepSpec> = when (sequence) {
        SequenceRule.RandomAssign -> cycle(listOf(pick(TeamSlot.A), pick(TeamSlot.B)))

        is SequenceRule.Lineup -> {
            // Seat counts are symmetric here, so slots map to Red/Blue either way.
            if (sequence.lineups.none { it.red.size == seatsA && it.blue.size == seatsB }) {
                throw DraftError.NotSupported(lineupSizes(sequence))
            }
            cycle(listOf(pick(TeamSlot.A), pick(TeamSlot.B)))
        }

        is SequenceRule.Pattern -> sequence.opening.asSequence() + cycle(sequence.repeat)

        is SequenceRule.ByCount -> table(sequence, seatsA, seatsB)?.steps?.asSequence()
            ?: throw DraftError.SequenceTooShort(seatsA, seatsB)
    }

    /** The table a [SequenceRule.ByCount] uses for this layout (smallest covering 2 × larger team). */
    fun table(rule: SequenceRule.ByCount, seatsA: Int, seatsB: Int): CountSequence? {
        val needed = 2 * maxOf(seatsA, seatsB)
        val sorted = rule.tables.sortedBy { it.players }
        return sorted.firstOrNull { it.players >= needed } ?: sorted.lastOrNull()
    }

    private fun lineupSizes(rule: SequenceRule.Lineup): String =
        "Needs " + rule.lineups.joinToString(" or ") { "${it.red.size} v ${it.blue.size}" } + " heroes"

    private fun cycle(steps: List<StepSpec>): Sequence<StepSpec> =
        if (steps.isEmpty()) emptySequence() else generateSequence(0) { it + 1 }.map { steps[it % steps.size] }

    private fun expand(source: Sequence<StepSpec>, seatsA: Int, seatsB: Int): List<SlotStep> {
        val filled = intArrayOf(0, 0)
        val need = intArrayOf(seatsA, seatsB)
        val out = mutableListOf<SlotStep>()
        var sincePick = 0
        val steps = source.iterator()
        fun full() = filled[0] == need[0] && filled[1] == need[1]
        fun runaway() = out.size > MAX_STEPS || sincePick > MAX_STEPS
        while (!full() && !runaway() && steps.hasNext()) {
            val spec = steps.next()
            val t = spec.team.ordinal
            when (spec.action) {
                DraftAction.PICK -> if (filled[t] < need[t]) {
                    out += SlotStep(spec.team, DraftAction.PICK, filled[t], spec.hidden, spec.seconds)
                    filled[t]++
                    sincePick = 0
                } else {
                    sincePick++
                }

                DraftAction.BAN -> {
                    out += SlotStep(spec.team, DraftAction.BAN, null, spec.hidden, spec.seconds)
                    sincePick++
                }
            }
        }
        if (!full()) throw DraftError.SequenceTooShort(seatsA, seatsB)
        return out
    }
}

/** Heroes a method needs in its pool for a layout: one per ban and one per pick. */
fun List<SlotStep>.heroesNeeded(): Int = size
