package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
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
        heroes = listOf("silverarrow", "arien", "xargatha", "brogan"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 3: play your card. Silverarrow holds Shoot and Scoot, Trailblazer and Disorient. " +
            "Xargatha's only card is Threatening Slash (7); Brogan holds Mad Dash (7) and Mighty Punch. Arien's " +
            "only card is Liquid Leap. Red has one minion left in the Battle Zone.",
        nudges = listOf(
            "Brogan's Mad Dash reaches Silverarrow where she stands, none of her cards holds it, and her own " +
                "steps all end next to Xargatha. She has to leave the Zone.",
            "No Fast Travel out of a Zone with an enemy minion in it, unless it stops being there.",
            "Play Shoot and Scoot and shoot Red's last minion, then Fast Travel away.",
        ),
        explanation = "Mad Dash moves Brogan 2 spaces in a straight line next to an enemy unit and attacks " +
            "(card text): from where he stands that is Silverarrow, and her best card defends 1 + 4 for the " +
            "Blue minions around her (p.14) against his 8. Her free neighbouring spaces are next to Xargatha, " +
            "and Disorient (3) comes too late. Trailblazer is her fastest card, but its Fast Travel is \"if able\", " +
            "and with Red's minion in the middle Zone it is not (p.11). Shoot and Scoot targets a unit exactly " +
            "2 spaces away: Xargatha, who has no card left, or Red's last minion. Defeating Xargatha is " +
            "tempting, but the minion still closes the Zone and Brogan arrives. Defeating the last enemy minion " +
            "pushes the lane in the middle of the turn (p.8): every minion of the old Battle Zone is removed, " +
            "Blue's too. After the attack (p.17) the middle Zone holds no enemy, so she may Fast Travel to an " +
            "adjacent Zone without enemies (p.11), far from both.",
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
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("silverarrow", Team.BLUE, Hex(-1, -3)) {
            level = 2
            resolved(1, "silverarrow-red-2a")
            resolved(2, "silverarrow-blue-1")
            hand("silverarrow-gold", "silverarrow-silver", "silverarrow-green-1")
            items("silverarrow-red-2b")
        }
        hero("arien", Team.BLUE, Hex(4, -6)) {
            level = 3
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            discard("arien-red-2b", "arien-blue-2a")
            hand("arien-green-1")
            items("arien-red-2a", "arien-blue-2b")
        }
        hero("xargatha", Team.RED, Hex(-3, -2)) {
            level = 2
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            discard("xargatha-green-1", "xargatha-blue-2a")
            hand("xargatha-red-1")
            items("xargatha-blue-2b")
        }
        hero("brogan", Team.RED, Hex(-4, 0)) {
            level = 3
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            discard("brogan-green-2b")
            hand("brogan-red-1", "brogan-blue-2a")
            items("brogan-green-2a", "brogan-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -2))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 0)
    }

    // Every Fast Travel space offered wins (Blue's pre-Throne Zone and the side Zones); (7,-7) is in Blue's.
    override val solution = listOf(
        card("silverarrow-gold"),
        action(ActionType.PRIMARY),
        unitAt(1, -4),
        space(7, -7),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry("Disorient: at 3 it comes after Brogan", listOf(card("silverarrow-green-1"))),
        WrongTry(
            "Trailblazer: Fast Travel \"if able\", and Red's minion closes the Zone",
            listOf(card("silverarrow-silver")),
        ),
        WrongTry(
            "Shoot Xargatha: she falls, but the minion still closes the Zone and Brogan arrives",
            listOf(card("silverarrow-gold"), action(ActionType.PRIMARY), hero("xargatha")),
        ),
    )
}
