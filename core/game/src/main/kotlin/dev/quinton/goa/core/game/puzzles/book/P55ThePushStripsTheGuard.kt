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

/** CONCEPTS.md C55 "The Push Strips the Guard". */
object P55ThePushStripsTheGuard : Puzzle() {
    override val concept = "C55"

    override val info = PuzzleInfo(
        id = "p55",
        number = 55,
        title = "The Push Strips the Guard",
        difficulty = 3,
        theme = "A push respawns minions; defence modifiers",
        heroes = listOf("swift", "brogan", "arien", "dodger"),
        goal = PuzzleGoal.DefeatHero("arien", Deadline.END_OF_TURN, "Defeat Arien this turn."),
        intro = "Round 4, turn 4. Swift (Snipe, 8) acts next, then Brogan (Mad Dash, 7), then Arien (Liquid " +
            "Leap, 4), who keeps Raging Stream (Defense 7, +1 from his item). Blue's only minion in the Battle " +
            "Zone is the melee minion four spaces east of Swift. Dodger has already acted and has no cards left.",
        nudges = listOf(
            "Arien stands alone at the edge of the next zone, for now.",
            "If Red pushes the lane, where do the fresh minions appear?",
            "Snipe Blue's last minion, not Dodger.",
        ),
        explanation = "Defeating the last enemy minion in the Battle Zone pushes the lane at once (p.8): the " +
            "minions of the old Battle Zone are removed, the next zone toward Blue becomes the Battle Zone, a " +
            "minion respawns on each of its spawn points, and the turn goes on in initiative order. Three of the " +
            "new Red minions land around Arien: a heavy and a melee next to him and a ranged one within 2, each " +
            "-1 to his Defense (p.14 Minion Defense Modifiers). Raging Stream now defends 7 + 1 - 3 = 5, and " +
            "Brogan's Mad Dash, two spaces north-east to the space beside him, hits with 6. Shooting Dodger is " +
            "the trap: with no cards he falls, but that costs Blue one Life counter, there is no push, and Arien " +
            "defends 8 against 6.",
        rules = listOf(
            "p.8 Push the Lane, Triggering a Push",
            "p.11 Movement in the Card Text",
            "p.14 Defense total, Minion Defense Modifiers",
        ),
    )

    override val playerSeats = setOf("swift", "brogan")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("swift", Team.RED, Hex(-1, -3)) {
            played("swift-red-1")
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-blue-1")
            hand("swift-green-1")
        }
        hero("brogan", Team.RED, Hex(4, -4)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-blue-1")
            hand("brogan-green-1")
        }
        hero("arien", Team.BLUE, Hex(7, -7)) {
            level = 2
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-blue-1")
            hand("arien-red-2a")
            items("arien-red-2b")
        }
        hero("dodger", Team.BLUE, Hex(3, -7)) {
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            resolved(4, "dodger-green-1")
            discard("dodger-blue-1")
            hand()
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving()
    }

    // After the push, Mad Dash ends on (6,-6) next to Arien (its only enemy hero there: not asked).
    override val solution = listOf(action(ActionType.PRIMARY), unitAt(3, -3), action(ActionType.PRIMARY), space(6, -6))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Snipe Dodger: a free kill, but no push, and Arien defends 8 against 6",
            listOf(action(ActionType.PRIMARY), hero("dodger")),
        ),
        WrongTry(
            "Push, then Mad Dash to the Blue minion instead of Arien",
            listOf(action(ActionType.PRIMARY), unitAt(3, -3), action(ActionType.PRIMARY), space(6, -4)),
        ),
    )
}
