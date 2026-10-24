package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C53 "Death Grenade". */
object P53DeathGrenade : Puzzle() {
    override val concept = "C53"

    override val info = PuzzleInfo(
        id = "p53",
        number = 53,
        title = "Death Grenade",
        difficulty = 3,
        theme = "End of turn, the opponent's reply",
        heroes = listOf("min", "wasp", "tigerclaw", "arien"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 3, turn 4. Min (Death Grenade, 11) acts next; Wasp (Lift Up, 10) acts after her. " +
            "Tigerclaw has already acted this turn and has no cards left. Arien is far away.",
        nudges = listOf(
            "Wasp acts after you and will try to pull the Grenade and Tigerclaw apart.",
            "Lift Up reaches only pieces within 2 spaces of Wasp, and keeps them at the same distance from her. " +
                "Where can it put Tigerclaw?",
            "Place the Grenade at (3,-6), west of Tigerclaw.",
        ),
        explanation = "End of turn text is applied after every hero has acted (p.17), so Wasp gets her reply " +
            "first. The three spaces next to Tigerclaw that Min can reach are all three spaces from Wasp, out of " +
            "Lift Up's radius 2 (p.19 Radius), so she cannot move the Grenade; she must move Tigerclaw instead. " +
            "Lift Up moves a piece 1 space without bringing it closer to or farther from Wasp (card text): the " +
            "only such space is (3,-5), and the repeat can only bring him back. (3,-5) is still next to (3,-6), so " +
            "at the end of turn Tigerclaw, with no card to discard, is defeated. With the Grenade on (4,-7) or " +
            "(5,-7), that one step breaks the contact.",
        rules = listOf("p.17 End of Turn (Active Effect)", "p.18 Place", "p.19 Radius, Tokens"),
    )

    override val playerSeats = setOf("min")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("min", Team.BLUE, Hex(5, -8)) {
            level = 2
            played("min-blue-2b")
            resolved(1, "min-gold")
            resolved(2, "min-silver")
            resolved(3, "min-red-1")
            hand("min-green-1")
            items("min-blue-2a")
        }
        hero("wasp", Team.RED, Hex(4, -4)) {
            played("wasp-blue-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-green-1")
        }
        hero("tigerclaw", Team.RED, Hex(4, -6)) {
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-red-1")
            resolved(4, "tigerclaw-green-1")
            discard("tigerclaw-blue-1")
            hand()
        }
        hero("arien", Team.BLUE, Hex(-1, 0)) {
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-red-1")
            resolved(4, "arien-green-1")
            hand("arien-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "min")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(3, -6), hero("tigerclaw"))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Grenade at (4,-7): Lift Up steps Tigerclaw to (3,-5), out of contact",
            listOf(action(ActionType.PRIMARY), space(4, -7)),
        ),
        WrongTry(
            "Grenade at (5,-7): Lift Up steps Tigerclaw to (3,-5), out of contact",
            listOf(action(ActionType.PRIMARY), space(5, -7)),
        ),
    )
}
