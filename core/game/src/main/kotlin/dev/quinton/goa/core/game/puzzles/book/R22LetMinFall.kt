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
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 22, mined from self-play (g1893_r4_RED_defeat-mortimer; ADR-052, ENCODING.md "Full rounds"). */
object R22LetMinFall : Puzzle() {
    override val concept = "R22"

    override val known = KnownPlays(
        cards = mapOf(
            "mortimer" to listOf("mortimer-red-2b", "mortimer-gold", "mortimer-silver", "mortimer-blue-2a"),
            "arien" to listOf("arien-green-1", "arien-gold", "arien-silver", "arien-red-2b"),
        ),
        actions = mapOf(
            "mortimer" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("min")),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("m1000")),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "arien" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Decline("Skip")),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r22",
        number = 22,
        title = "Let Min Fall",
        difficulty = 3,
        theme = "A hero spent for tempo, respawn and return",
        heroes = listOf("min", "tali", "mortimer", "arien"),
        goal = PuzzleGoal.DefeatHero("mortimer", Deadline.END_OF_ROUND, "Defeat Mortimer by the end of the round."),
        intro = "Round 4. You play Min and Tali for Red. Min stands next to Mortimer, whose Braains...! hits her " +
            "first for 8. Mortimer holds five cards and will play four. Turn 4 isn't known: there he answers as " +
            "hard as he can.",
        nudges = R22_NUDGES.first(),
        nudgesByTurn = R22_NUDGES,
        explanation =
            "Turn 1: Mortimer attacks Min at 8, and Smoke Bomb gives her only 3: she is defeated, and Red spends " +
                "2 Life counters. On this line that is the price. Ancestral Totem's Movement takes Tali 2 spaces, " +
                "next to the Red melee and heavy minions." +
                "\n\n" +
                "Turn 2: Min respawns in the Red Throne Zone and moves 1 space. Ice Blast's Movement takes Tali next " +
                "to Mortimer, who defeats a Red melee minion as planned." +
                "\n\n" +
                "Turn 3: Viper Stance's Movement takes Min 5 spaces to Side Zone 2. Tali plays Commune with Spirits, " +
                "which stops there with no gold card in the discard to perform." +
                "\n\n" +
                "Turn 4: Winter Spear attacks Mortimer at 6. His best answer is Gathering Horde: 4, +2 for items, -1 " +
                "for the adjacent enemy melee minion, makes 5. He is defeated before Morbid Mosh resolves (p.13): 4 " +
                "coins for Tali and 2 for Min, and Blue spends 2 Life counters." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.7 Hero Respawn",
        ),
        known = known.display(
            mapOf(
                "mortimer" to mapOf(
                    1 to "Attacks Min with Braains...!.",
                    2 to "Attacks a Red melee minion.",
                    3 to "Holds.",
                ),
                "arien" to mapOf(
                    1 to "Holds.",
                    2 to "Moves to the space outlined.",
                    3 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("min", "tali")

    override fun PositionBuilder.setUp() {
        round = 4
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "t20", "token_zombie", "Zombie",
                Hex(
                    5,
                    -4,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t22", "token_zombie", "Zombie",
                Hex(
                    -1,
                    -2,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t24", "token_zombie", "Zombie",
                Hex(
                    2,
                    -1,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t25", "token_zombie", "Zombie",
                Hex(
                    1,
                    -2,
                ),
                "mortimer", Team.BLUE, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("min", Team.RED, Hex(9, -10)) {
            level = 4
            coins = 1
            hand("min-gold", "min-silver", "min-red-2b", "min-green-2b", "min-blue-2b")
            items("min-red-2a", "min-green-2a", "min-blue-2a")
            deck("min-red-1", "min-green-1", "min-blue-1")
        }
        hero("tali", Team.RED, Hex(7, -6)) {
            level = 4
            hand("tali-gold", "tali-silver", "tali-red-2a", "tali-green-2b", "tali-blue-2b")
            items("tali-red-2b", "tali-green-2a", "tali-blue-2a")
            deck("tali-red-1", "tali-green-1", "tali-blue-1")
        }
        hero("mortimer", Team.BLUE, Hex(8, -10)) {
            level = 4
            hand("mortimer-gold", "mortimer-silver", "mortimer-red-2b", "mortimer-green-2b", "mortimer-blue-2a")
            items("mortimer-red-2a", "mortimer-green-2a", "mortimer-blue-2b")
            deck("mortimer-red-1", "mortimer-green-1", "mortimer-blue-1")
        }
        hero("arien", Team.BLUE, Hex(5, -5)) {
            level = 3
            hand("arien-gold", "arien-silver", "arien-green-1", "arien-red-2b", "arien-blue-2b")
            items("arien-red-2a", "arien-blue-2a")
            deck("arien-red-1", "arien-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(6, -4))
    }

    override val solution = listOf(
        card("min-green-2b"),
        card("tali-green-2b"),
        card("min-silver"),
        action(ActionType.MOVEMENT),
        space(7, -8),
        card("min-gold"),
        card("tali-gold"),
        space(-5, -5),
        action(ActionType.MOVEMENT),
        space(-5, -6),
        action(ActionType.MOVEMENT),
        space(7, -9),
        card("min-red-2b"),
        card("tali-silver"),
        action(ActionType.PRIMARY),
        option(0),
        action(ActionType.MOVEMENT),
        space(0, -8),
        card("min-blue-2b"),
        card("tali-red-2a"),
        action(ActionType.PRIMARY),
        space(1, -10),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("min-green-2b"),
                card("tali-green-2b"),
                decline(),
                action(ActionType.MOVEMENT),
                space(7, -5),
                card("min-blue-2b"),
                card("tali-gold"),
                hero("tali"),
                action(ActionType.PRIMARY),
                space(-5, -5),
                action(ActionType.FAST_TRAVEL),
                space(5, -11),
                card("min-silver"),
                card("tali-blue-2b"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(10, -8),
                card("min-gold"),
                card("tali-red-2a"),
                action(ActionType.FAST_TRAVEL),
                space(4, -5),
                action(ActionType.PRIMARY),
                card("min-red-2b"),
            ),
        ),
    )
}

private val R22_NUDGES = listOf(
    listOf(
        "Attack 8 is coming at Min. Tali is the one who ends this round next to Mortimer.",
        "Don't spend the round saving Min. Tali should walk.",
        "Play Inner Strength on Min and Ancestral Totem on Tali, who moves next to the Red melee and " +
            "heavy minions.",
    ),
    listOf(
        "Min is off the board, and she can come back. Tali only has one more step to take.",
        "Respawn Min. Tali's Ice Blast has a Movement of 1.",
        "Play Fast as Lightning on Min, respawning in the Red Throne Zone, and Ice Blast on Tali, moving " +
            "next to Mortimer.",
    ),
    listOf(
        "Keep Tali's best attack for last. What's left to spend?",
        "Min can run with Viper Stance's Movement. Commune with Spirits costs you nothing.",
        "Play Viper Stance on Min, who moves, and Commune with Spirits on Tali.",
    ),
    listOf(
        "Look at Mortimer's hand. What can he defend with, and what is Winter Spear worth?",
        "Winter Spear reaches 6 here. Gathering Horde gives him 5.",
        "Play Death Grenade on Min and Winter Spear on Tali, attacking Mortimer.",
    ),
)
