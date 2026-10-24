package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C17 "Into the Wall". */
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
        intro = "Round 3, turn 4: play your cards. Arien has played Liquid Leap (initiative 4) and keeps " +
            "Expert Duelist (Defense 6). Wasp holds Shock and Kinetic Repulse, Xargatha Threatening Slash " +
            "and Siren's Call. Brogan has no cards.",
        nudges = listOf(
            "Taking Arien's last card is not enough: someone must still hit him afterwards.",
            "A push that runs into a wall costs the victim a card. Where would Arien stop?",
            "Play Kinetic Repulse with Wasp and Threatening Slash with Xargatha.",
        ),
        explanation = "Shock looks right: Arien must spend Expert Duelist to defend it (Defense 6 against 5), " +
            "but he stays two spaces from Xargatha, and Threatening Slash only hits adjacent units (and Expert " +
            "Duelist's own text would make him immune to her attack this turn anyway, p.14). Kinetic " +
            "Repulse (initiative 10) does both jobs. A push moves the target directly away as far as it can, " +
            "possibly less than the full distance (p.18); terrain is an obstacle (p.19), so Arien stops after " +
            "one space, and a hero stopped by an obstacle discards a card (card text; Wasp FAQ). He loses his " +
            "last card and lands next to Xargatha, who acts at 7 before his 4 (p.7): Threatening Slash meets " +
            "no defense (p.13). Siren's Call would pull him too, but at initiative 3 it comes after him, and " +
            "it is not an attack.",
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
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        // Arien has already played (seat 0): his last card is known, as is the one he keeps.
        hero("arien", Team.BLUE, Hex(3, -3)) {
            level = 2
            played("arien-green-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            hand("arien-blue-2a")
            items("arien-blue-2b")
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
        hero("brogan", Team.BLUE, Hex(0, 0)) {
            resolved(1, "brogan-silver")
            resolved(2, "brogan-red-1")
            resolved(3, "brogan-blue-1")
            discard("brogan-gold", "brogan-green-1")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 1)
    }

    override val solution = listOf(
        card("wasp-blue-2b"),
        card("xargatha-red-1"),
        action(ActionType.PRIMARY),
        hero("arien"),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

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
            "Kinetic Repulse, then Wasp moves instead of pushing",
            listOf(card("wasp-blue-2b"), card("xargatha-red-1"), action(ActionType.MOVEMENT)),
        ),
    )
}
