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

/** CONCEPTS.md C57 "Turned to Stone". */
object P57TurnedToStone : Puzzle() {
    override val concept = "C57"

    override val info = PuzzleInfo(
        id = "p57",
        number = 57,
        title = "Turned to Stone",
        difficulty = 3,
        theme = "Counts as terrain; ordering a same-team tie",
        heroes = listOf("xargatha", "mrak", "tigerclaw", "wasp"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 5, turn 3. Mrak (Seismic Slam, 8 with his Initiative item) and Xargatha (Rapid Thrusts, " +
            "8) are tied; you choose who goes first. Xargatha's Petrifying Stare, resolved in turn 2, is in " +
            "effect this turn. Tigerclaw and Wasp act after them. Tigerclaw keeps Sidestep.",
        nudges = listOf(
            "What does Petrifying Stare turn Wasp into, and what does Seismic Slam need?",
            "Sidestep blocks a ranged attack and then lets Tigerclaw step aside. A petrified hero can't perform " +
                "a movement action, but is that one?",
            "Let Mrak act first and Slam Tigerclaw.",
        ),
        explanation = "Petrifying Stare makes enemy heroes in its radius count as both heroes and terrain (card " +
            "text; p.17 Counts as): Wasp is terrain, and Tigerclaw stands next to her, with no printed terrain " +
            "around him. Seismic Slam makes an enemy hero in radius next to terrain discard a card or be " +
            "defeated. Mrak first: Tigerclaw must throw away Sidestep, his only card, and Xargatha's Rapid " +
            "Thrusts (a ranged attack, range 1 +1 per adjacent enemy unit) then finds him with no defense (p.13). " +
            "Xargatha first is the trap: Sidestep blocks her ranged attack (p.14 Block), and its \"You may move 1 " +
            "space\" is movement in the card text, which a petrified hero may still make (p.11: only movement " +
            "actions are stopped). He steps to the one space beside him that touches no terrain and not Mrak, and " +
            "the Slam has nothing to grip. The tie is yours to order (p.7): the order is the whole puzzle.",
        rules = listOf(
            "p.7 Tied Initiative",
            "p.11 Movement in the Card Text",
            "p.13 Attacking a Hero",
            "p.14 Block",
            "p.17 Counts as",
            "p.18 Next Turn",
            "p.19 Terrain",
        ),
    )

    override val playerSeats = setOf("mrak", "xargatha")

    override fun PositionBuilder.setUp() {
        round = 5
        turn = 3
        tieBreaker = Team.RED
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("xargatha", Team.BLUE, Hex(4, -3)) {
            level = 5
            played("xargatha-red-3b")
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-blue-2a")
            hand("xargatha-silver", "xargatha-green-2b")
            items("xargatha-red-2b", "xargatha-red-3a", "xargatha-green-2a", "xargatha-blue-2b")
        }
        hero("mrak", Team.BLUE, Hex(1, -1)) {
            level = 2
            played("mrak-red-1")
            resolved(1, "mrak-gold")
            resolved(2, "mrak-silver")
            hand("mrak-green-2a", "mrak-blue-1")
            items("mrak-green-2b")
        }
        hero("tigerclaw", Team.RED, Hex(3, -2)) {
            level = 3
            played("tigerclaw-green-2a")
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-red-1")
            discard("tigerclaw-silver")
            hand("tigerclaw-blue-2a")
            items("tigerclaw-green-2b", "tigerclaw-blue-2b")
        }
        hero("wasp", Team.RED, Hex(3, -1)) {
            level = 2
            played("wasp-green-1")
            resolved(1, "wasp-gold")
            resolved(2, "wasp-silver")
            hand("wasp-red-2a", "wasp-blue-1")
            items("wasp-red-2b")
        }
        // Petrifying Stare, resolved in turn 2: "Next turn: Enemy heroes in radius count as both heroes and
        // terrain, and cannot perform movement actions."
        effect("xargatha", "xargatha-blue-2a", Timing.NEXT_TURN, ActionKind.SKILL, madeTurn = 2)
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(1, -4))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.Resolving()
    }

    override val solution = listOf(
        hero("mrak"),
        action(ActionType.PRIMARY),
        hero("tigerclaw"),
        action(ActionType.PRIMARY),
        hero("tigerclaw"),
    )

    override val nudgedMoves = solution.take(3)

    override val wrongTries = listOf(
        WrongTry(
            "Xargatha first: Sidestep blocks her ranged attack and Tigerclaw steps away from the stone",
            listOf(hero("xargatha")),
        ),
        WrongTry(
            "Slam Wasp: she discards a card, and Tigerclaw keeps Sidestep",
            listOf(hero("mrak"), action(ActionType.PRIMARY), hero("wasp")),
        ),
    )
}
