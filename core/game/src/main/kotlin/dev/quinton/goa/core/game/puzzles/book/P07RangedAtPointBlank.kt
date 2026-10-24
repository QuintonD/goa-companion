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

/**
 * CONCEPTS.md C07 "Ranged at Point Blank" (changed: Tigerclaw keeps Backstab and Sidestep, with the
 * Combat Reflexes and Parry items, so the first card in hand is the wrong answer to the first shot).
 */
object P07RangedAtPointBlank : Puzzle() {
    override val concept = "C07"

    override val info = PuzzleInfo(
        id = "p07",
        number = 7,
        title = "Ranged at Point Blank",
        difficulty = 1,
        theme = "Block: which attack is ranged",
        heroes = listOf("tigerclaw", "wasp", "sabina", "brogan"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 2, turn 3. Sabina has played Point Blank Shot (12) and acts next, then Brogan with Mad " +
            "Dash (7); both will attack Tigerclaw if they can. Tigerclaw holds Backstab (Defense 5) and " +
            "Sidestep; his items give him +1 Defense and +1 Initiative. Sabina holds Back to Back and Troop " +
            "Movement, Brogan Bulwark and Shield.",
        nudges = listOf(
            "Range 1 is still range.",
            "Each of your two cards answers exactly one of the two attacks.",
            "Defend Sabina's shot with Sidestep and keep Backstab for Brogan.",
        ),
        explanation = "An attack is ranged when its card says so, whatever its range: Point Blank Shot has " +
            "Range 1, so it only reaches adjacent targets, but it is still a ranged attack (p.9; common FAQ " +
            "\"range 1\"). Sidestep blocks ranged attacks, so it stops Sabina's shot; Block cards succeed only " +
            "when their condition is met, and Brogan's Mad Dash is not ranged (p.14). Each attack needs its " +
            "own card from your hand (p.14): spend Backstab on the weak shot and Sidestep is useless against " +
            "Brogan. Kept for him, Backstab's Defense 5 plus 1 from the item makes 6, and a tie defends (p.14).",
        rules = listOf(
            "p.9 Ranged and non-ranged",
            "p.13 Attacking a hero",
            "p.14 Defense, Block",
            "Common FAQ: a range 1 attack",
        ),
    )

    override val playerSeats = setOf("tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        hero("tigerclaw", Team.RED, Hex(1, -5)) {
            level = 3
            played("tigerclaw-green-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            hand("tigerclaw-red-2b", "tigerclaw-blue-2a")
            items("tigerclaw-red-2a", "tigerclaw-blue-2b")
        }
        hero("wasp", Team.RED, Hex(-1, 0)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-green-1", "wasp-blue-1")
        }
        hero("sabina", Team.BLUE, Hex(0, -5)) {
            played("sabina-gold")
            resolved(1, "sabina-red-1")
            resolved(2, "sabina-blue-1")
            hand("sabina-silver", "sabina-green-1")
        }
        hero("brogan", Team.BLUE, Hex(4, -6)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            hand("brogan-silver", "brogan-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "sabina")
    }

    override val solution = listOf(
        card("tigerclaw-blue-2a"),
        decline(),
        card("tigerclaw-red-2b"),
        action(ActionType.HOLD),
    )

    override val nudgedMoves = solution.take(1)

    override val wrongTries = listOf(
        WrongTry(
            "Backstab against the point-blank shot: Sidestep cannot block Brogan's charge",
            listOf(card("tigerclaw-red-2b")),
        ),
    )
}
