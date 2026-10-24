package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C12 "Fast Travel Within the Zone". */
object P12FastTravelWithinTheZone : Puzzle() {
    override val concept = "C12"

    override val info = PuzzleInfo(
        id = "p12",
        number = 12,
        title = "Fast Travel Within the Zone",
        difficulty = 1,
        theme = "Fast Travel",
        heroes = listOf("xargatha", "dodger", "brogan", "swift"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 4. Xargatha has played Cleave and acts next; she has no cards left in hand. " +
            "Swift has played Prepared Shot (initiative 9) and Brogan Mad Dash (7): both will attack her if " +
            "they can. Dodger has already acted.",
        nudges = listOf(
            "Walking one space won't get you clear.",
            "Your card's Movement action can be replaced by something much longer.",
            "Fast Travel to the far end of the side Zone Xargatha stands in.",
        ),
        explanation = "Fast Travel replaces a whole Movement action, whatever its value, and may land on " +
            "any empty space of the same Zone or an adjacent one, as long as neither the Zone you leave nor " +
            "the one you enter holds an enemy hero or minion (p.11; the Arien example allows your own Zone). " +
            "Swift in Blue's Throne Zone and Brogan with the Blue minions in the Battle Zone close those two " +
            "Zones, but Xargatha's own side Zone is long and empty: Fast Travel is not only for changing " +
            "Zones. Holding leaves her on Prepared Shot's line (Range 4, in a straight line, not adjacent: " +
            "p.13), and every space one step away is on that line or next to the end of one of Mad Dash's " +
            "two-space charges (p.11 Movement in the card text). Don't stop short either: the space three " +
            "steps along the Zone is still next to the end of Brogan's charge to the south-east.",
        rules = listOf("p.11 Fast Travel", "p.11 Movement in the card text", "p.13 Ranged attacks"),
    )

    override val playerSeats = setOf("xargatha")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("xargatha", Team.RED, Hex(5, -1)) {
            played("xargatha-gold")
            resolved(1, "xargatha-red-1")
            resolved(2, "xargatha-green-1")
            resolved(3, "xargatha-blue-1")
            discard("xargatha-silver")
            hand()
        }
        hero("dodger", Team.RED, Hex(-1, -2)) {
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            resolved(4, "dodger-green-1")
            hand("dodger-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(3, -1)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-green-1")
            resolved(3, "brogan-blue-1")
            hand("brogan-silver")
        }
        hero("swift", Team.BLUE, Hex(7, -1)) {
            level = 2
            played("swift-red-2a")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-1")
            hand("swift-blue-1")
            items("swift-red-2b")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "xargatha")
    }

    override val solution = listOf(action(ActionType.FAST_TRAVEL), space(0, 4))

    override val wrongTries = listOf(
        WrongTry("Hold: Swift's shot runs straight along the row", listOf(action(ActionType.HOLD))),
        WrongTry(
            "Walk one space: every step is on Swift's line or in Brogan's charge",
            listOf(action(ActionType.MOVEMENT)),
        ),
        WrongTry(
            "Fast Travel only three spaces along the Zone: Brogan charges two spaces south-east, next to her",
            listOf(action(ActionType.FAST_TRAVEL), space(2, 2)),
        ),
    )
}
