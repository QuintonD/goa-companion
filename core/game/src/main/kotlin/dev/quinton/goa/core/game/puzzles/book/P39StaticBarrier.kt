package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C39 "Static Barrier" (changed: starts at the card choice; the Barrier also shields Tigerclaw). */
object P39StaticBarrier : Puzzle() {
    override val concept = "C39"

    override val info = PuzzleInfo(
        id = "p39",
        number = 39,
        title = "Static Barrier",
        difficulty = 1,
        theme = "Active effects, Block, choosing your card",
        heroes = listOf("wasp", "tigerclaw", "brogan", "swift"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 2, turn 2. Swift (Snipe, with an Attack item) and Brogan (Mad Dash) have played their cards, " +
            "and so has Tigerclaw (Light-Fingered, 2). Choose Wasp's card: she holds Magnetic Dagger, Static " +
            "Barrier, Shock and Stop Projectiles. Tigerclaw holds Blink Strike, Blend Into Shadows and Hit and Run.",
        nudges = listOf(
            "Two attacks are coming: Snipe at Wasp, and Mad Dash at whichever of your heroes Brogan can reach. " +
                "Tigerclaw's cards can't stop it.",
            "Static Barrier makes the spaces around Wasp obstacles for an enemy acting from outside them. Where " +
                "would Brogan's runs have to end?",
            "Play Static Barrier, perform it, then block Snipe with Stop Projectiles.",
        ),
        explanation = "Snipe needs a target exactly four spaces away in a straight line (p.19): only Wasp. Mad Dash " +
            "must move exactly two spaces in a straight line to a space next to an enemy unit (card text, p.11): " +
            "Brogan has two runs, one ending beside both of your heroes and one beside Tigerclaw, whose cards " +
            "can't stop Attack 6. Running with Shock or Magnetic Dagger leaves Tigerclaw to Brogan: Wasp could " +
            "stand in one run, never in both. Static Barrier acts first at 13; while Brogan, outside its radius, " +
            "performs his action, every space within 2 of Wasp is an obstacle (card text; p.18 Obstacle), so " +
            "neither run can be made and there is no attack. The Barrier doesn't stop attacks (Wasp FAQ), so Snipe " +
            "still comes: Stop Projectiles blocks a ranged attack (p.14 Block), while Shock would defend at 6 - 1 " +
            "for the Blue ranged minion within 2 (p.14) = 5 against 6. Played as your card, Stop Projectiles acts " +
            "last and can't block: you defend only by discarding from your hand (p.14).",
        rules = listOf(
            "p.11 Movement in the Card Text",
            "p.14 Defense, Block, Minion Defense Modifiers",
            "p.17 Active Effects",
            "p.18 Obstacle",
            "p.19 Straight Line",
            "Wasp FAQ: Static Barrier",
        ),
    )

    override val playerSeats = setOf("wasp", "tigerclaw")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 2
        tieBreaker = Team.RED
        clearMinions()
        hero("swift", Team.BLUE, Hex(3, -5)) {
            level = 2
            played("swift-red-1")
            resolved(1, "swift-blue-1")
            hand("swift-gold", "swift-silver", "swift-green-2b")
            items("swift-green-2a")
        }
        hero("brogan", Team.BLUE, Hex(2, -6)) {
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            hand("brogan-silver", "brogan-green-1", "brogan-blue-1")
        }
        hero("tigerclaw", Team.RED, Hex(0, -5)) {
            played("tigerclaw-green-1")
            resolved(1, "tigerclaw-blue-1")
            hand("tigerclaw-gold", "tigerclaw-silver", "tigerclaw-red-1")
        }
        hero("wasp", Team.RED, Hex(-1, -5)) {
            resolved(1, "wasp-blue-1")
            hand("wasp-gold", "wasp-silver", "wasp-red-1", "wasp-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(-2, 1))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.RANGED, Hex(-1, -3))
        minion(Team.BLUE, MinionType.HEAVY, Hex(5, -8))
        phase = PositionPhase.PlayCards(fromSeat = 3)
    }

    override val solution = listOf(
        card("wasp-silver"),
        action(ActionType.PRIMARY),
        card("wasp-green-1"),
        action(ActionType.HOLD),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry("Magnetic Dagger's step: Tigerclaw is left to Brogan", listOf(card("wasp-gold"))),
        WrongTry("Shock to run away: one of Brogan's runs still reaches Tigerclaw", listOf(card("wasp-red-1"))),
        WrongTry(
            "Play Stop Projectiles: nothing stops Brogan, and Wasp can't block Snipe with a played card",
            listOf(card("wasp-green-1")),
        ),
        WrongTry("Static Barrier, but hold", listOf(card("wasp-silver"), action(ActionType.HOLD))),
        WrongTry(
            "Static Barrier, then defend with Shock: 5 against 6",
            listOf(card("wasp-silver"), action(ActionType.PRIMARY), card("wasp-red-1")),
        ),
    )
}
