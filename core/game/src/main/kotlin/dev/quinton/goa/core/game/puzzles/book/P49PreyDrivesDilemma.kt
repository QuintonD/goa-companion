package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.engine.ActionKind
import dev.quinton.goa.core.game.engine.Timing
import dev.quinton.goa.core.game.heroes.UrsafarHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C49 "Prey Drive's Dilemma". */
object P49PreyDrivesDilemma : Puzzle() {
    override val concept = "C49"

    override val info = PuzzleInfo(
        id = "p49",
        number = 49,
        title = "Prey Drive's Dilemma",
        difficulty = 3,
        theme = "The defender's best reply",
        heroes = listOf("ursafar", "brogan", "wasp", "garrus"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Ursafar has played Prey Drive and acts next; he is enraged (Claws That " +
            "Catch, turn 1). Brogan (level 3) keeps Bullrush, Wasp (level 4) keeps Control Gravity. Red has " +
            "2 Life counters, and one Wave counter is left.",
        nudges = listOf(
            "Prey Drive's bonus only works if the target is still standing.",
            "A defender may choose to be defeated. Pick a target whose defeat also loses the game.",
            "Attack Wasp with Prey Drive.",
        ),
        explanation = "A defender may always choose not to defend (p.13), and against Brogan that is Red's " +
            "best answer: at level 3 his lowest card is Tier I, so his defeat costs Red 1 of its 2 Life " +
            "counters (p.13), and a removed target switches off Prey Drive's bonus. Wasp is level 4: her " +
            "lowest card is Tier II, so her defeat spends Red's last 2 counters. If she defends instead " +
            "(Defense 5 + 1 item - 1 for the adjacent Blue heavy minion = 5 against 5, enough: p.14), she " +
            "stays, and Ursafar removes Red's only minion, two spaces away. Removing the last enemy minion pushes " +
            "the lane at once (p.8), and the last Wave counter makes it Blue's win (p.3 Last Push).",
        rules = listOf(
            "p.3 Last Push",
            "p.8 Triggering a Push",
            "p.13 Attacking a Hero, When a Hero is Defeated",
            "p.14 Defense",
            "p.19 Remove",
        ),
    )

    override val playerSeats = setOf("ursafar")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        wavesLeft("lane-1", 1)
        hero("ursafar", Team.BLUE, Hex(1, -5)) {
            played("ursafar-red-1")
            resolved(1, "ursafar-gold")
            resolved(2, "ursafar-blue-1")
            resolved(3, "ursafar-green-1")
            hand("ursafar-silver")
        }
        hero("brogan", Team.RED, Hex(0, -4)) {
            level = 3
            played("brogan-green-2b")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-blue-1")
            hand("brogan-red-2a")
            items("brogan-red-2b", "brogan-green-2a")
        }
        hero("wasp", Team.RED, Hex(2, -6)) {
            level = 4
            played("wasp-green-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            hand("wasp-blue-2a")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
        }
        hero("garrus", Team.BLUE, Hex(-1, -1)) {
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-1")
            resolved(4, "garrus-green-1")
            hand("garrus-blue-1")
        }
        // Claws That Catch, turn 1: "This round: You are enraged."
        effect(
            "ursafar",
            "ursafar-gold",
            Timing.THIS_ROUND,
            ActionKind.ATTACK,
            madeTurn = 1,
            key = UrsafarHero.ENRAGED,
        )
        minion(Team.RED, MinionType.MELEE, Hex(3, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "ursafar")
    }

    // Wasp's best reply is to defend (it holds out longest); then remove Red's last minion.
    override val solution = listOf(action(ActionType.PRIMARY), hero("wasp"), unitAt(3, -5))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Prey Drive on Brogan: he lets himself be defeated, Red keeps a Life counter and its minion",
            listOf(action(ActionType.PRIMARY), hero("brogan")),
        ),
        WrongTry(
            "Wasp defends, and Ursafar leaves the minion",
            listOf(action(ActionType.PRIMARY), hero("wasp"), decline()),
        ),
        WrongTry("A Movement action: no attack, no push", listOf(action(ActionType.MOVEMENT))),
    )
}
