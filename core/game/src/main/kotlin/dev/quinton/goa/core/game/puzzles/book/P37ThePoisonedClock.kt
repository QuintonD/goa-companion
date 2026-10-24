package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.TigerclawHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C37 "The Poisoned Clock". */
object P37ThePoisonedClock : Puzzle() {
    override val concept = "C37"

    override val info = PuzzleInfo(
        id = "p37",
        number = 37,
        title = "The Poisoned Clock",
        difficulty = 2,
        theme = "Markers change initiative",
        heroes = listOf("wasp", "tigerclaw", "arien", "brogan"),
        goal = PuzzleGoal.DefeatHero("arien", Deadline.END_OF_TURN, "Defeat Arien this turn."),
        intro = "Round 3, turn 4: play your card. Wasp holds Static Barrier (13) and Shock (8). Arien carries " +
            "Tigerclaw's Poison marker and has only Dangerous Current (8) left, so he must play it. Blue shows " +
            "on the Tie Breaker coin. Tigerclaw and Brogan have no cards.",
        nudges = listOf(
            "Look at Arien's markers before you count initiative.",
            "Is it really 8 against 8?",
            "Play Shock.",
        ),
        explanation = "Arien must play his last card (p.7): Dangerous Current, printed 8. The Poison marker gives " +
            "him -1 Initiative, -1 Attack and -1 Defense (card text, p.18 Markers), so it resolves at 7. There " +
            "is no tie and the coin never comes into it (p.7): Wasp's Shock at 8 goes first, and Arien, with no " +
            "card left in hand, cannot defend (p.14). Static Barrier at 13 also acts first, but it is no attack, " +
            "and Arien walks away from the turn untouched. Read the 8 as a tie with Blue on the coin and you " +
            "would fear his attack first; the marker settles it.",
        rules = listOf("p.7 Play one card, Check the Initiative, Tied Initiative", "p.14 Defense", "p.18 Markers"),
    )

    override val playerSeats = setOf("wasp")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("wasp", Team.RED, Hex(2, -5)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-green-1")
            resolved(3, "wasp-blue-1")
            hand("wasp-silver", "wasp-red-1")
        }
        hero("tigerclaw", Team.RED, Hex(-1, -1)) {
            level = 2
            resolved(1, "tigerclaw-red-1")
            resolved(2, "tigerclaw-green-2b")
            resolved(3, "tigerclaw-blue-1")
            discard("tigerclaw-gold", "tigerclaw-silver")
            hand()
            items("tigerclaw-green-2a")
        }
        hero("arien", Team.BLUE, Hex(1, -5)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            discard("arien-blue-1")
            hand("arien-red-1")
            marker(TigerclawHero.POISON, from = "tigerclaw")
        }
        hero("brogan", Team.BLUE, Hex(4, -2)) {
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-blue-1")
            discard("brogan-silver", "brogan-green-1")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 0)
    }

    override val solution = listOf(card("wasp-red-1"), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(1)

    override val wrongTries = listOf(
        WrongTry("Static Barrier, to act first for sure: no attack", listOf(card("wasp-silver"))),
        WrongTry(
            "Shock, then a Movement action instead of the attack",
            listOf(card("wasp-red-1"), action(ActionType.MOVEMENT)),
        ),
    )
}
