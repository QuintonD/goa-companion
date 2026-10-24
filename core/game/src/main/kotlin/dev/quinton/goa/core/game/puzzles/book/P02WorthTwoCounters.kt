package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C02 "Worth Two Counters". */
object P02WorthTwoCounters : Puzzle() {
    override val concept = "C02"

    override val info = PuzzleInfo(
        id = "p02",
        number = 2,
        title = "Worth Two Counters",
        difficulty = 1,
        theme = "Life counters by tier, Block cards",
        heroes = listOf("garrus", "arien", "brogan", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. Garrus has played Chase and acts next; he has a card in his discard. " +
            "Brogan (level 3) has no cards left in hand. Wasp (level 4) has already acted and holds only " +
            "Deflect Projectiles. Red has two Life counters left.",
        nudges = listOf(
            "Red has two Life counters left: which Red hero is worth both?",
            "Read Wasp's last card: what kind of attack does it stop?",
            "Use Chase's first option: move two spaces to stand next to Wasp, and attack her.",
        ),
        explanation = "A defeated hero's team spends Life counters equal to that hero's lowest card tier, " +
            "not one per hero (p.13): Wasp is level 4, her lowest tier is II, so she costs Red both counters, " +
            "while Brogan (level 3, a tier I card left) costs only one. Chase's first option lets Garrus move " +
            "up to 2 spaces before the attack because he has a card in his discard (card text), which puts " +
            "him beside Wasp. A Block card defends only when its condition is met (p.14): Deflect " +
            "Projectiles blocks ranged attacks, and Chase is not ranged, so Wasp cannot defend and Red is out " +
            "of Life counters. Hitting the defenceless Brogan next to you feels safe but leaves Red one counter.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated, the level and tier table",
            "p.14 Block",
        ),
    )

    override val playerSeats = setOf("garrus")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("garrus", Team.BLUE, Hex(0, -4)) {
            level = 2
            played("garrus-red-2a")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-blue-1")
            discard("garrus-green-1")
            items("garrus-red-2b")
            hand()
        }
        hero("arien", Team.BLUE, Hex(-1, -1)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        hero("brogan", Team.RED, Hex(-1, -4)) {
            level = 3
            coins = 3
            played("brogan-blue-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-red-2a")
            resolved(3, "brogan-green-2b")
            discard("brogan-silver")
            items("brogan-red-2b", "brogan-green-2a")
            hand()
        }
        hero("wasp", Team.RED, Hex(3, -6)) {
            level = 4
            resolved(1, "wasp-red-2a")
            resolved(2, "wasp-blue-2a")
            resolved(3, "wasp-silver")
            resolved(4, "wasp-gold")
            items("wasp-red-2b", "wasp-blue-2b", "wasp-green-2b")
            hand("wasp-green-2a")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "garrus")
    }

    override val solution = listOf(action(ActionType.PRIMARY), option(0), space(2, -5))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Chase's second option: only Brogan is adjacent, and his lowest tier is I, so Red keeps a counter",
            listOf(action(ActionType.PRIMARY), option(1)),
        ),
        WrongTry(
            "Chase's first option without moving: Brogan is still the only hero next to Garrus",
            listOf(action(ActionType.PRIMARY), option(0), decline()),
        ),
    )
}
