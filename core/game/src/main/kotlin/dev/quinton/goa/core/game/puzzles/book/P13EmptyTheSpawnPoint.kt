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
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C13 "Empty the Spawn Point" (changed: every spawn point near Dodger starts full, Tigerclaw
 * stands on one, and Wasp, tied with Dodger, can refill one; see the concept's "Encoded as").
 */
object P13EmptyTheSpawnPoint : Puzzle() {
    override val concept = "C13"

    override val info = PuzzleInfo(
        id = "p13",
        number = 13,
        title = "Empty the Spawn Point",
        difficulty = 3,
        theme = "Coins and empty spawn points",
        heroes = listOf("tigerclaw", "dodger", "arien", "wasp"),
        goal = PuzzleGoal.GainCoins(5, Deadline.END_OF_TURN, "Gain at least 5 coins for your team this turn."),
        intro = "Round 3, turn 4. Tigerclaw has played Hit and Run (9) and acts next; Arien has played Liquid " +
            "Leap (4), Wasp Stop Projectiles (3) and Dodger Darker Ritual (3). The Tie Breaker coin shows Blue. " +
            "Arien (level 2) has no cards in hand; Wasp holds Lift Up.",
        nudges = listOf(
            "Darker Ritual needs two empty spawn points within 3 of Dodger, and right now there are none. " +
                "What could empty two?",
            "A spawn point is empty only with nothing on it, and Tigerclaw is standing on one. Then look at " +
                "the coin: who acts first at 3?",
            "Defeat Arien, then step south-east, into Wasp's way.",
        ),
        explanation = "Darker Ritual pays 2 coins if 2 or more spawn points within its radius in the Battle " +
            "Zone are empty, and a space is empty only with nothing on it (p.17, Dodger FAQ). All six within " +
            "3 of Dodger are taken, two of them by Arien and by Tigerclaw himself. Hit and Run defeats " +
            "Arien (2 coins, and Dodger 1 assist coin, p.13) and empties his; Tigerclaw's step after the attack " +
            "(card text) empties his own. But Wasp and Dodger are tied at 3 and the coin shows Blue, so Wasp " +
            "moves first (p.7), and Stop Projectiles lets her move 2 spaces onto one of them, unless " +
            "Tigerclaw's step lands on the one space her path needs: south-east. Then the Ritual finds two " +
            "empty spawn points: 2 + 1 + 2 = 5. Defeating the Blue minion on a spawn point instead pays 2 " +
            "and no assist coin: 4 at the very most.",
        rules = listOf(
            "p.7 Tied initiative",
            "p.10 Mandatory and optional steps",
            "p.13 Coins",
            "p.17 Empty",
            "Dodger FAQ: empty spawn points in the Battle Zone",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "dodger")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        hero("tigerclaw", Team.RED, Hex(2, -1)) {
            level = 2
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-silver")
            items("tigerclaw-blue-2a")
            hand("tigerclaw-blue-2b")
        }
        hero("dodger", Team.RED, Hex(2, -3)) {
            level = 2
            played("dodger-green-2a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-blue-1")
            items("dodger-green-2b")
            hand("dodger-silver")
        }
        hero("arien", Team.BLUE, Hex(3, -1)) {
            level = 2
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-blue-1")
            discard("arien-red-2a")
            items("arien-red-2b")
            hand()
        }
        hero("wasp", Team.BLUE, Hex(1, 1)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            items("wasp-red-2b")
            hand("wasp-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(0, -6))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("arien"),
        space(2, 0),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat Arien and stay: his spawn point is the only empty one",
            listOf(action(ActionType.PRIMARY), hero("arien"), decline()),
        ),
        WrongTry(
            "Defeat Arien and step north-east: Wasp walks onto an empty spawn point",
            listOf(action(ActionType.PRIMARY), hero("arien"), space(3, -2)),
        ),
        WrongTry(
            "Defeat the Blue minion on the spawn point: no assist coin, 4 at most",
            listOf(action(ActionType.PRIMARY), unitAt(1, 0)),
        ),
    )
}
