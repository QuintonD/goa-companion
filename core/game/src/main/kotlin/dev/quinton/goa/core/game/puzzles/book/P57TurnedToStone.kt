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
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C57 "Turned to Stone". */
object P57TurnedToStone : Puzzle() {
    override val concept = "C57"

    override val info = PuzzleInfo(
        id = "p57",
        number = 57,
        title = "Turned to Stone",
        difficulty = 3,
        theme = "Counts as terrain; ordering a same-team tie",
        heroes = listOf("mrak", "xargatha", "wasp", "dodger"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 4. Mrak (Seismic Slam) and Xargatha (Threatening Slash) both act at 7; you " +
            "choose who goes first. Xargatha's Stone Gaze, resolved in turn 3, is in effect this turn. " +
            "Dodger has no cards left; Wasp keeps Lift Up. Red has 2 Life counters.",
        nudges = listOf(
            "What does Stone Gaze turn Wasp into?",
            "Seismic Slam wants a hero next to terrain, and Wasp has to stay petrified where she is.",
            "Let Mrak act first and Slam Dodger.",
        ),
        explanation = "Stone Gaze makes enemy heroes within 2 of Xargatha count as both heroes and terrain " +
            "(p.17 Counts as; Xargatha FAQ): Wasp stands next to Xargatha, so she is terrain, and Dodger " +
            "stands next to Wasp. Seismic Slam makes an enemy hero next to terrain discard a card or be " +
            "defeated, and Dodger has none. Slamming Wasp only costs her Lift Up. If Xargatha strikes Wasp " +
            "first, Red's best answer is not to defend (p.13): Wasp is defeated, Red spends 1 of its 2 Life " +
            "counters, and Dodger is next to no terrain any more. Moving Xargatha more than 2 spaces from " +
            "Wasp ends it too, since the radius moves with her (p.17 Active Effects). The tie is yours to " +
            "order (p.7): Mrak first is safest; Xargatha first works only if she leaves Wasp petrified.",
        rules = listOf(
            "p.7 Tied Initiative",
            "p.13 Attacking a Hero",
            "p.17 Counts as, Active Effects",
            "p.18 Next Turn",
            "p.19 Terrain",
            "Xargatha FAQ: Stone Gaze",
        ),
    )

    override val playerSeats = setOf("mrak", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("xargatha", Team.BLUE, Hex(2, -5)) {
            played("xargatha-red-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-green-1")
        }
        hero("mrak", Team.BLUE, Hex(2, -8)) {
            played("mrak-red-1")
            resolved(1, "mrak-gold")
            resolved(2, "mrak-silver")
            resolved(3, "mrak-green-1")
            hand("mrak-blue-1")
        }
        hero("wasp", Team.RED, Hex(3, -6)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
        }
        hero("dodger", Team.RED, Hex(4, -7)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-silver")
            resolved(3, "dodger-red-1")
            discard("dodger-blue-1")
            hand()
        }
        // Stone Gaze, resolved in turn 3: "Next turn: Enemy heroes in radius count as both heroes and
        // terrain, and cannot perform movement actions."
        effect("xargatha", "xargatha-blue-1", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 3)
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    override val solution = listOf(hero("mrak"), action(ActionType.PRIMARY), hero("dodger"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Xargatha first, striking Wasp: she lets herself be defeated and the terrain is gone",
            listOf(hero("xargatha"), action(ActionType.PRIMARY)),
        ),
        WrongTry(
            "Xargatha first, walking away: Wasp is no longer in Stone Gaze's radius",
            listOf(hero("xargatha"), action(ActionType.MOVEMENT), space(0, -4)),
        ),
        WrongTry(
            "Slam Wasp: she discards Lift Up and survives",
            listOf(hero("mrak"), action(ActionType.PRIMARY), hero("wasp")),
        ),
    )
}
