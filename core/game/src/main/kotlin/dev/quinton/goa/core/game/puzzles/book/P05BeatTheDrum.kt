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

/** CONCEPTS.md C05 "Beat the Drum". */
object P05BeatTheDrum : Puzzle() {
    override val concept = "C05"

    override val info = PuzzleInfo(
        id = "p05",
        number = 5,
        title = "Beat the Drum",
        difficulty = 1,
        theme = "Coins and assist coins",
        heroes = listOf("tigerclaw", "brogan", "arien", "wasp"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_TURN, "Gain at least 6 coins for your team this turn."),
        intro = "Round 2, turn 4. Tigerclaw has played Hit and Run (9) and acts next; Brogan has played War " +
            "Drummer (5). Arien (level 2) has no cards left in hand; Wasp has already acted. No hero has been " +
            "defeated this round.",
        nudges = listOf(
            "War Drummer pays much more once a hero has been defeated this round.",
            "Count the range from Brogan to where Tigerclaw ends his action.",
            "Defeat Arien with Hit and Run, then step one space towards Brogan.",
        ),
        explanation = "Defeating a hero pays the attacker coins equal to the defeated hero's level, and each " +
            "of the attacker's teammates assist coins set by that hero's lowest tier (p.13): Arien gives Tigerclaw 2 " +
            "and Brogan 1. War Drummer gives a friendly hero in range 1 coin, or 3 once any hero was " +
            "defeated this round, and it resolves at 5, after Hit and Run at 9 (p.7). Its target must be in " +
            "range when it resolves: from where he stands Tigerclaw is 5 spaces from Brogan, so the optional " +
            "step after the attack (p.10) is what brings him within 4. 2 + 1 + 3 = 6. Hitting the minion " +
            "instead pays 2, and the drum only 1. The lesson: when a teammate acts after you, end your " +
            "action where his card can still reach you.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.10 Mandatory and optional steps",
            "p.13 Coins",
            "p.17 Friendly",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "brogan")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("tigerclaw", Team.BLUE, Hex(3, -6)) {
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-blue-1")
            resolved(3, "tigerclaw-green-1")
            hand("tigerclaw-silver")
        }
        hero("brogan", Team.BLUE, Hex(-2, -3)) {
            level = 2
            coins = 1
            played("brogan-green-2b")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-blue-1")
            items("brogan-green-2a")
            hand("brogan-silver")
        }
        hero("arien", Team.RED, Hex(4, -7)) {
            level = 2
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-blue-1")
            discard("arien-red-2a")
            items("arien-red-2b")
            hand()
        }
        hero("wasp", Team.RED, Hex(0, 0)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(4, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("arien"),
        space(2, -6),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Hit the Red minion: 2 coins, and the drum pays only 1",
            listOf(action(ActionType.PRIMARY), unitAt(4, -6)),
        ),
        WrongTry(
            "Defeat Arien but stay put: Tigerclaw is 5 spaces from Brogan, out of War Drummer's range",
            listOf(action(ActionType.PRIMARY), hero("arien"), decline()),
        ),
    )
}
