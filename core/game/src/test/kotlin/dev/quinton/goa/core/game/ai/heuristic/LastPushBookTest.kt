package dev.quinton.goa.core.game.ai.heuristic

import com.google.common.truth.Truth.assertWithMessage
import dev.quinton.goa.core.game.AnswerResult
import dev.quinton.goa.core.game.Choice
import dev.quinton.goa.core.game.DecisionKind
import dev.quinton.goa.core.game.TestData
import dev.quinton.goa.core.game.WinReason
import dev.quinton.goa.core.game.engine.EngineSession
import dev.quinton.goa.core.game.position.PositionPhase
import dev.quinton.goa.core.game.position.position
import dev.quinton.goa.core.model.board.Hex
import dev.quinton.goa.core.model.board.MinionType
import dev.quinton.goa.core.model.match.Team
import org.junit.Test

/**
 * Wave 4 Track P: the book's turn 4 on the last wave (RESULTS.md "Wave 4 — Track P"). The strength-10 search plays
 * the book through turn 4 and the round end when a turn-3 question is asked on the last wave (`lpx`), so the
 * book's own turn-4 choice must count minions the way the Minion Battle does.
 *
 * Rules (rulebook p.8, `Flow.minionBattle`/`pushStep`): at the round's end the team with fewer minions in the Battle
 * Zone removes the difference; a team left with none loses the push; when the last Wave counter flips, the team
 * that won that push wins the game. So with a minions against b, a ≥ 2b pushes.
 *
 * The position: forgotten-island's Battle Zone lane-1-2 on its last wave, turn 4, Brogan (Red) the last to act
 * with his Gold card (Attack 4, "Target a unit adjacent to you"), adjacent to a Blue melee minion and to Arien,
 * who holds Blue 1 (Defense 5): a hit on Arien is a discard, not a defeat. Every other hero has acted.
 */
class LastPushBookTest {
    private val brogan = Hex(3, -6)
    private val arien = Hex(2, -6)
    private val target = Hex(4, -6)

    private fun lastWave(red: List<Hex>, blue: List<Hex>): EngineSession = position(TestData.engine, TestData.FOREST) {
        clearMinions()
        turn = 4
        wavesLeft("lane-1", 1)
        hero("brogan", Team.RED, brogan) {
            played("brogan-gold")
            hand("brogan-silver")
        }
        hero("mrak", Team.RED, Hex(-1, 1)) { resolved(4, "mrak-green-1") }
        hero("arien", Team.BLUE, arien) {
            resolved(4, "arien-green-1")
            hand("arien-blue-1", "arien-silver")
        }
        hero("wasp", Team.BLUE, Hex(5, -9)) { resolved(4, "wasp-blue-1") }
        minion(Team.BLUE, MinionType.MELEE, target)
        blue.forEach { minion(Team.BLUE, MinionType.MELEE, it) }
        red.forEach { minion(Team.RED, MinionType.MELEE, it) }
        phase = PositionPhase.Resolving(next = "brogan")
    }

    /** Brogan's Primary on [unit] (the minion or Arien), then the round's end with the first option everywhere. */
    private fun roundEnd(s: EngineSession, unit: String?): EngineSession {
        val f = s.forkSession()
        val round = f.state.round
        var n = 0
        while (f.state.winner == null && f.state.round == round && n++ < 200) {
            val d = f.pending ?: break
            val c = when {
                d.kind == DecisionKind.CHOOSE_ACTION && d.seat == "brogan" ->
                    d.options.first { it is Choice.Action && it.action == dev.quinton.goa.core.game.ActionType.PRIMARY }

                d.kind == DecisionKind.CHOOSE_UNIT && d.seat == "brogan" && unit != null -> Choice.Unit(unit)

                d.kind == DecisionKind.DEFEND -> d.options.first { it == Choice.Card("arien-blue-1") }

                else -> d.options.first()
            }
            check(f.answer(d.id, c) is AnswerResult.Accepted) { "$c for $d" }
        }
        return f
    }

    /** The book at [strength] answers Brogan's questions; the unit his attack targets. */
    private fun bookTarget(s: EngineSession, strength: Int): String? {
        val f = s.forkSession()
        val ai = HeuristicAi(strength, 7L)
        repeat(6) {
            val d = f.pending ?: return null
            if (d.seat != "brogan") return null
            val c = ai.choose(f, d)
            if (d.kind == DecisionKind.CHOOSE_UNIT) return (c as? Choice.Unit)?.unitId
            check(f.answer(d.id, c) is AnswerResult.Accepted)
        }
        return null
    }

    @Test
    fun `behind on the last wave, turn 4 - the attack takes the minion that keeps the push`() {
        // Red 2, Blue 4: as it stands the battle removes both Red minions and Blue wins the last push.
        val s = lastWave(red = listOf(Hex(-1, -1), Hex(-2, 2)), blue = listOf(Hex(5, -8), Hex(2, -1), Hex(3, -1)))
        val minion = s.state.minions.single { it.at == target }.id
        // The rules: hitting Arien loses the game at this round's end; the minion keeps it going.
        val onHero = roundEnd(s, "arien")
        assertWithMessage("hit Arien").that(onHero.state.winner).isEqualTo(Team.BLUE)
        assertWithMessage("hit Arien").that(onHero.state.winReason).isEqualTo(WinReason.LAST_PUSH)
        assertWithMessage("hit the minion").that(roundEnd(s, minion).state.winner).isNull()
        for (strength in listOf(10, 8, 5)) {
            assertWithMessage("h$strength").that(bookTarget(s, strength)).isEqualTo(minion)
        }
    }

    @Test
    fun `ahead on the last wave, turn 4 - the attack takes the minion that wins the push`() {
        // Red 2, Blue 2: the minion leaves Blue one against two, and the battle pushes for Red (2 ≥ 2 × 1).
        val s = lastWave(red = listOf(Hex(-1, -1), Hex(-2, 2)), blue = listOf(Hex(2, -1)))
        val minion = s.state.minions.single { it.at == target }.id
        val won = roundEnd(s, minion)
        assertWithMessage("hit the minion").that(won.state.winner).isEqualTo(Team.RED)
        assertWithMessage("hit Arien").that(roundEnd(s, "arien").state.winner).isNull()
        for (strength in listOf(10, 8, 5)) {
            assertWithMessage("h$strength").that(bookTarget(s, strength)).isEqualTo(minion)
        }
    }
}
