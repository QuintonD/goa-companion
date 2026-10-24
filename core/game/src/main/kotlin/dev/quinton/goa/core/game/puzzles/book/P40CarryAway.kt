package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.WidgetHero
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

/** CONCEPTS.md C40 "Carry Away". */
object P40CarryAway : Puzzle() {
    override val concept = "C40"

    override val info = PuzzleInfo(
        id = "p40",
        number = 40,
        title = "Carry Away",
        difficulty = 2,
        theme = "Minions outside the Battle Zone",
        heroes = listOf("widget", "swift", "dodger", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Red has one Life counter left and Blue two; one Wave counter remains. " +
            "Widget (Carry Away, 10) acts next, then Swift (Snipe, 8), then Dodger (Dark Ritual, 3), who has no " +
            "cards left in hand. Wasp has already acted. Red's only minion and Dodger both stand next to Pyro.",
        nudges = listOf(
            "Dragging Red's last minion out of the Battle Zone does not count as removing it.",
            "Snipe needs a target exactly four spaces away in a straight line. Where would Swift like Dodger?",
            "Carry Away on Dodger: Pyro and Dodger two spaces east each.",
        ),
        explanation = "\"A Lane Push can never be triggered by moving, pushing, or swapping a minion. It must be " +
            "either defeated, or removed\" (p.8): carried out of the Battle Zone, the last Red minion just walks " +
            "back (p.18 Minions Outside of the Battle Zone), and nowhere Carry Away can put it is in Snipe's " +
            "reach. Dodger is the real target: with no cards he cannot defend, so any attack that reaches him " +
            "defeats him (p.13), and Red's last Life counter goes with him. Carry Away moves Pyro and the enemy " +
            "next to it 2 or 3 spaces each in the same direction, ignoring obstacles (card text, p.18 Ignoring " +
            "Obstacles): two spaces east puts Dodger exactly four spaces from Swift in a straight line, Snipe's " +
            "only range (card text, p.19 Straight Line). Three spaces is one too far.",
        rules = listOf(
            "p.8 Triggering a Push",
            "p.13 Attacking a Hero",
            "p.18 Ignoring Obstacles, Minions Outside of the Battle Zone",
            "p.19 Straight Line",
        ),
    )

    override val playerSeats = setOf("widget", "swift")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 1)
        hero("widget", Team.BLUE, Hex(0, -5)) {
            level = 5
            played("widget-blue-3b")
            resolved(1, "widget-gold")
            resolved(2, "widget-silver")
            resolved(3, "widget-red-2a")
            hand("widget-green-2b")
            items("widget-red-2b", "widget-green-2a", "widget-blue-2a", "widget-blue-3a")
        }
        hero("swift", Team.BLUE, Hex(1, -7)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-blue-1")
            resolved(3, "swift-green-1")
            hand("swift-silver")
        }
        hero("dodger", Team.RED, Hex(3, -7)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        token(WidgetHero.PYRO, Hex(2, -6), owner = "widget")
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(3, -1))
        phase = PositionPhase.Resolving(next = "widget")
    }

    // Snipe's only target (Dodger) is not asked.
    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("dodger"),
        space(4, -6),
        space(5, -7),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Carry Red's last minion out of the Battle Zone: moving a minion never pushes the lane",
            listOf(action(ActionType.PRIMARY), unitAt(1, -6)),
        ),
        WrongTry(
            "Carry Dodger three spaces east: five spaces from Swift, out of Snipe's range",
            listOf(action(ActionType.PRIMARY), hero("dodger"), space(4, -6), space(6, -7)),
        ),
    )
}
