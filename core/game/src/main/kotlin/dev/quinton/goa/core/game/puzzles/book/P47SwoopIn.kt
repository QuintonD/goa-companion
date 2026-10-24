package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C47 "Swoop In". */
object P47SwoopIn : Puzzle() {
    override val concept = "C47"

    override val info = PuzzleInfo(
        id = "p47",
        number = 47,
        title = "Swoop In",
        difficulty = 3,
        theme = "Retrieve to survive, two threats",
        heroes = listOf("misa", "wasp", "garrus", "brynn"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 4. Misa has played Swoop In (6) and acts next; she holds no card and Challenge " +
            "Accepted is in her discard. Garrus has played Heavy Pilum (5) and Brynn Deadfall Trap (2); each " +
            "keeps one card. Wasp has acted.",
        nudges = listOf(
            "With one card you can survive exactly one of their effects.",
            "Garrus's pilum reaches 2 spaces; Brynn's second option counts every unit and wall next to you.",
            "Swoop to (0,-5) and take back Challenge Accepted.",
        ),
        explanation = "Garrus's Heavy Pilum hits a hero within 2, Brynn's Deadfall Trap one next to her or next to " +
            "3 or more obstacles: each \"discards a card, or is defeated\", which only defeats a hero who has no " +
            "card to discard (p.17 If Able). Staying put, Misa is in Garrus's range with nothing in hand. Swoop " +
            "In places her (p.18 Place) and its retrieve gives her one card back (p.10 Retrieve a Card). One card " +
            "survives one effect, so she must land out of Garrus's reach: (0,-5) or (-1,-3), each beside Brynn " +
            "and a Blue minion. The spaces near Garrus also touch 3 obstacles (units count, p.18 Obstacle), so " +
            "both heroes would hit her and the second one defeats her.",
        rules = listOf("p.10 Retrieve a Card", "p.17 If Able", "p.18 Obstacle, Place"),
    )

    override val playerSeats = setOf("misa")

    override fun PositionBuilder.setUp() {
        round = 4
        turn = 4
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("misa", Team.RED, Hex(1, -5)) {
            played("misa-silver")
            resolved(1, "misa-gold")
            resolved(2, "misa-green-1")
            resolved(3, "misa-blue-1")
            discard("misa-red-1")
            hand()
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            resolved(4, "wasp-green-1")
            hand("wasp-blue-1")
        }
        hero("garrus", Team.BLUE, Hex(3, -6)) {
            level = 5
            played("garrus-green-3b")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-2b")
            hand("garrus-blue-2a")
            items("garrus-red-2a", "garrus-green-2b", "garrus-green-3a", "garrus-blue-2b")
        }
        hero("brynn", Team.BLUE, Hex(-1, -4)) {
            level = 5
            played("brynn-green-3a")
            resolved(1, "brynn-gold")
            resolved(2, "brynn-silver")
            resolved(3, "brynn-red-2a")
            hand("brynn-blue-2a")
            items("brynn-red-2b", "brynn-green-2b", "brynn-green-3b", "brynn-blue-2b")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(0, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -3))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "misa")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(0, -5), card("misa-red-1"))

    override val wrongTries = listOf(
        WrongTry("Swoop next to Garrus: both heroes hit her", listOf(action(ActionType.PRIMARY), space(3, -7))),
        WrongTry("Swoop to (2,-5): in Garrus's range", listOf(action(ActionType.PRIMARY), space(2, -5))),
        WrongTry(
            "Swoop to (0,-5) but keep the discard",
            listOf(action(ActionType.PRIMARY), space(0, -5), decline()),
        ),
        WrongTry("Hold: Garrus's pilum finds her with no card", listOf(action(ActionType.HOLD))),
    )
}
