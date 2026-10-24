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
        intro = "Round 3, turn 4. Min (Death Grenade, 11) acts next; Wasp (Lift Up, 10) acts after him. " +
            "Tigerclaw and Arien have already acted this turn; Tigerclaw's hand is empty.",
        nudges = listOf(
            "Wasp acts after you and will try to pull the Grenade and Tigerclaw apart.",
            "Lift Up reaches only pieces within 2 spaces of Wasp, and keeps them at the same distance from her. " +
                "Where can it put Tigerclaw?",
            "Place the Grenade on the space west of Tigerclaw.",
        ),
        explanation = "End of turn text is applied after every hero has acted (p.17), so Wasp gets her reply " +
            "first. The three spaces next to Tigerclaw that Min can reach are all three spaces from Wasp, out of " +
            "Lift Up's radius 2 (p.9), so she cannot move the Grenade; she must move Tigerclaw instead. Lift Up " +
            "moves a piece 1 space without bringing it closer to or farther from Wasp (card text): the only such " +
            "space is the one south-west of him, and the repeat can only bring him back. That space still touches " +
            "a Grenade west of him, so at the end of turn Min picks Tigerclaw, who has no card to discard and is " +
            "defeated. With the Grenade north-west or north-east of him, that one step breaks the contact. The " +
            "lesson: before you set a trap that springs at the end of turn, work out every reply still to come.",
        rules = listOf("p.9 Radius", "p.17 End of Turn (Active Effect)", "p.18 Place", "p.19 Tokens"),
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
            level = 2
            played("wasp-blue-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            hand("wasp-green-1")
            items("wasp-red-2b")
        }
        hero("tigerclaw", Team.RED, Hex(4, -6)) {
            level = 2
            resolved(1, "tigerclaw-silver")
            resolved(2, "tigerclaw-red-1")
            resolved(3, "tigerclaw-green-2a")
            resolved(4, "tigerclaw-gold")
            discard("tigerclaw-blue-1")
            hand()
            items("tigerclaw-green-2b")
        }
        hero("arien", Team.BLUE, Hex(-1, 0)) {
            level = 2
            resolved(1, "arien-gold")
            resolved(2, "arien-red-2a")
            resolved(3, "arien-green-1")
            resolved(4, "arien-silver")
            hand("arien-blue-1")
            items("arien-red-2b")
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
            "Grenade north-west of Tigerclaw: Lift Up steps him south-west, out of contact",
            listOf(action(ActionType.PRIMARY), space(4, -7)),
        ),
        WrongTry(
            "Grenade north-east of Tigerclaw: Lift Up steps him south-west, out of contact",
            listOf(action(ActionType.PRIMARY), space(5, -7)),
        ),
    )
}
