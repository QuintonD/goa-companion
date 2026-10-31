package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.puzzles.KnownPlays
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 16, mined from self-play (g8907_r7_BLUE_defeat-snorri; ADR-052, ENCODING.md "Full rounds"). */
object R17OpeningShots : Puzzle() {
    override val concept = "R17"

    override val known = KnownPlays(
        cards = mapOf(
            "snorri" to listOf("snorri-green-2b", "snorri-gold", "snorri-blue-2b", "snorri-silver"),
            "wasp" to listOf("wasp-green-2a", "wasp-blue-3b", "wasp-red-2a", "wasp-gold"),
        ),
        actions = mapOf(
            "snorri" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Unit("m1003")),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "wasp" to mapOf(
                1 to listOf(Choice.Action(ActionType.FAST_TRAVEL), Choice.Space(Hex(-1, -7))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-1, -5))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -5))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r17",
        number = 16,
        title = "Opening Shots",
        difficulty = 2,
        theme = "A discard forced while both discards are empty, the last Life counter",
        heroes = listOf("rowenna", "swift", "snorri", "wasp"),
        goal = PuzzleGoal.DefeatHero("snorri", Deadline.END_OF_ROUND, "Defeat Snorri by the end of the round."),
        intro = "Round 7. You play Rowenna and Swift for Blue. Red has 1 Life counter left, and defeating Snorri " +
            "costs 2. Snorri has five cards and will play four. Opening Shots makes an enemy hero in radius " +
            "discard only if neither of you has a card in the discard.",
        nudges = R17_NUDGES.first(),
        nudgesByTurn = R17_NUDGES,
        explanation =
            "This is one way through, and other plans win too: making Snorri lose a card before turn 4 is proven " +
                "not to be needed." +
                "\n\n" +
                "Turn 1: Rowenna holds with Throw the Gauntlet and stays where she is. Wasp fast travels into Side " +
                "Zone 2." +
                "\n\n" +
                "Turn 2: Code of Chivalry's Movement takes Rowenna next to Snorri. Prepared Shot defeats the Red " +
                "melee minion. Snorri takes a Blue melee minion, as planned." +
                "\n\n" +
                "Turn 3: Opening Shots needs both discards empty, and they are, because Rowenna hasn't defended and " +
                "Snorri hasn't either. He discards Inscribe the Runes, so his hand is down to the one card he plays " +
                "next turn. Bounce moves Swift 2 spaces in a straight line." +
                "\n\n" +
                "Turn 4: Rowenna's initiative beats Snorri's. Paragon of Grace attacks at 7, he has no card to defend" +
                " with, and he is defeated (p.13). His Runic Battleaxe is resolved, without performing an action, and" +
                " Red has spent its last Life counter.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.10 Discard a Card",
            "p.7 Check the initiative",
        ),
        known = known.display(
            mapOf(
                "snorri" to mapOf(
                    1 to "Holds.",
                    2 to "Attacks the Blue melee minion outlined.",
                    3 to "Holds.",
                ),
                "wasp" to mapOf(
                    1 to "Fast travels to the space outlined.",
                    2 to "Moves to the space outlined.",
                    3 to "Moves to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("rowenna", "swift")

    override fun PositionBuilder.setUp() {
        round = 7
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 1)
        hero("snorri", Team.RED, Hex(0, -2)) {
            level = 5
            coins = 4
            hand("snorri-gold", "snorri-silver", "snorri-green-2b", "snorri-blue-2b", "snorri-red-3a")
            items("snorri-red-2b", "snorri-green-2a", "snorri-blue-2a", "snorri-red-3b")
            deck("snorri-red-1", "snorri-green-1", "snorri-blue-1")
            marker("marker_rune_axe", "snorri", true, mapOf("slot" to "1"))
            marker("marker_rune_bird", "snorri", true, mapOf("slot" to "2"))
            marker("marker_rune_horn", "snorri", true, mapOf("slot" to "3"))
            marker("marker_rune_anvil", "snorri", true, mapOf("slot" to "4"))
        }
        hero("wasp", Team.RED, Hex(-6, -3)) {
            level = 5
            coins = 2
            hand("wasp-gold", "wasp-silver", "wasp-red-2a", "wasp-green-2a", "wasp-blue-3b")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2a", "wasp-blue-3a")
            deck("wasp-red-1", "wasp-green-1", "wasp-blue-1")
        }
        hero("rowenna", Team.BLUE, Hex(0, -4)) {
            level = 6
            hand("rowenna-gold", "rowenna-silver", "rowenna-green-2b", "rowenna-red-3a", "rowenna-blue-3a")
            items("rowenna-red-2a", "rowenna-green-2a", "rowenna-blue-2b", "rowenna-red-3b", "rowenna-blue-3b")
            deck("rowenna-red-1", "rowenna-green-1", "rowenna-blue-1")
        }
        hero("swift", Team.BLUE, Hex(6, -7)) {
            level = 4
            coins = 3
            hand("swift-gold", "swift-silver", "swift-red-2a", "swift-green-2b", "swift-blue-2b")
            items("swift-red-2b", "swift-green-2a", "swift-blue-2a")
            deck("swift-red-1", "swift-green-1", "swift-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("rowenna-silver"),
        card("swift-gold"),
        action(ActionType.HOLD),
        action(ActionType.HOLD),
        card("rowenna-gold"),
        card("swift-red-2a"),
        action(ActionType.MOVEMENT),
        space(-1, -2),
        action(ActionType.PRIMARY),
        card("rowenna-green-2b"),
        card("swift-silver"),
        action(ActionType.PRIMARY),
        space(4, -7),
        action(ActionType.PRIMARY),
        card("rowenna-red-3a"),
        card("swift-green-2b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("rowenna-silver"),
                card("swift-red-2a"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                space(-1, -1),
                card("rowenna-green-2b"),
                card("swift-blue-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(-1, 1),
                space(5, -5),
                card("rowenna-gold"),
                card("swift-green-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                card("rowenna-red-3a"),
                card("swift-gold"),
                action(ActionType.MOVEMENT),
                space(4, -1),
                action(ActionType.PRIMARY),
            ),
        ),
    )
}

private val R17_NUDGES = listOf(
    listOf(
        "What will Snorri have left in turn 4? And what must Rowenna's own discard look like when you " +
            "take his spare?",
        "Don't defend, and don't discard, before turn 3. Rowenna needs to get close first.",
        "Play Throw the Gauntlet on Rowenna, who holds, and Reload! on Swift.",
    ),
    listOf(
        "Rowenna has a card that walks her next to Snorri. Swift has a clear shot at a Red minion.",
        "Take the Movement on Rowenna's gold card, not the attack.",
        "Play Code of Chivalry on Rowenna, moving next to Snorri, and Prepared Shot on Swift, shooting " +
            "the Red melee minion.",
    ),
    listOf(
        "Rowenna's discard is still empty, and so is Snorri's. Now is the moment for the card that needs " +
            "that.",
        "Opening Shots takes one of Snorri's cards, in radius 3. Swift only has to bounce clear.",
        "Play Opening Shots on Rowenna and Bounce on Swift.",
    ),
    listOf(
        "Snorri's fourth card is played. Who acts first?",
        "Paragon of Grace acts before him at Attack 7, and he has no card left to defend with.",
        "Play Paragon of Grace on Rowenna, attacking Snorri, and Mark for Death on Swift.",
    ),
)
