package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C24 "Reverse Time" (changed: Tigerclaw's forced card is Hit and Run (9) and he starts
 * away from the Tree; Wuk holds Tree Slam (11) as the tempting "faster" card, and Emmitt's Time Walk
 * must bring Tigerclaw next to the Tree before Wuk's Nature's Protector).
 */
object P24ReverseTime : Puzzle() {
    override val concept = "C24"

    override val info = PuzzleInfo(
        id = "p24",
        number = 24,
        title = "Reverse Time",
        difficulty = 2,
        theme = "Reversed initiative",
        heroes = listOf("wuk", "emmitt", "tigerclaw", "dodger"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 2, turn 4: play your cards. Emmitt resolved Reverse Time last turn, so this turn heroes " +
            "with lower initiative act first. Wuk holds Tree Slam (11) and Nature's Protector (7), Emmitt " +
            "Time Walk (6) and Time Snare (8). Tigerclaw has played Hit and Run (9), his last card, and has " +
            "not moved since last turn; Dodger has no cards.",
        nudges = listOf(
            "Who acts first this turn, really? Lower initiative goes first.",
            "Tree Slam can reach Tigerclaw now, but at 11 it comes after his 9. Nature's Protector at 7 comes " +
                "before him, if he stands next to a Tree.",
            "Play Nature's Protector with Wuk and Time Walk with Emmitt.",
        ),
        explanation = "Reverse Time is a Next turn effect, in force for this whole turn (p.18): lower " +
            "initiative acts first (card text, p.7). Tree Slam can hit Tigerclaw from where he stands, but " +
            "at 11 it would come after his Hit and Run at 9, and he would walk away. Nature's Protector at 7 " +
            "comes before him, but it needs a unit next to a Tree token. Time Walk at 6 comes earlier still: " +
            "Tigerclaw has not moved since last turn, so Emmitt can move him 2 spaces in a straight line, and " +
            "only the run to the south-west ends next to the Tree, within Wuk's range. He has no card to " +
            "defend with (p.13).",
        rules = listOf("p.7 Play one card, Check the initiative", "p.13 Attacking a hero", "p.18 Next turn"),
    )

    override val playerSeats = setOf("wuk", "emmitt")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        // Seated first: Tigerclaw's last card is already played, Dodger has none.
        hero("tigerclaw", Team.RED, Hex(2, -5)) {
            played("tigerclaw-red-1")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-silver")
            resolved(3, "tigerclaw-green-1")
            discard("tigerclaw-blue-1")
            hand()
        }
        hero("dodger", Team.RED, Hex(4, -2)) {
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-silver")
            discard("dodger-green-1", "dodger-blue-1")
            hand()
        }
        hero("wuk", Team.BLUE, Hex(0, -5)) {
            resolved(1, "wuk-silver")
            resolved(2, "wuk-blue-1")
            resolved(3, "wuk-green-1")
            hand("wuk-gold", "wuk-red-1")
        }
        hero("emmitt", Team.BLUE, Hex(-1, -2)) {
            resolved(1, "emmitt-red-1")
            resolved(2, "emmitt-silver")
            resolved(3, "emmitt-gold")
            hand("emmitt-green-1", "emmitt-blue-1")
        }
        token("token_tree", Hex(0, -4), owner = "wuk")
        effect("emmitt", "emmitt-gold", Timing.NEXT_TURN, ActionKind.ATTACK, madeTurn = 3)
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 2)
    }

    override val solution = listOf(
        card("wuk-red-1"),
        card("emmitt-green-1"),
        action(ActionType.PRIMARY),
        space(0, -3),
        action(ActionType.PRIMARY),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry("Tree Slam: at 11 it comes after Tigerclaw's 9 this turn", listOf(card("wuk-gold"))),
        WrongTry(
            "Time Snare: at 8 it comes after Wuk, and nobody brings Tigerclaw to the Tree",
            listOf(card("wuk-red-1"), card("emmitt-blue-1")),
        ),
        WrongTry(
            "Time Walk to the north-east: he ends far from the Tree",
            listOf(card("wuk-red-1"), card("emmitt-green-1"), action(ActionType.PRIMARY), space(4, -7)),
        ),
    )
}
