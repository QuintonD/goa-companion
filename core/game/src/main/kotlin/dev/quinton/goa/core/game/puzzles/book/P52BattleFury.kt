package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C52 "Battle Fury". */
object P52BattleFury : Puzzle() {
    override val concept = "C52"

    override val info = PuzzleInfo(
        id = "p52",
        number = 52,
        title = "Battle Fury",
        difficulty = 1,
        theme = "An Ultimate that turns a discard into an action",
        heroes = listOf("garrus", "wasp", "tigerclaw", "arien"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 6, turn 4. Garrus (level 8, Battle Fury unlocked) has played Chilling Howl (13) and acts " +
            "next; his discard pile is empty. Tigerclaw has played Sidestep (12 with his Initiative item) and " +
            "keeps Backstab. Wasp and Arien have already acted.",
        nudges = listOf(
            "Chilling Howl can throw away one of the cards Garrus has already used, and Battle Fury plays it again.",
            "Work out each card's Attack total, items and card text included, against Tigerclaw's whole Defense.",
            "Discard Angry Strike with Chilling Howl, then perform it on Tigerclaw.",
        ),
        explanation = "Chilling Howl lets Garrus discard one of his resolved cards, and Battle Fury then lets him " +
            "perform that card's primary action (Ultimate text; p.16). Acting at 13, he goes before Tigerclaw's " +
            "12 (p.7). Tigerclaw defends with Backstab: 5 + 1 for his Defense item + 1 for the friendly melee " +
            "minion beside him = 7 (p.14). Hunt Down, the obvious charge, hits with 4 + 3 Attack items = 7 (p.16 " +
            "Item Bonus): not enough, a tie defends. Angry Strike adds +1 for every card in Garrus's discard, and " +
            "once Chilling Howl has discarded it, it counts itself: 4 + 1 + 3 = 8, and Tigerclaw falls (p.13). " +
            "Terrify only moves enemies away. When a card's text counts the discard pile, count it at the moment the " +
            "attack is made.",
        rules = listOf(
            "p.7 Check the Initiative",
            "p.10 Discard a Card",
            "p.13 Attacking a Hero",
            "p.14 Defense Total, Minion Defense Modifiers",
            "p.16 Item Bonus, The Ultimate",
        ),
    )

    override val playerSeats = setOf("garrus")

    override fun PositionBuilder.setUp() {
        round = 6
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        wavesLeft("lane-1", 1)
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        hero("garrus", Team.BLUE, Hex(0, -4)) {
            ultimate()
            played("garrus-silver")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-3a")
            resolved(3, "garrus-blue-3a")
            hand("garrus-green-3a")
            items("garrus-red-2a", "garrus-red-3b", "garrus-green-2b", "garrus-green-3b")
            items("garrus-blue-2b", "garrus-blue-3b")
        }
        // Wasp, Garrus and Arien all showed 13: the Red side of the coin let Wasp act first and was flipped;
        // Blue then chose Arien before Garrus.
        hero("wasp", Team.RED, Hex(0, -5)) {
            level = 4
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-green-2a")
            resolved(4, "wasp-silver")
            hand("wasp-blue-2a")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
        }
        hero("tigerclaw", Team.RED, Hex(1, -5)) {
            level = 4
            played("tigerclaw-blue-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-2b")
            hand("tigerclaw-red-2b")
            items("tigerclaw-red-2a", "tigerclaw-green-2a", "tigerclaw-blue-2b")
        }
        hero("arien", Team.BLUE, Hex(-1, -1)) {
            level = 4
            resolved(1, "arien-gold")
            resolved(2, "arien-red-2a")
            resolved(3, "arien-green-2a")
            resolved(4, "arien-silver")
            hand("arien-blue-2a")
            items("arien-red-2b", "arien-green-2b", "arien-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "garrus")
    }

    override val solution =
        listOf(action(ActionType.PRIMARY), card("garrus-gold"), yes(), hero("tigerclaw"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Discard Hunt Down and charge: 7 against Backstab's 7, a tie that defends",
            listOf(action(ActionType.PRIMARY), card("garrus-red-3a")),
        ),
        WrongTry(
            "Discard Terrify: it moves enemies but never attacks",
            listOf(action(ActionType.PRIMARY), card("garrus-blue-3a")),
        ),
        WrongTry(
            "Discard nothing: Tigerclaw acts at 12 and walks away",
            listOf(action(ActionType.PRIMARY), decline()),
        ),
    )
}
