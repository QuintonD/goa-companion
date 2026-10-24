package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Moves.yes
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C29 "Threaten the Guards" (changed: an immune heavy minion guards Wasp too, so Wasp herself
 * must be moved twice; Xargatha is Red's idle second hero).
 */
object P29ThreatenTheGuards : Puzzle() {
    override val concept = "C29"

    override val info = PuzzleInfo(
        id = "p29",
        number = 29,
        title = "Threaten the Guards",
        difficulty = 3,
        theme = "Minion defense modifiers, heavy minion immunity, Repeat",
        heroes = listOf("garrus", "brogan", "wasp", "xargatha"),
        goal = PuzzleGoal.DefeatHero("wasp", Deadline.END_OF_TURN, "Defeat Wasp this turn."),
        intro = "Round 3, turn 4. Garrus has played Threaten (10) and acts next; Brogan has played Mad Dash (7) " +
            "and has an Attack item. Wasp has played Stop Projectiles (3) and keeps Kinetic Repulse (Defense 5); " +
            "she has a Defense item. Xargatha has already acted.",
        nudges = listOf(
            "Count Wasp's Defense against Brogan's Attack 7, and count every minion beside her.",
            "Red still has melee minions in the Battle Zone, so its heavy minion is immune: Threaten can't move " +
                "it. Threaten can move Wasp, though, and the repeat may move her again.",
            "Threaten Wasp one space west, then repeat and move her west again.",
        ),
        explanation = "Each adjacent friendly melee or heavy minion gives a defending hero +1 Defense (p.14), so " +
            "Wasp defends 5 + 1 (item) + 3 = 9 against Mad Dash's 6 + 1 = 7. While Red has a melee minion in the " +
            "Battle Zone its heavy minion is immune: no action can move it (p.13), yet it still guards her. Moving " +
            "both melee minions leaves 5 + 1 + 1 = 7 against 7, and equal totals defend (p.14). Threaten may move " +
            "Wasp herself, and the repeat may choose her again (p.10 Repeat), each time to a space farther from " +
            "Garrus (p.17 Farther). West and west again, no minion is left beside her: 6 against 7, and Brogan's " +
            "two-space run south-east ends next to her (p.11). North-east twice also strips every guard, but no " +
            "two-space line of Brogan's ends beside her there; any other second step leaves her next to the heavy " +
            "minion. The lesson: when a guard can't be moved, move the hero it guards.",
        rules = listOf(
            "p.10 Repeat",
            "p.11 Movement in the card text",
            "p.13 Heavy Minion Immunity",
            "p.14 Minion defense modifiers",
            "p.17 Farther",
        ),
    )

    override val playerSeats = setOf("garrus", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("garrus", Team.BLUE, Hex(1, 0)) {
            level = 2
            played("garrus-blue-2a")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-red-1")
            resolved(3, "garrus-silver")
            hand("garrus-green-1")
            items("garrus-blue-2b")
        }
        hero("brogan", Team.BLUE, Hex(0, -3)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-blue-1")
            resolved(3, "brogan-silver")
            hand("brogan-green-2b")
            items("brogan-green-2a")
        }
        hero("wasp", Team.RED, Hex(1, -1)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-2b")
            items("wasp-blue-2a")
        }
        hero("xargatha", Team.RED, Hex(4, -7)) {
            level = 2
            resolved(1, "xargatha-silver")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-green-1")
            resolved(4, "xargatha-gold")
            hand("xargatha-blue-2a")
            items("xargatha-blue-2b")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -1))
        minion(Team.RED, MinionType.MELEE, Hex(0, 0))
        minion(Team.RED, MinionType.HEAVY, Hex(1, -2))
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "garrus")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        hero("wasp"),
        space(0, -1),
        yes(),
        hero("wasp"),
        space(-1, -1),
        action(ActionType.PRIMARY),
        hero("wasp"),
    )

    override val nudgedMoves = solution.take(6)

    override val wrongTries = listOf(
        WrongTry(
            "Threaten a melee guard: with both moved, the immune heavy still guards her, 7 against 7",
            listOf(action(ActionType.PRIMARY), unitAt(2, -1)),
        ),
        WrongTry(
            "Move Wasp north-east: two steps that way strip her guards, but no Mad Dash line reaches her",
            listOf(action(ActionType.PRIMARY), hero("wasp"), space(2, -2)),
        ),
        WrongTry(
            "Move Wasp west, then north-west: she is next to the heavy minion again",
            listOf(action(ActionType.PRIMARY), hero("wasp"), space(0, -1), yes(), hero("wasp"), space(0, -2)),
        ),
        WrongTry(
            "Move Wasp once and stop: the heavy and a melee minion still guard her",
            listOf(action(ActionType.PRIMARY), hero("wasp"), space(0, -1), decline()),
        ),
    )
}
