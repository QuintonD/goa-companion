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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 24, mined from self-play (g4946_r3_BLUE_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R24ADiscardForAnAttack : Puzzle() {
    override val concept = "R24"

    override val known = KnownPlays(
        cards = mapOf(
            "arien" to listOf("arien-green-2b", "arien-blue-1", "arien-silver", "arien-red-1"),
            "silverarrow" to
                listOf("silverarrow-green-1", "silverarrow-blue-1", "silverarrow-gold", "silverarrow-red-2a"),
        ),
        actions = mapOf(
            "arien" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -8))),
            ),
            "silverarrow" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, 0))),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(5, -3))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r24",
        number = 24,
        title = "A Discard for an Attack",
        difficulty = 3,
        theme = "Feeding Angry Strike from your own discard, Gydion's spells",
        heroes = listOf("garrus", "gydion", "arien", "silverarrow"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 3. You play Garrus and Gydion for Blue. Minions pay 2 coins here, and Arien pays 2 to the hero" +
            " who defeats him and 1 to the other. Gydion's spellbook has spells in it. Only the first two turns " +
            "of the enemy's actions are known: from turn 3 they answer as hard as they can. Angry Strike gets +1 " +
            "Attack for every card in Garrus's discard.",
        nudges = R24_NUDGES.first(),
        nudgesByTurn = R24_NUDGES,
        explanation =
            "Turn 1: Burning Hands defeats the Red ranged minion next to Gydion at 6: 2 coins. Garrus plays Light" +
                " Pilum." +
                "\n\n" +
                "Turn 2: Chilling Howl discards Light Pilum, a card already resolved, and it costs Garrus nothing he " +
                "needs: Angry Strike counts it later. Cantrip casts Magic Missile at a Red melee minion: 2 more " +
                "coins." +
                "\n\n" +
                "Turn 3: Prepare Spells adds every spell to Gydion's spellbook. Trace's Movement takes Garrus 4 " +
                "spaces, next to Arien." +
                "\n\n" +
                "Turn 4: Angry Strike acts first and attacks Arien at 6, with the card in the discard counted. Noble " +
                "Blade gives 2, and he is defeated before his card resolves (p.13): 2 coins for Garrus and 1 for " +
                "Gydion, 7 in all." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.10 Discard a Card",
            "p.14 Defense",
        ),
        known = known.display(
            mapOf(
                "arien" to mapOf(
                    1 to "Holds.",
                    2 to "Moves to the space outlined.",
                ),
                "silverarrow" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Moves to the space outlined.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("garrus", "gydion")

    override fun PositionBuilder.setUp() {
        round = 3
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 4)
        hero("arien", Team.RED, Hex(1, -7)) {
            level = 2
            coins = 2
            hand("arien-gold", "arien-silver", "arien-red-1", "arien-blue-1", "arien-green-2b")
            items("arien-green-2a")
            deck("arien-green-1")
        }
        hero("silverarrow", Team.RED, Hex(0, 0)) {
            level = 2
            coins = 2
            hand(
                "silverarrow-gold",
                "silverarrow-silver",
                "silverarrow-green-1",
                "silverarrow-blue-1",
                "silverarrow-red-2a",
            )
            items("silverarrow-red-2b")
            deck("silverarrow-red-1")
        }
        hero("garrus", Team.BLUE, Hex(1, -5)) {
            level = 3
            hand("garrus-gold", "garrus-silver", "garrus-red-1", "garrus-green-2b", "garrus-blue-2a")
            items("garrus-green-2a", "garrus-blue-2b")
            deck("garrus-green-1", "garrus-blue-1")
        }
        hero("gydion", Team.BLUE, Hex(5, -7)) {
            level = 3
            hand("gydion-gold", "gydion-silver", "gydion-red-1", "gydion-green-2b", "gydion-blue-2a")
            items("gydion-green-2a", "gydion-blue-2b")
            deck("gydion-green-1", "gydion-blue-1")
            extra(
                "gydion-spell-magic-missile",
                "gydion-spell-shocking-grasp",
                "gydion-spell-expeditious-retreat",
                "gydion-spell-burning-hands",
                "gydion-spell-fireball",
                "gydion-spell-sunburst",
                "gydion-spell-vampiric-touch",
                "gydion-spell-create-undead",
                "gydion-spell-energy-drain",
                "gydion-spell-shield",
                "gydion-spell-banishment",
                "gydion-spell-invulnerability",
                "gydion-spell-dimension-door",
                "gydion-spell-find-familiar",
                "gydion-spell-cloud-kill",
                "gydion-spell-suggestion",
                "gydion-spell-dominate-person",
                "gydion-spell-power-word-kill",
                "gydion-spell-midas-touch",
                "gydion-spell-disintegrate",
                "gydion-spell-polymorph",
                "gydion-spell-wish",
                tag = "spellbook",
            )
            counter("spellbook", 22)
            counter("spellsRemoved", 0)
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -11))
        minion(Team.RED, MinionType.MELEE, Hex(7, -10))
        minion(Team.RED, MinionType.MELEE, Hex(8, -9))
        minion(Team.BLUE, MinionType.RANGED, Hex(9, -9))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(11, -8))
        minion(Team.RED, MinionType.RANGED, Hex(6, -7))
        minion(Team.RED, MinionType.MELEE, Hex(7, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(7, -5))
    }

    override val solution = listOf(
        card("garrus-green-2b"),
        card("gydion-red-1"),
        action(ActionType.PRIMARY),
        action(ActionType.HOLD),
        card("garrus-silver"),
        card("gydion-gold"),
        action(ActionType.PRIMARY),
        card("garrus-green-2b"),
        action(ActionType.PRIMARY),
        card("gydion-spell-magic-missile"),
        unitAt(7, -10),
        card("garrus-red-1"),
        card("gydion-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.MOVEMENT),
        space(3, -9),
        card("garrus-gold"),
        card("gydion-green-2b"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("garrus-blue-2a"),
                card("gydion-gold"),
                action(ActionType.PRIMARY),
                card("gydion-spell-magic-missile"),
                unitAt(8, -9),
                action(ActionType.MOVEMENT),
                space(4, -7),
                card("garrus-green-2b"),
                card("gydion-blue-2a"),
                action(ActionType.MOVEMENT),
                space(7, -8),
                action(ActionType.PRIMARY),
                space(5, -7),
                card("garrus-red-1"),
                card("gydion-green-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("garrus-gold"),
                card("gydion-silver"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(6, -7),
            ),
        ),
    )
}

private val R24_NUDGES = listOf(
    listOf(
        "Three times 2. Gydion stands next to one minion already, and he can reach another. Who is the " +
            "third 2?",
        "Gydion's first card casts a spell. Garrus's first card is for what it leaves behind.",
        "Play Light Pilum on Garrus and Elementary Evocation on Gydion, casting Burning Hands at the Red " +
            "ranged minion.",
    ),
    listOf(
        "Angry Strike wants cards in Garrus's discard. Is there a way to put one there?",
        "Chilling Howl may discard one of your resolved cards. Gydion's Cantrip casts a second spell.",
        "Play Chilling Howl on Garrus, discarding Light Pilum, and Cantrip on Gydion, casting Magic " +
            "Missile at a Red melee minion.",
    ),
    listOf(
        "Arien will be in the Zone. Garrus has to be next to him for the end.",
        "Trace's Movement takes Garrus 4 spaces. Gydion's spellbook can be filled again.",
        "Play Trace on Garrus, moving next to Arien, and Prepare Spells on Gydion.",
    ),
    listOf(
        "Count Angry Strike now. Arien's hand won't save him.",
        "Angry Strike reaches 6, and Noble Blade gives him 2.",
        "Play Angry Strike on Garrus, attacking Arien, and Lesser Conjuration on Gydion.",
    ),
)
