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
import dev.quinton.goa.core.game.puzzles.Moves.space
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
        difficulty = 2,
        theme = "The defender's best reply",
        heroes = listOf("ursafar", "garrus", "brogan", "wasp"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 5, turn 4. Garrus has played Menace (9) and acts next, then Ursafar, who has played " +
            "Prey Drive (8) and is enraged (Claws That Catch, turn 1). Brogan (level 3, Bullrush in hand) and " +
            "Wasp (level 4, Control Gravity in hand) act after them. Red has 2 Life counters and one minion, " +
            "and one Wave counter is left.",
        nudges = listOf(
            "Red can lose by its Life counters or by its last minion. Find a Red hero who can't save both.",
            "A defender may choose to be defeated, and Prey Drive's bonus needs its target standing: Brogan " +
                "would rather fall. And Wasp is two spaces from Ursafar.",
            "Menace Wasp one space south-west, next to Ursafar, then Prey Drive on Wasp.",
        ),
        explanation = "A defender may always choose not to defend (p.13), and against Brogan that is Red's best " +
            "answer: Bullrush would hold, but at level 3 his lowest card is Tier I, so his defeat costs Red 1 of " +
            "its 2 Life counters (p.13), and a removed target switches off Prey Drive's bonus. Wasp is level 4: " +
            "her lowest card is Tier II, and her defeat spends Red's last 2 counters. She is two spaces away, " +
            "so Garrus goes first: Menace moves an enemy unit in range 1 space farther from him (card text), " +
            "and of her three such spaces only the south-western one is next to Ursafar. Now both answers " +
            "lose for her: defeated, Red is out of counters; standing (Control Gravity holds 5 + 1 against 6, " +
            "p.14), she lets Ursafar remove Red's only " +
            "minion, two spaces from him, and removing the last enemy minion pushes the lane at once (p.8), " +
            "with the last Wave counter making it Blue's win (p.3 Last Push).",
        rules = listOf(
            "p.3 Last Push",
            "p.8 Triggering a Push",
            "p.13 Attacking a Hero, When a Hero is Defeated",
            "p.14 Defense",
            "p.19 Remove",
        ),
    )

    override val playerSeats = setOf("ursafar", "garrus")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        wavesLeft("lane-1", 1)
        hero("ursafar", Team.BLUE, Hex(1, -5)) {
            level = 3
            played("ursafar-red-1")
            resolved(1, "ursafar-gold")
            resolved(2, "ursafar-blue-2b")
            resolved(3, "ursafar-green-2b")
            hand("ursafar-silver")
            items("ursafar-green-2a", "ursafar-blue-2a")
        }
        hero("garrus", Team.BLUE, Hex(3, -8)) {
            level = 3
            played("garrus-blue-1")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-2a")
            resolved(3, "garrus-green-2a")
            hand("garrus-silver")
            items("garrus-red-2b", "garrus-green-2b")
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
        hero("wasp", Team.RED, Hex(3, -7)) {
            level = 4
            played("wasp-green-2a")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            hand("wasp-blue-2a")
            items("wasp-red-2b", "wasp-green-2b", "wasp-blue-2b")
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
        minion(Team.RED, MinionType.MELEE, Hex(-1, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -1))
        phase = PositionPhase.Resolving(next = "garrus")
    }

    // Garrus's only Menace target (Wasp) is not asked; Wasp's best reply is to defend (6 against 6).
    override val solution = listOf(
        action(ActionType.PRIMARY),
        space(2, -6),
        action(ActionType.PRIMARY),
        hero("wasp"),
        unitAt(-1, -5),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Menace Wasp east or south: not next to Ursafar",
            listOf(action(ActionType.PRIMARY), space(3, -6)),
        ),
        WrongTry(
            "Prey Drive on Brogan: he lets himself be defeated, Red keeps a Life counter and its minion",
            listOf(action(ActionType.PRIMARY), space(2, -6), action(ActionType.PRIMARY), hero("brogan")),
        ),
        WrongTry(
            "Wasp defends, and Ursafar leaves the minion",
            listOf(action(ActionType.PRIMARY), space(2, -6), action(ActionType.PRIMARY), hero("wasp"), decline()),
        ),
        WrongTry("Garrus moves instead", listOf(action(ActionType.MOVEMENT))),
    )
}
