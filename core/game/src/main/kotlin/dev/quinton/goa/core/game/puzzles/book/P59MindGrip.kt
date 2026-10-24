package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C59 "Mind Grip". */
object P59MindGrip : Puzzle() {
    override val concept = "C59"

    override val info = PuzzleInfo(
        id = "p59",
        number = 59,
        title = "Mind Grip",
        difficulty = 1,
        theme = "Using an enemy's previous card",
        heroes = listOf("nebkher", "sabina", "arien", "brogan", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 3, three heroes a side. NebKher has played Mind Grip (9) and acts next; Arien and " +
            "Sabina have already acted, the Red heroes act after him. Red has one Life counter left. Every " +
            "hero's turn-2 card is on its dashboard.",
        nudges = listOf(
            "Any Red hero you defeat ends the game. Which of them can't defend at all?",
            "A copied card keeps its own range and its own targeting rule; check them from where NebKher stands.",
            "Mind Grip Wasp's Charged Boomerang and shoot Dodger.",
        ),
        explanation = "Mind Grip performs an action of the card in an enemy hero's previous Turn slot as " +
            "NebKher's own, with that card's values and rules (NebKher FAQ). Dodger has no cards, so any " +
            "attack that can target her wins: Red spends its last Life counter (p.13). Brogan's Mad Dash is " +
            "the obvious copy, but the Red minion beside NebKher blocks the run toward Dodger, and the only run " +
            "that ends next to an enemy hero reaches Brogan, who defends with 6 + 1 - 1 = 6 against 6 (p.14). " +
            "Dodger's own Littlefinger of Death reaches a " +
            "hero with cards in the discard only within range 2, and she is three spaces away. Wasp's Charged " +
            "Boomerang targets a unit in range 3 and not in a straight line (card text; p.19 Straight Line): " +
            "Dodger is exactly that, and the ranged Attack 3 meets no defense. Defeating the Red minion next to " +
            "NebKher does not push the lane either: Red keeps its other minions. Read a borrowed card's " +
            "restrictions from the borrower's space.",
        rules = listOf(
            "p.9 Range",
            "p.13 Attacking a Hero, When a Hero is Defeated",
            "p.14 Defense Total, Minion Defense Modifiers",
            "p.19 Straight Line",
            "NebKher FAQ: Mind Grip",
        ),
    )

    override val playerSeats = setOf("nebkher")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 3)
        hero("nebkher", Team.BLUE, Hex(1, -2)) {
            level = 3
            played("nebkher-gold")
            resolved(1, "nebkher-red-2a")
            resolved(2, "nebkher-green-1")
            hand("nebkher-silver", "nebkher-blue-2a")
            items("nebkher-red-2b", "nebkher-blue-2b")
        }
        hero("sabina", Team.BLUE, Hex(-1, 0)) {
            level = 2
            resolved(1, "sabina-red-1")
            resolved(2, "sabina-green-1")
            resolved(3, "sabina-gold")
            hand("sabina-silver", "sabina-blue-2a")
            items("sabina-blue-2b")
        }
        hero("arien", Team.BLUE, Hex(-2, -3)) {
            level = 2
            resolved(1, "arien-gold")
            resolved(2, "arien-red-2a")
            resolved(3, "arien-silver")
            hand("arien-green-1", "arien-blue-1")
            items("arien-red-2b")
        }
        hero("brogan", Team.RED, Hex(4, -2)) {
            level = 3
            played("brogan-green-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-1")
            hand("brogan-silver", "brogan-blue-2a")
            items("brogan-green-2b", "brogan-blue-2b")
        }
        hero("wasp", Team.RED, Hex(3, -6)) {
            level = 3
            played("wasp-green-2a")
            resolved(1, "wasp-silver")
            resolved(2, "wasp-red-2b")
            hand("wasp-gold", "wasp-blue-1")
            items("wasp-red-2a", "wasp-green-2b")
        }
        hero("dodger", Team.RED, Hex(4, -4)) {
            level = 2
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            discard("dodger-silver", "dodger-blue-2a")
            hand()
            items("dodger-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "nebkher")
    }

    // Charged Boomerang's one target (Dodger, three spaces away off every straight line) is not asked.
    override val solution =
        listOf(action(ActionType.PRIMARY), option(0), hero("wasp"), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Copy Brogan's Mad Dash: the minion blocks the run to Dodger; Brogan holds 6 against 6",
            listOf(action(ActionType.PRIMARY), option(0), hero("brogan")),
        ),
        WrongTry(
            "Copy Dodger's Littlefinger of Death: she is three spaces away, out of its range 2",
            listOf(action(ActionType.PRIMARY), option(0), hero("dodger")),
        ),
        WrongTry(
            "Defeat the adjacent minion: Red keeps others, so no push",
            listOf(action(ActionType.PRIMARY), option(1)),
        ),
    )
}
