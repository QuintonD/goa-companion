package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.number
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C08 "Walk the Plank" (changed: Swift, tied with Cutter at 12, stands on the only space of
 * the side Zone that Wasp can reach, four spaces away; see the concept's "Encoded as").
 */
object P08WalkThePlank : Puzzle() {
    override val concept = "C08"

    override val info = PuzzleInfo(
        id = "p08",
        number = 8,
        title = "Walk the Plank",
        difficulty = 1,
        theme = "Pushes across a Zone border",
        heroes = listOf("cutter", "swift", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Cutter has played Walk the Plank (12) and Swift Bounce (12); Wasp and Dodger " +
            "act after them. Wasp has no cards in hand; Dodger holds Shield of Decay. Red has one Life " +
            "counter left.",
        nudges = listOf(
            "Walk the Plank only bites when the push ends in another Zone, and only on a hero with nothing " +
                "to discard.",
            "Trace Wasp's push to the end: which space of another Zone can she reach, and who is standing on it?",
            "Swift acts first and bounces east, out of the way; then Cutter pushes Wasp the full 4 spaces.",
        ),
        explanation = "A push moves the target in a straight line directly away from the pusher, as far as " +
            "it can up to the distance chosen; any obstacle stops it (p.18 Push). Straight away from Cutter, " +
            "Wasp crosses three more spaces of the middle Zone before the thin side strip by the water, a " +
            "separate Zone (p.19 Zone): only the full 4 spaces put her there, and Walk the Plank's " +
            "\"discards a card, or is defeated\" then defeats her, with no card to discard (card text). Red " +
            "spends its last Life counter (p.13). But Swift stands on that very space. Cutter and Swift are " +
            "tied at 12 and on the same team, so you decide who acts first (p.7): Swift's Bounce moves him 2 " +
            "spaces in a straight line, ignoring obstacles, and any direction but back down Wasp's line clears " +
            "the way. Dodger is the easy push, one space into the next Zone, but she discards Shield of Decay " +
            "and survives.",
        rules = listOf(
            "p.7 Tied initiative",
            "p.13 When a hero is defeated",
            "p.18 Push",
            "p.19 Zone, Terrain",
        ),
    )

    override val playerSeats = setOf("cutter", "swift")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 3)
        hero("cutter", Team.BLUE, Hex(0, -3)) {
            level = 2
            played("cutter-gold")
            resolved(1, "cutter-red-2a")
            resolved(2, "cutter-green-1")
            resolved(3, "cutter-blue-1")
            items("cutter-red-2b")
            hand("cutter-silver")
        }
        hero("swift", Team.BLUE, Hex(0, -8)) {
            level = 2
            played("swift-silver")
            resolved(1, "swift-gold")
            resolved(2, "swift-red-2b")
            resolved(3, "swift-green-1")
            items("swift-red-2a")
            hand("swift-blue-1")
        }
        hero("wasp", Team.RED, Hex(0, -4)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-silver")
            discard("wasp-blue-1")
            items("wasp-red-2b")
            hand()
        }
        hero("dodger", Team.RED, Hex(-1, -3)) {
            level = 2
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-2a")
            resolved(3, "dodger-silver")
            items("dodger-red-2b")
            hand("dodger-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -4))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    override val solution = listOf(
        hero("swift"),
        action(ActionType.PRIMARY),
        space(2, -8),
        action(ActionType.PRIMARY),
        option(0),
        hero("wasp"),
        number(4),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry("Cutter first: Swift still blocks the end of Wasp's line", listOf(hero("cutter"))),
        WrongTry(
            "Swift bounces down Wasp's line: now he blocks it in the middle",
            listOf(hero("swift"), action(ActionType.PRIMARY), space(0, -6)),
        ),
        WrongTry(
            "Push Dodger into the next Zone: she discards Shield of Decay",
            listOf(
                hero("swift"),
                action(ActionType.PRIMARY),
                space(2, -8),
                action(ActionType.PRIMARY),
                option(0),
                hero("dodger"),
            ),
        ),
        WrongTry(
            "Push Wasp 3 spaces: still in the middle Zone",
            listOf(
                hero("swift"),
                action(ActionType.PRIMARY),
                space(2, -8),
                action(ActionType.PRIMARY),
                option(0),
                hero("wasp"),
                number(3),
            ),
        ),
    )
}
