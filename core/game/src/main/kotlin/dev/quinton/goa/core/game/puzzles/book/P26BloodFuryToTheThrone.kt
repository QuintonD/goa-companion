package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C26 "Blood Fury to the Throne" (changed: Tigerclaw plays too, and Blue has three minions,
 * one out of Whisper's reach; the only enemy discard is Xargatha's, so she must survive the first blow).
 */
object P26BloodFuryToTheThrone : Puzzle() {
    override val concept = "C26"

    override val info = PuzzleInfo(
        id = "p26",
        number = 26,
        title = "Blood Fury to the Throne",
        difficulty = 3,
        theme = "Pushing the lane into the Throne; heavy immunity; a repeat that needs the enemy's discard",
        heroes = listOf("whisper", "tigerclaw", "xargatha", "arien"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4: play your cards. Red has pushed once: the Battle Zone is the last Zone before " +
            "Blue's Throne. Whisper holds Blood Fury (10 with his item) and Lesser Evil, Tigerclaw Blink Strike " +
            "and Combat Reflexes. Xargatha has played Charm (5), her last card; she has one card in the " +
            "discard. Arien has played Rogue Wave (9 with his item) and keeps one card, with nothing in the " +
            "discard.",
        nudges = listOf(
            "Three Blue minions guard the Throne Zone, and the heavy one is Immune while the others stand. " +
                "Who can reach each of them, and when?",
            "Blood Fury repeats only if an enemy hero in radius has a card in the discard after the attack. " +
                "Who has one now, and what happens to it if that hero falls?",
            "Play Blink Strike and Blood Fury. Tigerclaw leaps through the ranged minion; Whisper strikes the " +
                "melee minion, then repeats on the heavy.",
        ),
        explanation = "Defeating Blue's last minion in the Battle Zone pushes the lane at once, and with Blue's " +
            "Throne as the next Zone, Red wins the game (p.8). The heavy minion is Immune while a friendly " +
            "non-heavy minion is in the Battle Zone (p.13), so the melee and ranged minions must fall first, " +
            "and the ranged one is out of Whisper's reach. Tigerclaw can take it, but only before Whisper: " +
            "Combat Reflexes (9) would come after Blood Fury (10), so it must be Blink Strike (13), leaping " +
            "through the minion (card text; Tigerclaw FAQ). Blink Strike through Xargatha looks free, as she " +
            "has no card, but Blood Fury may repeat only if an enemy hero in radius has a card in the discard " +
            "(card text, p.10 Repeat), and hers is the only one: a defeated hero leaves the board (p.13). " +
            "Whisper too must leave her standing: the melee minion first, then the repeat on the heavy, no " +
            "longer Immune. Count the minions before the heroes, and keep alive what your card needs.",
        rules = listOf(
            "p.7 Check the initiative",
            "p.8 Push the Lane, Triggering a Push",
            "p.10 Repeat",
            "p.13 Heavy Minion Immunity, When a hero is defeated",
            "Tigerclaw FAQ: Blink Strike",
        ),
    )

    override val playerSeats = setOf("whisper", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        wavesLeft("lane-1", 2)
        battleZone("lane-1", "lane-1-3")
        // Seated first with their cards already played: the player's choices come last.
        hero("xargatha", Team.BLUE, Hex(7, -7)) {
            level = 2
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-2a")
            discard("xargatha-red-1")
            hand()
            items("xargatha-blue-2b")
        }
        hero("arien", Team.BLUE, Hex(5, -4)) {
            level = 3
            played("arien-red-2b")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            hand("arien-blue-2a")
            items("arien-red-2a", "arien-blue-2b")
        }
        hero("whisper", Team.RED, Hex(8, -7)) {
            level = 3
            resolved(1, "whisper-gold")
            resolved(2, "whisper-silver")
            resolved(3, "whisper-green-1")
            hand("whisper-red-2b", "whisper-blue-2b")
            items("whisper-red-2a", "whisper-blue-2a")
        }
        hero("tigerclaw", Team.RED, Hex(7, -6)) {
            level = 3
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-blue-2a")
            hand("tigerclaw-gold", "tigerclaw-red-2a")
            items("tigerclaw-red-2b", "tigerclaw-blue-2b")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(8, -6))
        minion(Team.BLUE, MinionType.RANGED, Hex(7, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(9, -8))
        minion(Team.RED, MinionType.HEAVY, Hex(8, -8))
        phase = PositionPhase.PlayCards(fromSeat = 2)
    }

    override val solution = listOf(
        card("whisper-red-2b"),
        card("tigerclaw-gold"),
        action(ActionType.PRIMARY),
        space(7, -4),
        action(ActionType.PRIMARY),
        unitAt(8, -6),
        yes(),
        unitAt(9, -8),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Combat Reflexes: Tigerclaw at 9 comes after Whisper, who cannot reach the ranged minion",
            listOf(card("whisper-red-2b"), card("tigerclaw-red-2a")),
        ),
        WrongTry(
            "Blink Strike through Xargatha: a free kill, but it takes away the only discard Blood Fury needs",
            listOf(card("whisper-red-2b"), card("tigerclaw-gold"), action(ActionType.PRIMARY), space(7, -8)),
        ),
        WrongTry(
            "Blink Strike through the melee minion: the ranged one stays out of Whisper's reach",
            listOf(card("whisper-red-2b"), card("tigerclaw-gold"), action(ActionType.PRIMARY), space(9, -6)),
        ),
        WrongTry(
            "Blood Fury on Xargatha first: she falls, and with her the only discard",
            listOf(
                card("whisper-red-2b"),
                card("tigerclaw-gold"),
                action(ActionType.PRIMARY),
                space(7, -4),
                action(ActionType.PRIMARY),
                hero("xargatha"),
            ),
        ),
    )
}
