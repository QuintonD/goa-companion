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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C21 "Charge onto the Spawn Point", turned to the other side of the map (every space
 * rotated half a turn, (q, r) to (3 - q, -7 - r), and the teams swapped) so the winning landing space
 * is not the first one offered.
 */
object P21ChargeOntoTheSpawnPoint : Puzzle() {
    override val concept = "C21"

    override val info = PuzzleInfo(
        id = "p21",
        number = 21,
        title = "Charge onto the Spawn Point",
        difficulty = 2,
        theme = "Mandatory moves, defense from the board",
        heroes = listOf("brogan", "arien", "dodger", "xargatha"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 2, turn 4. Brogan has played Mad Dash and acts next. Dodger holds Shield of Decay " +
            "(Defense 3, +2 if there are 2 or more empty spawn points within 2 spaces of her in the Battle " +
            "Zone). Arien and Xargatha have already acted.",
        nudges = listOf(
            "Count the empty spawn points around Dodger.",
            "Brogan's own body can fill one of them.",
            "Mad Dash south-west, onto the spawn point at the end of the run.",
        ),
        explanation = "Mad Dash's move is mandatory and exact: two spaces in a straight line to a space next " +
            "to an enemy unit (p.11 Movement in the card text; Brogan FAQ), so only a few landing spaces " +
            "exist, and two of them are next to Dodger. Shield of Decay is a primary Defense, so its text " +
            "applies when she defends (p.14), and an empty spawn point is one with nothing on it (p.17 " +
            "Empty): with two empty ones nearby she defends 3 + 2 + 1 for her melee minion = 6, equal to Mad " +
            "Dash's 6, and equal totals defend (p.14). Landing on one of those spawn points switches the +2 " +
            "off: 3 + 1 = 4 against 6. The run to the west stops beside her too, but leaves both spawn points " +
            "empty; the run to the east only reaches the Blue ranged minion.",
        rules = listOf(
            "p.11 Movement in the card text",
            "p.14 Defense total, Minion defense modifiers",
            "p.17 Empty",
            "Brogan FAQ: Mad Dash",
        ),
    )

    override val playerSeats = setOf("brogan")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("brogan", Team.RED, Hex(2, -5)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            resolved(3, "brogan-silver")
            hand("brogan-green-1")
        }
        hero("arien", Team.RED, Hex(4, -8)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        hero("dodger", Team.BLUE, Hex(0, -4)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            hand("dodger-blue-1")
        }
        hero("xargatha", Team.BLUE, Hex(-2, -3)) {
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            resolved(4, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, -3))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(0, -3), hero("dodger"))

    override val wrongTries = listOf(
        WrongTry(
            "Dash west next to Dodger: two spawn points stay empty and she defends 6 against 6",
            listOf(action(ActionType.PRIMARY), space(0, -5)),
        ),
        WrongTry(
            "Dash east: only the Blue ranged minion is in reach",
            listOf(action(ActionType.PRIMARY), space(4, -5)),
        ),
        WrongTry("Walk next to her with a Movement action: no attack", listOf(action(ActionType.MOVEMENT))),
    )
}
