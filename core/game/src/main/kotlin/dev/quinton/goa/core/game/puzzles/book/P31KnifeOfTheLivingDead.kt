package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.heroes.MortimerHero
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.decline
import dev.quinton.goa.core.game.puzzles.Moves.option
import dev.quinton.goa.core.game.puzzles.Moves.space
import dev.quinton.goa.core.game.puzzles.Moves.tokenAt
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C31 "Knife of the Living Dead". */
object P31KnifeOfTheLivingDead : Puzzle() {
    override val concept = "C31"

    override val info = PuzzleInfo(
        id = "p31",
        number = 31,
        title = "Knife of the Living Dead",
        difficulty = 2,
        theme = "Token arithmetic, Before the attack",
        heroes = listOf("mortimer", "sabina", "wasp", "brogan"),
        goal = PuzzleGoal.DefeatHero("wasp", Deadline.END_OF_TURN, "Defeat Wasp this turn."),
        intro = "Round 3, turn 4. Mortimer has played Knife of the Living Dead and acts next. Wasp has " +
            "played Stop Projectiles and keeps Lift Up (Defense 5). Sabina and Brogan have acted.",
        nudges = listOf(
            "Wasp defends at 6 with the minion beside her. Each Zombie you remove next to her adds one Attack.",
            "A Zombie must be next to Wasp at the moment you remove it, and you have three choices to spend.",
            "Knife of the Living Dead: move the far Zombie onto the free space beside Wasp, then remove both Zombies.",
        ),
        explanation = "Mortimer attacks at 5 (4 on the card, +1 from his Attack item); Wasp defends with Lift Up at " +
            "5, +1 for the friendly melee minion beside her (p.14), and an equal total defends. \"Before the " +
            "attack\" text comes first, before her defense (p.14 order), and \"Choose up to three times\" lets " +
            "you take the bullets in any order (p.10 Modal Actions). Removing the one Zombie next to her and " +
            "stopping makes it 6 against 6: she survives. Each removal needs a Zombie adjacent to the target at " +
            "that moment, so spend one choice moving the far Zombie onto the only free space beside her, then " +
            "remove both: Attack 7 against 6. Moving a Zombie twice, or to any other space, leaves you a point short.",
        rules = listOf("p.10 Modal Actions", "p.14 Attack/Defense Detailed Order, Minion Defense Modifiers", "p.19 Tokens"),
    )

    override val playerSeats = setOf("mortimer")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("mortimer", Team.BLUE, Hex(1, -5)) {
            level = 2
            played("mortimer-gold")
            resolved(1, "mortimer-silver")
            resolved(2, "mortimer-red-1")
            resolved(3, "mortimer-blue-1")
            hand("mortimer-green-2b")
            items("mortimer-green-2a")
        }
        hero("sabina", Team.BLUE, Hex(-1, -3)) {
            resolved(1, "sabina-gold")
            resolved(2, "sabina-silver")
            resolved(3, "sabina-red-1")
            resolved(4, "sabina-green-1")
            hand("sabina-blue-1")
        }
        hero("wasp", Team.RED, Hex(2, -5)) {
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            resolved(3, "wasp-red-1")
            hand("wasp-blue-1")
        }
        hero("brogan", Team.RED, Hex(4, -2)) {
            resolved(1, "brogan-gold")
            resolved(2, "brogan-silver")
            resolved(3, "brogan-red-1")
            resolved(4, "brogan-green-1")
            hand("brogan-blue-1")
        }
        token(MortimerHero.ZOMBIE, Hex(2, -6), owner = "mortimer")
        token(MortimerHero.ZOMBIE, Hex(4, -5), owner = "mortimer")
        minion(Team.RED, MinionType.MELEE, Hex(3, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving(next = "mortimer")
    }

    override val solution = listOf(
        action(ActionType.PRIMARY),
        option(0),
        tokenAt(4, -5),
        space(3, -5),
        option(1),
        tokenAt(2, -6),
        option(1),
    )

    override val nudgedMoves = solution.take(4)

    override val wrongTries = listOf(
        WrongTry(
            "Remove the Zombie next to Wasp and stop: 6 against 6, she defends",
            listOf(action(ActionType.PRIMARY), option(1), decline()),
        ),
        WrongTry(
            "Move the far Zombie twice: one choice left, one Attack short",
            listOf(action(ActionType.PRIMARY), option(0), tokenAt(4, -5), space(3, -5), option(0)),
        ),
        WrongTry(
            "Move the far Zombie to a space that is not next to Wasp",
            listOf(action(ActionType.PRIMARY), option(0), tokenAt(4, -5), space(4, -6)),
        ),
    )
}
