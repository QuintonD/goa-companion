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
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 12, mined from self-play (g4608_r7_BLUE_coins-5; ADR-052, ENCODING.md "Full rounds"). */
object R12Attack2StillCostsACard : Puzzle() {
    override val concept = "R12"

    override val known = KnownPlays(
        cards = mapOf(
            "min" to listOf("min-silver", "min-green-2b", "min-gold", "min-blue-2a"),
            "snorri" to listOf("snorri-green-2a", "snorri-gold", "snorri-red-2a", "snorri-blue-2b"),
        ),
        actions = mapOf(
            "min" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.PRIMARY)),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(3, -1))),
                4 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(4, -4)), Choice.Space(Hex(4, -2)), Choice.Space(Hex(2, 0)), Choice.Space(Hex(2, 0))),
            ),
            "snorri" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(2, -2))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(-1, -2))),
                4 to listOf(Choice.Action(ActionType.HOLD)),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r12",
        number = 12,
        title = "Attack 2 Still Costs a Card",
        difficulty = 2,
        theme = "A weak attack that spends the enemy's spare, assist coins",
        heroes = listOf("wasp", "whisper", "min", "snorri"),
        goal = PuzzleGoal.GainCoins(5, Deadline.END_OF_ROUND, "Gain 5 coins by the end of the round."),
        intro = "Round 7. You play Wasp and Whisper for Blue. Whisper stands next to Snorri. A hero who doesn't " +
            "defend is defeated, however small the attack, and defending costs a card (p.13, p.14). Snorri holds " +
            "five cards and will play four.",
        nudges = R12_NUDGES.first(),
        nudgesByTurn = R12_NUDGES,
        explanation = "Turn 1: Wasp fast travels with Control Gravity's Movement. Shadow Walk places Whisper on an empty " +
            "spawn point next to Snorri, so she can reach him next turn." +
            "\n\n" +
            "Turn 2: Swift Justice attacks Snorri for only 2, but a hero who doesn't defend is defeated (p.13), " +
            "so he has to spend a card: with Runic Hammer here, or with Inscribe the Runes or Ancestral Boon. If " +
            "he declines, the goal is met at once." +
            "\n\n" +
            "Turn 3: Nobody attacks. Static Barrier and Death Seeker are played now so that Charged Boomerang and" +
            " Sanguine Path are left for turn 4." +
            "\n\n" +
            "Turn 4: Snorri's fourth card is played and his spare has gone. Wasp moves first on the tie; Sanguine" +
            " Path moves Whisper 2 spaces and attacks Snorri at 3. He has no card to defend with and is defeated " +
            "before his card resolves: Whisper gains 4 coins and Wasp 2 for the assist (p.13), 6 in all." +
            "\n\n" +
            "The computer at strength 7, playing your heroes, doesn't get there, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense",
            "p.7 Tied Initiative",
        ),
        known = known.display(
            mapOf(
                "min" to mapOf(
                    1 to "Holds.",
                    2 to "Inner Strength.",
                    3 to "Moves to the space outlined.",
                    4 to "Cluster Mine, placing its Mines on the spaces outlined.",
                ),
                "snorri" to mapOf(
                    1 to "Holds.",
                    2 to "Moves to the space outlined.",
                    3 to "Inscribe the Runes.",
                    4 to "Plays Ancestral Boon.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("wasp", "whisper")

    override fun PositionBuilder.setUp() {
        round = 7
        tieBreaker = Team.BLUE
        clearMinions()
        battleZone("lane-1", "lane-1-2")
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("min", Team.RED, Hex(3, 0)) {
            level = 4
            coins = 4
            hand("min-gold", "min-silver", "min-red-2b", "min-green-2b", "min-blue-2a")
            items("min-red-2a", "min-green-2a", "min-blue-2b")
            deck("min-red-1", "min-green-1", "min-blue-1")
        }
        hero("snorri", Team.RED, Hex(3, -3)) {
            level = 4
            coins = 1
            hand("snorri-gold", "snorri-silver", "snorri-red-2a", "snorri-green-2a", "snorri-blue-2b")
            items("snorri-red-2b", "snorri-green-2b", "snorri-blue-2a")
            deck("snorri-red-1", "snorri-green-1", "snorri-blue-1")
            marker("marker_rune_axe", "snorri", true, mapOf("slot" to "1"))
            marker("marker_rune_bird", "snorri", true, mapOf("slot" to "2"))
            marker("marker_rune_anvil", "snorri", true, mapOf("slot" to "3"))
            marker("marker_rune_horn", "snorri", true, mapOf("slot" to "4"))
        }
        hero("wasp", Team.BLUE, Hex(6, -10)) {
            level = 4
            coins = 4
            hand("wasp-gold", "wasp-silver", "wasp-red-2b", "wasp-green-2b", "wasp-blue-2a")
            items("wasp-red-2a", "wasp-green-2a", "wasp-blue-2b")
            deck("wasp-red-1", "wasp-green-1", "wasp-blue-1")
        }
        hero("whisper", Team.BLUE, Hex(3, -4)) {
            level = 4
            coins = 2
            hand("whisper-gold", "whisper-silver", "whisper-red-2a", "whisper-green-2a", "whisper-blue-2b")
            items("whisper-red-2b", "whisper-green-2b", "whisper-blue-2a")
            deck("whisper-red-1", "whisper-green-1", "whisper-blue-1")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
    }

    override val solution = listOf(
        card("wasp-blue-2a"),
        card("whisper-green-2a"),
        action(ActionType.FAST_TRAVEL),
        space(8, -11),
        action(ActionType.PRIMARY),
        space(2, -3),
        card("wasp-gold"),
        card("whisper-gold"),
        hero("wasp"),
        action(ActionType.MOVEMENT),
        space(8, -12),
        action(ActionType.PRIMARY),
        option(1),
        card("wasp-silver"),
        card("whisper-silver"),
        hero("wasp"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        card("wasp-red-2b"),
        card("whisper-red-2a"),
        hero("wasp"),
        action(ActionType.MOVEMENT),
        space(9, -12),
        action(ActionType.PRIMARY),
        space(1, -2),
        space(1, -1),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("wasp-green-2b"),
                card("whisper-gold"),
                action(ActionType.MOVEMENT),
                space(4, -4),
                action(ActionType.MOVEMENT),
                space(4, -8),
                card("wasp-red-2b"),
                card("whisper-green-2a"),
                action(ActionType.PRIMARY),
                action(ActionType.HOLD),
                card("wasp-blue-2a"),
                card("whisper-blue-2b"),
                hero("wasp"),
                action(ActionType.MOVEMENT),
                space(1, -7),
                action(ActionType.MOVEMENT),
                space(3, -5),
                card("wasp-gold"),
                card("whisper-silver"),
                action(ActionType.PRIMARY),
                action(ActionType.PRIMARY),
            ),
        ),
    )
}

private val R12_NUDGES = listOf(
            listOf(
                "Snorri is worth more coins than any minion. What stops him being defeated?",
                "He has one spare card. You will need an attack in turn 2 and another in turn 4, and Whisper has " +
                "to stay close.",
                "Play Control Gravity on Wasp, fast travelling, and Shadow Walk on Whisper, onto the spawn point " +
                "next to Snorri.",
            ),
            listOf(
                "Even the smallest attack must be answered. Which of Whisper's cards can reach him now?",
                "Swift Justice has Attack 2 and can target a unit next to you. Snorri has to spend a card on it " +
                "or be defeated.",
                "Play Magnetic Dagger on Wasp, who moves, and Swift Justice on Whisper, attacking Snorri.",
            ),
            listOf(
                "This turn doesn't attack anybody. Which cards do you have to get out of the way, so that the " +
                "right ones are left for turn 4?",
                "Keep Whisper's card that moves and attacks, and Wasp's that can move her.",
                "Play Static Barrier on Wasp and Death Seeker on Whisper.",
            ),
            listOf(
                "Snorri's last card is on the table. Look at his hand. Who acts before him?",
                "Sanguine Path moves Whisper before the attack. Snorri can't defend, and the assist pays as well.",
                "Play Charged Boomerang on Wasp, who moves first, and Sanguine Path on Whisper, moving next to " +
                "Snorri and attacking him.",
            ),
)
