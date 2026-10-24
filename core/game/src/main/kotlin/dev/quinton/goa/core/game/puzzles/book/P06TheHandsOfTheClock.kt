package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.hero
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C06 "The Hands of the Clock". */
object P06TheHandsOfTheClock : Puzzle() {
    override val concept = "C06"

    override val info = PuzzleInfo(
        id = "p06",
        number = 6,
        title = "The Hands of the Clock",
        difficulty = 1,
        theme = "Defending with initiative",
        heroes = listOf("emmitt", "sabina", "tigerclaw", "wuk"),
        goal = PuzzleGoal.WinGame("Win the game this turn."),
        intro = "Round 3, turn 4. Emmitt has played Temporal Punch and acts next. Tigerclaw holds Blink " +
            "Strike (initiative 13, Defense 1); Wuk holds Nature's Protector (initiative 7, Defense 7). Red " +
            "has one Life counter left.",
        nudges = listOf(
            "Read what Temporal Punch makes the defender count.",
            "Fast heroes have high initiative; slow ones don't.",
            "Punch Wuk.",
        ),
        explanation = "Temporal Punch makes the defender use the Initiative value of their card and items " +
            "instead of its Defense value; minion defense modifiers still apply (card text, p.14). Tigerclaw's " +
            "Blink Strike has Defense 1 but initiative 13, so against this punch he is the safe one: 13 holds " +
            "against Attack 9. Wuk's Nature's Protector has initiative 7, plus 1 for the friendly heavy minion " +
            "next to him (p.14): 8 is not enough, and with no defense he is defeated as well (p.13). Red's last " +
            "Life counter goes and Blue wins at once.",
        rules = listOf(
            "p.13 Attacking a hero, When a hero is defeated",
            "p.14 Defense total, Minion defense modifiers",
        ),
    )

    override val playerSeats = setOf("emmitt")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 1)
        lifeCounters(Team.BLUE, 3)
        hero("emmitt", Team.BLUE, Hex(0, -1)) {
            played("emmitt-red-1")
            resolved(1, "emmitt-gold")
            resolved(2, "emmitt-blue-1")
            resolved(3, "emmitt-green-1")
            hand("emmitt-silver")
        }
        hero("sabina", Team.BLUE, Hex(3, -5)) {
            resolved(1, "sabina-gold")
            resolved(2, "sabina-silver")
            resolved(3, "sabina-red-1")
            resolved(4, "sabina-green-1")
            hand("sabina-blue-1")
        }
        hero("tigerclaw", Team.RED, Hex(-1, -1)) {
            played("tigerclaw-green-1")
            resolved(1, "tigerclaw-red-1")
            resolved(2, "tigerclaw-blue-1")
            resolved(3, "tigerclaw-silver")
            hand("tigerclaw-gold")
        }
        hero("wuk", Team.RED, Hex(0, 0)) {
            played("wuk-green-1")
            resolved(1, "wuk-gold")
            resolved(2, "wuk-silver")
            resolved(3, "wuk-blue-1")
            hand("wuk-red-1")
        }
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "emmitt")
    }

    override val solution = listOf(action(ActionType.PRIMARY), hero("wuk"))

    override val nudgedMoves = solution

    override val wrongTries = listOf(
        WrongTry(
            "Punch Tigerclaw for his Defense 1: he defends with Blink Strike's initiative 13",
            listOf(action(ActionType.PRIMARY), hero("tigerclaw")),
        ),
    )
}
