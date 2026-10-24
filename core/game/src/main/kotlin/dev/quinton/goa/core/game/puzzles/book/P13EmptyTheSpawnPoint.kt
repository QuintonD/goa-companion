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

/** CONCEPTS.md C13 "Empty the Spawn Point". */
object P13EmptyTheSpawnPoint : Puzzle() {
    override val concept = "C13"

    override val info = PuzzleInfo(
        id = "p13",
        number = 13,
        title = "Empty the Spawn Point",
        difficulty = 1,
        theme = "Coins and empty spawn points",
        heroes = listOf("tigerclaw", "dodger", "arien", "wasp"),
        goal = PuzzleGoal.GainCoins(4, Deadline.END_OF_TURN, "Gain at least 4 coins for your team this turn."),
        intro = "Round 2, turn 4. Tigerclaw has played Hit and Run (9) and acts next; Dodger has played " +
            "Darker Ritual (3). Both Blue heroes have already acted; Arien (level 2) has no cards left in hand.",
        nudges = listOf(
            "Dodger's Ritual needs two empty spawn points near her: count them.",
            "A spawn point is only empty if nothing stands on it.",
            "Defeat the Blue minion standing on the spawn point next to Tigerclaw, and stay where you are.",
        ),
        explanation = "Darker Ritual pays 2 coins if there are 2 or more empty spawn points within its radius " +
            "in the Battle Zone; a space is empty only with nothing on it (p.17, Dodger FAQ). Within 3 of " +
            "Dodger only one spawn point is empty, until Hit and Run defeats the Blue minion standing on " +
            "another (2 coins, p.13). Tigerclaw acts at 9, before the Ritual at 3 (p.7), so the spawn point is " +
            "empty when she checks, as long as he doesn't step onto it after the attack. 2 + 2 = 4. Arien with " +
            "no cards pays 2 and 1 assist coin, but the Ritual then finds one empty spawn point: 3.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.13 Coins",
            "p.17 Empty",
            "Dodger FAQ: empty spawn points in the Battle Zone",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "dodger")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        hero("tigerclaw", Team.RED, Hex(2, -5)) {
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-blue-1")
            resolved(3, "tigerclaw-green-1")
            hand("tigerclaw-silver")
        }
        hero("dodger", Team.RED, Hex(0, -5)) {
            level = 2
            played("dodger-green-2a")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-blue-1")
            items("dodger-green-2b")
            hand("dodger-silver")
        }
        hero("arien", Team.BLUE, Hex(3, -5)) {
            level = 2
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-blue-1")
            resolved(4, "arien-green-1")
            discard("arien-red-2a")
            items("arien-red-2b")
            hand()
        }
        hero("wasp", Team.BLUE, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -6))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        unitAt(1, -4),
        decline(),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat Arien: 2 coins and 1 assist coin, but the Ritual finds only one empty spawn point",
            listOf(action(ActionType.PRIMARY), hero("arien")),
        ),
        WrongTry(
            "Defeat the minion off the spawn point: still one empty spawn point",
            listOf(action(ActionType.PRIMARY), unitAt(2, -6)),
        ),
        WrongTry(
            "Defeat the right minion, then step onto its spawn point: it is no longer empty",
            listOf(action(ActionType.PRIMARY), unitAt(1, -4), space(1, -4)),
        ),
    )
}
