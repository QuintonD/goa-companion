package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.TaliHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C51 "Ice in the Veins". */
object P51IceInTheVeins : Puzzle() {
    override val concept = "C51"

    override val info = PuzzleInfo(
        id = "p51",
        number = 51,
        title = "Ice in the Veins",
        difficulty = 2,
        theme = "Initiative changed by tokens",
        heroes = listOf("tali", "brogan", "tigerclaw", "dodger"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 4, turn 4. Tali (Snowstorm, 10) acts next. Tigerclaw has played Hit and Run (9), Brogan " +
            "Mad Dash (8 with his Initiative item). The three Ice tokens are Tali's, placed with Pack Ice on " +
            "turn 1. Dodger has already acted. Tigerclaw's hand is empty.",
        nudges = listOf(
            "Brogan needs two things: to act before Tigerclaw, and a clear two-space run to him.",
            "Snowstorm moves every enemy unit near Tali, not only Tigerclaw. And who wins a tie of 8 against 8?",
            "Snowstorm to the north-east.",
        ),
        explanation = "Pack Ice's Ice tokens keep their property while they are on the board (p.19 Tokens): an " +
            "enemy hero has -1 Initiative for every one he is next to. Who acts next is checked again after each " +
            "card resolves (p.7). Snowstorm moves each enemy unit in radius 1 space in one direction (card text), " +
            "so it moves the Red melee minion too. North-east puts Tigerclaw next to one Ice token: 9 - 1 = 8, a " +
            "tie with Brogan's 8. A tie between teams goes to the team shown on the Tie Breaker coin (p.7), and it " +
            "shows Blue, so Brogan acts first. The same push carries the minion out of Brogan's way: Mad Dash runs " +
            "two spaces east (p.11) and ends beside Tigerclaw, who has no card to defend with (p.13). East is the " +
            "trap: two Ice tokens slow Tigerclaw to 7, but the minion only slides along and no straight run of two " +
            "ends next to him. South-east ties him at 8 too, with no run to him either, and the other directions " +
            "touch no token, so he acts first and runs. Count the slow-down, but also the coin and the path.",
        rules = listOf(
            "p.7 Check the Initiative, Tied Initiative",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
            "p.19 Tokens",
        ),
    )

    override val playerSeats = setOf("tali", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("tali", Team.BLUE, Hex(2, -5)) {
            level = 5
            played("tali-blue-2a")
            resolved(1, "tali-green-3a")
            resolved(2, "tali-gold")
            resolved(3, "tali-red-2a")
            hand("tali-silver")
            items("tali-green-2b", "tali-blue-2b", "tali-green-3b", "tali-red-2b")
        }
        hero("brogan", Team.BLUE, Hex(-2, -6)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-green-1")
            hand("brogan-blue-2b")
            items("brogan-blue-2a")
        }
        hero("tigerclaw", Team.RED, Hex(0, -5)) {
            level = 2
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            discard("tigerclaw-blue-2a")
            hand()
            items("tigerclaw-blue-2b")
        }
        hero("dodger", Team.RED, Hex(4, -2)) {
            level = 2
            resolved(1, "dodger-silver")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-green-2b")
            resolved(4, "dodger-gold")
            hand("dodger-blue-1")
            items("dodger-green-2a")
        }
        token(TaliHero.ICE, Hex(2, -6), owner = "tali")
        token(TaliHero.ICE, Hex(0, -7), owner = "tali")
        token(TaliHero.ICE, Hex(1, -4), owner = "tali")
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "tali")
    }

    // Directions are "Choose one" bullets: 0 east, 1 north-east, 2 north-west, ... Mad Dash's one run (east,
    // to the minion's old space) is not asked; it ends next to Tigerclaw and the pushed minion.
    override val solution =
        listOf(action(ActionType.PRIMARY), option(1), action(ActionType.PRIMARY), hero("tigerclaw"))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Snowstorm east: two Ice tokens, Tigerclaw at 7 acts after Brogan, but no run ends next to him",
            listOf(action(ActionType.PRIMARY), option(0)),
        ),
        WrongTry(
            "Snowstorm south-east: a tie at 8 that Brogan wins, but his run ends next to the minion, not Tigerclaw",
            listOf(action(ActionType.PRIMARY), option(5)),
        ),
        WrongTry(
            "Snowstorm north-east, then Mad Dash into the minion",
            listOf(action(ActionType.PRIMARY), option(1), action(ActionType.PRIMARY), unitAt(1, -7)),
        ),
    )
}
