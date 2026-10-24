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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C05 "Beat the Drum" (changed: Xargatha replaces Arien and strikes between Tigerclaw and
 * the drum, and Tigerclaw plays Combat Reflexes; see the concept's "Encoded as").
 */
object P05BeatTheDrum : Puzzle() {
    override val concept = "C05"

    override val info = PuzzleInfo(
        id = "p05",
        number = 5,
        title = "Beat the Drum",
        difficulty = 3,
        theme = "Coins and assist coins",
        heroes = listOf("tigerclaw", "brogan", "wasp", "xargatha"),
        goal = PuzzleGoal.GainCoins(7, Deadline.END_OF_TURN, "Gain at least 7 coins for your team this turn."),
        intro = "Round 3, turn 4. Tigerclaw has played Combat Reflexes (9) and acts next; Xargatha has played " +
            "Threatening Slash (7), Brogan War Drummer (5). Wasp (level 3) has already acted. Neither Wasp nor " +
            "Xargatha (level 2) has a card in hand; Tigerclaw holds Dodge. No hero has been defeated this round.",
        nudges = listOf(
            "Count every coin: the defeat, the assist, and what War Drummer pays once a hero has fallen.",
            "Xargatha strikes at 7, before the drum at 5. Where can Tigerclaw end that she can't reach and " +
                "the drum still can?",
            "Defeat Wasp without moving first, then step into the space she leaves.",
        ),
        explanation = "Defeating a hero pays the attacker her level in coins, and each of his teammates assist " +
            "coins for her lowest tier (p.13): Wasp gives Tigerclaw 3 and Brogan 1. War Drummer then gives a " +
            "friendly hero in range 3 coins, since a hero was defeated this round: 3 + 1 + 3 = 7. Defeating " +
            "Xargatha removes the threat but pays only 2: 6. The catch is the order (p.7): Xargatha's " +
            "Threatening Slash at 7 comes before the drum at 5, and Dodge blocks only ranged attacks (p.14), " +
            "so if Tigerclaw ends next to her she defeats him, and the drum has no one to pay. Combat " +
            "Reflexes moves him 1 space before the attack or, if he didn't, 1 space after (card text). Every " +
            "space he can end on is next to Xargatha or more than 4 spaces from Brogan, except one: the space " +
            "Wasp stood on, free only once she is defeated. So attack from where he stands and step into it " +
            "afterwards. The lesson: when a teammate acts after you, end your action where his card can " +
            "still reach you and the enemy's can't.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.10 Mandatory and optional steps",
            "p.13 Coins, When a hero is defeated",
            "p.14 Block",
            "p.17 Friendly",
        ),
    )

    override val playerSeats = setOf("tigerclaw", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        hero("tigerclaw", Team.BLUE, Hex(4, -3)) {
            level = 2
            played("tigerclaw-red-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-silver")
            items("tigerclaw-red-2b")
            hand("tigerclaw-blue-1")
        }
        hero("brogan", Team.BLUE, Hex(2, -5)) {
            level = 2
            played("brogan-green-2b")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-blue-1")
            items("brogan-green-2a")
            hand("brogan-silver")
        }
        hero("wasp", Team.RED, Hex(3, -2)) {
            level = 3
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-silver")
            resolved(4, "wasp-blue-2a")
            discard("wasp-green-1")
            items("wasp-red-2b", "wasp-blue-2b")
            hand()
        }
        hero("xargatha", Team.RED, Hex(4, -4)) {
            level = 2
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-green-1")
            resolved(3, "xargatha-blue-2a")
            discard("xargatha-silver")
            items("xargatha-blue-2b")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "tigerclaw")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        decline(),
        hero("wasp"),
        space(3, -2),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat Xargatha, the threat: 2 + 1 + 3 is only 6",
            listOf(action(ActionType.PRIMARY), decline(), hero("xargatha")),
        ),
        WrongTry(
            "Defeat Wasp and stay put: Xargatha strikes first and the drum finds no one",
            listOf(action(ActionType.PRIMARY), decline(), hero("wasp"), decline()),
        ),
        WrongTry(
            "Defeat Wasp and step south-east: out of the drum's range",
            listOf(action(ActionType.PRIMARY), decline(), hero("wasp"), space(4, -2)),
        ),
    )
}
