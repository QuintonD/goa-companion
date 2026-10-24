package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C56 "Pile On the Fresh Wave". */
object P56PileOnTheFreshWave : Puzzle() {
    override val concept = "C56"

    override val info = PuzzleInfo(
        id = "p56",
        number = 56,
        title = "Pile On the Fresh Wave",
        difficulty = 3,
        theme = "Pushing the lane in the middle of an action",
        heroes = listOf("hanu", "dodger", "tigerclaw", "arien"),
        goal = PuzzleGoal.GainCoins(4, Deadline.END_OF_TURN, "Hanu gains at least 4 coins this turn."),
        intro = "Round 3, turn 4. Hanu has played Pile On and is the last to act. Dodger (level 3) has no " +
            "cards left. Tigerclaw has acted; he stands in Blue's pre-Throne Zone. Blue has one minion left " +
            "in the Battle Zone.",
        nudges = listOf(
            "Pile On has two shots, and you choose their order.",
            "After a push, fresh minions appear. Maybe next to a friend.",
            "Shoot the Blue ranged minion next to you first.",
        ),
        explanation = "Dodger has no cards and is worth 3 coins (her level, p.13), but then the second bullet " +
            "has nothing to shoot: no Blue minion stands next to Tigerclaw. The ranged minion is worth only " +
            "2, but it is Blue's last minion in the Battle Zone, so defeating it pushes the lane at once " +
            "(p.8): the Battle Zone moves to Blue's pre-Throne Zone, where Tigerclaw stands, and a Blue melee " +
            "minion respawns on the spawn point next to him. Pile On's bullets may come in any order (p.10 " +
            "Modal Actions), so the second one now targets that fresh minion, 3 spaces away, for 2 more " +
            "coins: 4 in all. The rulebook hints at exactly this (p.10 Repeat).",
        rules = listOf(
            "p.8 Push the Lane, Triggering a Push",
            "p.10 Modal Actions, Repeat",
            "p.13 Attacking a Minion, When a Hero is Defeated",
            "p.17 Friendly",
        ),
    )

    override val playerSeats = setOf("hanu")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("dodger", Team.BLUE, Hex(4, -3)) {
            level = 3
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-2a")
            resolved(4, "dodger-green-2a")
            discard("dodger-blue-1")
            hand()
            items("dodger-red-2b", "dodger-green-2b")
        }
        hero("hanu", Team.RED, Hex(4, -4)) {
            level = 5
            played("hanu-red-3b")
            resolved(1, "hanu-gold")
            resolved(2, "hanu-silver")
            resolved(3, "hanu-green-2b")
            hand("hanu-blue-2b")
            items("hanu-red-2a", "hanu-red-3a", "hanu-green-2a", "hanu-blue-2a")
        }
        hero("tigerclaw", Team.RED, Hex(6, -4)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-green-1")
            hand("tigerclaw-blue-1")
        }
        hero("arien", Team.BLUE, Hex(-1, -5)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "hanu")
    }

    // After the push the second bullet's only target (the fresh melee minion at (7,-5)) is not asked.
    override val solution = listOf(action(ActionType.PRIMARY), unitAt(3, -4), yes())

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Shoot Dodger: 3 coins, and the second bullet has no target",
            listOf(action(ActionType.PRIMARY), hero("dodger")),
        ),
        WrongTry(
            "Shoot the minion and stop: 2 coins",
            listOf(action(ActionType.PRIMARY), unitAt(3, -4), decline()),
        ),
    )
}
