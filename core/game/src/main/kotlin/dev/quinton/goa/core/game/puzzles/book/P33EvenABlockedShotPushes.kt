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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C33 "Even a Blocked Shot Pushes". */
object P33EvenABlockedShotPushes : Puzzle() {
    override val concept = "C33"

    override val info = PuzzleInfo(
        id = "p33",
        number = 33,
        title = "Even a Blocked Shot Pushes",
        difficulty = 2,
        theme = "After the attack, Block",
        heroes = listOf("sabina", "brogan", "tigerclaw", "arien"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 4, turn 4. Sabina has played Point Blank Shot (12) and acts next; Brogan has played " +
            "Mad Dash (7). Tigerclaw has acted and holds only Dodge (Block a ranged attack). Arien has acted " +
            "and keeps Dangerous Current (Defense 6).",
        nudges = listOf(
            "Brogan can't reach Tigerclaw where he stands: Mad Dash needs exactly two spaces in a straight line.",
            "Dodge will block Sabina's shot. But a blocked attack still finishes its After-the-attack text.",
            "Shoot Tigerclaw with Point Blank Shot anyway.",
        ),
        explanation = "Point Blank Shot is ranged, so Tigerclaw blocks it with Dodge. A block is a successful " +
            "defense, and \"After the attack\" text applies after the target defends (p.14 order, step 7; " +
            "p.17): he is still adjacent, so he is pushed one space directly away, to (1,-5) (p.18 Push), " +
            "with no card left. Mad Dash's two-space run (p.11 Movement in the card text) can now end beside " +
            "him at (0,-5), and its Attack 6 meets no defense (p.14: you cannot defend without a card). " +
            "Shooting the minion leaves him out of Brogan's reach, and the only other run, west, finds Arien, " +
            "who defends with Dangerous Current.",
        rules = listOf(
            "p.11 Movement in the card text",
            "p.14 Block, Attack/Defense Detailed Order",
            "p.17 After the Attack",
            "p.18 Push",
        ),
    )

    override val playerSeats = setOf("sabina", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("sabina", Team.RED, Hex(3, -5)) {
            played("sabina-gold")
            resolved(1, "sabina-red-1")
            resolved(2, "sabina-green-1")
            resolved(3, "sabina-blue-1")
            hand("sabina-silver")
        }
        hero("brogan", Team.RED, Hex(0, -7)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-green-1")
            resolved(3, "brogan-blue-1")
            hand("brogan-silver")
        }
        hero("arien", Team.BLUE, Hex(-3, -7)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            resolved(4, "arien-blue-1")
            hand("arien-red-1")
        }
        hero("tigerclaw", Team.BLUE, Hex(2, -5)) {
            resolved(1, "tigerclaw-red-1")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-silver")
            resolved(4, "tigerclaw-gold")
            hand("tigerclaw-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "sabina")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("tigerclaw"),
        action(ActionType.PRIMARY),
        space(0, -5),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Shoot the Blue minion instead: Tigerclaw keeps Dodge and stays out of Brogan's reach",
            listOf(action(ActionType.PRIMARY), unitAt(4, -6)),
        ),
        WrongTry(
            "Push Tigerclaw, then dash west: only Arien is there, and he defends",
            listOf(action(ActionType.PRIMARY), hero("tigerclaw"), action(ActionType.PRIMARY), space(-2, -7)),
        ),
        WrongTry("Sabina moves instead of shooting", listOf(action(ActionType.MOVEMENT))),
    )
}
