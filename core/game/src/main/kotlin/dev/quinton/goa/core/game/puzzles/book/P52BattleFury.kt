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
import dev.quinton.goa.core.game.puzzles.Moves.space
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
        difficulty = 3,
        theme = "An Ultimate that turns a discard into an action",
        heroes = listOf("garrus", "tigerclaw", "wasp", "arien"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 4, turn 3. Garrus (level 8, Battle Fury unlocked) has played Chilling Howl and acts " +
            "first. He resolved Angry Strike and Hunt Down earlier this round; his discard pile is empty. " +
            "Tigerclaw has played Sidestep (12 with his Initiative item) and keeps Backstab.",
        nudges = listOf(
            "Your Ultimate can give you a second action. What sets it off?",
            "Chilling Howl lets you throw away a card you have already used.",
            "Discard Hunt Down with Chilling Howl, then perform it.",
        ),
        explanation = "Chilling Howl's first sentence lets Garrus discard one of his resolved cards, and Battle " +
            "Fury then lets him perform that card's primary action (Ultimate text; p.16). Angry Strike would " +
            "need an adjacent target, and there is none. Hunt Down's first bullet needs a card in the " +
            "discard: Hunt Down itself is there now, so Garrus moves up to 3 spaces to Tigerclaw and attacks " +
            "with 4 + 3 Attack items = 7 (p.16 Item Bonus). Backstab defends with 5 + 1 = 6, not enough " +
            "(p.14). Acting at 14, Garrus goes before Tigerclaw's 12 (p.7); skip the discard and Tigerclaw " +
            "walks away.",
        rules = listOf(
            "p.7 Check the Initiative",
            "p.10 Discard a Card",
            "p.14 Defense",
            "p.16 Item Bonus, The Ultimate",
        ),
    )

    override val playerSeats = setOf("garrus")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.BLUE
        clearMinions()
        hero("garrus", Team.BLUE, Hex(0, -4)) {
            ultimate()
            played("garrus-silver")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-3a")
            hand("garrus-green-3a", "garrus-blue-3a")
            items("garrus-red-2b", "garrus-red-3b", "garrus-green-2b", "garrus-green-3b")
            items("garrus-blue-2b", "garrus-blue-3b")
        }
        hero("tigerclaw", Team.RED, Hex(3, -6)) {
            level = 3
            played("tigerclaw-blue-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-green-1")
            discard("tigerclaw-silver")
            hand("tigerclaw-red-2b")
            items("tigerclaw-red-2a", "tigerclaw-blue-2b")
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-green-1")
            hand("wasp-red-1", "wasp-blue-1")
        }
        hero("arien", Team.BLUE, Hex(-1, -1)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            hand("arien-red-1", "arien-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "garrus")
    }

    // Any of the four spaces next to Tigerclaw wins: (3,-7), (2,-6), (2,-5), (3,-5).
    override val solution = listOf(action(ActionType.PRIMARY), card("garrus-red-3a"), yes(), space(2, -5))

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Discard Angry Strike: it has no adjacent target",
            listOf(action(ActionType.PRIMARY), card("garrus-gold")),
        ),
        WrongTry(
            "Discard nothing: Tigerclaw acts at 12 and escapes",
            listOf(action(ActionType.PRIMARY), decline()),
        ),
        WrongTry(
            "Discard Hunt Down but don't perform it",
            listOf(action(ActionType.PRIMARY), card("garrus-red-3a"), decline()),
        ),
        WrongTry(
            "Perform Hunt Down but stop short of Tigerclaw",
            listOf(action(ActionType.PRIMARY), card("garrus-red-3a"), yes(), space(1, -5)),
        ),
    )
}
