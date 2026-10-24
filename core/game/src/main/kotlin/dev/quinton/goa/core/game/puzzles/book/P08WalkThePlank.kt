package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Moves.number
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C08 "Walk the Plank". */
object P08WalkThePlank : Puzzle() {
    override val concept = "C08"

    override val info = PuzzleInfo(
        id = "p08",
        number = 8,
        title = "Walk the Plank",
        difficulty = 1,
        theme = "Pushes across a Zone border",
        heroes = listOf("cutter", "swift", "wasp", "dodger"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 4, turn 4. Cutter has played Walk the Plank and acts next. Wasp and Dodger have " +
            "no cards left in hand. Red has one Life counter left.",
        nudges = listOf(
            "Walk the Plank only bites when the push crosses a Zone border.",
            "The narrow strip along the north-west edge of the middle Zone is a Zone of its own.",
            "Push Wasp at least two spaces.",
        ),
        explanation = "A push moves the target in a straight line directly away from the pusher, as far " +
            "as it can up to the distance chosen; terrain, water included, stops it (p.18 Push, p.19 " +
            "Terrain). Pushed two or more spaces, Wasp ends in the narrow side strip by the water: a " +
            "separate Zone even though it is only a thin strip (p.19 Zone), so Walk the Plank's \"discards " +
            "a card, or is defeated\" applies and, with no card to discard, she is defeated (card text). " +
            "Red spends its last Life counter and Blue wins at once (p.13). Dodger is the tempting target, " +
            "already standing in another Zone, but every push carries her further along that same Zone, " +
            "and one space is not enough for Wasp. Before a push, check where the Zone borders are, not " +
            "just how far the target flies.",
        rules = listOf("p.13 When a hero is defeated", "p.18 Push", "p.19 Zone, Terrain"),
    )

    override val playerSeats = setOf("cutter")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 3)
        hero("cutter", Team.BLUE, Hex(-1, -4)) {
            played("cutter-gold")
            resolved(1, "cutter-red-1")
            resolved(2, "cutter-green-1")
            resolved(3, "cutter-blue-1")
            hand("cutter-silver")
        }
        hero("swift", Team.BLUE, Hex(3, -6)) {
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-red-1")
            resolved(4, "swift-blue-1")
            hand("swift-green-1")
        }
        hero("wasp", Team.RED, Hex(-1, -5)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-red-1")
            resolved(3, "wasp-blue-1")
            discard("wasp-silver")
            hand()
        }
        hero("dodger", Team.RED, Hex(-2, -3)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            resolved(3, "dodger-blue-1")
            discard("dodger-silver")
            hand()
        }
        minion(Team.RED, MinionType.MELEE, Hex(0, -4))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "cutter")
    }

    override val solution = listOf(action(ActionType.PRIMARY), option(0), hero("wasp"), number(2))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Push Dodger: she stays in the Zone she started in",
            listOf(action(ActionType.PRIMARY), option(0), hero("dodger")),
        ),
        WrongTry(
            "Push Wasp one space: she is still in the middle Zone",
            listOf(action(ActionType.PRIMARY), option(0), hero("wasp"), number(1)),
        ),
        WrongTry("Defeat the adjacent minion: two coins, no win", listOf(action(ActionType.PRIMARY), option(1))),
    )
}
