package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.engine.PieceState
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 16, mined from self-play (g1082_r6_RED_defeat-xargatha; ADR-052, ENCODING.md "Full rounds"). */
object R16StrikeLikeATiger : Puzzle() {
    override val concept = "R16"

    override val known = KnownPlays(
        cards = mapOf(
            "xargatha" to listOf("xargatha-blue-2b", "xargatha-silver", "xargatha-green-2b", "xargatha-gold"),
            "mrak" to listOf("mrak-gold", "mrak-blue-2a", "mrak-red-1", "mrak-green-2b"),
        ),
        actions = mapOf(
            "xargatha" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(8, -7))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("takahide"), Choice.Space(Hex(7, -7))),
                3 to listOf(Choice.Action(ActionType.PRIMARY)),
            ),
            "mrak" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -5))),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r16",
        number = 16,
        title = "Strike Like a Tiger",
        difficulty = 3,
        theme = "Gold cards swapped for an attack, a big attack that only spends a card",
        heroes = listOf("takahide", "wuk", "xargatha", "mrak"),
        goal = PuzzleGoal.DefeatHero("xargatha", Deadline.END_OF_ROUND, "Defeat Xargatha by the end of the round."),
        intro = "Round 6. You play Takahide and Wuk for Red. Xargatha has five cards and will play four. Takahide's " +
            "gold cards can be swapped with those in his deck: Strike like a Tiger is there, and it hits hard. " +
            "Turn 4 isn't known.",
        nudges = R16_NUDGES.first(),
        nudgesByTurn = R16_NUDGES,
        explanation =
            "Turn 1: Bushido swaps Takahide's gold card with Strike like a Tiger from the deck. Wuk plays Tree " +
                "Slam, which removes a Tree and finds no target." +
                "\n\n" +
                "Turn 2: Wuk places Trees again with Mystic Saplings. Takahide's Pledge of Allegiance lets Wuk " +
                "discard Nature's Guardian, and you each gain a coin. Xargatha's Siren's Call moves Takahide next to " +
                "the Red heavy minion, which is where he wanted to go." +
                "\n\n" +
                "Turn 3: Takahide wins the tie and Strike like a Tiger attacks at 8. Deadly Swipe holds, with her " +
                "items and modifiers, and anything less defeats her at once. So her spare is gone. The card swaps " +
                "itself for Sting like a Bee, and Tree of Plenty removes a Tree to retrieve Nature's Guardian." +
                "\n\n" +
                "Turn 4: Takahide acts before her. Lead from the Front attacks at 4, she has no card to defend with, " +
                "and she is defeated (p.13). Her Cleave goes unresolved. Blue spends 2 Life counters." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.7 Tied Initiative",
            "p.19 Tokens",
        ),
        known = known.display(
            mapOf(
                "xargatha" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Siren's Call: moves Takahide next to the Red heavy minion.",
                    3 to "Plays her skill.",
                ),
                "mrak" to mapOf(
                    1 to "Holds.",
                    2 to "Moves to the space outlined.",
                    3 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("takahide", "wuk")

    override fun PositionBuilder.setUp() {
        round = 6
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "t41", "token_tree", "Tree",
                Hex(
                    9,
                    -12,
                ),
                "wuk", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t42", "token_tree", "Tree",
                Hex(
                    6,
                    -9,
                ),
                "wuk", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t43", "token_tree", "Tree",
                Hex(
                    7,
                    -11,
                ),
                "wuk", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("takahide", Team.RED, Hex(7, -9)) {
            level = 4
            coins = 1
            hand("takahide-gold-extra-1", "takahide-silver", "takahide-red-2a", "takahide-green-2b", "takahide-blue-2a")
            items("takahide-red-2b", "takahide-green-2a", "takahide-blue-2b")
            deck("takahide-gold", "takahide-red-1", "takahide-green-1", "takahide-blue-1")
        }
        hero("wuk", Team.RED, Hex(6, -8)) {
            level = 5
            coins = 2
            hand("wuk-gold", "wuk-silver", "wuk-red-2a", "wuk-blue-2a", "wuk-green-3b")
            items("wuk-red-2b", "wuk-green-2b", "wuk-blue-2b", "wuk-green-3a")
            deck("wuk-red-1", "wuk-green-1", "wuk-blue-1")
        }
        hero("xargatha", Team.BLUE, Hex(5, -4)) {
            level = 4
            coins = 4
            hand("xargatha-gold", "xargatha-silver", "xargatha-red-2a", "xargatha-green-2b", "xargatha-blue-2b")
            items("xargatha-red-2b", "xargatha-green-2a", "xargatha-blue-2a")
            deck("xargatha-red-1", "xargatha-green-1", "xargatha-blue-1")
        }
        hero("mrak", Team.BLUE, Hex(5, -3)) {
            level = 3
            coins = 2
            hand("mrak-gold", "mrak-silver", "mrak-red-1", "mrak-green-2b", "mrak-blue-2a")
            items("mrak-green-2a", "mrak-blue-2b")
            deck("mrak-green-1", "mrak-blue-1")
        }
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("takahide-silver"),
        card("wuk-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        card("takahide-gold-extra-2"),
        card("takahide-green-2b"),
        card("wuk-silver"),
        action(ActionType.PRIMARY),
        space(8, -11),
        tokenAt(9, -12),
        space(9, -11),
        tokenAt(7, -11),
        space(7, -11),
        action(ActionType.PRIMARY),
        card("wuk-red-2a"),
        card("takahide-gold-extra-2"),
        card("wuk-blue-2a"),
        hero("takahide"),
        action(ActionType.PRIMARY),
        card("takahide-gold-extra-1"),
        action(ActionType.PRIMARY),
        tokenAt(8, -11),
        card("wuk-red-2a"),
        card("takahide-red-2a"),
        card("wuk-green-3b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("takahide-red-2a"),
                card("wuk-silver"),
                action(ActionType.MOVEMENT),
                space(8, -9),
                action(ActionType.HOLD),
                card("takahide-green-2b"),
                card("wuk-red-2a"),
                action(ActionType.MOVEMENT),
                space(10, -10),
                action(ActionType.PRIMARY),
                decline(),
                card("takahide-gold-extra-1"),
                card("wuk-blue-2a"),
                action(ActionType.MOVEMENT),
                space(10, -11),
                action(ActionType.HOLD),
                card("takahide-blue-2a"),
                card("wuk-green-3b"),
                action(ActionType.MOVEMENT),
                decline(),
                card("takahide-silver"),
                action(ActionType.MOVEMENT),
                space(8, -10),
            ),
        ),
    )
}

private val R16_NUDGES = listOf(
    listOf(
        "Xargatha can defend once and still play four cards. How do you take that away from her?",
        "Takahide's big attack is in his deck. Which card fetches it?",
        "Play Bushido on Takahide, taking Strike like a Tiger, and Tree Slam on Wuk.",
    ),
    listOf(
        "You will want something back next turn. Discarding isn't always a loss.",
        "Pledge of Allegiance pays you both a coin if Wuk discards. Mystic Saplings places the Trees " +
            "again.",
        "Play Pledge of Allegiance on Takahide and Mystic Saplings on Wuk, who discards Nature's " +
            "Guardian.",
    ),
    listOf(
        "Xargatha has pulled Takahide near her. Attack 8 is coming: what does she have to do?",
        "She has to spend Deadly Swipe, her only card that holds. Tree of Plenty brings your discard " +
            "back.",
        "Play Strike like a Tiger on Takahide, attacking Xargatha, and Tree of Plenty on Wuk, retrieving " +
            "Nature's Guardian.",
    ),
    listOf(
        "Look at Xargatha's hand. Takahide's initiative beats hers.",
        "A small attack is enough now.",
        "Play Lead from the Front on Takahide, attacking Xargatha, and Treetop Ride on Wuk.",
    ),
)
