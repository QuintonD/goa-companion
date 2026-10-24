package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
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
 * CONCEPTS.md C21 "Charge onto the Spawn Point" (changed: two Red heroes play their cards; Dodger has
 * three empty spawn points around her, so Arien's Noble Blade must fill one with his own minion, which
 * also clears Brogan's run onto another).
 */
object P21ChargeOntoTheSpawnPoint : Puzzle() {
    override val concept = "C21"

    override val info = PuzzleInfo(
        id = "p21",
        number = 21,
        title = "Charge onto the Spawn Point",
        difficulty = 3,
        theme = "Defense from the board: spawn points, mandatory moves",
        heroes = listOf("arien", "brogan", "dodger", "xargatha"),
        goal = PuzzleGoal.DefeatHero("dodger", Deadline.END_OF_TURN, "Defeat Dodger this turn."),
        intro = "Round 3, turn 4: play your cards. Arien holds Noble Blade and Dangerous Current, Brogan Mad " +
            "Dash and Bolster. Dodger has played Dark Ritual (4 with her item) and keeps Shield of Decay (Defense " +
            "3, +2 if there are 2 or more empty spawn points within 2 spaces of her in the Battle Zone). " +
            "Xargatha has played Charm (5).",
        nudges = listOf(
            "Count the empty spawn points around Dodger, and count Brogan's ways to reach her.",
            "One hero cannot fill two spawn points. A unit Arien moves before his attack can fill one, and " +
                "free a path.",
            "Play Noble Blade and Mad Dash. Attack the Blue minion, and before the attack move your own minion " +
                "one space north-west, onto the spawn point there.",
        ),
        explanation = "Shield of Decay is a primary Defense, so its text applies when Dodger defends (p.14), and " +
            "an empty spawn point is one with nothing on it (p.17 Empty). Three of the spawn points within " +
            "two spaces of her are empty: even after Arien's attack has removed the Blue melee minion, she " +
            "defends 3 + 2, +1 for the Blue heavy minion beside her (p.14), = 6 against Mad Dash's 6, and " +
            "equal totals defend. Mad Dash's move is mandatory and exact: two " +
            "spaces in a straight line to a space next to an enemy unit (card text; Brogan FAQ), and the only " +
            "run that reaches her is blocked by Arien's own minion. Noble Blade fixes both: before attacking " +
            "the Blue minion, Arien may move another unit next to it 1 space (card text), so his minion steps " +
            "north-west onto an empty spawn point, out of Brogan's path (the spawn point to its north-east " +
            "would block Brogan's landing). Brogan's run then ends on a second " +
            "spawn point, next to Dodger: one empty spawn point is left, no +2, 4 against 6. Dangerous Current " +
            "is the tempting card: Dodger stands right behind the minion and must discard her only card (card " +
            "text), but Brogan still cannot reach her. Your own minions are pieces you can place, too.",
        rules = listOf(
            "p.11 Movement in the card text",
            "p.14 Defense total, Minion defense modifiers",
            "p.17 Empty",
            "Brogan FAQ: Mad Dash",
        ),
    )

    override val playerSeats = setOf("arien", "brogan")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        // Seated first with their cards already played: the player's choices come last.
        hero("dodger", Team.BLUE, Hex(2, -6)) {
            level = 2
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-2b")
            resolved(3, "dodger-silver")
            hand("dodger-blue-1")
            items("dodger-red-2a")
        }
        hero("xargatha", Team.BLUE, Hex(4, -2)) {
            level = 2
            played("xargatha-green-1")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-silver")
            resolved(3, "xargatha-red-1")
            hand("xargatha-blue-2a")
            items("xargatha-blue-2b")
        }
        hero("arien", Team.RED, Hex(0, -4)) {
            level = 2
            resolved(1, "arien-silver")
            resolved(2, "arien-green-2a")
            resolved(3, "arien-blue-1")
            hand("arien-gold", "arien-red-1")
            items("arien-green-2b")
        }
        hero("brogan", Team.RED, Hex(-1, -4)) {
            level = 2
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-blue-1")
            hand("brogan-red-1", "brogan-green-2a")
            items("brogan-green-2b")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -5))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -5))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.PlayCards(fromSeat = 2)
    }

    override val solution = listOf(
        card("arien-gold"),
        card("brogan-red-1"),
        action(ActionType.PRIMARY),
        yes(),
        unitAt(0, -5),
        space(0, -6),
        action(ActionType.PRIMARY),
        hero("dodger"),
    )

    override val nudgedMoves = solution.take(6)

    private val nobleBlade = listOf(card("arien-gold"), card("brogan-red-1"), action(ActionType.PRIMARY), yes())

    override val wrongTries = listOf(
        WrongTry(
            "Dangerous Current: Dodger discards her card, but Brogan's only run to her is still blocked",
            listOf(card("arien-red-1")),
        ),
        WrongTry(
            "Noble Blade moving Dodger: the spawn points around her stay empty, or Brogan cannot reach her",
            nobleBlade + hero("dodger"),
        ),
        WrongTry(
            "Clear the path but fill nothing: two spawn points stay empty and she defends 6 against 6",
            nobleBlade + listOf(unitAt(0, -5), space(-1, -5)),
        ),
        WrongTry(
            "Brogan defeats the heavy minion instead: the lane is pushed, but Dodger lives",
            nobleBlade + listOf(unitAt(0, -5), space(0, -6), action(ActionType.PRIMARY), unitAt(2, -7)),
        ),
    )
}
