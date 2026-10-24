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
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C38 "Strip the Silver". */
object P38StripTheSilver : Puzzle() {
    override val concept = "C38"

    override val info = PuzzleInfo(
        id = "p38",
        number = 38,
        title = "Strip the Silver",
        difficulty = 2,
        theme = "Active effects, same-team order",
        heroes = listOf("swift", "wasp", "brogan", "dodger"),
        goal = PuzzleGoal.PushLane(Deadline.END_OF_TURN, "Push the lane this turn."),
        intro = "Round 2, turn 3. Wasp's Shock and Swift's Snipe both have initiative 8: you choose who " +
            "acts first. Brogan (Mad Dash, 7) acts after both of them, Dodger (Dark Ritual, 3) last. Brogan's " +
            "Bolster has been in force since turn 1, and the only card in his hand is his silver, Bulwark. " +
            "Dodger holds Shield of Decay.",
        nudges = listOf(
            "Something keeps saving Red's last minion. What does it cost Brogan?",
            "Shock's After the attack text reaches an enemy hero you are not touching.",
            "Let Wasp act first and Shock Dodger: Brogan must drop his silver before Swift shoots.",
        ),
        explanation = "Bolster is a \"This round\" Active effect that lasts while its card stays resolved " +
            "(p.17): when a Red minion near Brogan is defeated he may discard a silver card to keep it on the " +
            "board, and Bulwark is the silver in his hand. So Swift first is the trap: Snipe defeats the minion, " +
            "Brogan pays with Bulwark and the minion stays. Wasp and Swift are tied on the same team, so you " +
            "choose who goes first (p.7). Shock on Dodger, the only enemy next to Wasp, makes an enemy hero in " +
            "radius and not adjacent to her discard after the attack (card text): Brogan, two spaces away, loses " +
            "Bulwark. Now Snipe, exactly four spaces in a straight line, defeats Red's last minion in the Battle " +
            "Zone and the lane is pushed at once (p.8).",
        rules = listOf(
            "p.7 Tied Initiative",
            "p.8 Triggering a Push",
            "p.17 Active Effects, After the Attack",
            "p.19 Straight Line",
        ),
    )

    override val playerSeats = setOf("swift", "wasp")

    override fun PositionBuilder.setUp() {
        round = 2
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        hero("swift", Team.BLUE, Hex(3, -2)) {
            played("swift-red-1")
            resolved(1, "swift-blue-1")
            resolved(2, "swift-green-1")
            hand("swift-gold", "swift-silver")
        }
        hero("wasp", Team.BLUE, Hex(0, -5)) {
            played("wasp-red-1")
            resolved(1, "wasp-silver")
            resolved(2, "wasp-blue-1")
            hand("wasp-gold", "wasp-green-1")
        }
        hero("brogan", Team.RED, Hex(2, -5)) {
            level = 2
            played("brogan-red-1")
            resolved(1, "brogan-green-2a")
            resolved(2, "brogan-gold")
            discard("brogan-blue-1")
            hand("brogan-silver")
            items("brogan-green-2b")
        }
        hero("dodger", Team.RED, Hex(0, -6)) {
            played("dodger-green-1")
            resolved(1, "dodger-gold")
            resolved(2, "dodger-red-1")
            discard("dodger-silver")
            hand("dodger-blue-1")
        }
        effect("brogan", "brogan-green-2a", Timing.THIS_ROUND, ActionKind.SKILL, madeTurn = 1)
        minion(Team.RED, MinionType.MELEE, Hex(3, -6))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    // Shock's only target (Dodger) and Snipe's only target (the minion) are not asked.
    override val solution = listOf(hero("wasp"), action(ActionType.PRIMARY), action(ActionType.PRIMARY))

    override val nudgedMoves = solution.take(2)

    override val wrongTries = listOf(
        WrongTry("Swift first: Brogan spends his silver and the minion stays", listOf(hero("swift"))),
        WrongTry(
            "Wasp first, but she moves: Brogan keeps his silver",
            listOf(hero("wasp"), action(ActionType.MOVEMENT)),
        ),
    )
}
