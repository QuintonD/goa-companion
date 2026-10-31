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
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 1, mined from self-play (g2312_r2_RED_coins-6; ADR-052, ENCODING.md "Full rounds"). */
object R15ClawsThatCatchTwice : Puzzle() {
    override val concept = "R15"

    override val known = KnownPlays(
        cards = mapOf(
            "gydion" to listOf("gydion-green-1", "gydion-gold", "gydion-silver", "gydion-red-1"),
            "wuk" to listOf("wuk-gold", "wuk-silver", "wuk-red-1", "wuk-green-1"),
        ),
        actions = mapOf(
            "gydion" to mapOf(
                1 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, 0))),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Card("gydion-spell-magic-missile")),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
            "wuk" to mapOf(
                1 to listOf(Choice.Action(ActionType.PRIMARY)),
                2 to
                    listOf(
                        Choice.Action(ActionType.PRIMARY),
                        Choice.Space(Hex(-1, -5)),
                        Choice.Space(Hex(-1, -6)),
                        Choice.Space(Hex(0, -5)),
                    ),
                3 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r15",
        number = 1,
        title = "Claws That Catch, Twice",
        difficulty = 1,
        theme = "An active card used again, coins counted to the last one",
        heroes = listOf("mortimer", "ursafar", "gydion", "wuk"),
        goal = PuzzleGoal.GainCoins(6, Deadline.END_OF_ROUND, "Gain 6 coins by the end of the round."),
        intro = "Round 2. You play Mortimer and Ursafar for Red, both still level 1. Melee and ranged minions pay 2 " +
            "coins. Wuk, also " +
            "level 1, pays 1 to the hero who defeats him and 1 to the other. Wuk holds five cards and will play " +
            "four.",
        nudges = R15_NUDGES.first(),
        nudgesByTurn = R15_NUDGES,
        explanation =
            "This is one way through, and other plans win too: making Wuk spend a card before turn 4 is proven " +
                "not to be needed. What this line shows is Claws That Catch used twice." +
                "\n\n" +
                "Turn 1: Wuk takes a Red melee minion as planned. Ursafar uses Sniff Out's Movement to stand next to " +
                "a Blue melee minion, and Mortimer plays Awaken!." +
                "\n\n" +
                "Turn 2: Claws That Catch and Knife of the Living Dead each defeat a Blue melee minion: 4 coins. " +
                "Claws That Catch stays active, and Ursafar is enraged this round." +
                "\n\n" +
                "Turn 3: Angry Roar performs Claws That Catch again: Ursafar moves 1 space, next to Wuk, and attacks " +
                "at 4. Wuk must spend a card: Toss Away holds, with 4 and +1 for his adjacent heavy minion (p.14). " +
                "Mortimer walks into Lane Zone 3." +
                "\n\n" +
                "Turn 4: Prey Drive attacks Wuk at 5. He has no card to defend with and is defeated before his card " +
                "resolves (p.13): 1 coin for Ursafar and 1 for Mortimer's assist. That makes 6, and the goal is met.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense, Minion Defense Modifiers",
            "p.17 Active Effects",
        ),
        known = known.display(
            mapOf(
                "gydion" to mapOf(
                    1 to "Moves to the space outlined.",
                    2 to "Casts its spell at a Red melee minion.",
                    3 to "Holds.",
                ),
                "wuk" to mapOf(
                    1 to "Attacks a unit in its reach.",
                    2 to "Places Trees on the spaces outlined.",
                    3 to "Holds.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("mortimer", "ursafar")

    override fun PositionBuilder.setUp() {
        round = 2
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 3)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 4)
        piece(
            PieceState(
                "t17", "token_zombie", "Zombie",
                Hex(
                    -4,
                    -6,
                ),
                "mortimer", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t18", "token_zombie", "Zombie",
                Hex(
                    -4,
                    -5,
                ),
                "mortimer", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t19", "token_zombie", "Zombie",
                Hex(
                    -5,
                    -4,
                ),
                "mortimer", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        piece(
            PieceState(
                "t20", "token_zombie", "Zombie",
                Hex(
                    -5,
                    -6,
                ),
                "mortimer", Team.RED, true, false, false, false, false, null, linkedMapOf(),
            ),
        )
        hero("mortimer", Team.RED, Hex(5, -9)) {
            level = 1
            coins = 1
            hand("mortimer-gold", "mortimer-silver", "mortimer-red-1", "mortimer-green-1", "mortimer-blue-1")
        }
        hero("ursafar", Team.RED, Hex(-1, -4)) {
            level = 1
            coins = 1
            hand("ursafar-gold", "ursafar-silver", "ursafar-red-1", "ursafar-green-1", "ursafar-blue-1")
        }
        hero("gydion", Team.BLUE, Hex(4, -1)) {
            level = 1
            coins = 1
            hand("gydion-gold", "gydion-silver", "gydion-red-1", "gydion-green-1", "gydion-blue-1")
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
        hero("wuk", Team.BLUE, Hex(2, -6)) {
            level = 1
            coins = 1
            hand("wuk-gold", "wuk-silver", "wuk-red-1", "wuk-green-1", "wuk-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("mortimer-silver"),
        card("ursafar-green-1"),
        action(ActionType.HOLD),
        action(ActionType.MOVEMENT),
        space(0, -4),
        card("mortimer-gold"),
        card("ursafar-gold"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        card("mortimer-red-1"),
        card("ursafar-silver"),
        hero("mortimer"),
        action(ActionType.MOVEMENT),
        space(8, -12),
        action(ActionType.PRIMARY),
        space(1, -5),
        card("mortimer-green-1"),
        card("ursafar-red-1"),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "A natural line",
            listOf(
                card("mortimer-gold"),
                card("ursafar-silver"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("mortimer-silver"),
                card("ursafar-green-1"),
                action(ActionType.PRIMARY),
                tokenAt(-4, -6),
                space(1, -6),
                tokenAt(-5, -4),
                space(4, -9),
                tokenAt(1, -6),
                space(6, -9),
                tokenAt(-4, -5),
                space(5, -8),
                action(ActionType.MOVEMENT),
                space(0, -4),
                card("mortimer-green-1"),
                card("ursafar-red-1"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
                option(0),
                tokenAt(4, -9),
                space(3, -9),
                card("mortimer-red-1"),
                card("ursafar-blue-1"),
                action(ActionType.PRIMARY),
                space(1, -4),
                decline(),
                action(ActionType.HOLD),
            ),
        ),
    )
}

private val R15_NUDGES = listOf(
    listOf(
        "Two minions make four coins. Where do the last two come from?",
        "Ursafar needs to be next to a Blue minion next turn, and enraged by the turn after. Mortimer is " +
            "close already.",
        "Play Awaken! on Mortimer and Sniff Out on Ursafar, who moves next to the Blue melee minion.",
    ),
    listOf(
        "Each of you can take a Blue minion now. Only one of your attack cards also matters next turn.",
        "Claws That Catch makes Ursafar enraged this round and stays active. Keep that in mind.",
        "Play Knife of the Living Dead on Mortimer and Claws That Catch on Ursafar, each attacking a Blue" +
            " melee minion.",
    ),
    listOf(
        "Wuk is in reach of something you have already played. What will it cost him?",
        "Angry Roar performs the primary action of an active card when you are enraged: Claws That Catch," +
            " with its move next to an enemy hero.",
        "Play Crawling Dead on Mortimer, who moves, and Angry Roar on Ursafar, attacking Wuk through " +
            "Claws That Catch.",
    ),
    listOf(
        "On this line Wuk has spent his spare. Who acts first this turn?",
        "Ursafar's initiative ties Wuk's, and you are first. Attack 5, and he has nothing to defend with.",
        "Play Stage Dive on Mortimer and Prey Drive on Ursafar, attacking Wuk.",
    ),
)
