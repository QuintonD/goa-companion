package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.number
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/**
 * CONCEPTS.md C01 "Through the Line" (changed: Red needs two defeats, so Brogan's Mighty Punch must
 * first put Wasp in front of Xargatha, where Wasp's guard is weaker; see the concept's "Encoded as").
 */
object P01ThroughTheLine : Puzzle() {
    override val concept = "C01"

    override val info = PuzzleInfo(
        id = "p01",
        number = 1,
        title = "Through the Line",
        difficulty = 1,
        theme = "Targets and straight lines",
        heroes = listOf("brogan", "arien", "wasp", "xargatha"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Brogan has played Mighty Punch (9) and acts first, then Arien with " +
            "Dangerous Current (8); Wasp and Xargatha act after them. Wasp holds Lift Up; Xargatha has no " +
            "cards in hand. Arien has an Attack item, Wasp a Defense item. All four heroes are level 2. " +
            "Red has two Life counters left.",
        nudges = listOf(
            "Two Life counters, and one Dangerous Current can reach two heroes: its target, and one behind it.",
            "Xargatha already stands in one of Arien's lines. Who could be the target in front of her, and " +
                "what is that hero's defense there?",
            "Brogan steps to the space north-west of Wasp and punches her one space; then Arien attacks Wasp.",
        ),
        explanation = "Each Red hero is level 2, lowest tier I, so each costs one Life counter (p.13): Red " +
            "must lose both. Dangerous Current's \"Before the attack\" reaches the 2 spaces in a straight " +
            "line directly behind its target, whatever stands between: there is no line of sight (p.3, " +
            "p.9). Xargatha stands two spaces behind the space east of Arien, past the Blue minion, so Wasp " +
            "must be the target in that space. Brogan steps north-west of her and pushes her one space " +
            "straight away from him (p.18); two would carry her past Arien. Arien attacks her: first " +
            "Xargatha, with no card to discard, is defeated; then Wasp defends with Lift Up 5, +1 for her " +
            "item, +1 for the Red minion beside her, -1 for the Blue one (p.14): 6 against Arien's 6 + 1, " +
            "and she falls too. Where she stood, two Red minions and the Blue heavy made it 7, and a tie " +
            "defends. Punching a Red minion off her lets Arien defeat her there, but nobody stands behind " +
            "her, and Red keeps a Life counter.",
        rules = listOf(
            "p.3 Line of sight",
            "p.9 Range and Radius",
            "p.13 When a hero is defeated",
            "p.14 Attack and defense, detailed order; Minion defense modifiers",
            "p.18 Push",
        ),
    )

    override val playerSeats = setOf("brogan", "arien")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 2)
        lifeCounters(Team.BLUE, 3)
        hero("brogan", Team.BLUE, Hex(2, -8)) {
            level = 2
            played("brogan-blue-2a")
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-red-1")
            items("brogan-blue-2b")
            hand("brogan-green-1")
        }
        hero("arien", Team.BLUE, Hex(0, -5)) {
            level = 2
            played("arien-red-1")
            resolved(1, "arien-gold")
            resolved(2, "arien-silver")
            resolved(3, "arien-green-1")
            items("arien-blue-2b")
            hand("arien-blue-2a")
        }
        hero("wasp", Team.RED, Hex(1, -6)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            items("wasp-red-2b")
            hand("wasp-blue-1")
        }
        hero("xargatha", Team.RED, Hex(3, -5)) {
            level = 2
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-2a")
            discard("xargatha-blue-1")
            items("xargatha-red-2b")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.MELEE, Hex(2, -6))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "brogan")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        space(1, -7),
        hero("wasp"),
        number(1),
        action(ActionType.PRIMARY),
        hero("wasp"),
        hero("xargatha"),
    )

    override val nudgedMoves = solution.take(5)

    override val wrongTries = listOf(
        WrongTry(
            "Punch the Red minion away from Wasp: she falls, but nobody stands behind her",
            listOf(action(ActionType.PRIMARY), space(1, -7), unitAt(0, -6)),
        ),
        WrongTry(
            "Push Wasp two spaces: she is no longer next to Arien",
            listOf(action(ActionType.PRIMARY), space(1, -7), hero("wasp"), number(2)),
        ),
    )
}
