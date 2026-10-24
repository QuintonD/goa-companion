package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
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
 * CONCEPTS.md C02 "Worth Two Counters" (changed: Wasp holds a real defense card besides Deflect
 * Projectiles, so Arien's Noble Blade must strip it and clear Garrus's way first; see "Encoded as").
 */
object P02WorthTwoCounters : Puzzle() {
    override val concept = "C02"

    override val info = PuzzleInfo(
        id = "p02",
        number = 2,
        title = "Worth Two Counters",
        difficulty = 3,
        theme = "Life counters by tier, Block cards",
        heroes = listOf("arien", "garrus", "wasp", "brogan"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 3. Wasp has already acted. Arien has played Noble Blade (11) and acts next, " +
            "then Garrus with Chase (9); Brogan acts last. Wasp (level 4) holds Deflect Projectiles and " +
            "Control Gravity and has a Defense item; Brogan (level 3) has no cards in hand. Garrus has a card " +
            "in his discard. Red has two Life counters left.",
        nudges = listOf(
            "Red has two Life counters left: which Red hero is worth both, and what can she still defend with?",
            "Deflect Projectiles only blocks a ranged attack. Make Wasp spend her other card first, and find " +
                "Garrus a way to her.",
            "Arien attacks Wasp and, before the attack, moves the Red minion south-east of her one space east; " +
                "then Garrus chases her.",
        ),
        explanation = "A defeated hero's team spends Life counters equal to that hero's lowest card tier, not " +
            "one per hero (p.13): Wasp, level 4, has no tier I card left and costs Red both counters; Brogan, " +
            "level 3, still has a tier I card and costs one. Wasp holds two cards. Deflect Projectiles is a " +
            "Block card: it defends only against a ranged attack (p.14), and neither Noble Blade nor Chase is " +
            "ranged, so against Arien's 4 she must discard Control Gravity (5, +1 for her item, +2 for her " +
            "minions) to survive. Arien's \"Before the attack\" also moves another unit next to the target one " +
            "space: moving the Red minion south-east of her one space east opens that space, and nothing " +
            "else does, since every other space beside her is taken and too far. Garrus has a card in his " +
            "discard, so Chase lets him move up to 2 spaces first (card text): around the Blue minion into " +
            "the opened space, and Wasp, left with only Deflect Projectiles, is defeated. Defeating Brogan " +
            "with Arien wins a counter and three coins, but leaves Wasp both cards; defeating the minion opens " +
            "the space but leaves her Control Gravity.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated, the level and tier table",
            "p.14 Defense action, Block",
            "p.11 Movement in the card text",
        ),
    )

    override val playerSeats = setOf("arien", "garrus")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("arien", Team.BLUE, Hex(2, -2)) {
            level = 2
            played("arien-gold")
            resolved(1, "arien-red-2a")
            resolved(2, "arien-silver")
            items("arien-red-2b")
            hand("arien-green-1", "arien-blue-1")
        }
        hero("garrus", Team.BLUE, Hex(2, 0)) {
            level = 2
            played("garrus-red-2a")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-blue-1")
            discard("garrus-green-1")
            items("garrus-red-2b")
            hand("garrus-silver")
        }
        hero("wasp", Team.RED, Hex(3, -3)) {
            level = 4
            coins = 1
            resolved(1, "wasp-silver")
            resolved(2, "wasp-red-2a")
            resolved(3, "wasp-gold")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
            hand("wasp-green-2a", "wasp-blue-2a")
        }
        hero("brogan", Team.RED, Hex(2, -3)) {
            level = 3
            played("brogan-blue-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            discard("brogan-red-2a", "brogan-green-2a")
            items("brogan-red-2b", "brogan-green-2b")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(3, -2))
        minion(Team.RED, MinionType.MELEE, Hex(4, -3))
        minion(Team.RED, MinionType.MELEE, Hex(4, -4))
        minion(Team.RED, MinionType.RANGED, Hex(3, -4))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "arien")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("wasp"),
        yes(),
        unitAt(3, -2),
        space(4, -2),
        action(ActionType.PRIMARY),
        space(3, -2),
    )

    override val nudgedMoves = solution.take(5)

    override val wrongTries = listOf(
        WrongTry(
            "Defeat Brogan with Arien: one counter and three coins, but Wasp keeps both cards",
            listOf(action(ActionType.PRIMARY), hero("brogan")),
        ),
        WrongTry(
            "Defeat the minion beside Wasp: the space opens, but she keeps Control Gravity",
            listOf(action(ActionType.PRIMARY), unitAt(3, -2)),
        ),
        WrongTry(
            "Strip Wasp's card but move the minion the other way: it blocks Garrus's path",
            listOf(action(ActionType.PRIMARY), hero("wasp"), yes(), unitAt(3, -2), space(3, -1)),
        ),
    )
}
