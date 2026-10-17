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

    fun plan(sequence: SequenceRule, seatsA: Int, seatsB: Int): Result<List<SlotStep>> {
        if (seatsA < 1 || seatsB < 1) return Result.failure(DraftError.InvalidSeats("Each team needs at least one seat"))
        val source: Sequence<StepSpec> = when (sequence) {
            SequenceRule.RandomAssign -> cycle(listOf(pick(TeamSlot.A), pick(TeamSlot.B)))

            is SequenceRule.Lineup -> {
                // Seat counts are symmetric here, so slots map to Red/Blue either way.
                if (sequence.lineups.none { it.red.size == seatsA && it.blue.size == seatsB }) {
                    return Result.failure(DraftError.NotSupported(lineupSizes(sequence)))
                }
                cycle(listOf(pick(TeamSlot.A), pick(TeamSlot.B)))
            }

            is SequenceRule.Pattern -> {
                if (sequence.repeat.none { it.action == DraftAction.PICK } && sequence.opening.isEmpty()) {
                    return Result.failure(DraftError.SequenceTooShort(seatsA, seatsB))
                }
                sequence.opening.asSequence() + cycle(sequence.repeat)
            }

            is SequenceRule.ByCount -> {
                val table = table(sequence, seatsA, seatsB)
                    ?: return Result.failure(DraftError.SequenceTooShort(seatsA, seatsB))
                table.steps.asSequence()
            }
        }
        return expand(source, seatsA, seatsB)
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

    private fun expand(source: Sequence<StepSpec>, seatsA: Int, seatsB: Int): Result<List<SlotStep>> {
        val filled = intArrayOf(0, 0)
        val need = intArrayOf(seatsA, seatsB)
        val out = mutableListOf<SlotStep>()
        var sincePick = 0
        for (spec in source) {
            if (filled[0] == need[0] && filled[1] == need[1]) break
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
            if (out.size > MAX_STEPS || sincePick > MAX_STEPS) break
        }
        return if (filled[0] == need[0] && filled[1] == need[1]) {
            Result.success(out)
        } else {
            Result.failure(DraftError.SequenceTooShort(seatsA, seatsB))
        }
    }
}

/** Heroes a method needs in its pool for a layout: one per ban and one per pick. */
fun List<SlotStep>.heroesNeeded(): Int = size
