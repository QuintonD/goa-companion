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
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** Full round 9, mined from self-play (g2974_r5_RED_coins-5; ADR-052, ENCODING.md "Full rounds"). */
object R09WhatToLose : Puzzle() {
    override val concept = "R09"

    override val known = KnownPlays(
        cards = mapOf(
            "garrus" to listOf("garrus-blue-2b", "garrus-green-2b", "garrus-gold", "garrus-red-2b"),
            "dodger" to listOf("dodger-green-1", "dodger-silver", "dodger-gold", "dodger-red-2a"),
        ),
        actions = mapOf(
            "garrus" to mapOf(
                1 to listOf(Choice.Action(ActionType.HOLD)),
                2 to listOf(Choice.Action(ActionType.PRIMARY), Choice.Space(Hex(5, -3))),
                3 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -4))),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(5, -3))),
            ),
            "dodger" to mapOf(
                1 to listOf(Choice.Space(Hex(11, -6)), Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(9, -6))),
                2 to listOf(Choice.Action(ActionType.HOLD)),
                3 to listOf(Choice.Action(ActionType.HOLD)),
                4 to listOf(Choice.Action(ActionType.MOVEMENT), Choice.Space(Hex(6, -4))),
            ),
        ),
    )

    override val info = PuzzleInfo(
        id = "r09",
        number = 9,
        title = "What to Lose",
        difficulty = 2,
        theme = "Keeping the card that wins through a forced discard",
        heroes = listOf("emmitt", "whisper", "garrus", "dodger"),
        goal = PuzzleGoal.GainCoins(5, Deadline.END_OF_ROUND, "Gain 5 coins by the end of the round."),
        intro = "Round 5. You play Emmitt and Whisper for Red. Blue has 2 Life counters left. Garrus pays 4 coins to " +
            "the hero who defeats him and 2 to the other. His best defense in turn 4 will be 5. His Light Pilum " +
            "is going to cost Whisper a card in turn 2, and you choose which.",
        nudges = R09_NUDGES.first(),
        nudgesByTurn = R09_NUDGES,
        explanation =
            "Turn 1: Dodger respawns and moves in, as planned. Emmitt plays Reverse Time and Whisper Shadow Walk." +
                "\n\n" +
                "Turn 2: Lesser Evil's Movement takes Whisper 2 spaces, and Back to the Future places her back again." +
                " Garrus's Light Pilum then makes her discard: Swift Justice goes, and Blood Fury stays." +
                "\n\n" +
                "Turn 3: Garrus moves next to Whisper as planned. Emmitt's Unstable Timeline makes Garrus choose the " +
                "Glitch he swaps with, and Whisper plays Death Seeker. She has one card left now." +
                "\n\n" +
                "Turn 4: Emmitt and Whisper tie, and Emmitt moves first. Blood Fury attacks Garrus at 6. Chilling " +
                "Howl gives 3 and 2 for items, 5, not enough, and he is defeated before Blunt Force resolves (p.13): " +
                "4 coins for Whisper and 2 for Emmitt. Blue spends its last 2 Life counters, too." +
                "\n\n" +
                "The computer at strength 7, playing your heroes, doesn't find it, and neither does strength 10.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.10 Discard a Card",
            "p.14 Defense",
        ),
        known = known.display(
            mapOf(
                "garrus" to mapOf(
                    1 to "Holds.",
                    2 to "Light Pilum: Whisper discards a card, then it moves to the space outlined.",
                    3 to "Moves to the space outlined, next to Whisper.",
                    4 to "Plays Blunt Force.",
                ),
                "dodger" to mapOf(
                    1 to "Respawns, then moves to the space outlined.",
                    2 to "Holds.",
                    3 to "Holds.",
                    4 to "Plays its last card as its skill.",
                ),
            ),
        ),
    )

    override val playerSeats = setOf("emmitt", "whisper")

    override fun PositionBuilder.setUp() {
        round = 5
        tieBreaker = Team.RED
        clearMinions()
        battleZone("lane-1", "lane-1-3")
        wavesLeft("lane-1", 2)
        lifeCounters(Team.RED, 4)
        lifeCounters(Team.BLUE, 2)
        hero("emmitt", Team.RED, Hex(6, -10)) {
            level = 5
            hand("emmitt-gold", "emmitt-silver", "emmitt-red-2a", "emmitt-blue-2a", "emmitt-green-3a")
            items("emmitt-red-2b", "emmitt-green-2a", "emmitt-blue-2b", "emmitt-green-3b")
            deck("emmitt-red-1", "emmitt-green-1", "emmitt-blue-1")
        }
        hero("whisper", Team.RED, Hex(6, -5)) {
            level = 4
            coins = 2
            hand("whisper-gold", "whisper-silver", "whisper-red-2b", "whisper-green-2a", "whisper-blue-2b")
            items("whisper-red-2a", "whisper-green-2b", "whisper-blue-2a")
            deck("whisper-red-1", "whisper-green-1", "whisper-blue-1")
        }
        hero("garrus", Team.BLUE, Hex(4, -3)) {
            level = 4
            coins = 1
            hand("garrus-gold", "garrus-silver", "garrus-red-2b", "garrus-green-2b", "garrus-blue-2b")
            items("garrus-red-2a", "garrus-green-2a", "garrus-blue-2a")
            deck("garrus-red-1", "garrus-green-1", "garrus-blue-1")
        }
        hero("dodger", Team.BLUE, null) {
            level = 2
            coins = 2
            hand("dodger-gold", "dodger-silver", "dodger-green-1", "dodger-blue-1", "dodger-red-2a")
            items("dodger-red-2b")
            deck("dodger-red-1")
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
    }

    override val solution = listOf(
        card("emmitt-gold"),
        card("whisper-green-2a"),
        action(ActionType.HOLD),
        action(ActionType.HOLD),
        card("emmitt-green-3a"),
        card("whisper-blue-2b"),
        action(ActionType.MOVEMENT),
        space(7, -7),
        action(ActionType.PRIMARY),
        card("whisper-gold"),
        card("emmitt-silver"),
        card("whisper-silver"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        space(9, -12),
        space(5, -11),
        hero("garrus"),
        card("emmitt-blue-2a"),
        card("whisper-red-2b"),
        hero("emmitt"),
        action(ActionType.MOVEMENT),
        space(8, -12),
        action(ActionType.PRIMARY),
    )

    override val wrongTries = listOf(
        WrongTry(
            "The computer at strength 7 on your heroes",
            listOf(
                card("emmitt-green-3a"),
                card("whisper-silver"),
                action(ActionType.HOLD),
                action(ActionType.MOVEMENT),
                space(7, -11),
                card("emmitt-gold"),
                card("whisper-blue-2b"),
                action(ActionType.PRIMARY),
                action(ActionType.MOVEMENT),
                space(6, -6),
                card("emmitt-silver"),
                card("whisper-gold"),
                action(ActionType.PRIMARY),
                space(8, -11),
                space(7, -8),
                hero("dodger"),
                action(ActionType.PRIMARY),
                hero("garrus"),
                card("emmitt-blue-2a"),
                card("whisper-red-2b"),
                hero("whisper"),
                action(ActionType.MOVEMENT),
                space(8, -7),
                action(ActionType.MOVEMENT),
                decline(),
            ),
        ),
    )
}

private val R09_NUDGES = listOf(
    listOf(
        "Which of your cards beats Garrus's best defense? That one has to last until turn 4.",
        "Blood Fury is Attack 6. Everything else this round serves it.",
        "Play Reverse Time on Emmitt and Shadow Walk on Whisper.",
    ),
    listOf(
        "Whisper is going to lose a card this turn. Be sure it is not the one that matters.",
        "Discard Swift Justice. And Lesser Evil's Movement starts her walking.",
        "Play Back to the Future on Emmitt and Lesser Evil on Whisper, who moves; discard Swift Justice.",
    ),
    listOf(
        "Garrus walks next to Whisper this turn. Nothing you do has to stop him.",
        "Keep Time Trap and Blood Fury for last.",
        "Play Unstable Timeline on Emmitt and Death Seeker on Whisper.",
    ),
    listOf(
        "Garrus stands next to Whisper. Which of you goes first on the tie doesn't matter to him.",
        "Blood Fury acts before his Blunt Force, and Attack 6 beats Chilling Howl's 5.",
        "Play Time Trap on Emmitt and Blood Fury on Whisper, attacking Garrus.",
    ),
)
