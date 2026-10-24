package dev.quinton.goa.core.game.puzzles.book

import dev.quinton.goa.core.game.ActionType
import dev.quinton.goa.core.game.Deadline
import dev.quinton.goa.core.game.PuzzleGoal
import dev.quinton.goa.core.game.PuzzleInfo
import dev.quinton.goa.core.game.position.PositionBuilder
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.puzzles.Moves.action
import dev.quinton.goa.core.game.puzzles.Moves.card
import dev.quinton.goa.core.game.puzzles.Puzzle
import dev.quinton.goa.core.game.puzzles.WrongTry
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team

/** CONCEPTS.md C42 "The Courteous Trap". */
object P42TheCourteousTrap : Puzzle() {
    override val concept = "C42"

    override val info = PuzzleInfo(
        id = "p42",
        number = 42,
        title = "The Courteous Trap",
        difficulty = 3,
        theme = "Card choice, tied initiative",
        heroes = listOf("rowenna", "tigerclaw", "swift", "xargatha"),
        goal = PuzzleGoal.DefeatHero("tigerclaw", Deadline.END_OF_TURN, "Defeat Tigerclaw this turn."),
        intro = "Round 3, turn 4: play your card. Rowenna holds Code of Chivalry (11) and Accept Surrender " +
            "(10). Tigerclaw's only card is Dodge (10), so he will play it and have no cards in hand; Combat " +
            "Reflexes (Defense 3) is in his discard. Swift and Xargatha are far away with one card each. Blue " +
            "shows on the Tie Breaker coin.",
        nudges = listOf(
            "One of your cards needs no attack at all.",
            "Who acts first when both of you show initiative 10?",
            "Play Accept Surrender.",
        ),
        explanation = "Tigerclaw has to play his only card, so he reaches his turn with an empty hand (p.7 Play " +
            "One Card). Accept Surrender is a skill, not an attack: there is nothing to defend (p.12), it simply " +
            "defeats an adjacent enemy hero with no cards in hand (card text). Both cards show 10; heroes of " +
            "different teams tied are separated by the Tie Breaker coin, Blue shows, so Rowenna acts first and " +
            "flips it afterwards (p.7 Tied Initiative). Code of Chivalry (11) is the trap: it is faster, but " +
            "before the attack both heroes may retrieve a discarded card, so Tigerclaw takes back Combat Reflexes " +
            "and defends 3 + 1 (item) + 1 (adjacent Red minion) = 5 against Rowenna's 4 + 1 = 5, and an equal " +
            "Defense total holds (p.14).",
        rules = listOf(
            "p.7 Play One Card, Tied Initiative",
            "p.12 Skill Action",
            "p.14 Defense total, Minion Defense Modifiers",
        ),
    )

    override val playerSeats = setOf("rowenna")

    override fun PositionBuilder.setUp() {
        round = 3
        turn = 4
        tieBreaker = Team.BLUE
        clearMinions()
        lifeCounters(Team.RED, 3)
        lifeCounters(Team.BLUE, 3)
        hero("rowenna", Team.BLUE, Hex(3, -2)) {
            level = 2
            resolved(1, "rowenna-red-1")
            resolved(2, "rowenna-silver")
            resolved(3, "rowenna-green-1")
            hand("rowenna-gold", "rowenna-blue-2b")
            items("rowenna-blue-2a")
        }
        hero("tigerclaw", Team.RED, Hex(3, -3)) {
            level = 2
            resolved(1, "tigerclaw-gold")
            resolved(2, "tigerclaw-green-1")
            resolved(3, "tigerclaw-silver")
            discard("tigerclaw-red-2a")
            hand("tigerclaw-blue-1")
            items("tigerclaw-red-2b")
        }
        hero("swift", Team.BLUE, Hex(-1, 1)) {
            resolved(1, "swift-gold")
            resolved(2, "swift-silver")
            resolved(3, "swift-red-1")
            hand("swift-green-1")
        }
        hero("xargatha", Team.RED, Hex(7, -8)) {
            resolved(1, "xargatha-gold")
            resolved(2, "xargatha-red-1")
            resolved(3, "xargatha-blue-1")
            hand("xargatha-green-1")
        }
        minion(Team.RED, MinionType.MELEE, Hex(2, -3))
        minion(Team.RED, MinionType.MELEE, Hex(0, -6))
        minion(Team.RED, MinionType.HEAVY, Hex(1, 0))
        minion(Team.BLUE, MinionType.MELEE, Hex(2, -1))
        minion(Team.BLUE, MinionType.MELEE, Hex(3, -1))
        minion(Team.BLUE, MinionType.HEAVY, Hex(2, -7))
        phase = PositionPhase.PlayCards(fromSeat = 0)
    }

    override val solution = listOf(card("rowenna-blue-2b"), action(ActionType.PRIMARY))

    override val wrongTries = listOf(
        WrongTry(
            "Code of Chivalry to act first for sure: Tigerclaw retrieves Combat Reflexes and defends 5 against 5",
            listOf(card("rowenna-gold")),
        ),
    )
}
