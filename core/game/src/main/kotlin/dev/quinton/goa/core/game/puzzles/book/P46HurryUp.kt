package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C46 "Hurry Up!". */
object P46HurryUp : Puzzle() {
    override val concept = "C46"

    override val info = PuzzleInfo(
        id = "p46",
        number = 46,
        title = "Hurry Up!",
        difficulty = 3,
        theme = "Changing a teammate's initiative, ties",
        heroes = listOf("hanu", "brogan", "tigerclaw", "xargatha"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 3, turn 4. Hanu has played Hurry up! (12) and acts next; Brogan has played Mad Dash (7). " +
            "Xargatha has played Cleave (11) and keeps Threatening Slash; Tigerclaw has played Dodge (10) and " +
            "holds no card. Red shows on the Tie Breaker coin.",
        nudges = listOf(
            "Tigerclaw gets away at 10 unless Brogan goes sooner.",
            "Hurry up! can speed up a friend, and a tie is settled by the coin.",
            "Set Brogan's Mad Dash to 11.",
        ),
        explanation = "Initiative is checked again after every card (p.7), so Hurry up! moves a card in the queue. " +
            "Set Brogan's Mad Dash to 11: it ties Xargatha's Cleave across teams, Red shows, so she goes first " +
            "and then flips the coin (p.7); Brogan at 11 still acts before Tigerclaw's 10. One step lets her " +
            "block at most one of Brogan's two runs, east (to land between Tigerclaw and Xargatha) or north-east " +
            "(between Tigerclaw and the Red minion); either ends beside Tigerclaw, who has no card to defend " +
            "with (p.14), so make him the target, not his neighbour. Hurrying Tigerclaw's Dodge only ties him " +
            "with his own teammate, still ahead of Brogan, and he runs.",
        rules = listOf("p.7 Check the Initiative, Tied Initiative", "p.11 Movement in the card text", "p.14 Defense"),
    )

    override val playerSeats = setOf("hanu", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("tigerclaw", Team.RED, Hex(3, -3)) {
            played("tigerclaw-blue-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            discard("tigerclaw-green-1")
            hand()
        }
        hero("xargatha", Team.RED, Hex(4, -2)) {
            played("xargatha-gold")
            resolved(1, "xargatha-silver")
            resolved(2, "xargatha-green-1")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-red-1")
        }
        hero("hanu", Team.BLUE, Hex(-1, -1)) {
            played("hanu-silver")
            resolved(1, "hanu-gold")
            resolved(2, "hanu-red-1")
            resolved(3, "hanu-green-1")
            hand("hanu-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(1, -2)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-green-1")
            resolved(3, "brogan-blue-1")
            hand("brogan-silver")
        }
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "hanu")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("brogan"),
        action(ActionType.PRIMARY),
        space(3, -4),
        hero("tigerclaw"),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Hurry up! on Tigerclaw's Dodge: he still acts before Brogan and runs",
            listOf(action(ActionType.PRIMARY), hero("tigerclaw")),
        ),
        WrongTry("Hanu holds: Tigerclaw moves away at 10", listOf(action(ActionType.HOLD))),
        WrongTry(
            "Brogan dashes next to Tigerclaw but hits the minion",
            listOf(
                action(ActionType.PRIMARY),
                hero("brogan"),
                action(ActionType.PRIMARY),
                space(3, -4),
                unitAt(4, -4),
            ),
        ),
    )
}
