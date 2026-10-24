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
import dev.quinton.goa.core.game.puzzles.Moves.unitAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C38 "Strip the Silver" (changed: Brogan holds two cards, so Shock's discard is not enough). */
object P38StripTheSilver : Puzzle() {
    override val concept = "C38"

    override val info = PuzzleInfo(
        id = "p38",
        number = 38,
        title = "Strip the Silver",
        difficulty = 2,
        theme = "Active effects, who chooses the discard, same-team order",
        heroes = listOf("wasp", "swift", "dodger", "brogan"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 2, turn 3. Wasp's Shock and Swift's Snipe both have initiative 8: you choose who acts " +
            "first. Brogan (Mad Dash, 7) and Dodger (Dark Ritual, 3) act after them. Brogan's Bolster has been in " +
            "force since turn 1; he holds Onslaught and his silver, Bulwark, and has a Defense item. Dodger holds " +
            "Shield of Decay.",
        nudges = listOf(
            "Something keeps saving Red's last minion. What does it cost Brogan, and what could make him pay?",
            "When Shock's After-the-attack text makes Brogan discard, he picks the card. When he must defend " +
                "an attack, he can only pick a card that is good enough.",
            "Let Swift act first and Snipe Brogan.",
        ),
        explanation = "Bolster is a \"This round\" Active effect that lasts while its card stays resolved (p.17): " +
            "when a Red minion within 2 of Brogan is defeated he may discard a silver card to keep it on the " +
            "board, and Bulwark is his silver. The heavy minion is Red's last minion in the Battle Zone, so it is " +
            "not immune (p.13) and Shock defeats it, but Brogan pays with Bulwark and it stays. Shock's " +
            "After-the-attack text (an enemy hero in radius and not adjacent to Wasp discards) reaches Brogan, but " +
            "he chooses the card (p.10): he gives up Onslaught and keeps Bulwark. So make him pay first. Snipe, " +
            "exactly four spaces in a straight line (p.19), attacks him at 5: Onslaught defends at 3 + 1 (item) = " +
            "4, Bulwark at 4 + 1 = 5, and an equal total defends (p.14). To survive he must discard Bulwark; if " +
            "he doesn't, he is defeated and Bolster ends with him (p.13). Either way Shock then defeats the heavy " +
            "minion for good and the lane is pushed at once (p.8). Wasp and Swift are tied on the same team, so " +
            "you choose who goes first (p.7). The lesson: when an opponent chooses what to discard, attack him " +
            "so that only the card you want gone can save him.",
        rules = listOf(
            "p.7 Tied Initiative",
            "p.8 Triggering a Push",
            "p.10 Discard",
            "p.13 Heavy Minion Immunity, When a hero is defeated",
            "p.14 Defense",
            "p.17 Active Effects",
            "p.19 Straight Line",
        ),
    )

    override val playerSeats = setOf("swift", "wasp")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        hero("wasp", Team.BLUE, Hex(0, -6)) {
            played("wasp-red-1")
            resolved(1, "wasp-silver")
            resolved(2, "wasp-blue-1")
            hand("wasp-gold", "wasp-green-1")
        }
        hero("swift", Team.BLUE, Hex(4, -8)) {
            played("swift-red-1")
            resolved(1, "swift-blue-1")
            resolved(2, "swift-green-1")
            hand("swift-gold", "swift-silver")
        }
        hero("dodger", Team.RED, Hex(1, -6)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            discard("dodger-silver")
            hand("dodger-blue-1")
        }
        hero("brogan", Team.RED, Hex(0, -4)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-green-2a")
            resolved(2, "brogan-blue-1")
            hand("brogan-gold", "brogan-silver")
            items("brogan-green-2b")
        }
        effect("brogan", "brogan-green-2a", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
        minion(Team.RED, MinionType.HEAVY, Hex(-1, -5))
        minion(Team.BLUE, MinionType.MELEE, Hex(5, -8))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    // Snipe's only target (Brogan) is not asked.
    override val solution = listOf(
        hero("swift"),
        action(ActionType.PRIMARY),
        action(ActionType.PRIMARY),
        unitAt(-1, -5),
    )

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry(
            "Wasp first: Brogan saves the heavy with Bulwark, or keeps Bulwark when Shock makes him discard",
            listOf(hero("wasp")),
        ),
        WrongTry(
            "Swift first, but she moves: Brogan keeps his silver",
            listOf(hero("swift"), action(ActionType.MOVEMENT)),
        ),
        WrongTry(
            "Swift snipes Brogan, then Wasp shocks Dodger: the heavy minion stays",
            listOf(hero("swift"), action(ActionType.PRIMARY), action(ActionType.PRIMARY), hero("dodger")),
        ),
    )
}
