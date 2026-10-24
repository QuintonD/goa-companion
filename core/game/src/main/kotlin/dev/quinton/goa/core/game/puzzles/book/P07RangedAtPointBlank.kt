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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C07 "Ranged at Point Blank" (changed: Point Blank Shot's push decides where Tigerclaw
 * meets Brogan, so Sidestep's step matters as much as the card; see the concept's "Encoded as").
 */
object P07RangedAtPointBlank : Puzzle() {
    override val concept = "C07"

    override val info = PuzzleInfo(
        id = "p07",
        number = 7,
        title = "Ranged at Point Blank",
        difficulty = 2,
        theme = "Block: which attack is ranged",
        heroes = listOf("tigerclaw", "wasp", "sabina", "brogan"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 3, turn 3. Wasp has already acted. Sabina has played Point Blank Shot (12) and acts " +
            "next, then Brogan with Mad Dash (7); Tigerclaw's Light-Fingered comes last. Tigerclaw holds " +
            "Backstab (Defense 5) and Sidestep; his items give him +1 Defense and +1 Initiative. Sabina holds " +
            "Back to Back and Troop Movement, Brogan Bulwark and Bolster.",
        nudges = listOf(
            "Range 1 is still range, and Point Blank Shot has something to say after the attack.",
            "Wherever Sabina's shot leaves you, count Brogan's charges and your Backstab there: the Blue " +
                "minions beside you count.",
            "Block Sabina's shot with Sidestep and step north-west, still beside her.",
        ),
        explanation = "An attack is ranged when its card says so, whatever its range: Point Blank Shot has " +
            "Range 1 but is a ranged attack (p.9; common FAQ \"range 1\"), so Sidestep blocks it, and against " +
            "Brogan's Mad Dash, not ranged, only Backstab can defend (p.14 Block): spend Backstab on the shot " +
            "and you have nothing left for him. Backstab is not enough everywhere, though: 5, +1 item, -1 for " +
            "each Blue melee minion beside you (p.14), against Mad Dash's 6. After the attack, Point Blank " +
            "Shot pushes Tigerclaw 1 space if he is still beside Sabina (card text, p.18). Stay put and the " +
            "push lands him beside a Blue minion, where Brogan's charge reaches him: 5 against 6. Every other " +
            "step ends beside a Blue minion too. Step north-west instead, still beside her: her own push " +
            "carries him on to a space next to Brogan, and Mad Dash, which must first move 2 spaces in a " +
            "straight line to a space next to its target (Brogan FAQ), has no such line. The safest square " +
            "is the one beside the enemy who can't reach it.",
        rules = listOf(
            "p.9 Ranged and non-ranged",
            "p.13 Attacking a hero",
            "p.14 Defense, Block, Minion defense modifiers",
            "p.18 Push",
            "Common FAQ: a range 1 attack",
            "Brogan FAQ: Mad Dash",
        ),
    )

    override val playerSeats = setOf("tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        hero("tigerclaw", Team.RED, Hex(0, -2)) {
            level = 3
            played("tigerclaw-green-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            hand("tigerclaw-red-2b", "tigerclaw-blue-2a")
            items("tigerclaw-red-2a", "tigerclaw-blue-2b")
        }
        hero("wasp", Team.RED, Hex(5, -7)) {
            level = 2
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            resolved(3, "wasp-silver")
            items("wasp-blue-2b")
            hand("wasp-green-1", "wasp-blue-2a")
        }
        hero("sabina", Team.BLUE, Hex(-1, -2)) {
            level = 2
            played("sabina-gold")
            resolved(1, "sabina-red-2a")
            resolved(2, "sabina-blue-1")
            items("sabina-red-2b")
            hand("sabina-silver", "sabina-green-1")
        }
        hero("brogan", Team.BLUE, Hex(0, -4)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            items("brogan-green-2b")
            hand("brogan-silver", "brogan-green-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -5))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(-1, 0))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "sabina")
    }

    override val solution = listOf(
        card("tigerclaw-blue-2a"),
        space(0, -3),
        action(ActionType.HOLD),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Backstab against the point-blank shot: Sidestep cannot block Brogan's charge",
            listOf(card("tigerclaw-red-2b")),
        ),
        WrongTry(
            "Sidestep and stay: the push puts Tigerclaw beside a Blue minion, in Brogan's reach",
            listOf(card("tigerclaw-blue-2a"), decline()),
        ),
        WrongTry(
            "Sidestep east, away from Sabina: beside a Blue minion, in Brogan's reach",
            listOf(card("tigerclaw-blue-2a"), space(1, -2)),
        ),
    )
}
