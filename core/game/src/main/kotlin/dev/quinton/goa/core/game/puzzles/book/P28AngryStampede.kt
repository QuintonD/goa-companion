package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C28 "Angry Stampede". */
object P28AngryStampede : Puzzle() {
    override val concept = "C28"

    override val info = PuzzleInfo(
        id = "p28",
        number = 28,
        title = "Angry Stampede",
        difficulty = 2,
        theme = "Straight lines through units, the Last Push",
        heroes = listOf("wuk", "garrus", "dodger", "xargatha"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 5, turn 4. Wuk (level 5) has played Angry Stampede and acts next. One Wave counter " +
            "is left. Red's heavy minion has already fallen; Dodger has no cards left in hand. Garrus and " +
            "Xargatha have already acted.",
        nudges = listOf(
            "One Wave counter left, and Red has exactly two minions in the Battle Zone.",
            "Find a single straight line through both of them, with an empty space to stop on.",
            "Stampede four spaces east.",
        ),
        explanation = "Angry Stampede's text works only on a straight move: Wuk may then ignore obstacles " +
            "and pass through units, but must end on an empty space (p.18 Ignoring obstacles, p.19 Straight " +
            "line). It defeats up to two minions he moved through, both at once (card text; Wuk FAQ). The " +
            "line east runs through Red's melee and ranged minions and stops on the empty space behind them. " +
            "Defeating the last enemy minion in the Battle Zone pushes the lane at once (p.8), and with the " +
            "last Wave counter that push wins the game (p.3 Last Push). Trampling Dodger, who has no card, " +
            "costs Red a Life counter but pushes nothing.",
        rules = listOf("p.3 Last Push", "p.8 Triggering a Push", "p.18 Ignoring obstacles", "p.19 Straight line"),
    )

    override val playerSeats = setOf("wuk")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 2)
        wavesLeft("lane-1", 1)
        hero("wuk", Team.BLUE, Hex(0, -2)) {
            level = 5
            played("wuk-red-3b")
            resolved(1, "wuk-gold")
            resolved(2, "wuk-green-2b")
            resolved(3, "wuk-blue-2b")
            hand("wuk-silver")
            items("wuk-red-2b", "wuk-red-3a", "wuk-green-2a", "wuk-blue-2a")
        }
        hero("garrus", Team.BLUE, Hex(-1, -4)) {
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-1")
            resolved(4, "garrus-green-1")
            hand("garrus-blue-1")
        }
        hero("dodger", Team.RED, Hex(-1, -1)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        hero("xargatha", Team.RED, Hex(3, -6)) {
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            resolved(4, "xargatha-green-1")
            hand("xargatha-blue-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(1, -2))
        minion(Team.RED, MinionType.RANGED, Hex(3, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "wuk")
    }

    override val solution = listOf(action(ActionType.PRIMARY), yes(), space(4, -2), unitAt(1, -2), unitAt(3, -2))

    override val wrongTries = listOf(
        WrongTry(
            "Trample Dodger to the south-west: a Life counter, but both minions stay",
            listOf(action(ActionType.PRIMARY), yes(), space(-3, 1)),
        ),
        WrongTry(
            "Stop two spaces east: only the melee minion is passed",
            listOf(action(ActionType.PRIMARY), yes(), space(2, -2)),
        ),
    )
}
