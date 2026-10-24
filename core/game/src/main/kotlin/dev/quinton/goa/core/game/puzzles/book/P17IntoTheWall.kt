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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C17 "Into the Wall" (changed: round 2; Brogan has played Brutal Jab and stands next to
 * Wasp, so Kinetic Repulse must push him away too).
 */
object P17IntoTheWall : Puzzle() {
    override val concept = "C17"

    override val info = PuzzleInfo(
        id = "p17",
        number = 17,
        title = "Into the Wall",
        difficulty = 1,
        theme = "Pushes stopped by obstacles",
        heroes = listOf("wasp", "xargatha", "arien", "brogan"),
        goal = PuzzleGoal.DefeatHero("arien", Deadline.END_OF_TURN, "Defeat Arien this turn."),
        intro = "Round 2, turn 4: play your cards. Arien has played Liquid Leap (initiative 4) and keeps " +
            "Expert Duelist (Defense 6). Brogan has played Brutal Jab (8), his last card. Wasp holds Shock and " +
            "Kinetic Repulse, Xargatha Threatening Slash and Siren's Call.",
        nudges = listOf(
            "Arien must lose his last card and still be next to Xargatha when she acts. Who could spoil that?",
            "A push that runs into a wall costs the victim a card. Kinetic Repulse pushes up to two units, and " +
                "Brutal Jab comes between Wasp and Xargatha.",
            "Play Kinetic Repulse and Threatening Slash, and push both Arien and Brogan.",
        ),
        explanation = "Shock looks right: Arien must spend Expert Duelist to defend it (Defense 6 against 5), " +
            "but he stays two spaces from Xargatha, and the card's own text then makes him immune to her attack " +
            "this turn (p.14). Kinetic Repulse (initiative 10) does the work instead. A push moves the target " +
            "directly away as far as it can, possibly less than the full distance (p.18); terrain is an " +
            "obstacle (p.19), so Arien stops after one space, and a hero stopped by an obstacle discards a card " +
            "(card text; Wasp FAQ). He loses his last card and lands next to Xargatha. But Brogan acts at 8, " +
            "before her 7 (p.7): Brutal Jab would step next to Xargatha and push her away from Arien. The card " +
            "pushes up to two enemy units, so Wasp sends Brogan three spaces south-west as well, too far to " +
            "reach her. Then Threatening Slash meets no defense (p.13). Siren's Call would pull Arien too, but " +
            "at initiative 3 it comes after him, and it is not an attack. Before you spend your push, look at " +
            "what the enemy can still do before your hit.",
        rules = listOf(
            "p.7 Play one card, Check the initiative",
            "p.13 Attacking a hero",
            "p.14 Defense",
            "p.18 Push",
            "p.19 Terrain",
            "Wasp FAQ: Kinetic Repulse",
        ),
    )

    override val playerSeats = setOf("wasp", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        // Arien and Brogan have already played (seats 0 and 1): their cards are known.
        hero("arien", Team.BLUE, Hex(3, -3)) {
            level = 2
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            hand("arien-blue-2a")
            items("arien-blue-2b")
        }
        hero("brogan", Team.BLUE, Hex(3, -2)) {
            played("brogan-blue-1")
            resolved(1, "brogan-silver")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-gold")
            discard("brogan-green-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -3)) {
            level = 2
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-green-1")
            hand("wasp-red-1", "wasp-blue-2b")
            items("wasp-blue-2a")
        }
        hero("xargatha", Team.RED, Hex(1, -2)) {
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-green-1")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-red-1", "xargatha-silver")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        // Moved one space off its spawn point earlier this round (Arien's Noble Blade).
        minion(Team.RED, MinionType.HEAVY, Hex(2, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 2)
    }

    override val solution = listOf(
        card("wasp-blue-2b"),
        card("xargatha-red-1"),
        action(ActionType.PRIMARY),
        hero("arien"),
        hero("brogan"),
        action(ActionType.PRIMARY),
        hero("arien"),
    )

    override val nudgedMoves = solution.take(5)

    override val wrongTries = listOf(
        WrongTry(
            "Shock first: Arien defends with Expert Duelist, but stays out of Xargatha's reach",
            listOf(card("wasp-red-1")),
        ),
        WrongTry(
            "Kinetic Repulse, then Siren's Call: Arien acts at 4 before Xargatha's 3 and the pull is no attack",
            listOf(card("wasp-blue-2b"), card("xargatha-silver")),
        ),
        WrongTry(
            "Push Arien alone: Brogan's Brutal Jab pushes Xargatha away from him first",
            listOf(card("wasp-blue-2b"), card("xargatha-red-1"), action(ActionType.PRIMARY), hero("arien"), decline()),
        ),
        WrongTry(
            "Kinetic Repulse, then Wasp moves instead of pushing",
            listOf(card("wasp-blue-2b"), card("xargatha-red-1"), action(ActionType.MOVEMENT)),
        ),
    )
}
