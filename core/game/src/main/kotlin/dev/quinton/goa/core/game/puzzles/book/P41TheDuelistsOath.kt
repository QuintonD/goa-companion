package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C41 "The Duelist's Oath". */
object P41TheDuelistsOath : Puzzle() {
    override val concept = "C41"

    override val info = PuzzleInfo(
        id = "p41",
        number = 41,
        title = "The Duelist's Oath",
        difficulty = 3,
        theme = "Defend with the right card, conditional immunity",
        heroes = listOf("arien", "sabina", "wasp", "dodger"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 2, turn 3. Wasp (Magnetic Dagger, 12) and Dodger (Middlefinger of Death, 10) act " +
            "before Arien's Liquid Leap (5). Arien holds Rogue Wave (Defense 4) and Expert Duelist (Defense 6); " +
            "his discard is empty. Wasp keeps Static Barrier and Stop Projectiles, Dodger two cards. Sabina has " +
            "acted.",
        nudges = listOf(
            "Dodger's second attack can only find you if the first one changes something.",
            "Read the condition on Middlefinger's ranged bullet, and what Expert Duelist does after you defend.",
            "Defend Wasp's dagger with Expert Duelist.",
        ),
        explanation = "Rogue Wave looks thrifty: 4, -1 for the Red minion beside Arien, still holds Wasp's 3, and " +
            "Expert Duelist stays in hand for the big attack. But the discarded card goes to Arien's discard " +
            "(p.10), and Middlefinger's second bullet targets a hero in range with a card in the discard: Dodger's " +
            "7 (5, +2 from her Attack items, p.16) beats Expert Duelist's 6. Defend with Expert Duelist instead: " +
            "a primary Defense card's text applies when you defend with it (p.14), so it ignores the minion and " +
            "makes Arien immune to attack actions of every enemy hero except Wasp this turn (p.18 Immune). " +
            "Dodger is not adjacent, so she has no way to reach him.",
        rules = listOf("p.10 Discard a Card", "p.14 Defense", "p.16 Items", "p.18 Immune"),
    )

    override val playerSeats = setOf("arien")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        hero("arien", Team.BLUE, Hex(1, -5)) {
            level = 3
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            hand("arien-red-2b", "arien-blue-2a")
            items("arien-red-2a", "arien-blue-2b")
        }
        hero("sabina", Team.BLUE, Hex(-1, 0)) {
            resolved(1, "sabina-gold")
            resolved(2, "sabina-silver")
            resolved(3, "sabina-red-1")
            hand("sabina-green-1", "sabina-blue-1")
        }
        hero("wasp", Team.RED, Hex(2, -6)) {
            played("wasp-gold")
            resolved(1, "wasp-red-1")
            resolved(2, "wasp-blue-1")
            hand("wasp-silver", "wasp-green-1")
        }
        hero("dodger", Team.RED, Hex(3, -5)) {
            level = 5
            played("dodger-red-3a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            hand("dodger-green-2a", "dodger-blue-2a")
            items("dodger-red-2b", "dodger-red-3b", "dodger-green-2b", "dodger-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    override val solution = listOf(card("arien-blue-2a"), action(ActionType.HOLD))

    override val nudgedMoves = solution.take(1)

    override val wrongTries = listOf(
        WrongTry(
            "Rogue Wave against the dagger, keeping Expert Duelist: Middlefinger now finds Arien's discard",
            listOf(card("arien-red-2b")),
        ),
        WrongTry("No defense", listOf(decline())),
    )
}
