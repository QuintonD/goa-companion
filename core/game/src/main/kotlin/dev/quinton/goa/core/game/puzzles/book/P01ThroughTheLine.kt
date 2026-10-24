package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C01 "Through the Line". */
object P01ThroughTheLine : Puzzle() {
    override val concept = "C01"

    override val info = PuzzleInfo(
        id = "p01",
        number = 1,
        title = "Through the Line",
        difficulty = 1,
        theme = "Targets and straight lines",
        heroes = listOf("arien", "wasp", "xargatha", "brogan"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Arien has played Dangerous Current and acts next. Wasp has no cards " +
            "left in hand; Xargatha holds Siren's Call. Red has one Life counter left.",
        nudges = listOf(
            "Who on Red has no card left to give up?",
            "Dangerous Current reaches further than the unit you hit: look at the spaces behind it.",
            "Attack the melee minion east of Arien; Wasp stands two spaces behind it.",
        ),
        explanation = "Dangerous Current's \"Before the attack\" text resolves before the target is hit " +
            "(p.14 step 1, p.17) and reaches the 2 spaces in a straight line behind the target, whatever " +
            "stands between: there is no line of sight (p.3, p.9). Wasp has no card left, so \"discards a " +
            "card, or is defeated\" defeats her. A defeated hero's team spends Life counters equal to her " +
            "lowest card tier (p.13); Red had one left, so the game ends at once. Hitting the minion to the " +
            "north-west instead finds Xargatha behind it, and she still has a card to discard.",
        rules = listOf(
            "p.3 Line of sight",
            "p.9 Range and Radius",
            "p.13 When a hero is defeated",
            "p.14 Attack and defense, detailed order",
            "p.17 Before the attack",
        ),
    )

    override val playerSeats = setOf("arien")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 3)
        hero("wasp", Team.RED, Hex(3, -5)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            resolved(3, "wasp-blue-1")
            discard("wasp-silver")
            hand()
        }
        hero("xargatha", Team.RED, Hex(0, -7)) {
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-silver")
        }
        hero("arien", Team.BLUE, Hex(0, -5)) {
            played("arien-red-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-blue-1")
            hand("arien-green-1")
        }
        hero("brogan", Team.BLUE, Hex(4, -2)) {
            resolved(1, "brogan-silver")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-blue-1")
            resolved(4, "brogan-gold")
            hand("brogan-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -5))
        minion(Team.RED, MinionType.MELEE, Hex(2, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "arien")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(1, -5), hero("wasp"))

    override val wrongTries = listOf(
        WrongTry(
            "Dangerous Current on the minion north-west: Xargatha discards and survives",
            listOf(action(ActionType.PRIMARY), unitAt(0, -6)),
        ),
        WrongTry("A Movement action towards Wasp: no attack follows", listOf(action(ActionType.MOVEMENT))),
    )
}
