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
        difficulty = 1,
        theme = "Retrieve to survive, counting obstacles",
        heroes = listOf("misa", "wasp", "garrus", "brynn"),
        goal = PuzzleGoal.Survive(Deadline.END_OF_TURN, "Survive this turn."),
        intro = "Round 4, turn 4. Misa has played Swoop In (6) and acts next; her hand is empty and Challenge " +
            "Accepted is in her discard. Garrus has played Heavy Pilum (5) and Brynn Deadfall Trap (2); each " +
            "keeps one card. Wasp has acted.",
        nudges = listOf(
            "With one card you can survive exactly one of their effects, and every space Swoop In can reach " +
                "is within Garrus's range.",
            "So stay out of Brynn's trap: not next to her, and next to fewer than 3 obstacles. Units on " +
                "both sides count.",
            "Swoop In next to Garrus, on the space west of him, and take back Challenge Accepted.",
        ),
        explanation = "Garrus's Heavy Pilum hits a hero within 2, Brynn's Deadfall Trap one next to her or one in " +
            "radius next to 3 or more obstacles: each \"discards a card, or is defeated\", which only defeats a " +
            "hero with no card to discard (p.17 If Able). Swoop In places Misa (p.18 Place) next to two or " +
            "more enemy units, and its retrieve gives her one card back (p.10 Retrieve a Card): enough for one " +
            "effect. Every space it offers is within 2 of Garrus, so Brynn must be kept out. Units and terrain " +
            "are all obstacles (p.18 Obstacle): the spaces in the corner between the Blue units touch 3 or " +
            "more, and the one beside Brynn is in her other bullet. The space west of Garrus touches only " +
            "Garrus and the Blue minion north-west of it, and Garrus stepping away after his pilum only takes one " +
            "away: Brynn can't take her, Garrus takes the retrieved card, and Misa survives. Swooping in without " +
            "retrieving leaves her nothing to give.",
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
        hero("misa", Team.RED, Hex(4, -7)) {
            level = 2
            played("misa-silver")
            resolved(1, "misa-gold")
            resolved(2, "misa-green-1")
            resolved(3, "misa-blue-2a")
            discard("misa-red-1")
            hand()
            items("misa-blue-2b")
        }
        hero("wasp", Team.RED, Hex(4, -2)) {
            level = 2
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-2a")
            resolved(4, "wasp-blue-1")
            hand("wasp-green-1")
            items("wasp-red-2b")
        }
        hero("garrus", Team.BLUE, Hex(2, -6)) {
            level = 5
            played("garrus-green-3b")
            resolved(1, "garrus-gold")
            resolved(2, "garrus-silver")
            resolved(3, "garrus-red-2b")
            hand("garrus-blue-2a")
            items("garrus-red-2a", "garrus-green-2b", "garrus-green-3a", "garrus-blue-2b")
        }
        hero("brynn", Team.BLUE, Hex(3, -9)) {
            level = 5
            played("brynn-green-3a")
            resolved(1, "brynn-gold")
            resolved(2, "brynn-silver")
            resolved(3, "brynn-red-2a")
            hand("brynn-blue-2a")
            items("brynn-red-2b", "brynn-green-2b", "brynn-green-3b", "brynn-blue-2b")
        }
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -7))
        minion(Team.BLUE, MinionType.MELEE, Hex(4, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -7))
        minion(Team.BLUE, MinionType.HEAVY, Hex(3, -1))
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.RANGED, Hex(0, -3))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        phase = PositionPhase.Resolving(next = "misa")
    }

    override val solution = listOf(action(ActionType.PRIMARY), space(1, -6), card("misa-red-1"))

    override val wrongTries = listOf(
        WrongTry(
            "Swoop in beside Brynn, two obstacles only: she is next to Brynn, and Garrus is in range",
            listOf(action(ActionType.PRIMARY), space(3, -8)),
        ),
        WrongTry(
            "Swoop in east of Garrus, between the Blue units: 3 obstacles for Brynn",
            listOf(action(ActionType.PRIMARY), space(3, -6)),
        ),
        WrongTry(
            "Swoop in west of Garrus but leave Challenge Accepted in the discard",
            listOf(action(ActionType.PRIMARY), space(1, -6), decline()),
        ),
        WrongTry("Hold: Garrus's pilum finds her with no card", listOf(action(ActionType.HOLD))),
    )
}
