package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C16 "Get Over Here!" (changed: Wasp stands on a clear line too, so pulling her is a
 * real, tempting choice; a Red melee minion guards the space she would land on).
 */
object P16GetOverHere : Puzzle() {
    override val concept = "C16"

    override val info = PuzzleInfo(
        id = "p16",
        number = 16,
        title = "Get Over Here!",
        difficulty = 1,
        theme = "Moving an enemy hero, minion defense modifiers",
        heroes = listOf("bain", "xargatha", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. Bain has played Get over here! and acts next; Xargatha has played " +
            "Threatening Slash. Wasp holds Lift Up (Defense 5); Dodger has no cards left in hand. Red has " +
            "one Life counter left.",
        nudges = listOf(
            "Xargatha can only hit what is next to her, so Bain has to bring someone to her.",
            "Before you pull Wasp, add up her defense where she would land: who stands next to that space?",
            "Pull Dodger down the line to Bain's north-west.",
        ),
        explanation = "Get over here! pulls a unit along a straight line with nothing in between until it " +
            "is next to Bain (card text; p.11, p.19 Straight line). Both Red heroes stand on such a line, and " +
            "either one lands next to Xargatha too. Wasp looks like the bigger prize because Threatening " +
            "Slash gains +1 Attack for the Red minion beside Xargatha (6), but she would land next to another " +
            "Red melee minion, which gives her +1 Defense (p.14): Lift Up's 5 + 1 = 6 holds against 6. Dodger " +
            "has no card to defend with (p.13): Bain at 13 pulls her, Xargatha at 7 strikes before Red's 3s " +
            "(p.7), and Red spends its last Life counter.",
        rules = listOf(
            "p.7 Initiative",
            "p.11 Movement in the card text",
            "p.13 Attacking a hero",
            "p.14 Minion defense modifiers",
            "p.19 Straight line",
        ),
    )

    override val playerSeats = setOf("bain", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        hero("bain", Team.BLUE, Hex(0, -4)) {
            played("bain-silver")
            resolved(1, "bain-gold")
            resolved(2, "bain-red-1")
            resolved(3, "bain-blue-1")
            hand("bain-green-1")
        }
        hero("xargatha", Team.BLUE, Hex(-1, -4)) {
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-green-1")
        }
        hero("wasp", Team.RED, Hex(-2, -2)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
        }
        hero("dodger", Team.RED, Hex(0, -7)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            discard("dodger-blue-1")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
        minion(Team.RED, MinionType.MELEE, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "bain")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("dodger"),
        action(ActionType.PRIMARY),
        hero("dodger"),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Pull Wasp: 5 + 1 for her guard holds against Threatening Slash's 6",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Pull Dodger, then Xargatha moves instead of striking",
            listOf(action(ActionType.PRIMARY), hero("dodger"), action(ActionType.MOVEMENT)),
        ),
    )
}
