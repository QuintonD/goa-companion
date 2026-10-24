package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
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

/** CONCEPTS.md C04 "Price on Her Head". */
object P04PriceOnHerHead : Puzzle() {
    override val concept = "C04"

    override val info = PuzzleInfo(
        id = "p04",
        number = 4,
        title = "Price on Her Head",
        difficulty = 1,
        theme = "Life counters and the Bounty marker",
        heroes = listOf("bain", "brogan", "xargatha", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. Bain has played Dead or Alive (11) and acts next; Brogan has played Mad " +
            "Dash (7). Dodger has no cards left in hand; Xargatha holds Stone Gaze. Both Red heroes act " +
            "after Brogan. Red has two Life counters left.",
        nudges = listOf(
            "Red has two Life counters, and a level 1 hero only pays one.",
            "Bain can make one hero cost more, but only one of them can Brogan actually reach.",
            "Attack the ranged minion with Bain and give Dodger the Bounty marker.",
        ),
        explanation = "Bain acts at 11, before Brogan's 7, so the marker is in place before Brogan attacks " +
            "(p.7). \"A hero with the Bounty marker spends 1 additional Life counter when defeated\" (card " +
            "text), on top of the counters for her lowest tier (p.13): Dodger, level 1 and with no card to " +
            "defend with, then costs Red both counters. The bounty comes after Bain's own attack, so he needs " +
            "an adjacent target first (p.10). Xargatha is the obvious one to mark, standing right next to " +
            "Bain, but Mad Dash must move exactly 2 spaces in a straight line to a space next to its target " +
            "(Brogan FAQ), and no such line ends beside her.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.10 Mandatory and optional steps",
            "p.13 When a hero is defeated",
            "Brogan FAQ: Mad Dash",
        ),
    )

    override val playerSeats = setOf("bain", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("bain", Team.BLUE, Hex(0, -4)) {
            played("bain-gold")
            resolved(1, "bain-red-1")
            resolved(2, "bain-blue-1")
            resolved(3, "bain-silver")
            hand("bain-green-1")
        }
        hero("brogan", Team.BLUE, Hex(1, -2)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            resolved(3, "brogan-silver")
            hand("brogan-green-1")
        }
        hero("xargatha", Team.RED, Hex(1, -5)) {
            played("xargatha-silver")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        hero("dodger", Team.RED, Hex(3, -3)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            discard("dodger-blue-1")
            hand()
        }
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "bain")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        unitAt(0, -3),
        hero("dodger"),
        action(ActionType.PRIMARY),
        space(3, -2),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Put the Bounty on Xargatha: Brogan has no straight 2-space line that ends next to her",
            listOf(action(ActionType.PRIMARY), unitAt(0, -3), hero("xargatha")),
        ),
        WrongTry(
            "No bounty: Dodger alone costs Red one counter",
            listOf(action(ActionType.PRIMARY), unitAt(0, -3), decline()),
        ),
    )
}
