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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C32 "Fight as One" (changed: the reused skill is Gnaw, and the heavy minion must fall last). */
object P32FightAsOne : Puzzle() {
    override val concept = "C32"

    override val info = PuzzleInfo(
        id = "p32",
        number = 32,
        title = "Fight as One",
        difficulty = 2,
        theme = "Reusing a resolved skill, heavy minion immunity",
        heroes = listOf("widget", "garrus", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Blue has pushed once: the Battle Zone is the Lane Zone next to Red's Throne Zone. " +
            "Widget has played Fight as One (initiative 12 with her item) and acts next; Gnaw, Dragon Bond and " +
            "Drag Off are her resolved cards. Red has two Life counters left. Wasp has no cards in hand. Garrus " +
            "has acted.",
        nudges = listOf(
            "Count Red's minions in the Battle Zone. What would it take to remove them all this turn?",
            "The heavy minion is immune while Red's melee minion stands, and Fight as One's second action comes " +
                "after the attack.",
            "Attack the melee minion, then perform Gnaw on the heavy minion beside Pyro.",
        ),
        explanation = "Fight as One's After-the-attack text lets Widget perform the primary action of one of her " +
            "resolved skill cards, targeting a different unit (card text). Red has two minions left in the " +
            "Battle Zone, and while its melee minion stands the heavy one is immune (p.13). So attack the melee " +
            "minion first; then perform Gnaw again: it defeats a minion in range of Widget and adjacent to Pyro, " +
            "and the heavy minion is no longer immune. Red has no minion left in the Battle Zone, so the lane is " +
            "pushed at once, and the next zone toward Red is its Throne Zone: Blue wins (p.8). Defeating Wasp, who " +
            "has no card, is tempting, but she costs Red one of its two Life counters (p.13), and Gnaw on the " +
            "melee minion then leaves the heavy one standing. Drag Off can carry the heavy minion away, but moving " +
            "a minion never pushes the lane (p.8); Dragon Bond only moves or places Pyro. The lesson: the heavy " +
            "minion falls last, so spend the attack on the minion that shields it.",
        rules = listOf(
            "p.8 Push the Lane, Triggering a Push",
            "p.9 Range",
            "p.13 Heavy Minion Immunity, When a hero is defeated",
        ),
    )

    override val playerSeats = setOf("widget")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 2)
        battleZone("lane-1", "lane-1-1")
        hero("widget", Team.BLUE, Hex(-4, 0)) {
            level = 5
            played("widget-gold")
            resolved(1, "widget-green-3b")
            resolved(2, "widget-silver")
            resolved(3, "widget-blue-2b")
            hand("widget-red-2b")
            items("widget-red-2a", "widget-green-2a", "widget-green-3a", "widget-blue-2a")
        }
        hero("garrus", Team.BLUE, Hex(-2, -2)) {
            level = 4
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-2a")
            resolved(3, "garrus-green-2a")
            resolved(4, "garrus-silver")
            hand("garrus-blue-2a")
            items("garrus-red-2b", "garrus-green-2b", "garrus-blue-2b")
        }
        hero("wasp", Team.RED, Hex(-4, 1)) {
            level = 3
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            discard("wasp-blue-2a")
            hand()
            items("wasp-red-2b", "wasp-blue-2b")
        }
        hero("dodger", Team.RED, Hex(-7, 2)) {
            level = 3
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-2a")
            resolved(3, "dodger-silver")
            hand("dodger-blue-2a")
            items("dodger-red-2b", "dodger-blue-2b")
        }
        token(WidgetHero.PYRO, Hex(-6, 0), owner = "widget")
        minion(Team.RED, MinionType.MELEE, Hex(-5, 0))
        minion(Team.RED, MinionType.HEAVY, Hex(-6, 1))
        minion(Team.BLUE, MinionType.MELEE, Hex(-4, 3))
        minion(Team.BLUE, MinionType.RANGED, Hex(-3, 0))
        minion(Team.BLUE, MinionType.HEAVY, Hex(-5, 1))
        phase = PositionPhase.Resolving(next = "widget")
    }

    override val solution = listOf(action(ActionType.PRIMARY), unitAt(-5, 0), card("widget-green-3b"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Defeat Wasp: one Life counter, and Gnaw on the melee minion leaves the heavy one",
            listOf(action(ActionType.PRIMARY), hero("wasp")),
        ),
        WrongTry(
            "Melee minion, then Drag Off the heavy one: moving a minion never pushes the lane",
            listOf(action(ActionType.PRIMARY), unitAt(-5, 0), card("widget-blue-2b")),
        ),
        WrongTry(
            "Melee minion, then Dragon Bond: it only moves or places Pyro",
            listOf(action(ActionType.PRIMARY), unitAt(-5, 0), card("widget-silver")),
        ),
        WrongTry("A Movement action: no attack, no second action", listOf(action(ActionType.MOVEMENT))),
    )
}
