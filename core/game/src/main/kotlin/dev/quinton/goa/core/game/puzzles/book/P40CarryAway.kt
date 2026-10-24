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
        theme = "Minions outside the Battle Zone, defense by the board",
        heroes = listOf("widget", "swift", "dodger", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Red has one Life counter left and Blue two; one Wave counter remains. " +
            "Widget (Carry Away, 10) acts next, then Swift (Snipe, 9), then Dodger (Darker Ritual, 4). Dodger " +
            "holds one card, Shield of Decay. Wasp has acted.",
        nudges = listOf(
            "Red loses with one more Life counter or one more minion. Which of those can Carry Away really take?",
            "Snipe needs a target exactly four spaces from Swift in a straight line. Then read Shield of Decay " +
                "and count what stands around each such space.",
            "Carry Away on Dodger: Pyro and Dodger two spaces north-east.",
        ),
        explanation = "\"A Lane Push can never be triggered by moving, pushing, or swapping a minion. It must be " +
            "either defeated, or removed\" (p.8): carried out of the Battle Zone, Red's last minion just walks " +
            "back (p.18 Minions Outside of the Battle Zone), and nowhere Carry Away can put it is in Snipe's " +
            "reach. Dodger is the target: Carry Away moves Pyro and the enemy next to it 2 or 3 spaces each in " +
            "the same direction, ignoring obstacles (card text, p.18 Ignoring Obstacles), and two landings are " +
            "exactly four spaces from Swift in a straight line, Snipe's only range (p.19 Straight Line). " +
            "South-west, beside Widget, is the trap: Shield of Decay gives +2 there for the two empty spawn " +
            "points within 2 (p.14), and the Red minion next to her (+1) cancels the Blue one (-1): 3 + 2 = 5 " +
            "holds Swift's 5. Two spaces north-east she has one empty spawn point near her and no minion: 3 " +
            "against 5, she is defeated (p.13) and Red's last Life counter goes with her.",
        rules = listOf(
            "p.8 Triggering a Push",
            "p.13 Attacking a Hero",
            "p.14 Defense total, Minion Defense Modifiers",
            "p.18 Ignoring Obstacles, Minions Outside of the Battle Zone",
            "p.19 Straight Line",
        ),
    )

    override val playerSeats = setOf("widget", "swift")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        // Wasp's Kinetic Repulse (10) tied Widget's Carry Away; Red showed, Wasp went first and flipped it.
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
        hero("swift", Team.BLUE, Hex(5, -5)) {
            level = 3
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-green-2a")
            hand("swift-blue-2a")
            items("swift-green-2b", "swift-blue-2b")
        }
        hero("dodger", Team.RED, Hex(3, -7)) {
            level = 3
            played("dodger-green-2a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-2b")
            hand("dodger-blue-1")
            items("dodger-red-2a", "dodger-green-2b")
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            level = 2
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-blue-2b")
            hand("wasp-green-1")
            items("wasp-blue-2a")
        }
        token(WidgetHero.PYRO, Hex(2, -6), owner = "widget")
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "widget")
    }

    // Snipe's only target (Dodger) is not asked; Dodger's defense is the solver's.
    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("dodger"),
        space(4, -8),
        space(5, -9),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Carry Red's last minion out of the Battle Zone: moving a minion never pushes the lane",
            listOf(action(ActionType.PRIMARY), unitAt(1, -6)),
        ),
        WrongTry(
            "Carry Dodger two spaces south-west, four from Swift: Shield of Decay holds 5 against 5",
            listOf(action(ActionType.PRIMARY), hero("dodger"), space(0, -4)),
        ),
    )
}
