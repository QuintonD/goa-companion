package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.TaliHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.option
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
        difficulty = 3,
        theme = "Initiative changed by tokens",
        heroes = listOf("tali", "brogan", "tigerclaw", "dodger"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 4, turn 4. Tali (Snowstorm, 10) acts next. Tigerclaw has played Hit and Run (9) and has " +
            "no cards left in hand; Brogan has played Mad Dash (7). The three Ice tokens Tali placed with Pack Ice " +
            "on turn 1 are still on the board. Dodger has already acted.",
        nudges = listOf(
            "Tigerclaw is faster than Brogan, for now.",
            "Each Ice token an enemy hero stands next to costs him 1 Initiative.",
            "Snowstorm to the north-east, into the ice.",
        ),
        explanation = "A token keeps its properties as long as it is on the board (p.19 Tokens), and Pack Ice " +
            "gives an enemy hero -1 Initiative for every Ice token he is next to (card text). Who acts " +
            "next is checked again after each card resolves (p.7), so moving Tigerclaw changes the order at once. " +
            "Snowstorm moves every enemy unit in radius 1 space in the chosen direction: north-east puts " +
            "Tigerclaw next to all three tokens, 9 - 3 = 6, below Brogan's 7. Brogan acts next: Mad Dash runs two " +
            "spaces south-east to the space Tigerclaw left, right beside him, and with no cards he cannot defend " +
            "(p.13). East or north-west touch only one token (8, still ahead of Brogan) and he runs; the other " +
            "directions leave him next to none. The order is not fixed when the cards are revealed: whatever " +
            "changes an Initiative mid-turn reorders everyone still to act.",
        rules = listOf(
            "p.7 Check the Initiative, Resolve Your Card",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
            "p.19 Tokens",
        ),
    )

    override val playerSeats = setOf("tali", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("tali", Team.BLUE, Hex(0, -4)) {
            level = 5
            played("tali-blue-2a")
            resolved(1, "tali-green-3a")
            resolved(2, "tali-gold")
            resolved(3, "tali-red-2a")
            hand("tali-silver")
            items("tali-green-2b", "tali-blue-2b", "tali-green-3b", "tali-red-2b")
        }
        hero("brogan", Team.BLUE, Hex(2, -7)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-blue-1")
            hand("brogan-green-1")
        }
        hero("tigerclaw", Team.RED, Hex(2, -5)) {
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            discard("tigerclaw-blue-1")
            hand()
        }
        hero("dodger", Team.RED, Hex(4, -2)) {
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            resolved(4, "dodger-green-1")
            hand("dodger-blue-1")
        }
        token(TaliHero.ICE, Hex(4, -6), owner = "tali")
        token(TaliHero.ICE, Hex(4, -7), owner = "tali")
        token(TaliHero.ICE, Hex(3, -7), owner = "tali")
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        phase = PositionPhase.Resolving(next = "tali")
    }

    // Directions are "Choose one" bullets: 0 east, 1 north-east, 2 north-west, ... Mad Dash's one run is not asked.
    override val solution = listOf(action(ActionType.PRIMARY), option(1), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Snowstorm east: one Ice token, Tigerclaw at 8 still acts before Brogan",
            listOf(action(ActionType.PRIMARY), option(0)),
        ),
        WrongTry(
            "Snowstorm north-west: one Ice token, Tigerclaw at 8 still acts before Brogan",
            listOf(action(ActionType.PRIMARY), option(2)),
        ),
    )
}
