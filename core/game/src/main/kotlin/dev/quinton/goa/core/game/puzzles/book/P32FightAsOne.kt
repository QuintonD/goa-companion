package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.WidgetHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C32 "Fight as One". */
object P32FightAsOne : Puzzle() {
    override val concept = "C32"

    override val info = PuzzleInfo(
        id = "p32",
        number = 32,
        title = "Fight as One",
        difficulty = 2,
        theme = "Reusing a resolved skill, range from Pyro",
        heroes = listOf("widget", "garrus", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Widget has played Fight as One (initiative 12 with her item) and acts next; " +
            "Scorching Breath and Dragon Bond are among her resolved cards. Red has one Life counter left. Wasp has " +
            "played Stop Projectiles and keeps Lift Up; Dodger has played her last card and holds none. " +
            "Garrus has acted.",
        nudges = listOf(
            "Your gold attack unlocks a second action once it is done.",
            "That second action is a card you have already resolved, and it measures from your dragon, not from you.",
            "Attack the minion, then perform Scorching Breath through Pyro on Dodger.",
        ),
        explanation = "Fight as One's After-the-attack text lets Widget perform the primary action of one of her " +
            "resolved skill cards on a different unit (card text), so first attack the minion beside her. " +
            "Scorching Breath is still that card's own action: its range and straight line are counted from " +
            "Pyro (card text; p.9 Range, p.19 Straight Line), and an enemy hero who can't discard is defeated. " +
            "Wasp is the closer target, in line with both Widget and Pyro, but she still holds Lift Up and " +
            "simply discards it. Dodger, three spaces up the same line from Pyro, has no card left: her defeat " +
            "spends Red's last Life counter and Blue wins (p.3, p.13). Dragon Bond only moves or places Pyro.",
        rules = listOf("p.3 Life counters", "p.9 Range", "p.13 When a hero is defeated", "p.19 Straight Line"),
    )

    override val playerSeats = setOf("widget")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 2)
        hero("widget", Team.BLUE, Hex(1, -5)) {
            level = 5
            played("widget-gold")
            resolved(1, "widget-silver")
            resolved(2, "widget-green-3a")
            resolved(3, "widget-red-2b")
            hand("widget-blue-2b")
            items("widget-red-2a", "widget-green-2a", "widget-blue-2a", "widget-green-3b")
        }
        hero("garrus", Team.BLUE, Hex(-1, -2)) {
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-1")
            resolved(4, "garrus-green-1")
            hand("garrus-blue-1")
        }
        hero("wasp", Team.RED, Hex(3, -7)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
        }
        hero("dodger", Team.RED, Hex(3, -8)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        token(WidgetHero.PYRO, Hex(3, -5), owner = "widget")
        minion(Team.RED, MinionType.MELEE, Hex(0, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "widget")
    }

    override val solution = listOf(action(ActionType.PRIMARY), card("widget-green-3a"), hero("dodger"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Scorching Breath on Wasp, the closer hero in line: she discards Lift Up and survives",
            listOf(action(ActionType.PRIMARY), card("widget-green-3a"), hero("wasp")),
        ),
        WrongTry(
            "Dragon Bond after the attack: it only moves Pyro",
            listOf(action(ActionType.PRIMARY), card("widget-silver")),
        ),
        WrongTry("A Movement action: no attack, no second action", listOf(action(ActionType.MOVEMENT))),
    )
}
