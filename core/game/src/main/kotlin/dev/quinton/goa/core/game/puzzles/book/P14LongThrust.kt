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

/** CONCEPTS.md C14 "Long Thrust" (Brogan stands in for Tigerclaw as the hero with no cards). */
object P14LongThrust : Puzzle() {
    override val concept = "C14"

    override val info = PuzzleInfo(
        id = "p14",
        number = 14,
        title = "Long Thrust",
        difficulty = 1,
        theme = "Range set by card text",
        heroes = listOf("xargatha", "wasp", "arien", "brogan"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Xargatha has played Long Thrust and acts next. Arien holds Aspiring " +
            "Duelist; Brogan has no cards left in hand. Blue has one Life counter left.",
        nudges = listOf(
            "Your range isn't the number printed on the card.",
            "Count the enemy units touching Xargatha.",
            "Thrust at Brogan: three spaces is in range.",
        ),
        explanation = "The Range value on a card can be changed by its text (the + sign next to the icon, " +
            "p.9, p.13): Long Thrust gains 1 Range for each enemy unit adjacent to Xargatha, minions and " +
            "heroes alike (p.19 Unit). The Blue minion and Arien make it Range 3, and Brogan stands exactly " +
            "three spaces away with no card left to defend with (p.13). Blue spends its last Life counter " +
            "(his lowest card tier is I) and Red wins at once. Arien, the obvious target, defends with " +
            "Aspiring Duelist (5 against 3).",
        rules = listOf("p.9 Range", "p.13 Attacking a hero, When a hero is defeated", "p.19 Unit"),
    )

    override val playerSeats = setOf("xargatha")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 1)
        hero("xargatha", Team.RED, Hex(1, -2)) {
            level = 2
            played("xargatha-red-2b")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-green-1")
            items("xargatha-red-2a")
        }
        hero("wasp", Team.RED, Hex(3, -6)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-blue-1")
            resolved(4, "wasp-red-1")
            hand("wasp-green-1")
        }
        hero("arien", Team.BLUE, Hex(1, -1)) {
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            hand("arien-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(1, -5)) {
            played("brogan-green-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-blue-1")
            discard("brogan-silver")
            hand()
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "xargatha")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("brogan"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Thrust at Arien: Aspiring Duelist defends 5 against 3",
            listOf(action(ActionType.PRIMARY), hero("arien")),
        ),
        WrongTry("Move towards Brogan: no attack follows", listOf(action(ActionType.MOVEMENT))),
    )
}
