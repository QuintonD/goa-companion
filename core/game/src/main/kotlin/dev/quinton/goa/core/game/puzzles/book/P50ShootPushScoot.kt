package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C50 "Shoot, Push, Scoot". */
object P50ShootPushScoot : Puzzle() {
    override val concept = "C50"

    override val info = PuzzleInfo(
        id = "p50",
        number = 50,
        title = "Shoot, Push, Scoot",
        difficulty = 3,
        theme = "A push in the middle of a turn opens Fast Travel",
        heroes = listOf("silverarrow", "xargatha", "brogan", "arien"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 4. Silverarrow has played Shoot and Scoot and acts next; she has no cards " +
            "left in hand, so she cannot defend. Xargatha (Threatening Slash) and Brogan (Mad Dash) act " +
            "after her, both at 7. Xargatha keeps Stone Gaze, Brogan Brutal Jab. Red has one minion left " +
            "in the Battle Zone.",
        nudges = listOf(
            "You can't Fast Travel out of a Zone with an enemy minion in it, unless it stops being there.",
            "What happens to every minion of the Battle Zone when the lane is pushed?",
            "Shoot Red's last minion, then Fast Travel far away.",
        ),
        explanation = "Shoot and Scoot targets a unit exactly 2 spaces away: Xargatha or Red's last minion. " +
            "Xargatha simply defends, and with Red's minion still in the middle Zone Silverarrow cannot Fast " +
            "Travel (p.11), so Brogan's Mad Dash reaches her. Defeating the last enemy minion pushes the lane " +
            "in the middle of the turn (p.8): a Wave counter flips, the Battle Zone moves towards Red, and " +
            "every minion of the old Battle Zone is removed, Blue's too. After the attack (p.17) the middle " +
            "Zone holds no enemy hero or minion, so she may Fast Travel to any adjacent Zone without enemies " +
            "(p.11), far from both attackers. Stepping 1 space instead leaves her next to Xargatha.",
        rules = listOf(
            "p.8 Push the Lane, Triggering a Push",
            "p.9 Range",
            "p.11 Fast Travel",
            "p.17 After the Attack",
        ),
    )

    override val playerSeats = setOf("silverarrow")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("silverarrow", Team.BLUE, Hex(-1, -3)) {
            played("silverarrow-gold")
            resolved(1, "silverarrow-silver")
            resolved(2, "silverarrow-red-1")
            resolved(3, "silverarrow-blue-1")
            discard("silverarrow-green-1")
            hand()
        }
        hero("xargatha", Team.RED, Hex(-3, -2)) {
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        hero("brogan", Team.RED, Hex(-4, 0)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-1")
            hand("brogan-blue-1")
        }
        hero("arien", Team.BLUE, Hex(4, -6)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "silverarrow")
    }

    // Every Fast Travel space offered wins (Red's pre-Throne Zone holds Red, so none is there); (7,-7) is
    // in Blue's pre-Throne Zone.
    override val solution = listOf(action(ActionType.PRIMARY), unitAt(1, -4), space(7, -7))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Xargatha: she defends, no push, and Fast Travel stays closed",
            listOf(action(ActionType.PRIMARY), hero("xargatha")),
        ),
        WrongTry(
            "Shoot the minion but stay: Brogan's Mad Dash reaches her",
            listOf(action(ActionType.PRIMARY), unitAt(1, -4), decline()),
        ),
        WrongTry("Walk instead: both free spaces are next to Xargatha", listOf(action(ActionType.MOVEMENT))),
    )
}
