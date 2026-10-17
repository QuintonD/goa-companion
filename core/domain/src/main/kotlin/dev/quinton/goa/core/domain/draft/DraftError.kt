package dev.quinton.goa.core.domain.draft

import dev.quinton.goa.core.model.draft.DraftAction
import dev.quinton.goa.core.model.match.Team

/**
 * Typed failures from [DraftEngine] and [DraftPlanner], returned inside [Result] (never thrown
 * to callers). The UI maps each to a human message.
 */
sealed class DraftError(message: String) : Exception(message) {
    // Setup
    data class InvalidSeats(val reason: String) : DraftError(reason)

    data class SequenceTooShort(val seatsA: Int, val seatsB: Int) :
        DraftError("The method's sequence never fills $seatsA + $seatsB seats")

    data class NotSupported(val reason: String) : DraftError(reason)

    data class PoolTooSmall(val required: Int, val available: Int) :
        DraftError("Needs $required heroes, only $available available")

    // Steps
    data object Complete : DraftError("The draft is already complete")

    data class WrongTeam(val expected: Team, val actual: Team) : DraftError("It is $expected's turn, not $actual's")

    data class WrongAction(val expected: DraftAction, val actual: DraftAction) :
        DraftError("This step is a $expected, not a $actual")

    data class NotInPool(val heroId: String) : DraftError("$heroId is not in the pool")

    data class NotInHand(val heroId: String, val seatId: String) : DraftError("$heroId was not dealt to $seatId")

    data class AlreadyPicked(val heroId: String, val team: Team) : DraftError("$heroId was already picked by $team")

    data class AlreadyBanned(val heroId: String) : DraftError("$heroId is banned")

    data object PoolExhausted : DraftError("No hero left to choose for this step")

    data class Taken(val heroId: String) : DraftError("$heroId is already taken")

    // Editing
    data object NothingToUndo : DraftError("Nothing to undo")

    data object AlreadyStarted : DraftError("The pool can only change before the first step")

    data object NotComplete : DraftError("Seats can be rearranged once the draft is complete")

    data class UnknownSeat(val seatId: String) : DraftError("Unknown seat $seatId")
}
