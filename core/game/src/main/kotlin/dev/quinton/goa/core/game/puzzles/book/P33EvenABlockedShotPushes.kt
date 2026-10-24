package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
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

/** CONCEPTS.md C33 "Even a Blocked Shot Pushes" (changed: win the game, so Arien is a second target). */
object P33EvenABlockedShotPushes : Puzzle() {
    override val concept = "C33"

    override val info = PuzzleInfo(
        id = "p33",
        number = 33,
        title = "Even a Blocked Shot Pushes",
        difficulty = 1,
        theme = "After the attack, Block",
        heroes = listOf("sabina", "brogan", "arien", "tigerclaw"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 3. Blue has one Life counter left. Sabina has played Point Blank Shot (12) and has " +
            "an Attack item; she acts next, then Brogan with Bullrush (7), who also has an Attack item. Tigerclaw " +
            "has acted and holds only Dodge (Block a ranged attack). Arien has played Magical Current (4); he " +
            "holds Dangerous Current (Defense 6) and Aspiring Duelist (Defense 5) and has a Defense item.",
        nudges = listOf(
            "Where they stand now, neither Blue hero is at the end of a Bullrush line.",
            "Point Blank Shot pushes after the attack, whether the target defends or not. Where does each hero " +
                "end up, and what does he still hold?",
            "Shoot Tigerclaw with Point Blank Shot, even though Dodge will block it.",
        ),
        explanation = "Bullrush must move 2 or 3 spaces in a straight line to a space next to an enemy unit (card " +
            "text, p.11), and from where they stand neither Tigerclaw nor Arien is in reach. Point Blank Shot is " +
            "ranged, so Tigerclaw blocks it with Dodge. A block is a successful defense, and \"After the attack\" " +
            "text applies after the target defends (p.14 order, step 7; p.17), so he is still pushed one space " +
            "directly away from Sabina, to the west (p.18 Push), with no card left. Bullrush north-west, two " +
            "spaces or three, now ends beside him, and its Attack 7 meets no defense (p.14): Blue's last Life " +
            "counter goes (p.13). Shooting Arien pushes him within reach too, but he defends the shot with " +
            "Aspiring Duelist and keeps Dangerous Current: 6 + 1 (item) = 7 against 7 holds. The lesson: a Block " +
            "stops the damage, not the card; text after the attack still happens, so an attack you know will be " +
            "blocked can still be the move.",
        rules = listOf(
            "p.11 Movement in the card text",
            "p.13 When a hero is defeated",
            "p.14 Block, Attack/Defense Detailed Order",
            "p.17 After the Attack",
            "p.18 Push",
        ),
    )

    override val playerSeats = setOf("sabina", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 1)
        hero("sabina", Team.RED, Hex(3, -5)) {
            level = 3
            played("sabina-gold")
            resolved(1, "sabina-red-1")
            resolved(2, "sabina-green-2b")
            hand("sabina-silver", "sabina-blue-2a")
            items("sabina-green-2a", "sabina-blue-2b")
        }
        hero("brogan", Team.RED, Hex(0, -2)) {
            level = 3
            played("brogan-red-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            hand("brogan-silver", "brogan-green-2b")
            items("brogan-red-2b", "brogan-green-2a")
        }
        hero("arien", Team.BLUE, Hex(3, -4)) {
            level = 2
            played("arien-green-2a")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            hand("arien-red-1", "arien-blue-1")
            items("arien-green-2b")
        }
        hero("tigerclaw", Team.BLUE, Hex(2, -5)) {
            level = 3
            resolved(1, "tigerclaw-red-2a")
            resolved(2, "tigerclaw-green-2b")
            resolved(3, "tigerclaw-gold")
            discard("tigerclaw-silver")
            hand("tigerclaw-blue-1")
            items("tigerclaw-red-2b", "tigerclaw-green-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "sabina")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("tigerclaw"),
        action(ActionType.PRIMARY),
        space(0, -4),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Arien: the push puts him in reach, but he keeps Dangerous Current and holds 7 against 7",
            listOf(action(ActionType.PRIMARY), hero("arien")),
        ),
        WrongTry("Sabina moves instead of shooting", listOf(action(ActionType.MOVEMENT))),
        WrongTry(
            "Push Tigerclaw, then Brogan only moves",
            listOf(action(ActionType.PRIMARY), hero("tigerclaw"), action(ActionType.MOVEMENT)),
        ),
    )
}
